package com.family.jiandie.camera

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.family.monitor.shared.pairing.PairingPayload

/**
 * The "Radius" settings section — the single place all transmission config
 * lives (enable/disable, pairing, receiver auth, A/V config, connection info,
 * diagnostics). STAGE 2: shows a freshly generated pairing payload and wires
 * start/stop of the foreground TransmissionService. Full UI + QR in Stage 3.
 */
class RadiusActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Demonstrates the shared pairing library is wired in. In Stage 3 this
        // is persisted to Keystore-backed storage and rendered as a QR code.
        val pairing = PairingPayload.generate()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.radius_title); textSize = 22f
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.radius_pairing_label); setPadding(0, 24, 0, 8)
        })
        root.addView(TextView(this).apply {
            text = pairing.toManualCode(); textSize = 12f
        })
        root.addView(Button(this).apply {
            text = getString(R.string.start_transmission); gravity = Gravity.CENTER
            setOnClickListener { TransmissionService.start(this@RadiusActivity) }
        })
        root.addView(Button(this).apply {
            text = getString(R.string.stop_transmission); gravity = Gravity.CENTER
            setOnClickListener {
                stopService(Intent(this@RadiusActivity, TransmissionService::class.java))
            }
        })
        setContentView(root)
    }
}
