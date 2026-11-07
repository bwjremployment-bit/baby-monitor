package com.family.jiandie.camera

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService

/**
 * Foreground service that will own camera + microphone capture and the WebRTC
 * PeerConnection, so transmission survives the app being backgrounded, the
 * screen off, and the phone locked.
 *
 * STAGE 2: establishes the correct foreground-service lifecycle + the mandatory
 * ongoing notification (camera|microphone type). Capture + WebRTC wiring is
 * Stage 3. The notification is SILENT (no sound in the room) but always visible
 * — Android's privacy indicators + this notification are never suppressed.
 */
class TransmissionService : LifecycleService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        ensureChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .setSilent(true)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, notification, type)

        // TODO Stage 3: start camera + mic capture, create PeerConnection,
        //  connect to signalling, begin transmitting.
        return START_STICKY
    }

    private fun ensureChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel),
            NotificationManager.IMPORTANCE_LOW, // low = no sound
        ).apply { setSound(null, null) }
        mgr.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "transmission"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, TransmissionService::class.java)
            context.startForegroundService(intent)
        }
    }
}
