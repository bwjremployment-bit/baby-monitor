package com.family.jiandie.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.family.jiandie.camera.data.SecurePrefs
import com.family.jiandie.camera.rtc.CameraEngine
import com.family.jiandie.camera.service.TransmissionController
import com.family.jiandie.camera.service.TransmissionController.Mode
import kotlinx.coroutines.launch
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

/**
 * The main screen: an ordinary camera preview with flip, flash and a settings
 * button. It is honest about the camera's state: a pill at the top always says
 * whether the phone is off, on standby (camera/mic off) or LIVE. All
 * transmission controls are in Radius.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SecurePrefs
    private lateinit var controller: TransmissionController
    private lateinit var engine: CameraEngine
    private lateinit var renderer: SurfaceViewRenderer
    private lateinit var pill: TextView
    private lateinit var flash: ImageButton
    private var previewOn = false

    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startPreview() else Toast.makeText(this, R.string.perm_camera_needed, Toast.LENGTH_LONG).show()
    }
    private val askRearm = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (Arming.missingPermissions(this).isEmpty()) startStandby()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = SecurePrefs.get(this)
        controller = TransmissionController.get(this)
        engine = CameraEngine.get(this)
        NotificationHelper.ensureChannels(this)
        buildUi()
        // Launched once: repeatOnLifecycle itself pauses/resumes collection with the screen.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                controller.status.collect { render(it) }
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Tapping the "standby stopped" notification lands here and resumes standby. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(NotificationHelper.EXTRA_REARM, false) != true) return
        intent.removeExtra(NotificationHelper.EXTRA_REARM)
        NotificationHelper.clearRearmPrompt(this)
        val missing = Arming.missingPermissions(this)
        if (missing.isEmpty()) startStandby() else askRearm.launch(missing)
    }

    private fun startStandby() {
        Arming.start(this)?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
    }

    private fun buildUi() {
        val dp = resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        renderer = SurfaceViewRenderer(this).apply {
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        }
        root.addView(renderer, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        pill = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            textSize = 14f
            setPadding(px(16), px(8), px(16), px(8))
            setBackgroundResource(R.drawable.pill_bg)
        }
        val pillLp = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        pillLp.topMargin = px(16)
        root.addView(pill, pillLp)

        fun iconButton(icon: Int, description: Int, onClick: () -> Unit) = ImageButton(this).apply {
            setImageResource(icon)
            setColorFilter(Color.WHITE)
            contentDescription = getString(description)
            setBackgroundResource(R.drawable.pill_bg)
            setPadding(px(14), px(14), px(14), px(14))
            setOnClickListener { onClick() }
        }

        val flip = iconButton(android.R.drawable.ic_menu_rotate, R.string.cd_flip) { flipCamera() }
        flash = iconButton(R.drawable.ic_flash, R.string.cd_flash) { toggleFlash() }
        val radius = iconButton(android.R.drawable.ic_menu_preferences, R.string.cd_radius) {
            startActivity(Intent(this, RadiusActivity::class.java))
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        for (b in listOf(flip, flash, radius)) {
            bar.addView(b, LinearLayout.LayoutParams(px(56), px(56)).apply { setMargins(px(18), 0, px(18), 0) })
        }
        val barLp = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM)
        barLp.bottomMargin = px(28)
        root.addView(bar, barLp)

        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            (pill.layoutParams as FrameLayout.LayoutParams).topMargin = bars.top + px(16)
            (bar.layoutParams as FrameLayout.LayoutParams).bottomMargin = bars.bottom + px(28)
            pill.requestLayout(); bar.requestLayout()
            insets
        }
    }

    override fun onStart() {
        super.onStart()
        if (hasCameraPermission()) startPreview() else askCamera.launch(Manifest.permission.CAMERA)
    }

    override fun onStop() {
        stopPreview()
        super.onStop()
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startPreview() {
        if (previewOn) return
        previewOn = true
        engine.acquire(CameraEngine.Reason.PREVIEW, prefs.quality, prefs.preferFrontCamera)
        renderer.init(engine.egl.eglBaseContext, null)
        renderer.setEnableHardwareScaler(true)
        renderer.setMirror(engine.isFront)
        engine.addSink(renderer)
        refreshFlash()
    }

    private fun stopPreview() {
        if (!previewOn) return
        previewOn = false
        engine.removeSink(renderer)
        renderer.release()
        engine.release(CameraEngine.Reason.PREVIEW)
    }

    private fun flipCamera() {
        engine.switchCamera { front ->
            prefs.preferFrontCamera = front
            renderer.setMirror(front)
            refreshFlash()
        }
    }

    private fun toggleFlash() {
        if (!engine.torchSupported()) {
            Toast.makeText(this, R.string.flash_unsupported, Toast.LENGTH_SHORT).show()
            return
        }
        engine.setTorch(!engine.isTorchOn)
        refreshFlash()
    }

    private fun refreshFlash() {
        flash.alpha = if (engine.isTorchOn) 1f else 0.6f
        flash.visibility = if (engine.torchSupported()) View.VISIBLE else View.INVISIBLE
    }

    private fun render(s: TransmissionController.Status) {
        val (text, color) = when (s.mode) {
            Mode.OFF -> getString(R.string.status_off) to Color.parseColor("#99000000")
            Mode.ARMED -> getString(R.string.status_standby) to Color.parseColor("#CC8A6D00")
            Mode.STARTING -> getString(R.string.status_starting) to Color.parseColor("#CC8A6D00")
            Mode.LIVE -> getString(R.string.status_live) to Color.parseColor("#E6B00020")
        }
        pill.text = text
        pill.background.mutate().setTint(color)
    }
}
