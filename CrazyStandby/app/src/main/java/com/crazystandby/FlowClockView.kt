package com.crazystandby

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FlowClockView(ctx: Context) : View(ctx) {

    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        setShadowLayer(40f, 0f, 0f, 0xAA7C4DFF.toInt())   // neon glow
    }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.3f
    }
    private val bg = Paint()
    private val fmtTime = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val fmtDate = SimpleDateFormat("EEEE · d MMMM", Locale.getDefault())
    private var phase = 0f

    override fun onDraw(c: Canvas) {
        phase += 0.002f
        val now = Date()
        val w = width.toFloat()
        val h = height.toFloat()

        // animated aurora gradient
        val a = (Math.sin(phase.toDouble()) * 0.5 + 0.5).toFloat()
        bg.shader = LinearGradient(
            0f, 0f, w * a + 1, h,
            intArrayOf(0xFF0B0F2B.toInt(), 0xFF3A1C71.toInt(), 0xFFD76D77.toInt()),
            null, Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, bg)

        // slow drift to protect OLED from burn-in
        val t = System.currentTimeMillis() / 60000.0
        val dx = (Math.sin(t) * 20).toFloat()
        val dy = (Math.cos(t) * 20).toFloat()

        timePaint.textSize = w * 0.30f
        datePaint.textSize = w * 0.045f
        c.drawText(fmtTime.format(now), w / 2 + dx, h / 2 + dy, timePaint)
        c.drawText(
            fmtDate.format(now).uppercase(),
            w / 2 + dx, h / 2 + w * 0.12f + dy, datePaint
        )

        postInvalidateOnAnimation()
    }
}
