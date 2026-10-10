package com.crazystandby

import android.service.dreams.DreamService
import java.util.Calendar

class StandbyDream : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false   // touch wakes the screen and exits
        isFullscreen = true
        setContentView(FlowClockView(this))

        if (Prefs.dimAtNight(this)) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (hour >= 23 || hour < 6) {
                try {
                    val lp = window.attributes
                    lp.screenBrightness = 0.08f
                    window.attributes = lp
                } catch (e: Exception) {
                    // ignore: dimming is optional
                }
            }
        }
    }
}
