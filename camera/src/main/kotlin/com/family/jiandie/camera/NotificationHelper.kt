package com.family.jiandie.camera

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Notification plumbing. Everything here is silent: no sound or vibration in the baby's room. */
object NotificationHelper {
    const val CHANNEL_STATUS = "status"
    const val CHANNEL_PROMPT = "prompt"
    const val ID_STATUS = 1001
    const val ID_REARM = 1002
    const val EXTRA_REARM = "com.family.jiandie.camera.REARM"

    fun ensureChannels(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, context.getString(R.string.notif_channel_status), NotificationManager.IMPORTANCE_LOW)
                .apply { setSound(null, null); enableVibration(false); setShowBadge(false) },
        )
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_PROMPT, context.getString(R.string.notif_channel_prompt), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { setSound(null, null); enableVibration(false) },
        )
    }

    fun openAppIntent(context: Context, rearm: Boolean = false): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_REARM, rearm)
        return PendingIntent.getActivity(
            context, if (rearm) 1 else 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * After a reboot or if Android killed the standby service, the camera cannot
     * simply restart itself: since Android 11 a service started in the background
     * may not open the camera or microphone. A tap on this notification brings the
     * app to the foreground, which is allowed to re-arm.
     */
    fun postRearmPrompt(context: Context) {
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_PROMPT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.notif_rearm_title))
            .setContentText(context.getString(R.string.notif_rearm_text))
            .setContentIntent(openAppIntent(context, rearm = true))
            .setAutoCancel(true)
            .setSilent(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_REARM, n) }
    }

    fun clearRearmPrompt(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_REARM)
    }
}
