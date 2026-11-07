package com.family.yps.viewer

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.family.monitor.shared.state.ConnectionState

/**
 * Yps Viewer main screen. STAGE 2: opens straight to a live-view placeholder
 * with a connection-status line (driven by the shared ConnectionState in later
 * stages). Pairing (scan/enter), remote video + audio, reconnection and
 * full-screen arrive in Stage 4.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(48, 48, 48, 48)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(Color.WHITE); textSize = 22f; gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            // Shows the shared state enum is linked in.
            text = getString(R.string.status_prefix, ConnectionState.DISCONNECTED.name)
            setTextColor(Color.LTGRAY); gravity = Gravity.CENTER; setPadding(0, 24, 0, 0)
        })
        setContentView(root)
    }
}
