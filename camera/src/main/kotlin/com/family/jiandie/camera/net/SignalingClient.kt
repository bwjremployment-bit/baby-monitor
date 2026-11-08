package com.family.jiandie.camera.net

import android.os.Handler
import android.os.Looper
import com.family.monitor.shared.signaling.Role
import com.family.monitor.shared.signaling.SignalCodec
import com.family.monitor.shared.signaling.SignalMessage
import kotlinx.serialization.SerializationException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * WebSocket transport to the signalling relay using the shared protocol.
 * Keeps itself connected: reconnects with exponential backoff + jitter, and
 * immediately on [reconnectNow] (e.g. when the network changes). Every callback
 * is delivered on the main thread, so callers need no extra synchronisation.
 */
class SignalingClient(
    private val url: String,
    private val room: String,
    private val role: Role,
    private val listener: Listener,
) {
    interface Listener {
        /** [connected] = socket open AND joined to the room. */
        fun onConnection(connected: Boolean, detail: String?)
        fun onMessage(message: SignalMessage)
    }

    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)   // keeps NAT/LTE mappings alive while on standby
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null
    private var generation = 0
    private var running = false
    private var attempt = 0
    var isJoined = false
        private set

    private val reconnect = Runnable { if (running) open() }

    fun start() {
        if (running) return
        running = true
        attempt = 0
        open()
    }

    /** Drop the current socket (if any) and reconnect right away. */
    fun reconnectNow() {
        if (!running) return
        main.removeCallbacks(reconnect)
        attempt = 0
        open()
    }

    fun stop() {
        running = false
        main.removeCallbacks(reconnect)
        isJoined = false
        closeSocket(graceful = true)
    }

    fun send(message: SignalMessage): Boolean = socket?.send(SignalCodec.encode(message)) ?: false

    private fun open() {
        closeSocket(graceful = false)
        val g = ++generation
        val request = try {
            Request.Builder().url(url).build()
        } catch (e: IllegalArgumentException) {
            listener.onConnection(false, "Invalid server address")
            return
        }
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                main.post { if (g == generation) webSocket.send(SignalCodec.encode(SignalMessage.Join(room, role))) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                main.post { if (g == generation) handle(text) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                main.post { if (g == generation) lost("closed ($code)") }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                main.post { if (g == generation) lost(t.message ?: "connection failed") }
            }
        })
    }

    private fun handle(text: String) {
        val msg = try {
            SignalCodec.decode(text)
        } catch (e: SerializationException) {
            return
        } catch (e: IllegalArgumentException) {
            return
        }
        when (msg) {
            is SignalMessage.Joined -> {
                attempt = 0
                isJoined = true
                listener.onConnection(true, null)
                listener.onMessage(msg)
            }
            is SignalMessage.AuthError -> lost("rejected by server: ${msg.reason}")
            else -> listener.onMessage(msg)
        }
    }

    private fun lost(detail: String) {
        val wasJoined = isJoined
        isJoined = false
        closeSocket(graceful = false)
        if (wasJoined || attempt == 0) listener.onConnection(false, detail)
        if (!running) return
        val delayMs = (1000L shl minOf(attempt, 5)).coerceAtMost(30_000L) + Random.nextLong(0, 500)
        attempt++
        main.postDelayed(reconnect, delayMs)
    }

    private fun closeSocket(graceful: Boolean) {
        generation++            // any late callback from the old socket is ignored
        val s = socket
        socket = null
        if (graceful) s?.close(1000, "bye") else s?.cancel()
    }
}
