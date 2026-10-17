package com.crazystandby

import android.service.dreams.DreamService
import kotlin.math.max
import kotlin.math.min

class StandbyDream : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false   // touch wakes the screen and exits
        isFullscreen = true
        setContentView(FlowClockView(this))

        // The screen saver starts when charging begins, so lower the brightness here.
        // "Soft glow" is meant for sleeping, so it is capped at a very low level.
        try {
            var pct = Prefs.brightness(this)
            if (Prefs.style(this) == 11) pct = min(pct, 4)
            val lp = window.attributes
            lp.screenBrightness = max(0.01f, pct / 100f)
            window.attributes = lp
        } catch (e: Exception) {
            // ignore: dimming is optional
        }
    }
}
