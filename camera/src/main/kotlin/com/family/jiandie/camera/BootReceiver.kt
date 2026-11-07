package com.family.jiandie.camera

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Optional auto-start hook (Q3=C). STAGE 2: no-op by default — manual start is
 * the primary, always-working path. Stage 3 will consult a user-set "auto-start
 * when armed" preference and, where Android 14+ permits, re-arm transmission.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // TODO Stage 3: if (prefs.autoStartEnabled) TransmissionService.start(context)
    }
}
