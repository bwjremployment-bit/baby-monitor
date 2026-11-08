package com.family.jiandie.camera

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.family.jiandie.camera.data.QrCode
import com.family.jiandie.camera.data.Quality
import com.family.jiandie.camera.data.SecurePrefs
import com.family.jiandie.camera.service.TransmissionController
import com.family.jiandie.camera.service.TransmissionController.Mode
import kotlinx.coroutines.launch

/**
 * "Radius": the one place for everything about transmitting — standby on/off,
 * pairing, the server, video/audio, diagnostics. Nothing about transmission is
 * configured anywhere else in the app.
 */
class RadiusActivity : AppCompatActivity() {

    private lateinit var prefs: SecurePrefs
    private lateinit var controller: TransmissionController

    private lateinit var statusText: TextView
    private lateinit var armButton: Button
    private lateinit var stopButton: Button
    private lateinit var qrImage: ImageView
    private lateinit var codeText: TextView
    private lateinit var logText: TextView

    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val missing = Arming.missingPermissions(this)
        if (missing.isEmpty()) startStandby()
        else Toast.makeText(this, getString(R.string.perm_camera_needed), Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = SecurePrefs.get(this)
        controller = TransmissionController.get(this)
        buildUi()
        refreshPairing()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { controller.status.collect { renderStatus(it) } }
                launch { controller.log.collect { renderLog(it) } }
            }
        }
    }

    // ---- UI construction ----------------------------------------------------------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
        }

        content.addView(title(getString(R.string.radius_title), 26f))
        content.addView(body(getString(R.string.radius_subtitle)))

        // --- standby ---
        statusText = body("").apply {
            typeface = Typeface.MONOSPACE; textSize = 13f
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.parseColor("#14000000"))
        }
        content.addView(spaced(statusText, 16))

        armButton = Button(this).apply { setOnClickListener { onArmClicked() } }
        content.addView(spaced(armButton, 12))
        stopButton = Button(this).apply {
            text = getString(R.string.stop_streaming)
            setOnClickListener { controller.stopStreaming() }
        }
        content.addView(spaced(stopButton, 4))
        content.addView(small(getString(R.string.arm_hint)))

        // --- pairing ---
        content.addView(section(getString(R.string.sec_pair)))
        content.addView(small(getString(R.string.pair_hint)))
        qrImage = ImageView(this).apply { adjustViewBounds = true; setBackgroundColor(Color.WHITE) }
        content.addView(spaced(qrImage, 12), LinearLayout.LayoutParams(dp(240), dp(240)))
        codeText = body("").apply {
            typeface = Typeface.MONOSPACE; textSize = 16f; setTextIsSelectable(true)
        }
        content.addView(spaced(codeText, 8))
        content.addView(row(
            button(R.string.copy_code) { copyCode() },
            button(R.string.share_code) { shareCode() },
        ))
        content.addView(spaced(button(R.string.repair) { confirmRepair() }, 8))

        // --- server ---
        content.addView(section(getString(R.string.sec_server)))
        content.addView(small(getString(R.string.server_hint)))
        val url = field(prefs.signalingUrl, getString(R.string.server_placeholder), InputType.TYPE_TEXT_VARIATION_URI)
        content.addView(spaced(url, 4))
        content.addView(button(R.string.save) {
            if (url.text.isNotBlank() && !SecurePrefs.isValidSignalingUrl(url.text.toString())) {
                Toast.makeText(this, R.string.server_invalid, Toast.LENGTH_LONG).show()
            } else {
                prefs.signalingUrl = url.text.toString()
                Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            }
        })

        // --- video & audio ---
        content.addView(section(getString(R.string.sec_av)))
        content.addView(small(getString(R.string.quality)))
        content.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@RadiusActivity, android.R.layout.simple_spinner_dropdown_item, Quality.values().map { it.label })
            setSelection(prefs.quality.ordinal)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, position: Int, id: Long) {
                    prefs.quality = Quality.values()[position]
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        })
        content.addView(switch(R.string.send_audio, prefs.sendAudio) { prefs.sendAudio = it })
        content.addView(switch(R.string.keep_awake, prefs.keepAwake) { prefs.keepAwake = it })
        content.addView(small(getString(R.string.applies_next_arm)))

        // --- relay (optional) ---
        content.addView(section(getString(R.string.sec_relay)))
        content.addView(small(getString(R.string.relay_hint)))
        val turnUrl = field(prefs.turnUrl, getString(R.string.relay_url), InputType.TYPE_TEXT_VARIATION_URI)
        val turnUser = field(prefs.turnUser, getString(R.string.relay_user), InputType.TYPE_CLASS_TEXT)
        val turnPass = field(prefs.turnPass, getString(R.string.relay_pass),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        content.addView(spaced(turnUrl, 4)); content.addView(turnUser); content.addView(turnPass)
        content.addView(button(R.string.save) {
            prefs.turnUrl = turnUrl.text.toString()
            prefs.turnUser = turnUser.text.toString()
            prefs.turnPass = turnPass.text.toString()
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        })

        // --- reliability ---
        content.addView(section(getString(R.string.sec_keep)))
        content.addView(small(getString(R.string.keep_hint)))
        content.addView(spaced(button(R.string.battery_exempt) { requestBatteryExemption() }, 8))
        content.addView(spaced(button(R.string.app_settings) { openAppSettings() }, 8))

        // --- diagnostics ---
        content.addView(section(getString(R.string.sec_diag)))
        logText = body("").apply {
            typeface = Typeface.MONOSPACE; textSize = 11f; setTextIsSelectable(true)
        }
        content.addView(logText)
        content.addView(spaced(button(R.string.copy_log) { copyLog() }, 8))

        val scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    // ---- actions ------------------------------------------------------------------

    private fun onArmClicked() {
        if (controller.status.value.mode != Mode.OFF) {
            TransmissionService.stop(this)
            return
        }
        controller.armError()?.let {
            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            return
        }
        val missing = Arming.missingPermissions(this)
        if (missing.isEmpty()) startStandby() else askPermissions.launch(missing)
    }

    private fun startStandby() {
        Arming.start(this)?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
    }

    private fun refreshPairing() {
        val p = prefs.ensurePairing()
        qrImage.setImageBitmap(QrCode.render(p.toUri()))
        codeText.text = p.toManualCode()
    }

    private fun copyCode() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Pairing code", codeText.text))
        Toast.makeText(this, R.string.code_copied, Toast.LENGTH_SHORT).show()
    }

    private fun shareCode() {
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, codeText.text.toString()),
            getString(R.string.share_code),
        ))
    }

    private fun confirmRepair() {
        AlertDialog.Builder(this)
            .setTitle(R.string.repair_title)
            .setMessage(R.string.repair_msg)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.repair_confirm) { _, _ ->
                if (controller.status.value.mode != Mode.OFF) TransmissionService.stop(this)
                prefs.rotatePairing()
                refreshPairing()
            }
            .show()
    }

    private fun requestBatteryExemption() {
        runCatching {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }.onFailure { openAppSettings() }
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun copyLog() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Jiandie log", controller.log.value.joinToString("\n")))
        Toast.makeText(this, R.string.log_copied, Toast.LENGTH_SHORT).show()
    }

    // ---- rendering ----------------------------------------------------------------

    private fun renderStatus(s: TransmissionController.Status) {
        fun yn(b: Boolean) = getString(if (b) R.string.yes else R.string.no)
        val streaming = when (s.mode) {
            Mode.LIVE -> "LIVE"; Mode.STARTING -> "starting"; else -> "no"
        }
        statusText.text = buildString {
            appendLine("Standby ........ ${yn(s.mode != Mode.OFF)}")
            appendLine("Server ......... ${if (s.signalingConnected) "connected" else "not connected"}")
            appendLine("Viewer online .. ${yn(s.viewerPresent)}")
            appendLine("Network ........ ${if (s.networkAvailable) "ok" else "none"}")
            appendLine("Streaming ...... $streaming")
            appendLine("Path ........... ${s.path ?: "-"}")
            appendLine("Requests ....... ${s.startsAccepted} accepted, ${s.startsRejected} refused")
            append("Last problem ... ${s.lastError ?: "-"}")
        }
        armButton.text = getString(if (s.mode == Mode.OFF) R.string.arm else R.string.disarm)
        stopButton.visibility = if (s.mode == Mode.LIVE || s.mode == Mode.STARTING) View.VISIBLE else View.GONE
    }

    private fun renderLog(lines: List<String>) {
        logText.text = lines.takeLast(60).joinToString("\n").ifEmpty { "—" }
    }

    // ---- tiny view helpers ------------------------------------------------------------

    private fun title(t: String, size: Float) = TextView(this).apply {
        text = t; textSize = size; setTypeface(typeface, Typeface.BOLD)
    }

    private fun section(t: String) = title(t, 18f).apply { setPadding(0, dp(28), 0, dp(4)) }

    private fun body(t: String) = TextView(this).apply { text = t; textSize = 15f }

    private fun small(t: String) = TextView(this).apply { text = t; textSize = 12.5f; alpha = 0.75f; setPadding(0, dp(4), 0, dp(4)) }

    private fun <T : View> spaced(v: T, top: Int): T {
        v.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(top) }
        return v
    }

    private fun button(label: Int, onClick: () -> Unit) = Button(this).apply {
        setText(label); setOnClickListener { onClick() }
    }

    private fun row(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        for (v in views) addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun field(value: String, hint: String, inputType: Int) = EditText(this).apply {
        setText(value); this.hint = hint; this.inputType = inputType; setSingleLine()
    }

    private fun switch(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) = SwitchCompat(this).apply {
        setText(label); isChecked = checked; setPadding(0, dp(8), 0, dp(8))
        setOnCheckedChangeListener { _, c -> onChange(c) }
    }
}
