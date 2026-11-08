package com.family.jiandie.camera

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.family.jiandie.camera.data.SecurePrefs

/**
 * Optional auto-start (Q3=C), done as far as Android allows. After a reboot the
 * camera cannot silently resume: services started from boot may not open the
 * camera or microphone. If standby was on before the reboot we post a quiet
 * "tap to resume" notification; tapping it opens the app, which can re-arm.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (SecurePrefs.get(context).armedDesired) NotificationHelper.postRearmPrompt(context)
    }
}
