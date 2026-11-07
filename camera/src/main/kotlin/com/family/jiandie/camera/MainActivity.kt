package com.family.jiandie.camera

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * The main camera screen. STAGE 2: a placeholder that is deliberately honest
 * about being a monitor and routes all transmission controls to the Radius
 * screen. The real camera preview + LIVE indicator arrive in Stage 3.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 24f
            gravity = Gravity.CENTER
        })

        // Honest status line — this app shows when it is transmitting.
        root.addView(TextView(this).apply {
            text = getString(R.string.status_idle)
            setTextColor(Color.parseColor("#B00020"))
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        })

        root.addView(Button(this).apply {
            text = getString(R.string.open_radius)
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, RadiusActivity::class.java))
            }
        })

        setContentView(root)
    }
}
