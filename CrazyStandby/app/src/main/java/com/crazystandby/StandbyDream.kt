package com.crazystandby

import android.service.dreams.DreamService
import kotlin.math.max

class StandbyDream : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false   // touch wakes the screen and exits
        isFullscreen = true
        setContentView(FlowClockView(this))

        // The screen saver starts when charging begins, so lower the brightness here.
        try {
            val lp = window.attributes
            lp.screenBrightness = max(0.01f, Prefs.brightness(this) / 100f)
            window.attributes = lp
        } catch (e: Exception) {
            // ignore: dimming is optional
        }
    }
}
