package com.crazystandby

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply {
            text = "Crazy Standby\n\nGo to Settings → Display → Screen saver,\n" +
                "choose Crazy Standby, then tap Start now."
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        setContentView(tv)
    }
}
