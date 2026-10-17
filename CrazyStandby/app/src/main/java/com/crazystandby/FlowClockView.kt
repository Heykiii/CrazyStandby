package com.crazystandby

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.BatteryManager
import android.view.View
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Big tall time on the left, weekday + date and battery on the right.
 * Pure black background (saves power on OLED), redraws once per second.
 */
class FlowClockView(ctx: Context) : View(ctx) {

    private var accent = Color.WHITE
    private var use24h = true

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tb = Rect()
    private val cal: Calendar = Calendar.getInstance()
    private val loc: Locale = Locale.getDefault()
    private val fmts = HashMap<String, SimpleDateFormat>()

    private val tfCond = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val tfMed = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private var battLevel = 0
    private var charging = false
    private var battAt = 0L

    private val tick = Runnable { invalidate() }

    init {
        reload()
    }

    /** Re-reads the saved settings. */
    fun reload() {
        accent = Prefs.color(context)
        use24h = Prefs.use24h(context)
        battAt = 0L
        invalidate()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    // ------------------------------------------------------------------ helpers

    private fun pr() {
        p.reset()
        p.isAntiAlias = true
    }

    private fun fmt(pattern: String, now: Long): String {
        val f = fmts.getOrPut(pattern) { SimpleDateFormat(pattern, loc) }
        return f.format(Date(now))
    }

    private fun hourStr(): String {
        val h = if (use24h) {
            cal.get(Calendar.HOUR_OF_DAY)
        } else {
            val x = cal.get(Calendar.HOUR)
            if (x == 0) 12 else x
        }
        return h.toString()
    }

    private fun minStr() = String.format(Locale.ROOT, "%02d", cal.get(Calendar.MINUTE))

    private fun fit(s: String, maxW: Float, start: Float) {
        p.textSize = start
        val m = p.measureText(s)
        if (m > maxW && m > 0f) p.textSize = start * maxW / m
    }

    /** Baseline that vertically centres [s] on [cy]; leaves its bounds in [tb]. */
    private fun baseAt(s: String, cy: Float): Float {
        p.getTextBounds(s, 0, s.length, tb)
        return cy - (tb.top + tb.bottom) / 2f
    }

    private fun batText() = (if (charging) "⚡ " else "") + "$battLevel%"

    private fun updateBattery(now: Long) {
        if (battAt != 0L && now - battAt < 15000L) return
        battAt = now
        val i: Intent = context.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return
        val lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (lvl >= 0 && scale > 0) battLevel = lvl * 100 / scale
        val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        charging = st == BatteryManager.BATTERY_STATUS_CHARGING ||
            st == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun drawDay(c: Canvas, d: String, n: String, x: Float, y: Float, s: Float) {
        pr()
        p.typeface = tfMed
        p.textSize = s
        p.color = accent
        c.drawText(d, x, y, p)
        val dw = p.measureText("$d ")
        p.color = Color.WHITE
        c.drawText(n, x + dw, y, p)
    }

    // ------------------------------------------------------------------ draw

    override fun onDraw(c: Canvas) {
        val now = System.currentTimeMillis()
        cal.timeInMillis = now
        updateBattery(now)

        val w = width.toFloat()
        val h = height.toFloat()
        c.drawColor(Color.BLACK)

        // slow drift to protect OLED from burn-in
        val drift = min(w, h) * 0.012f
        val t = now / 60000.0
        c.save()
        c.translate((sin(t) * drift).toFloat(), (cos(t) * drift).toFloat())
        drawClock(c, w, h, now)
        c.restore()

        removeCallbacks(tick)
        postDelayed(tick, 1000L - now % 1000L + 15L)
    }

    private fun drawClock(c: Canvas, w: Float, h: Float, now: Long) {
        val land = w > h * 1.1f
        val ts = hourStr() + ":" + minStr()

        pr()
        p.typeface = tfCond
        p.color = accent
        p.textScaleX = 0.82f   // narrower digits, so they look tall
        fit(ts, if (land) w * 0.66f else w * 0.9f, if (land) h * 1.0f else w * 0.55f)
        val mw = p.measureText(ts)
        val x0 = if (land) w * 0.05f else (w - mw) / 2f
        val cy = if (land) h * 0.5f else h * 0.34f
        val base = baseAt("0", cy)
        val top = base + tb.top
        val dh = tb.height().toFloat()
        c.drawText(ts, x0, base, p)

        val dayW = fmt("EEE", now).uppercase()
        val dayN = cal.get(Calendar.DAY_OF_MONTH).toString()
        if (land) {
            val ix = x0 + mw + w * 0.04f
            val avail = w - ix - w * 0.03f
            val s = min(h * 0.12f, avail / 3.6f)
            drawDay(c, dayW, dayN, ix, top + s, s)
            pr()
            p.typeface = tfMed
            p.color = Color.WHITE
            p.textSize = s
            c.drawText(batText(), ix, top + s * 2.3f, p)
        } else {
            val s = w * 0.09f
            val y = cy + dh / 2f + s * 2.2f
            drawDay(c, dayW, dayN, x0, y, s)
            pr()
            p.typeface = tfMed
            p.color = Color.WHITE
            p.textSize = s
            c.drawText(batText(), x0, y + s * 1.4f, p)
        }
    }
}
