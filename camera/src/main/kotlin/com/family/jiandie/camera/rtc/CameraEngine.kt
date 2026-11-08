package com.family.jiandie.camera.rtc

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import com.family.jiandie.camera.data.Quality
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnectionFactory
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSink
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Owns the single camera (and, only while live, the microphone track) and the
 * WebRTC factory. Reference-counted by [Reason]: the on-screen preview and a
 * live session can both want the camera, and capture stops only when nobody
 * does. The microphone is only opened for [Reason.LIVE], never for the preview.
 *
 * All methods must be called on the main thread.
 */
class CameraEngine private constructor(private val app: Context) {

    enum class Reason { PREVIEW, LIVE }

    interface Listener {
        fun onCameraError(message: String)
    }

    var listener: Listener? = null

    val egl: EglBase = EglBase.create()
    val factory: PeerConnectionFactory

    private val main = Handler(Looper.getMainLooper())
    private val reasons = mutableSetOf<Reason>()
    private val sinks = CopyOnWriteArrayList<VideoSink>()
    private val fanout = VideoSink { frame -> for (s in sinks) s.onFrame(frame) }
    private val cameraManager = app.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var capturer: CameraVideoCapturer? = null
    private var helper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private var quality: Quality = Quality.BALANCED
    private var cameraId: String? = null
    private var torchOn = false

    var videoTrack: VideoTrack? = null
        private set
    var audioTrack: AudioTrack? = null
        private set
    var isFront: Boolean = false
        private set
    val isCapturing: Boolean get() = capturer != null

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(app).createInitializationOptions(),
        )
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
    }

    // ---- preview sinks -------------------------------------------------------------

    fun addSink(sink: VideoSink) { sinks.addIfAbsent(sink) }
    fun removeSink(sink: VideoSink) { sinks.remove(sink) }

    // ---- lifecycle -----------------------------------------------------------------

    fun acquire(reason: Reason, q: Quality, preferFront: Boolean) {
        reasons += reason
        if (capturer == null) startVideo(q, preferFront) else if (quality != q) applyQuality(q)
        if (reason == Reason.LIVE && audioTrack == null && wantsAudio) startAudio()
    }

    /** Whether the live session should include a microphone track. Set before acquiring LIVE. */
    var wantsAudio: Boolean = true

    fun release(reason: Reason) {
        reasons -= reason
        if (reason == Reason.LIVE) stopAudio()
        if (reasons.isEmpty()) stopVideo()
    }

    private fun startVideo(q: Quality, preferFront: Boolean) {
        val enumerator = Camera2Enumerator(app)
        val name = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) == preferFront }
            ?: enumerator.deviceNames.firstOrNull()
        if (name == null) { report("No camera found on this device"); return }

        val cap = enumerator.createCapturer(name, object : CameraVideoCapturer.CameraEventsHandler {
            override fun onCameraError(errorDescription: String?) { report("Camera error: $errorDescription") }
            override fun onCameraDisconnected() { report("Camera disconnected (another app may be using it)") }
            override fun onCameraFreezed(errorDescription: String?) { report("Camera froze: $errorDescription") }
            override fun onCameraOpening(cameraName: String?) {}
            override fun onFirstFrameAvailable() {}
            override fun onCameraClosed() {}
        })
        if (cap == null) { report("Could not open the camera"); return }

        val h = SurfaceTextureHelper.create("JiandieCapture", egl.eglBaseContext)
        val src = factory.createVideoSource(false)
        try {
            cap.initialize(h, app, src.capturerObserver)
            cap.startCapture(q.width, q.height, q.fps)
        } catch (e: Exception) {
            runCatching { cap.dispose() }
            h.dispose(); src.dispose()
            report("Could not start the camera: ${e.message}")
            return
        }
        capturer = cap; helper = h; videoSource = src
        quality = q
        cameraId = name
        isFront = enumerator.isFrontFacing(name)
        videoTrack = factory.createVideoTrack("video0", src).also { it.addSink(fanout) }
    }

    private fun stopVideo() {
        setTorch(false)
        videoTrack?.let { it.removeSink(fanout); it.dispose() }
        videoTrack = null
        try { capturer?.stopCapture() } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
        capturer?.dispose(); capturer = null
        helper?.dispose(); helper = null
        videoSource?.dispose(); videoSource = null
        cameraId = null
    }

    private fun startAudio() {
        val src = factory.createAudioSource(MediaConstraints())
        audioSource = src
        audioTrack = factory.createAudioTrack("audio0", src)
    }

    private fun stopAudio() {
        audioTrack?.dispose(); audioTrack = null
        audioSource?.dispose(); audioSource = null
    }

    private fun applyQuality(q: Quality) {
        capturer?.changeCaptureFormat(q.width, q.height, q.fps)
        quality = q
    }

    // ---- user controls -------------------------------------------------------------

    /** Switches front/back. [onDone] receives whether the front camera is now active. */
    fun switchCamera(onDone: (Boolean) -> Unit) {
        val cap = capturer
        if (cap == null) { onDone(isFront); return }
        setTorch(false)
        cap.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(front: Boolean) {
                main.post {
                    isFront = front
                    val e = Camera2Enumerator(app)
                    cameraId = e.deviceNames.firstOrNull { e.isFrontFacing(it) == front } ?: cameraId
                    onDone(front)
                }
            }
            override fun onCameraSwitchError(errorDescription: String?) {
                main.post { report("Camera switch failed: $errorDescription"); onDone(isFront) }
            }
        })
    }

    fun torchSupported(): Boolean = try {
        cameraId?.let { cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } ?: false
    } catch (e: Exception) { false }

    /**
     * Best-effort torch. Some phones refuse the torch while the same camera is
     * streaming; returns false (and reports why) rather than pretending.
     */
    fun setTorch(on: Boolean): Boolean {
        val id = cameraId ?: return false.also { torchOn = false }
        if (!on && !torchOn) return true
        return try {
            cameraManager.setTorchMode(id, on)
            torchOn = on
            true
        } catch (e: Exception) {
            torchOn = false
            if (on) report("Flash is not available right now: ${e.message}")
            false
        }
    }

    val isTorchOn: Boolean get() = torchOn

    private fun report(message: String) {
        main.post { listener?.onCameraError(message) }
    }

    companion object {
        @Volatile private var instance: CameraEngine? = null
        fun get(context: Context): CameraEngine =
            instance ?: synchronized(this) {
                instance ?: CameraEngine(context.applicationContext).also { instance = it }
            }
    }
}
