package com.family.jiandie.camera.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.family.monitor.shared.pairing.PairingPayload

/** Capture/encode presets. BALANCED is the default (Q5=C). */
enum class Quality(val label: String, val width: Int, val height: Int, val fps: Int, val maxKbps: Int) {
    LOW("Low — saves battery and data", 480, 360, 15, 250),
    BALANCED("Balanced", 640, 480, 20, 500),
    HIGH("High — sharper, uses more data", 1280, 720, 24, 1200),
}

/**
 * All persistent settings, including the pairing secret, in
 * EncryptedSharedPreferences backed by the Android Keystore. Nothing sensitive
 * is ever written in plaintext, and there is no plaintext fallback.
 */
class SecurePrefs private constructor(private val prefs: SharedPreferences) {

    var pairingSecret: String?
        get() = prefs.getString(K_SECRET, null)
        private set(v) = prefs.edit().apply { if (v == null) remove(K_SECRET) else putString(K_SECRET, v) }.apply()

    val pairing: PairingPayload?
        get() = pairingSecret?.let { PairingPayload(PairingPayload.VERSION, it) }

    /** Returns the existing pairing, creating one on first use. */
    fun ensurePairing(): PairingPayload = pairing ?: rotatePairing()

    /** New secret: revokes every previously paired viewer (and moves the signalling room). */
    fun rotatePairing(): PairingPayload {
        val p = PairingPayload.generate()
        pairingSecret = p.secret
        return p
    }

    var signalingUrl: String
        get() = prefs.getString(K_URL, "") ?: ""
        set(v) = prefs.edit().putString(K_URL, v.trim()).apply()

    var quality: Quality
        get() = runCatching { Quality.valueOf(prefs.getString(K_QUALITY, null) ?: "") }.getOrDefault(Quality.BALANCED)
        set(v) = prefs.edit().putString(K_QUALITY, v.name).apply()

    /** Send microphone audio with the video. Applies the next time the camera is armed. */
    var sendAudio: Boolean
        get() = prefs.getBoolean(K_AUDIO, true)
        set(v) = prefs.edit().putBoolean(K_AUDIO, v).apply()

    /** Hold CPU + Wi-Fi awake while on standby (recommended when charging). */
    var keepAwake: Boolean
        get() = prefs.getBoolean(K_AWAKE, true)
        set(v) = prefs.edit().putBoolean(K_AWAKE, v).apply()

    var preferFrontCamera: Boolean
        get() = prefs.getBoolean(K_FRONT, false)
        set(v) = prefs.edit().putBoolean(K_FRONT, v).apply()

    /** True while the user wants standby on; used to offer a re-arm prompt after a reboot or kill. */
    var armedDesired: Boolean
        get() = prefs.getBoolean(K_ARMED, false)
        set(v) = prefs.edit().putBoolean(K_ARMED, v).apply()

    var turnUrl: String
        get() = prefs.getString(K_TURN_URL, "") ?: ""
        set(v) = prefs.edit().putString(K_TURN_URL, v.trim()).apply()
    var turnUser: String
        get() = prefs.getString(K_TURN_USER, "") ?: ""
        set(v) = prefs.edit().putString(K_TURN_USER, v.trim()).apply()
    var turnPass: String
        get() = prefs.getString(K_TURN_PASS, "") ?: ""
        set(v) = prefs.edit().putString(K_TURN_PASS, v).apply()

    companion object {
        private const val FILE = "jiandie_secure"
        private const val K_SECRET = "pairing_secret"
        private const val K_URL = "signaling_url"
        private const val K_QUALITY = "quality"
        private const val K_AUDIO = "send_audio"
        private const val K_AWAKE = "keep_awake"
        private const val K_FRONT = "prefer_front"
        private const val K_ARMED = "armed_desired"
        private const val K_TURN_URL = "turn_url"
        private const val K_TURN_USER = "turn_user"
        private const val K_TURN_PASS = "turn_pass"

        @Volatile private var instance: SecurePrefs? = null

        fun get(context: Context): SecurePrefs =
            instance ?: synchronized(this) { instance ?: create(context.applicationContext).also { instance = it } }

        private fun create(app: Context): SecurePrefs = try {
            SecurePrefs(open(app))
        } catch (e: Exception) {
            // Keystore/prefs unreadable (e.g. device restored or keys invalidated): start clean.
            // The user simply re-pairs. We never fall back to plaintext storage.
            app.deleteSharedPreferences(FILE)
            SecurePrefs(open(app))
        }

        private fun open(app: Context): SharedPreferences {
            val key = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            return EncryptedSharedPreferences.create(
                app, FILE, key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        /** Signalling must be TLS: cleartext ws:// is rejected. */
        fun isValidSignalingUrl(url: String): Boolean {
            val u = url.trim()
            return u.startsWith("wss://") && u.length > "wss://".length + 3 && !u.contains(' ')
        }
    }
}
