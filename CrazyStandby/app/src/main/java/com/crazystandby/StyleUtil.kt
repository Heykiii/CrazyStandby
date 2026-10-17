package com.crazystandby

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Canvas
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Small helpers shared by the style files. */

internal fun FlowClockView.timeStr(): String = hourStr() + ":" + minStr()

internal fun FlowClockView.timeStr2(): String = hourStr().padStart(2, '0') + ":" + minStr()

/** Pseudo random number in 0..1 from a seed (stable, no state). */
internal fun rnd(seed: Double): Float {
    val v = sin(seed) * 43758.5453
    return (v - floor(v)).toFloat()
}

internal fun darken(col: Int, f: Float): Int = Color.rgb(
    (Color.red(col) * (1f - f)).toInt(),
    (Color.green(col) * (1f - f)).toInt(),
    (Color.blue(col) * (1f - f)).toInt()
)

internal fun lighten(col: Int, f: Float): Int = Color.rgb(
    (Color.red(col) + (255 - Color.red(col)) * f).toInt(),
    (Color.green(col) + (255 - Color.green(col)) * f).toInt(),
    (Color.blue(col) + (255 - Color.blue(col)) * f).toInt()
)

/** Sets the text size so [s] fits in maxW x maxH (the height is measured on the digit 0). */
internal fun FlowClockView.fitBox(s: String, maxW: Float, maxH: Float) {
    p.textSize = 100f
    val m = p.measureText(s)
    p.getTextBounds("0", 0, 1, tb)
    val gh = max(1, tb.height()).toFloat()
    val sc = min(maxW / max(1f, m), maxH / gh)
    p.textSize = 100f * sc
}

/** Thin ring/arc with a soft glow, drawn with a few passes. */
internal fun FlowClockView.glowArc(
    c: Canvas, cx: Float, cy: Float, r: Float, sweep: Float, col: Int, sw: Float
) {
    val rect = RectF(cx - r, cy - r, cx + r, cy + r)
    val widths = floatArrayOf(sw * 6f, sw * 3.5f, sw * 2f, sw)
    val alphas = intArrayOf(0x12, 0x20, 0x40, 0xFF)
    pr()
    p.style = Paint.Style.STROKE
    p.strokeCap = Paint.Cap.ROUND
    for (i in 0..3) {
        p.strokeWidth = widths[i]
        p.color = withAlpha(col, alphas[i])
        c.drawArc(rect, -90f, sweep, false, p)
    }
}
