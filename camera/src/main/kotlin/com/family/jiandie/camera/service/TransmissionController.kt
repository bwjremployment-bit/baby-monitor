package com.family.jiandie.camera.service

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import org.webrtc.VideoSink
import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.family.jiandie.camera.data.SecurePrefs
import com.family.jiandie.camera.net.SignalingClient
import com.family.jiandie.camera.rtc.CameraEngine
import com.family.jiandie.camera.rtc.IceConfig
import com.family.jiandie.camera.rtc.PeerSession
import com.family.monitor.shared.pairing.PairingCrypto
import com.family.monitor.shared.pairing.ReplayGuard
import com.family.monitor.shared.signaling.Role
import com.family.monitor.shared.signaling.SignalMessage
import com.family.monitor.shared.state.ConnectionEvent
import com.family.monitor.shared.state.ConnectionState
import com.family.monitor.shared.state.ConnectionStateMachine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.webrtc.PeerConnection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.family.monitor.shared.signaling.IceCandidate as SignalIce

/**
 * The transmitter's brain. Lives for the whole process; the foreground service
 * keeps the process alive while armed. All calls must be on the main thread.
 *
 *   OFF ──arm──► ARMED ──authenticated start-request──► STARTING ──► LIVE
 *                  ▲                                                   │
 *                  └────────── viewer gone / stop / camera problem ◄───┘
 *
 * While ARMED the camera and microphone are OFF: only the signalling socket is
 * open. Capture starts only after the camera has verified the viewer's signed,
 * single-use start-request (see PairingCrypto).
 */
class TransmissionController private constructor(private val app: Context) {

    enum class Mode { OFF, ARMED, STARTING, LIVE }

    data class Status(
        val mode: Mode = Mode.OFF,
        val signalingConnected: Boolean = false,
        val viewerPresent: Boolean = false,
        val networkAvailable: Boolean = true,
        val connection: ConnectionState = ConnectionState.DISCONNECTED,
        val path: String? = null,
        val lastError: String? = null,
        val startsAccepted: Int = 0,
        val startsRejected: Int = 0,
        /** null, "waiting" or "running" — the background self-test (see [runSelfTest]). */
        val selfTest: String? = null,
    )

    private val prefs = SecurePrefs.get(app)
    private val main = Handler(Looper.getMainLooper())
    private val engine: CameraEngine by lazy {
        CameraEngine.get(app).also { it.listener = cameraListener }
    }

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()
    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private var machine = ConnectionStateMachine()
    private var signaling: SignalingClient? = null
    private var session: PeerSession? = null
    private var answerAccepted = false
    private var peerConnected = false
    private var liveAcquired = false
    private val replayGuard = ReplayGuard()
    private var grace: Runnable? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private var networkSeen = false

    // ---- public API ------------------------------------------------------------------

    /** Why the camera cannot be armed right now, or null if it can. */
    fun armError(): String? =
        if (!SecurePrefs.isValidSignalingUrl(prefs.signalingUrl))
            "Enter the server address (it starts with wss://) under Server first."
        else null

    fun reportError(message: String) {
        log(message)
        publish { copy(lastError = message) }
    }

    /** Turn on standby. Call from the foreground service, after startForeground. */
    fun arm(): String? {
        armError()?.let { return it }
        if (_status.value.mode != Mode.OFF) return null
        val pairing = prefs.ensurePairing()
        machine = ConnectionStateMachine()
        startLocks()
        registerNetwork()
        signaling = SignalingClient(prefs.signalingUrl, pairing.room, Role.CAMERA, signalingListener).also { it.start() }
        machine.on(ConnectionEvent.CONNECT_SIGNALING)
        prefs.armedDesired = true
        publish { Status(mode = Mode.ARMED, networkAvailable = networkAvailableNow()) }
        log("Standby on — camera and microphone are off until your viewer connects.")
        return null
    }

    /** Turn standby off. [userInitiated] false keeps the "re-arm me" hint (e.g. service killed). */
    fun disarm(userInitiated: Boolean = true) {
        if (_status.value.mode == Mode.OFF) return
        stopLive("camera turned off", tellViewer = true)
        signaling?.stop(); signaling = null
        unregisterNetwork()
        stopLocks()
        if (userInitiated) prefs.armedDesired = false
        machine.on(ConnectionEvent.DISCONNECT)
        publish { Status(connection = machine.state, lastError = lastError) }
        log("Standby off.")
    }

    /**
     * Background self-test. Answers the design's biggest question on a real phone, with no
     * viewer or server: after [delaySeconds] (lock the screen meanwhile) the standby service
     * briefly opens the camera and microphone and reports whether Android allowed it.
     * It is announced in the notification and the log; it is never hidden.
     */
    fun runSelfTest(delaySeconds: Int = 30) {
        if (_status.value.mode != Mode.ARMED || _status.value.selfTest != null) {
            log("Self-test: turn on standby first (and wait for any earlier test to finish).")
            return
        }
        publish { copy(selfTest = "waiting") }
        log("Self-test starts in $delaySeconds s. Lock the screen now, then unlock after about ${delaySeconds + 10} s.")
        main.postDelayed({ doSelfTest() }, delaySeconds * 1000L)
    }

    private fun doSelfTest() {
        if (_status.value.mode != Mode.ARMED) { publish { copy(selfTest = null) }; return }
        publish { copy(selfTest = "running") }
        val frames = java.util.concurrent.atomic.AtomicInteger()   // written on the camera thread
        val counter = VideoSink { frames.incrementAndGet() }
        engine.addSink(counter)
        engine.acquire(CameraEngine.Reason.TEST, prefs.quality, prefs.preferFrontCamera)
        val mic = java.util.concurrent.atomic.AtomicReference(if (prefs.sendAudio) "…" else "skipped (sound is switched off)")
        if (prefs.sendAudio) Thread { mic.set(probeMic()) }.start()
        main.postDelayed({
            engine.removeSink(counter)
            engine.release(CameraEngine.Reason.TEST)
            val n = frames.get()
            val cam = if (n > 0) "OK ($n frames in 4 s)" else "FAILED — no frames, Android is probably blocking the camera"
            log("Self-test result — camera: $cam; microphone: ${mic.get()}")
            publish { copy(selfTest = null) }
        }, 4000)
    }

    /** Listens for ~1.5 s. Android 11+ silently delivers all-zero audio to a blocked background app. */
    @SuppressLint("MissingPermission")
    private fun probeMic(): String {
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            return "no permission"
        val rate = 16000
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return "unavailable"
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 2)
        } catch (e: Exception) { return "error: ${e.message}" }
        return try {
            if (rec.state != AudioRecord.STATE_INITIALIZED) return "could not start"
            rec.startRecording()
            val buf = ShortArray(rate / 2)
            var total = 0; var peak = 0
            val end = SystemClock.elapsedRealtime() + 1500
            while (SystemClock.elapsedRealtime() < end) {
                val n = rec.read(buf, 0, buf.size)
                if (n < 0) break
                total += n
                for (i in 0 until n) peak = maxOf(peak, kotlin.math.abs(buf[i].toInt()))
            }
            when {
                total == 0 -> "FAILED — no samples"
                peak == 0 -> "SILENT — Android is blocking the microphone in the background"
                else -> "OK (loudest sample $peak)"
            }
        } finally { rec.release() }
    }

    /** Manual "stop streaming now": back to standby, camera and microphone off. */
    fun stopStreaming() = stopLive("stopped on the camera", tellViewer = true)

    // ---- signalling ------------------------------------------------------------------

    private val signalingListener = object : SignalingClient.Listener {
        override fun onConnection(connected: Boolean, detail: String?) {
            if (connected) {
                if (machine.state == ConnectionState.FAILED || machine.state == ConnectionState.AUTH_FAILED) {
                    machine.on(ConnectionEvent.RETRY)
                }
                if (machine.state == ConnectionState.SIGNALING_CONNECTING) machine.on(ConnectionEvent.SIGNALING_OPEN)
                log("Connected to the server.")
            } else {
                // Media is peer-to-peer and survives a signalling blip, so only react while idle.
                if (machine.state == ConnectionState.SIGNALING_CONNECTING ||
                    machine.state == ConnectionState.SIGNALING_READY
                ) machine.on(ConnectionEvent.NETWORK_LOST)
                log("Server connection lost (${detail ?: "unknown"}) — retrying.")
            }
            publish { copy(signalingConnected = connected, viewerPresent = if (connected) viewerPresent else false) }
        }

        override fun onMessage(message: SignalMessage) {
            when (message) {
                is SignalMessage.Joined ->
                    publish { copy(viewerPresent = Role.VIEWER in message.peers) }
                is SignalMessage.PeerEvent -> if (message.role == Role.VIEWER) {
                    publish { copy(viewerPresent = message.present) }
                    if (!message.present && session != null && !peerConnected) startGrace()
                }
                is SignalMessage.StartRequest -> onStartRequest(message)
                is SignalMessage.Answer -> onAnswer(message)
                is SignalMessage.Ice -> if (answerAccepted) {
                    session?.onRemoteIce(message.candidate.sdpMid, message.candidate.sdpMLineIndex, message.candidate.candidate)
                }
                is SignalMessage.Bye -> if (session != null) stopLive("viewer ended the session", tellViewer = false)
                else -> Unit
            }
        }
    }

    private fun onStartRequest(m: SignalMessage.StartRequest) {
        val pairing = prefs.pairing ?: return
        if (_status.value.mode == Mode.OFF) return
        val now = System.currentTimeMillis()
        // 1) Must be signed with the pairing secret. 2) Must never have been seen before.
        // Order matters: only authentic requests may occupy the replay cache.
        if (!PairingCrypto.verifyStart(m.auth, pairing.secret, pairing.room, m.nonce, now)) {
            publish { copy(startsRejected = startsRejected + 1) }
            log("Ignored a start request: not signed by your pairing secret.")
            return
        }
        if (!replayGuard.firstUse(m.nonce, now)) {
            publish { copy(startsRejected = startsRejected + 1) }
            log("Ignored a start request: it was a replay.")
            return
        }
        publish { copy(startsAccepted = startsAccepted + 1) }
        log("Start request accepted from your viewer.")
        beginLive()
    }

    private fun onAnswer(m: SignalMessage.Answer) {
        val pairing = prefs.pairing ?: return
        val s = session ?: return
        if (answerAccepted) return
        if (!PairingCrypto.verifySdp(m.auth, pairing.secret, pairing.room, Role.VIEWER, m.sdp, System.currentTimeMillis())) {
            log("Rejected an answer that was not signed by your viewer — stopping.")
            publish { copy(startsRejected = startsRejected + 1) }
            stopLive("unauthenticated answer", tellViewer = false)
            return
        }
        answerAccepted = true
        s.onRemoteAnswer(m.sdp)
        log("Viewer answer verified.")
    }

    // ---- live session ----------------------------------------------------------------

    private fun beginLive() {
        // A new valid request while a session exists means the viewer is reconnecting.
        session?.close(); session = null
        answerAccepted = false; peerConnected = false
        cancelGrace()

        if (!liveAcquired) {
            engine.wantsAudio = prefs.sendAudio
            engine.acquire(CameraEngine.Reason.LIVE, prefs.quality, prefs.preferFrontCamera)
            liveAcquired = true
        }
        if (engine.videoTrack == null) {
            fail("Could not start the camera.")
            return
        }
        if (machine.state != ConnectionState.SIGNALING_READY) machine.on(ConnectionEvent.PEER_LEFT)
        machine.on(ConnectionEvent.PEER_FOUND)
        publish { copy(mode = Mode.STARTING, path = null) }

        val s = PeerSession(engine, IceConfig.servers(prefs), prefs.quality, prefs.sendAudio, sessionCallbacks)
        session = s
        s.start()
    }

    private val sessionCallbacks = object : PeerSession.Callbacks {
        override fun onOffer(sdp: String) {
            val pairing = prefs.pairing ?: return
            val auth = PairingCrypto.signSdp(pairing.secret, pairing.room, Role.CAMERA, sdp, System.currentTimeMillis())
            signaling?.send(SignalMessage.Offer(sdp, auth))
            log("Offer sent.")
        }

        override fun onLocalIce(sdpMid: String?, sdpMLineIndex: Int, candidate: String) {
            signaling?.send(SignalMessage.Ice(SignalIce(sdpMid, sdpMLineIndex, candidate)))
        }

        override fun onConnectionState(state: PeerConnection.PeerConnectionState) {
            when (state) {
                PeerConnection.PeerConnectionState.CONNECTED -> {
                    cancelGrace()
                    peerConnected = true
                    machine.on(ConnectionEvent.PEER_CONNECTED)
                    publish { copy(mode = Mode.LIVE) }
                    log("Viewer connected — LIVE.")
                    main.postDelayed({
                        session?.describePath { p -> log("Connection path: $p"); publish { copy(path = p) } }
                    }, 3000)
                }
                PeerConnection.PeerConnectionState.DISCONNECTED,
                PeerConnection.PeerConnectionState.FAILED -> {
                    peerConnected = false
                    log("Connection ${state.name.lowercase()}.")
                    machine.on(ConnectionEvent.PEER_DROPPED)
                    publish { this }
                    startGrace()
                }
                else -> Unit
            }
        }

        override fun onError(message: String) = fail(message)
    }

    private fun fail(message: String) {
        log(message)
        publish { copy(lastError = message) }
        stopLive(message, tellViewer = true)
    }

    private fun stopLive(reason: String, tellViewer: Boolean) {
        cancelGrace()
        val wasActive = session != null || liveAcquired
        session?.close(); session = null
        answerAccepted = false; peerConnected = false
        if (liveAcquired) { engine.release(CameraEngine.Reason.LIVE); liveAcquired = false }
        if (!wasActive) return
        if (tellViewer) signaling?.send(SignalMessage.Bye(reason))
        log("Streaming stopped: $reason.")
        machine.on(ConnectionEvent.PEER_LEFT)
        publish { copy(mode = if (mode == Mode.OFF) Mode.OFF else Mode.ARMED, path = null) }
    }

    /** If the viewer does not come back within a minute, stop streaming to save battery and privacy. */
    private fun startGrace() {
        cancelGrace()
        val r = Runnable { stopLive("viewer went away", tellViewer = false) }
        grace = r
        main.postDelayed(r, GRACE_MS)
    }

    private fun cancelGrace() {
        grace?.let { main.removeCallbacks(it) }
        grace = null
    }

    private val cameraListener = object : CameraEngine.Listener {
        override fun onCameraError(message: String) {
            log(message)
            publish { copy(lastError = message) }
            if (session != null || liveAcquired) stopLive("camera problem", tellViewer = true)
        }
    }

    // ---- environment: network + power ------------------------------------------------

    private fun registerNetwork() {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        networkSeen = false
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { main.post { onNetworkAvailable() } }
            override fun onLost(network: Network) { main.post { onNetworkLost() } }
        }
        netCallback = cb
        cm.registerDefaultNetworkCallback(cb)
    }

    private fun unregisterNetwork() {
        netCallback?.let { runCatching { app.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } }
        netCallback = null
    }

    private fun networkAvailableNow(): Boolean =
        app.getSystemService(ConnectivityManager::class.java).activeNetwork != null

    private fun onNetworkAvailable() {
        publish { copy(networkAvailable = true) }
        // The first callback just reports the current network; later ones mean it changed.
        if (networkSeen) { log("Network changed — reconnecting."); signaling?.reconnectNow() }
        networkSeen = true
    }

    private fun onNetworkLost() {
        publish { copy(networkAvailable = false) }
        log("Network lost.")
    }

    @Suppress("DEPRECATION")
    private fun startLocks() {
        if (!prefs.keepAwake) return
        val pm = app.getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jiandie:standby").apply {
            setReferenceCounted(false); acquire()
        }
        val wm = app.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "jiandie:standby").apply {
            setReferenceCounted(false); acquire()
        }
    }

    private fun stopLocks() {
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
        wifiLock?.let { if (it.isHeld) it.release() }; wifiLock = null
    }

    // ---- helpers ---------------------------------------------------------------------

    private val lastError: String? get() = _status.value.lastError

    private fun publish(change: Status.() -> Status) {
        _status.update { it.change().copy(connection = machine.state) }
    }

    private fun log(line: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _log.update { (it + "$ts  $line").takeLast(150) }
    }

    companion object {
        private const val GRACE_MS = 60_000L

        @Volatile private var instance: TransmissionController? = null
        fun get(context: Context): TransmissionController =
            instance ?: synchronized(this) {
                instance ?: TransmissionController(context.applicationContext).also { instance = it }
            }
    }
}
