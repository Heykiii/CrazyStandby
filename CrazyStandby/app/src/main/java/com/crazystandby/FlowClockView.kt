package com.crazystandby

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.os.BatteryManager
import android.view.View
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the standby screens. Style 0: big time + date, 1: colorful digital clock,
 * 2: analog clock + weather. Black background everywhere (saves power on OLED).
 */
class FlowClockView(ctx: Context) : View(ctx) {

    private enum class Wx { SUN, PARTLY, CLOUD, FOG, RAIN, SNOW, STORM }

    private var style = 0
    private var accent = Color.WHITE
    private var use24h = true
    private var fahrenheit = false

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tb = Rect()
    private val cal: Calendar = Calendar.getInstance()
    private val loc: Locale = Locale.getDefault()
    private val fmts = HashMap<String, SimpleDateFormat>()

    private val tfCond = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val tfMed = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val tfBlack = Typeface.create("sans-serif-black", Typeface.NORMAL)
    private val tfLight = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val tfBold = Typeface.create("sans-serif", Typeface.BOLD)

    private var battLevel = 0
    private var charging = false
    private var battAt = 0L

    // weather (style 2)
    private var weather: WeatherData? = null
    private var cityLabel = ""
    private var hasCity = false
    private var weatherCheckAt = 0L
    private var forceWeather = false
    private var loadedOnce = false

    private val tick = Runnable { invalidate() }

    init {
        reload()
    }

    /** Re-reads the saved settings. */
    fun reload(forceWeather: Boolean = false) {
        style = Prefs.style(context)
        accent = Prefs.color(context)
        use24h = Prefs.use24h(context)
        fahrenheit = Prefs.fahrenheit(context)
        hasCity = Prefs.hasCity(context)
        cityLabel = Prefs.cityName(context)
        weather = if (style == 2) WeatherRepo.cached(context) else null
        loadedOnce = false
        weatherCheckAt = 0L
        this.forceWeather = forceWeather
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
        f.timeZone = TimeZone.getDefault()   // follows time-zone / DST changes
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

    private fun maybeWeather(now: Long) {
        if (!forceWeather && now - weatherCheckAt < 60000L) return
        weatherCheckAt = now
        val force = forceWeather
        forceWeather = false
        val started = WeatherRepo.refreshIfNeeded(context, force) { r ->
            if (r != null) weather = r
            loadedOnce = true
            invalidate()
        }
        if (!started) loadedOnce = true
    }

    // ------------------------------------------------------------------ main draw

    override fun onDraw(c: Canvas) {
        val now = System.currentTimeMillis()
        cal.timeZone = TimeZone.getDefault()
        cal.timeInMillis = now
        updateBattery(now)
        if (style == 2) maybeWeather(now)

        val w = width.toFloat()
        val h = height.toFloat()
        c.drawColor(Color.BLACK)

        // slow drift to protect OLED from burn-in
        val drift = min(w, h) * 0.012f
        val t = now / 60000.0
        c.save()
        c.translate((sin(t) * drift).toFloat(), (cos(t) * drift).toFloat())
        when (style) {
            1 -> drawGradient(c, w, h)
            2 -> drawAnalog(c, w, h, now)
            else -> drawClock(c, w, h, now)
        }
        c.restore()

        // analog clock redraws ~20x per second for a smooth second hand;
        // the other styles redraw once per second
        val delay = if (style == 2) ANALOG_FRAME_MS else 1000L - now % 1000L + 15L
        removeCallbacks(tick)
        postDelayed(tick, delay)
    }

    // ------------------------------------------------------------------ Style 0: big time + date

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

    // ------------------------------------------------------------------ Style 1: colorful digital clock

    private fun drawGradient(c: Canvas, w: Float, h: Float) {
        val hs = hourStr()
        val chars = (hs + minStr()).toCharArray()
        val nH = hs.length

        pr()
        p.typeface = tfBlack
        p.textSize = h * 0.95f
        val tight = 0.93f   // pulls the digits closer together

        fun total(): Float {
            var t = 0f
            for (ch in chars) t += p.measureText(ch.toString()) * tight
            return t + p.textSize * 0.22f   // room for the colon
        }

        val tot0 = total()
        if (tot0 > w * 0.92f) p.textSize = p.textSize * w * 0.92f / tot0
        val size = p.textSize
        val tot = total()
        val x0 = (w - tot) / 2f
        val cy = h * 0.5f
        val base = baseAt("0", cy)
        val top = base + tb.top
        val dh = tb.height().toFloat()

        // gradient colors (start, end): hours pink, minutes tens blue-violet, minutes ones purple
        val c1 = intArrayOf(0xFFFF6EC7.toInt(), 0xFFA9A3FF.toInt(), 0xFFC83FDB.toInt())
        val c2 = intArrayOf(0xFFE84FD9.toInt(), 0xFF8E89FF.toInt(), 0xFF9528D4.toInt())

        var x = x0
        for (i in chars.indices) {
            val s = chars[i].toString()
            val adv = p.measureText(s) * tight
            val g = if (i < nH) 0 else if (i == nH) 1 else 2
            p.shader = LinearGradient(x, top, x + adv, base, c1[g], c2[g], Shader.TileMode.CLAMP)
            c.drawText(s, x, base, p)
            x += adv

            if (i == nH - 1) {   // grey round colon between hours and minutes
                p.shader = null
                p.color = 0xFF9E9E9E.toInt()
                val r = size * 0.075f
                val cx = x + size * 0.11f
                c.drawCircle(cx, cy - dh * 0.2f, r, p)
                c.drawCircle(cx, cy + dh * 0.2f, r, p)
                x += size * 0.22f
            }
        }
    }

    // ------------------------------------------------------------------ Style 2: analog clock + weather

    private fun drawAnalog(c: Canvas, w: Float, h: Float, now: Long) {
        val land = w > h * 1.1f
        val r: Float
        val cx: Float
        val cy: Float
        if (land) {
            r = min(w * 0.225f * 0.96f, h * 0.46f)   // clock takes the left ~45% of the width
            cx = w * 0.25f
            cy = h * 0.5f
        } else {
            r = min(w * 0.42f, h * 0.22f)
            cx = w / 2f
            cy = h * 0.28f
        }
        drawDial(c, cx, cy, r)
        drawWeather(c, w, h, land)
    }

    private fun drawDial(c: Canvas, cx: Float, cy: Float, r: Float) {
        // dark, low-contrast face with a faint rim
        pr()
        p.color = 0xFF0C0C0E.toInt()
        c.drawCircle(cx, cy, r, p)
        pr()
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1.5f, r * 0.008f)
        p.color = 0xFF2C2C30.toInt()
        c.drawCircle(cx, cy, r, p)

        // tick marks
        pr()
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 60) {
            val major = i % 5 == 0
            val a = Math.toRadians(i * 6.0)
            val sa = sin(a).toFloat()
            val ca = cos(a).toFloat()
            val r1 = r * 0.95f
            val r2 = r1 - r * (if (major) 0.07f else 0.035f)
            p.strokeWidth = r * (if (major) 0.016f else 0.008f)
            p.color = if (major) accent else 0xFF6A6A70.toInt()
            c.drawLine(cx + r1 * sa, cy - r1 * ca, cx + r2 * sa, cy - r2 * ca, p)
        }

        // hour numbers
        pr()
        p.typeface = tfLight
        p.color = withAlpha(accent, 0xE0)
        p.textAlign = Paint.Align.CENTER
        p.textSize = r * 0.16f
        for (n in 1..12) {
            val a = Math.toRadians(n * 30.0)
            val nr = r * 0.76f
            val x = cx + nr * sin(a).toFloat()
            val y = cy - nr * cos(a).toFloat()
            val s = n.toString()
            c.drawText(s, x, baseAt(s, y), p)
        }

        // hands (continuous movement from the real local time)
        val sec = cal.get(Calendar.SECOND) + cal.get(Calendar.MILLISECOND) / 1000f
        val minF = cal.get(Calendar.MINUTE) + sec / 60f
        val hrF = (cal.get(Calendar.HOUR) % 12) + minF / 60f
        hand(c, cx, cy, hrF * 30f, r * 0.50f, r * 0.030f, accent, 0f)
        hand(c, cx, cy, minF * 6f, r * 0.78f, r * 0.020f, accent, 0f)
        hand(c, cx, cy, sec * 6f, r * 0.88f, r * 0.008f, 0xFFB5B5BA.toInt(), r * 0.14f)

        pr()
        p.color = accent
        c.drawCircle(cx, cy, r * 0.03f, p)
    }

    private fun hand(
        c: Canvas, cx: Float, cy: Float, deg: Float,
        len: Float, wid: Float, col: Int, tail: Float
    ) {
        val a = Math.toRadians(deg.toDouble())
        val sa = sin(a).toFloat()
        val ca = cos(a).toFloat()
        pr()
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = wid
        p.color = col
        c.drawLine(cx - tail * sa, cy + tail * ca, cx + len * sa, cy - len * ca, p)
    }

    private fun temp(tc: Double): String {
        val v = if (fahrenheit) tc * 9.0 / 5.0 + 32.0 else tc
        return Math.round(v).toString() + "°"
    }

    private fun drawWeather(c: Canvas, w: Float, h: Float, land: Boolean) {
        val gray = 0xFF8E8E93.toInt()
        val soft = 0xFFE6E6E9.toInt()
        val x: Float
        val align: Paint.Align
        val maxW: Float
        val yCity: Float
        val yTemp: Float
        val yRow: Float
        val yHl: Float
        val yUpd: Float
        val sCity: Float
        val sTemp: Float
        val sTxt: Float
        if (land) {
            x = w * 0.56f; align = Paint.Align.LEFT; maxW = w * 0.40f
            yCity = h * 0.22f; yTemp = h * 0.50f; yRow = h * 0.62f; yHl = h * 0.74f; yUpd = h * 0.85f
            sCity = h * 0.07f; sTemp = h * 0.30f; sTxt = h * 0.06f
        } else {
            x = w / 2f; align = Paint.Align.CENTER; maxW = w * 0.86f
            yCity = h * 0.60f; yTemp = h * 0.72f; yRow = h * 0.785f; yHl = h * 0.84f; yUpd = h * 0.91f
            sCity = w * 0.06f; sTemp = min(h * 0.14f, w * 0.30f); sTxt = w * 0.05f
        }

        val wd = weather

        // unavailable states: never show made-up numbers
        if (!hasCity || wd == null) {
            val line1 = if (hasCity) cityLabel else "No city selected"
            val line2 = when {
                !hasCity -> "Open the app to choose one"
                !loadedOnce -> "Loading weather…"
                else -> "Weather unavailable"
            }
            pr()
            p.typeface = tfMed
            p.textAlign = align
            p.color = soft
            p.textSize = sCity
            c.drawText(clip(line1, maxW), x, yCity, p)
            p.color = gray
            p.textSize = sTxt * 1.2f
            c.drawText(clip(line2, maxW), x, yTemp, p)
            return
        }

        // 1) location
        pr()
        p.typeface = tfMed
        p.textAlign = align
        p.color = soft
        p.textSize = sCity
        c.drawText(clip(wd.city, maxW), x, yCity, p)

        // 2) temperature
        pr()
        p.typeface = tfBold
        p.textAlign = align
        p.color = accent
        fit(temp(wd.tempC), maxW, sTemp)
        c.drawText(temp(wd.tempC), x, yTemp, p)

        // 3) icon + description
        pr()
        p.typeface = tfMed
        p.textSize = sTxt
        val iconS = sTxt * 1.8f
        val gap = sTxt * 0.5f
        val desc = clip(describe(wd.code), maxW - iconS - gap)
        val total = iconS + gap + p.measureText(desc)
        val sx = if (land) x else x - total / 2f
        drawIcon(c, kindOf(wd.code), wd.isDay, sx + iconS / 2f, yRow - sTxt * 0.35f, iconS)
        pr()
        p.typeface = tfMed
        p.textSize = sTxt
        p.color = soft
        c.drawText(desc, sx + iconS + gap, yRow, p)

        // 4) high / low
        if (!wd.hiC.isNaN() && !wd.loC.isNaN()) {
            pr()
            p.typeface = tfMed
            p.textAlign = align
            p.color = gray
            p.textSize = sTxt * 0.95f
            c.drawText("H:" + temp(wd.hiC) + "   L:" + temp(wd.loC), x, yHl, p)
        }

        // 5) when the data was last updated
        val tp = if (use24h) "HH:mm" else "h:mm a"
        val sameDay = fmt("yyyyMMdd", wd.updatedAt) == fmt("yyyyMMdd", System.currentTimeMillis())
        val upd = if (sameDay) fmt(tp, wd.updatedAt) else fmt("d MMM $tp", wd.updatedAt)
        pr()
        p.typeface = tfMed
        p.textAlign = align
        p.color = 0xFF5F5F64.toInt()
        p.textSize = sTxt * 0.7f
        c.drawText("Updated $upd", x, yUpd, p)
    }

    private fun kindOf(code: Int): Wx = when (code) {
        0, 1 -> Wx.SUN
        2 -> Wx.PARTLY
        3 -> Wx.CLOUD
        45, 48 -> Wx.FOG
        in 51..67 -> Wx.RAIN
        in 71..77 -> Wx.SNOW
        in 80..82 -> Wx.RAIN
        85, 86 -> Wx.SNOW
        in 95..99 -> Wx.STORM
        else -> Wx.CLOUD
    }

    private fun describe(code: Int): String = when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Rain showers"
        85, 86 -> "Snow showers"
        in 95..99 -> "Thunderstorm"
        else -> "Cloudy"
    }

    // ---- icons drawn with plain canvas shapes ----

    private fun drawIcon(c: Canvas, k: Wx, isDay: Boolean, cx: Float, cy: Float, s: Float) {
        val cloudCol = 0xFFD0D5DB.toInt()
        val sunCol = 0xFFFFC857.toInt()
        when (k) {
            Wx.SUN -> {
                if (isDay) sun(c, cx, cy, s * 0.5f, sunCol) else moon(c, cx, cy, s * 0.5f)
            }
            Wx.PARTLY -> {
                if (isDay) sun(c, cx - s * 0.17f, cy - s * 0.17f, s * 0.3f, sunCol)
                else moon(c, cx - s * 0.17f, cy - s * 0.17f, s * 0.3f)
                cloud(c, cx + s * 0.06f, cy + s * 0.1f, s * 0.8f, cloudCol)
            }
            Wx.CLOUD -> cloud(c, cx, cy, s * 0.95f, cloudCol)
            Wx.FOG -> {
                pr()
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                p.strokeWidth = s * 0.09f
                p.color = 0xFF9A9FA6.toInt()
                c.drawLine(cx - s * 0.4f, cy - s * 0.15f, cx + s * 0.4f, cy - s * 0.15f, p)
                c.drawLine(cx - s * 0.4f, cy, cx + s * 0.4f, cy, p)
                c.drawLine(cx - s * 0.4f, cy + s * 0.15f, cx + s * 0.1f, cy + s * 0.15f, p)
            }
            Wx.RAIN -> {
                cloud(c, cx, cy - s * 0.12f, s * 0.9f, cloudCol)
                pr()
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                p.strokeWidth = s * 0.07f
                p.color = 0xFF7EB0F7.toInt()
                for (dx in floatArrayOf(-0.22f, 0f, 0.22f)) {
                    c.drawLine(cx + dx * s, cy + s * 0.27f, cx + dx * s - s * 0.05f, cy + s * 0.46f, p)
                }
            }
            Wx.SNOW -> {
                cloud(c, cx, cy - s * 0.12f, s * 0.9f, cloudCol)
                pr()
                p.color = 0xFFEAF2FF.toInt()
                for (dx in floatArrayOf(-0.22f, 0f, 0.22f)) {
                    c.drawCircle(cx + dx * s, cy + s * 0.37f, s * 0.045f, p)
                }
            }
            Wx.STORM -> {
                cloud(c, cx, cy - s * 0.12f, s * 0.9f, 0xFFB8BDC4.toInt())
                val bolt = Path()
                bolt.moveTo(cx + s * 0.03f, cy + s * 0.18f)
                bolt.lineTo(cx - s * 0.10f, cy + s * 0.38f)
                bolt.lineTo(cx - s * 0.01f, cy + s * 0.38f)
                bolt.lineTo(cx - s * 0.06f, cy + s * 0.56f)
                bolt.lineTo(cx + s * 0.12f, cy + s * 0.32f)
                bolt.lineTo(cx + s * 0.03f, cy + s * 0.32f)
                bolt.lineTo(cx + s * 0.09f, cy + s * 0.18f)
                bolt.close()
                pr()
                p.color = sunCol
                c.drawPath(bolt, p)
            }
        }
    }

    private fun sun(c: Canvas, cx: Float, cy: Float, r: Float, col: Int) {
        pr()
        p.color = col
        c.drawCircle(cx, cy, r * 0.55f, p)
        pr()
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = r * 0.12f
        p.color = col
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val sa = sin(a).toFloat()
            val ca = cos(a).toFloat()
            c.drawLine(cx + r * 0.78f * sa, cy - r * 0.78f * ca, cx + r * sa, cy - r * ca, p)
        }
    }

    private fun moon(c: Canvas, cx: Float, cy: Float, r: Float) {
        pr()
        p.color = 0xFFE8E8EC.toInt()
        c.drawCircle(cx, cy, r * 0.75f, p)
        p.color = Color.BLACK   // carve the crescent out of the black background
        c.drawCircle(cx + r * 0.35f, cy - r * 0.25f, r * 0.65f, p)
    }

    private fun cloud(c: Canvas, cx: Float, cy0: Float, s: Float, col: Int) {
        val cy = cy0 + s * 0.06f
        pr()
        p.color = col
        c.drawRoundRect(cx - s * 0.5f, cy - s * 0.05f, cx + s * 0.5f, cy + s * 0.30f, s * 0.175f, s * 0.175f, p)
        c.drawCircle(cx - s * 0.2f, cy - s * 0.06f, s * 0.2f, p)
        c.drawCircle(cx + s * 0.1f, cy - s * 0.14f, s * 0.28f, p)
    }

    companion object {
        /** ~20 fps. Raise to 1000 for a once-per-second ticking second hand (uses less power). */
        private const val ANALOG_FRAME_MS = 50L
    }
}
