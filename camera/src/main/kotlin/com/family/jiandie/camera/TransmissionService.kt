package com.family.jiandie.camera

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.family.jiandie.camera.data.SecurePrefs
import com.family.jiandie.camera.service.TransmissionController
import com.family.jiandie.camera.service.TransmissionController.Mode
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps standby (and, when asked, live streaming) alive
 * with the screen off. Its ongoing notification is mandatory and honest: it says
 * whether the camera and microphone are off (standby) or LIVE. It is never hidden.
 *
 * It must be started while the app is visible (see NotificationHelper.postRearmPrompt
 * for why), which is why arming lives in the Radius screen.
 */
class TransmissionService : LifecycleService() {

    private var statusJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val controller = TransmissionController.get(this)
        NotificationHelper.ensureChannels(this)

        when (intent?.action) {
            ACTION_STOP -> {
                controller.disarm(userInitiated = true)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> return handleStart(controller)
            else -> {
                // Null intent = Android restarted us after killing the process. We are now a
                // background start, which cannot open the camera/microphone, so ask the user
                // to re-arm instead of pretending to be on standby.
                if (SecurePrefs.get(this).armedDesired) NotificationHelper.postRearmPrompt(this)
                stopSelf()
                return START_NOT_STICKY
            }
        }
    }

    private fun handleStart(controller: TransmissionController): Int {
        // Android requires startForeground() promptly after startForegroundService(), even if we
        // are about to give up, so promote first and validate second.
        try {
            promoteToForeground(buildNotification(controller.status.value))
        } catch (e: Exception) {
            // Typically a missing camera/microphone permission, or Android refusing a background start.
            controller.reportError("Android would not start standby: ${e.message}. Open the app and try again.")
            stopSelf()
            return START_NOT_STICKY
        }
        controller.armError()?.let {
            controller.reportError(it)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        NotificationHelper.clearRearmPrompt(this)
        controller.arm()

        statusJob?.cancel()
        statusJob = lifecycleScope.launch {
            controller.status.collect { s ->
                if (s.mode == Mode.OFF) return@collect
                getSystemService(NotificationManager::class.java)
                    .notify(NotificationHelper.ID_STATUS, buildNotification(s))
            }
        }
        return START_STICKY
    }

    private fun promoteToForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= 30) {
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            if (SecurePrefs.get(this).sendAudio) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            ServiceCompat.startForeground(this, NotificationHelper.ID_STATUS, n, types)
        } else {
            startForeground(NotificationHelper.ID_STATUS, n)   // Android 10: no camera/mic service types yet
        }
    }

    private fun buildNotification(s: TransmissionController.Status): Notification {
        val audio = SecurePrefs.get(this).sendAudio
        val (title, text) = when {
            s.selfTest == "running" ->
                getString(R.string.notif_selftest_title) to getString(R.string.notif_selftest_text)
            s.mode == Mode.LIVE ->
                getString(R.string.notif_live_title) to
                    getString(if (audio) R.string.notif_live_text_av else R.string.notif_live_text_v)
            s.mode == Mode.STARTING ->
                getString(R.string.notif_starting_title) to getString(R.string.notif_starting_text)
            s.signalingConnected ->
                getString(R.string.notif_standby_title) to getString(R.string.notif_standby_text)
            else ->
                getString(R.string.notif_standby_title) to getString(R.string.notif_standby_offline_text)
        }
        val stop = PendingIntent.getService(
            this, 0, Intent(this, TransmissionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(NotificationHelper.openAppIntent(this))
            .addAction(0, getString(R.string.notif_turn_off), stop)
            .build()
    }

    override fun onDestroy() {
        statusJob?.cancel()
        // Not user-initiated: keep the "re-arm me" hint so a restart can prompt the user.
        TransmissionController.get(this).disarm(userInitiated = false)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.family.jiandie.camera.START"
        const val ACTION_STOP = "com.family.jiandie.camera.STOP"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, TransmissionService::class.java).setAction(ACTION_START))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, TransmissionService::class.java).setAction(ACTION_STOP))
        }
    }
}
