package com.crazystandby

import android.service.dreams.DreamService

class StandbyDream : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false   // touch wakes the screen and exits
        isFullscreen = true
        setContentView(FlowClockView(this))
    }
}
