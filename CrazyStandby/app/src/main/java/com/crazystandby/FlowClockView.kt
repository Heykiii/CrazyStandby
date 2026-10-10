package com.crazystandby

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.BatteryManager
import android.provider.CalendarContract
import android.view.View
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * One view that draws all the standby styles.
 * 0 Aurora, 1 Flip, 2 Split, 3 Big time, 4 Calendar, 5 Overlap, 6 Battery ring
 */
class FlowClockView(ctx: Context) : View(ctx) {

    private class Ev(val begin: Long, val title: String, val allDay: Boolean)

    private var style = 1
    private var accent = Color.WHITE
    private var use24h = true

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val tb = Rect()
    private val cal: Calendar = Calendar.getInstance()
    private val loc: Locale = Locale.getDefault()
    private val fmts = HashMap<String, SimpleDateFormat>()

    private val tfCond = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val tfBlack = Typeface.create("sans-serif-black", Typeface.NORMAL)
    private val tfThin = Typeface.create("sans-serif-thin", Typeface.NORMAL)
    private val tfLight = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val tfMed = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    // battery
    private var battLevel = 0
    private var charging = false
    private var battAt = 0L

    // calendar events
    private var events: List<Ev> = emptyList()
    private var evAt = 0L
    private var calOk = false

    // flip clock state
    private val curS = Array(3) { "" }
    private val prevS = Array(3) { "" }
    private val flipT = LongArray(3)
    private val flipMs = 450L

    private val tick = Runnable { invalidate() }

    init {
        reload()
    }

    /** Re-reads the saved settings. */
    fun reload() {
        style = Prefs.style(context)
        accent = Prefs.color(context)
        use24h = Prefs.use24h(context)
        for (i in 0..2) curS[i] = ""
        evAt = 0L
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

    private fun withAlpha(col: Int, a: Int): Int = (col and 0x00FFFFFF) or (a shl 24)

    private fun lighten(col: Int, f: Float): Int {
        val r = Color.red(col)
        val g = Color.green(col)
        val b = Color.blue(col)
        return Color.rgb(
            (r + (255 - r) * f).toInt(),
            (g + (255 - g) * f).toInt(),
            (b + (255 - b) * f).toInt()
        )
    }

    private fun fmt(pattern: String, now: Long): String {
        val f = fmts.getOrPut(pattern) { SimpleDateFormat(pattern, loc) }
        return f.format(Date(now))
    }

    private fun hourStr(pad: Boolean): String {
        val h = if (use24h) {
            cal.get(Calendar.HOUR_OF_DAY)
        } else {
            val x = cal.get(Calendar.HOUR)
            if (x == 0) 12 else x
        }
        return if (pad) String.format(Locale.ROOT, "%02d", h) else h.toString()
    }

    private fun minStr() = String.format(Locale.ROOT, "%02d", cal.get(Calendar.MINUTE))
    private fun secStr() = String.format(Locale.ROOT, "%02d", cal.get(Calendar.SECOND))

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

    private fun clip(s: String, maxW: Float): String {
        if (maxW <= 0f) return ""
        if (p.measureText(s) <= maxW) return s
        var n = s.length
        val dots = p.measureText("…")
        while (n > 1 && p.measureText(s, 0, n) + dots > maxW) n--
        return s.substring(0, n) + "…"
    }

    private fun batText() = (if (charging) "⚡ " else "") + "$battLevel%"

    private fun updateBattery(now: Long) {
        if (now - battAt < 15000L && battAt != 0L) return
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

    private fun updateEvents(now: Long) {
        if (evAt != 0L && now - evAt < 300000L) return
        evAt = now
        calOk = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        if (!calOk) {
            events = emptyList()
            return
        }
        try {
            val out = ArrayList<Ev>()
            val cur = CalendarContract.Instances.query(
                context.contentResolver,
                arrayOf(
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.ALL_DAY
                ),
                now,
                now + 48L * 3600L * 1000L
            )
            cur?.use {
                while (it.moveToNext()) {
                    out.add(Ev(it.getLong(1), it.getString(0) ?: "", it.getInt(2) == 1))
                }
            }
            events = out.sortedBy { it.begin }.take(3)
        } catch (e: Exception) {
            events = emptyList()
        }
    }

    // ------------------------------------------------------------------ main draw

    override fun onDraw(c: Canvas) {
        val now = System.currentTimeMillis()
        cal.timeInMillis = now
        updateBattery(now)

        val w = width.toFloat()
        val h = height.toFloat()
        var animating = false

        if (style == 0) drawAuroraBg(c, w, h, now) else c.drawColor(Color.BLACK)

        // slow drift to protect OLED from burn-in
        val drift = min(w, h) * 0.012f
        val t = now / 60000.0
        c.save()
        c.translate((sin(t) * drift).toFloat(), (cos(t) * drift).toFloat())
        when (style) {
            0 -> drawAurora(c, w, h, now)
            1 -> animating = drawFlip(c, w, h, now)
            2 -> drawSplit(c, w, h, now)
            3 -> drawBig(c, w, h, now)
            4 -> drawCalendar(c, w, h, now)
            5 -> drawOverlap(c, w, h)
            else -> drawRing(c, w, h, now)
        }
        c.restore()

        val delay = if (style == 0 || animating) 33L else 1000L - now % 1000L + 15L
        removeCallbacks(tick)
        postDelayed(tick, delay)
    }

    // ------------------------------------------------------------------ 0 Aurora

    private fun drawAuroraBg(c: Canvas, w: Float, h: Float, now: Long) {
        val a = (sin(now / 4000.0) * 0.5 + 0.5).toFloat()
        pr()
        p.shader = LinearGradient(
            0f, 0f, w * (0.3f + 0.7f * a), h,
            intArrayOf(0xFF0B0F2B.toInt(), 0xFF3A1C71.toInt(), 0xFFD76D77.toInt()),
            null, Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, p)
    }

    private fun drawAurora(c: Canvas, w: Float, h: Float, now: Long) {
        val time = hourStr(true) + ":" + minStr()
        pr()
        p.typeface = tfThin
        p.color = Color.WHITE
        p.textAlign = Paint.Align.CENTER
        p.setShadowLayer(w * 0.03f, 0f, 0f, withAlpha(accent, 0xCC))
        fit(time, w * 0.88f, min(w * 0.3f, h * 0.55f))
        val cy = h * 0.46f
        val base = baseAt("0", cy)
        c.drawText(time, w / 2f, base, p)
        val half = tb.height() / 2f

        val u = min(w, h * 1.6f)
        pr()
        p.typeface = tfLight
        p.color = 0xCCFFFFFF.toInt()
        p.textAlign = Paint.Align.CENTER
        p.letterSpacing = 0.3f
        p.textSize = u * 0.04f
        c.drawText(fmt("EEEE · d MMMM", now).uppercase(), w / 2f, cy + half + u * 0.09f, p)
    }

    // ------------------------------------------------------------------ 1 Flip

    private fun drawFlip(c: Canvas, w: Float, h: Float, now: Long): Boolean {
        val u = min(w, h * 1.6f)
        val gap = w * 0.02f
        val cw = (w * 0.92f - 2f * gap) / 3f
        val ch = min(cw * 1.3f, h * 0.6f)
        val left0 = (w - (3f * cw + 2f * gap)) / 2f
        val top = (h - ch) / 2f
        val parts = arrayOf(hourStr(true), minStr(), secStr())
        var anim = false
        for (i in 0..2) {
            if (drawFlipCard(c, i, parts[i], left0 + i * (cw + gap), top, cw, ch, now)) anim = true
        }

        pr()
        p.typeface = tfMed
        p.color = 0xFFBBBBBB.toInt()
        p.textAlign = Paint.Align.CENTER
        p.textSize = u * 0.04f
        c.drawText(fmt("dd-MMM-yyyy EEE", now), w / 2f, top - u * 0.05f, p)
        p.letterSpacing = 0.15f
        c.drawText(batText(), w / 2f, top + ch + u * 0.09f, p)
        return anim
    }

    private fun half(
        c: Canvas, s: String, cx: Float, base: Float,
        l: Float, t: Float, r: Float, b: Float
    ) {
        c.save()
        c.clipRect(l, t, r, b)
        c.drawText(s, cx, base, p)
        c.restore()
    }

    private fun drawFlipCard(
        c: Canvas, i: Int, s: String,
        l: Float, t: Float, cw: Float, ch: Float, now: Long
    ): Boolean {
        if (curS[i].isEmpty()) {
            curS[i] = s; prevS[i] = s; flipT[i] = 0L
        } else if (curS[i] != s) {
            prevS[i] = curS[i]; curS[i] = s; flipT[i] = now
        }
        val prog = if (flipT[i] == 0L) 1f
        else ((now - flipT[i]) / flipMs.toFloat()).coerceIn(0f, 1f)

        val r = l + cw
        val b = t + ch
        val cy = t + ch / 2f
        val cx = l + cw / 2f
        val rad = cw * 0.08f

        pr()
        p.color = 0xFF1B1B1D.toInt()
        c.drawRoundRect(l, t, r, b, rad, rad, p)

        pr()
        p.typeface = tfCond
        p.textAlign = Paint.Align.CENTER
        p.color = accent
        fit("00", cw * 0.88f, ch * 0.98f)
        val base = baseAt("0", cy)

        // static halves: new on top, old on bottom until the flip finishes
        half(c, curS[i], cx, base, l, t, r, cy)
        half(c, if (prog < 1f) prevS[i] else curS[i], cx, base, l, cy, r, b)

        if (prog < 1f) {
            val sc = max(0.001f, if (prog < 0.5f) 1f - 2f * prog else 2f * prog - 1f)
            c.save()
            c.scale(1f, sc, cx, cy)
            if (prog < 0.5f) {
                c.clipRect(l, t, r, cy)
                p.color = 0xFF29292C.toInt()
                c.drawRect(l, t, r, cy, p)
                p.color = accent
                c.drawText(prevS[i], cx, base, p)
            } else {
                c.clipRect(l, cy, r, b)
                p.color = 0xFF1B1B1D.toInt()
                c.drawRect(l, cy, r, b, p)
                p.color = accent
                c.drawText(curS[i], cx, base, p)
            }
            c.restore()
        }

        p.color = Color.BLACK
        p.strokeWidth = max(2f, ch * 0.012f)
        c.drawLine(l, cy, r, cy, p)
        return prog < 1f
    }

    // ------------------------------------------------------------------ 2 Split

    private fun drawSplit(c: Canvas, w: Float, h: Float, now: Long) {
        val hs = hourStr(true)
        val ms = minStr()
        pr()
        p.typeface = tfCond
        p.color = accent
        p.textSize = h * 0.62f
        val total0 = p.measureText(hs) + p.measureText(ms) + p.textSize * 0.30f
        if (total0 > w * 0.9f) p.textSize = p.textSize * w * 0.9f / total0

        val size = p.textSize
        val wh = p.measureText(hs)
        val wm = p.measureText(ms)
        val gapW = size * 0.30f
        val total = wh + wm + gapW
        val x0 = (w - total) / 2f
        val cy = h * 0.52f
        val base = baseAt("0", cy)
        val dh = tb.height().toFloat()

        c.drawText(hs, x0, base, p)
        c.drawText(ms, x0 + wh + gapW, base, p)

        // square colon
        val sq = size * 0.10f
        val cxm = x0 + wh + gapW / 2f
        for (k in intArrayOf(-1, 1)) {
            val yy = cy + k * dh * 0.2f
            c.drawRect(cxm - sq / 2f, yy - sq / 2f, cxm + sq / 2f, yy + sq / 2f, p)
        }

        // thin split line through the middle
        p.color = Color.BLACK
        val th = max(2f, size * 0.012f)
        c.drawRect(x0 - 4f, cy - th / 2f, x0 + total + 4f, cy + th / 2f, p)

        // small label above the digits
        pr()
        p.typeface = tfMed
        p.color = Color.WHITE
        p.textSize = size * 0.11f
        val label = if (use24h) fmt("EEE d MMM", now).uppercase()
        else if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        c.drawText(label, x0, cy - dh / 2f - size * 0.08f, p)
    }

    // ------------------------------------------------------------------ 3 Big time

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

    private fun drawBig(c: Canvas, w: Float, h: Float, now: Long) {
        val land = w > h * 1.1f
        val ts = hourStr(false) + ":" + minStr()
        pr()
        p.typeface = tfCond
        p.color = accent
        fit(ts, if (land) w * 0.64f else w * 0.9f, if (land) h * 0.85f else w * 0.5f)
        val mw = p.measureText(ts)
        val x0 = if (land) w * 0.06f else (w - mw) / 2f
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

    // ------------------------------------------------------------------ 4 Calendar

    private fun drawCalendar(c: Canvas, w: Float, h: Float, now: Long) {
        updateEvents(now)
        val land = w > h * 1.1f
        val gray = 0xFF8E8E93.toInt()
        val dayN = cal.get(Calendar.DAY_OF_MONTH).toString()
        val wd = fmt("EEE", now).replaceFirstChar { it.uppercase() }
        val mo = fmt("MMM", now).replaceFirstChar { it.uppercase() }

        val x0 = if (land) w * 0.06f else w * 0.08f
        val hs = if (land) h * 0.17f else w * 0.13f
        val hy = if (land) h * 0.24f else h * 0.11f

        pr()
        p.typeface = tfMed
        p.textSize = hs
        p.color = accent
        c.drawText(wd, x0, hy, p)
        val ww = p.measureText("$wd ")
        p.color = gray
        c.drawText(mo, x0 + ww, hy, p)

        pr()
        p.typeface = tfMed
        p.color = Color.WHITE
        fit(dayN, if (land) w * 0.45f else w * 0.84f, if (land) h * 0.72f else h * 0.3f)
        c.drawText(dayN, x0, if (land) h * 0.93f else h * 0.40f, p)

        val ix = if (land) w * 0.58f else x0
        val y = if (land) h * 0.30f else h * 0.54f
        val bs = if (land) h * 0.2f else w * 0.14f

        pr()
        p.typeface = tfLight
        p.color = Color.WHITE
        p.textSize = bs
        c.drawText("$battLevel%", ix, y, p)

        pr()
        p.typeface = tfMed
        p.color = gray
        p.textSize = bs * 0.3f
        c.drawText(if (charging) "⚡ CHARGING" else "BATTERY", ix, y + bs * 0.4f, p)

        val es = bs * 0.3f
        var ey = y + bs * 1.15f
        val avail = w - ix - (if (land) w * 0.04f else w * 0.08f)
        val tp = if (use24h) "HH:mm" else "h:mm a"
        val today = fmt("yyyyMMdd", now)

        pr()
        p.typeface = tfMed
        p.textSize = es
        if (events.isEmpty()) {
            p.color = gray
            c.drawText(
                if (calOk) "No upcoming events" else "Enable calendar in app settings",
                ix, ey, p
            )
        } else {
            for (e in events) {
                val tm = when {
                    e.allDay -> "All day"
                    fmt("yyyyMMdd", e.begin) == today -> fmt(tp, e.begin)
                    else -> fmt("EEE $tp", e.begin)
                }
                p.color = accent
                c.drawText(tm, ix, ey, p)
                val tw = p.measureText("$tm  ")
                p.color = Color.WHITE
                c.drawText(clip(e.title, avail - tw), ix + tw, ey, p)
                ey += es * 1.8f
            }
        }
    }

    // ------------------------------------------------------------------ 5 Overlap

    private fun drawOverlap(c: Canvas, w: Float, h: Float) {
        val hs = hourStr(false)
        val ms = minStr()
        pr()
        p.typeface = tfBlack
        p.textSize = h * 0.9f
        var mh = p.measureText(hs)
        var mm = p.measureText(ms)
        var ov = p.textSize * 0.12f
        var total = mh + mm - ov
        if (total > w * 0.94f) {
            p.textSize = p.textSize * w * 0.94f / total
            mh = p.measureText(hs)
            mm = p.measureText(ms)
            ov = p.textSize * 0.12f
            total = mh + mm - ov
        }
        val size = p.textSize
        val x0 = (w - total) / 2f
        val cy = h * 0.5f
        val base = baseAt("0", cy)
        val dh = tb.height().toFloat()

        p.color = accent
        c.drawText(hs, x0, base, p)
        p.color = withAlpha(lighten(accent, 0.45f), 0xD2)
        c.drawText(ms, x0 + mh - ov, base, p)

        p.color = 0xDDEEEEEE.toInt()
        val cx = x0 + mh - ov / 2f
        val r = size * 0.065f
        c.drawCircle(cx, cy - dh * 0.2f, r, p)
        c.drawCircle(cx, cy + dh * 0.2f, r, p)
    }

    // ------------------------------------------------------------------ 6 Battery ring

    private fun drawRing(c: Canvas, w: Float, h: Float, now: Long) {
        val rr = min(h * 0.22f, w * 0.12f)
        val cx = w / 2f
        val cy = h / 2f
        val sw = rr * 0.2f
        val gray = 0xFF9A9A9A.toInt()

        // ring
        pr()
        p.style = Paint.Style.STROKE
        p.strokeWidth = sw
        p.color = 0x44FFFFFF
        c.drawCircle(cx, cy, rr, p)
        p.color = accent
        rect.set(cx - rr, cy - rr, cx + rr, cy + rr)
        c.drawArc(rect, -90f, 360f * battLevel / 100f, false, p)

        // percent
        pr()
        p.typeface = tfLight
        p.color = Color.WHITE
        p.textAlign = Paint.Align.CENTER
        p.textSize = rr * 0.5f
        c.drawText("$battLevel%", cx, baseAt("0", cy), p)

        if (charging) {
            pr()
            p.typeface = tfMed
            p.color = gray
            p.textAlign = Paint.Align.CENTER
            p.textSize = rr * 0.16f
            p.letterSpacing = 0.2f
            c.drawText("CHARGING", cx, cy + rr * 1.55f, p)
        }

        // date + time on the left
        val rx = cx - rr * 1.55f
        pr()
        p.textAlign = Paint.Align.RIGHT
        p.typeface = tfLight
        p.color = gray
        p.textSize = rr * 0.26f
        c.drawText(fmt("MM.dd", now), rx, cy - rr * 0.28f, p)
        c.drawText(fmt("EEEE", now), rx, cy + rr * 0.05f, p)
        p.color = Color.WHITE
        p.textSize = rr * 0.5f
        c.drawText(hourStr(true) + ":" + minStr(), rx, cy + rr * 0.58f, p)

        // tick arc on the far left; one tick lights up as seconds pass
        val tr = rr * 3.2f
        val len = rr * 0.16f
        val hi = (cal.get(Calendar.SECOND) / 3) % 21
        pr()
        p.style = Paint.Style.STROKE
        p.strokeWidth = rr * 0.03f
        p.strokeCap = Paint.Cap.ROUND
        for (i in 0..20) {
            val ang = Math.toRadians(180.0 + (i - 10) * 3.0)
            val ca = cos(ang).toFloat()
            val sa = sin(ang).toFloat()
            val fade = 1f - 0.7f * abs(i - 10) / 10f
            p.color = if (i == hi) accent else withAlpha(Color.WHITE, (fade * 200f).toInt())
            c.drawLine(
                cx + tr * ca, cy + tr * sa,
                cx + (tr - len) * ca, cy + (tr - len) * sa, p
            )
        }
    }
}
