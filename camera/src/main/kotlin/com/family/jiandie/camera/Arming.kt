package com.family.jiandie.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.family.jiandie.camera.data.SecurePrefs
import com.family.jiandie.camera.service.TransmissionController

/** Permission checks and the single entry point for turning standby on. */
object Arming {
    /** Runtime permissions still missing for standby (camera always; microphone only if sending sound). */
    fun missingPermissions(context: Context): Array<String> {
        val wanted = buildList {
            add(Manifest.permission.CAMERA)
            if (SecurePrefs.get(context).sendAudio) add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return wanted.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }

    /**
     * Starts standby. Call only while the app is visible (Android 11+ will not let a
     * background-started service use the camera or microphone). Returns an error to
     * show the user, or null on success.
     */
    fun start(context: Context): String? {
        TransmissionController.get(context).armError()?.let { return it }
        TransmissionService.start(context)
        return null
    }
}
