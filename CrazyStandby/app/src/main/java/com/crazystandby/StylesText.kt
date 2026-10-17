package com.crazystandby

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

private val GRAY = 0xFF8E8E93.toInt()

// ======================================================================
// 6. Retro flip clock
// ======================================================================

private fun FlowClockView.flipHalf(
    c: Canvas, s: String, cx: Float, base: Float, l: Float, t: Float, r: Float, b: Float
) {
    c.save()
    c.clipRect(l, t, r, b)
    c.drawText(s, cx, base, p)
    c.restore()
}

private fun FlowClockView.flipCard(
    c: Canvas, i: Int, s: String, l: Float, t: Float, cw: Float, ch: Float, now: Long
): Boolean {
    if (flipCur[i].isEmpty()) {
        flipCur[i] = s; flipPrev[i] = s; flipT[i] = 0L
    } else if (flipCur[i] != s) {
        flipPrev[i] = flipCur[i]; flipCur[i] = s; flipT[i] = now
    }
    val prog = if (flipT[i] == 0L) 1f else ((now - flipT[i]) / 450f).coerceIn(0f, 1f)

    val r = l + cw
    val b = t + ch
    val cy = t + ch / 2f
    val cx = l + cw / 2f
    val rad = cw * 0.05f

    pr()
    p.color = 0xFF1B1B1D.toInt()
    c.drawRoundRect(l, t, r, b, rad, rad, p)

    pr()
    p.typeface = digitFace(tfCond)
    p.textAlign = Paint.Align.CENTER
    p.color = accent
    fit("00", cw * 0.88f, ch * 0.98f)
    val base = baseAt("0", cy)

    // static halves: new on top, old on the bottom until the flip is done
    flipHalf(c, flipCur[i], cx, base, l, t, r, cy)
    flipHalf(c, if (prog < 1f) flipPrev[i] else flipCur[i], cx, base, l, cy, r, b)

    if (prog < 1f) {
        val sc = max(0.001f, if (prog < 0.5f) 1f - 2f * prog else 2f * prog - 1f)
        c.save()
        c.scale(1f, sc, cx, cy)
        if (prog < 0.5f) {
            c.clipRect(l, t, r, cy)
            p.color = 0xFF29292C.toInt()
            c.drawRect(l, t, r, cy, p)
            p.color = accent
            c.drawText(flipPrev[i], cx, base, p)
        } else {
            c.clipRect(l, cy, r, b)
            p.color = 0xFF1B1B1D.toInt()
            c.drawRect(l, cy, r, b, p)
            p.color = accent
            c.drawText(flipCur[i], cx, base, p)
        }
        c.restore()
    }

    p.color = Color.BLACK
    p.strokeWidth = max(2f, ch * 0.012f)
    c.drawLine(l, cy, r, cy, p)
    return prog < 1f
}

internal fun FlowClockView.drawRetroFlip(c: Canvas, w: Float, h: Float, now: Long) {
    val land = w > h * 1.1f
    val parts = arrayOf(hourStr().padStart(2, '0'), minStr())
    val gap = w * 0.03f
    val cw: Float
    val ch: Float
    val lx = FloatArray(2)
    val ty = FloatArray(2)
    if (land) {
        cw = (w * 0.86f - gap) / 2f
        ch = min(cw * 1.05f, h * 0.72f)
        val x0 = (w - (2f * cw + gap)) / 2f
        val y0 = (h - ch) / 2f - h * 0.02f
        lx[0] = x0; lx[1] = x0 + cw + gap
        ty[0] = y0; ty[1] = y0
    } else {
        cw = w * 0.8f
        ch = min(cw * 0.9f, h * 0.28f)
        val x0 = (w - cw) / 2f
        val yTop = h * 0.5f - ch - gap / 2f
        lx[0] = x0; lx[1] = x0
        ty[0] = yTop; ty[1] = yTop + ch + gap
    }
    var anim = false
    for (i in 0..1) {
        if (flipCard(c, i, parts[i], lx[i], ty[i], cw, ch, now)) anim = true
    }
    if (anim) frameMs = 33L

    val u = min(w, h * 1.6f)
    pr()
    p.typeface = tfMed
    p.color = GRAY
    p.textAlign = Paint.Align.CENTER
    p.textSize = u * 0.034f
    p.letterSpacing = 0.15f
    val bottom = ty[1] + ch
    c.drawText(fmt("EEE d MMM", now).uppercase(), w / 2f, bottom + u * 0.075f, p)
    if (!use24h) {
        p.textAlign = Paint.Align.LEFT
        c.drawText(if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM", lx[0], ty[0] - u * 0.02f, p)
    }
}

// ======================================================================
// 7. Dual ring: digital center, battery ring and minutes ring
// ======================================================================

internal fun FlowClockView.drawDualRing(c: Canvas, w: Float, h: Float, now: Long) {
    val cx = w / 2f
    val cy = h / 2f
    val r1 = min(w, h) * 0.44f      // battery ring
    val r2 = r1 * 0.88f             // minutes ring
    val sw = max(2f, r1 * 0.012f)
    val col2 = if (accent == Palette.VANILLA) Palette.ALICE_BLUE else Palette.VANILLA

    // faint tracks
    pr()
    p.style = Paint.Style.STROKE
    p.strokeWidth = sw
    p.color = withAlpha(Color.WHITE, 0x22)
    c.drawCircle(cx, cy, r1, p)
    c.drawCircle(cx, cy, r2, p)

    val minProg = (cal.get(Calendar.MINUTE) + cal.get(Calendar.SECOND) / 60f) / 60f
    glowArc(c, cx, cy, r1, 360f * battLevel / 100f, accent, sw)
    glowArc(c, cx, cy, r2, 360f * minProg, col2, sw)

    // time in the center
    val ts = timeStr()
    pr()
    p.typeface = digitFace(tfLight)
    p.color = accent
    p.textAlign = Paint.Align.CENTER
    fitBox(ts, r2 * 1.5f, r2 * 0.62f)
    c.drawText(ts, cx, baseAt("0", cy - r2 * 0.05f), p)
    val half = tb.height() / 2f

    pr()
    p.typeface = tfMed
    p.color = GRAY
    p.textAlign = Paint.Align.CENTER
    p.textSize = r2 * 0.1f
    p.letterSpacing = 0.12f
    c.drawText(fmt("EEE d MMM", now).uppercase(), cx, cy - r2 * 0.05f + half + r2 * 0.2f, p)
    c.drawText(batText(), cx, cy - r2 * 0.05f + half + r2 * 0.36f, p)
}

// ======================================================================
// 8. Bold modern block
// ======================================================================

internal fun FlowClockView.drawBoldBlock(c: Canvas, w: Float, h: Float, now: Long) {
    val land = w > h * 1.1f
    val hs = hourStr().padStart(2, '0')
    val ms = minStr()
    val face = digitFace(tfBlack)

    // hours: huge, top left
    pr()
    p.typeface = face
    p.color = accent
    fitBox(hs, if (land) w * 0.56f else w * 0.9f, if (land) h * 0.86f else h * 0.34f)
    p.getTextBounds("0", 0, 1, tb)
    c.drawText(hs, w * 0.03f, (if (land) h * 0.07f else h * 0.06f) - tb.top, p)

    // minutes: big, bottom right
    pr()
    p.typeface = face
    p.color = Palette.GHOST_WHITE
    p.textAlign = Paint.Align.RIGHT
    fitBox(ms, if (land) w * 0.38f else w * 0.9f, if (land) h * 0.6f else h * 0.34f)
    p.getTextBounds("0", 0, 1, tb)
    c.drawText(ms, w * 0.97f, (if (land) h * 0.93f else h * 0.88f) - tb.bottom, p)

    // small date label
    val u = min(w, h * 1.6f)
    pr()
    p.typeface = tfMed
    p.color = GRAY
    p.textSize = u * 0.034f
    p.letterSpacing = 0.2f
    val label = fmt("EEE d MMM", now).uppercase()
    if (land) {
        p.textAlign = Paint.Align.RIGHT
        c.drawText(label, w * 0.97f, h * 0.09f, p)
    } else {
        p.textAlign = Paint.Align.LEFT
        c.drawText(label, w * 0.05f, h * 0.95f, p)
    }
}

// ======================================================================
// 9. Ambient weather + time
// ======================================================================

internal fun FlowClockView.drawAmbient(c: Canvas, w: Float, h: Float, now: Long) {
    val land = w > h * 1.1f
    val ts = timeStr()

    // clock side
    val tx = if (land) w * 0.27f else w / 2f
    val ty = if (land) h * 0.46f else h * 0.22f
    pr()
    p.typeface = digitFace(tfLight)
    p.color = accent
    p.textAlign = Paint.Align.CENTER
    fitBox(ts, if (land) w * 0.46f else w * 0.88f, if (land) h * 0.5f else h * 0.16f)
    c.drawText(ts, tx, baseAt("0", ty), p)
    val half = tb.height() / 2f
    val u = min(w, h * 1.6f)
    pr()
    p.typeface = tfMed
    p.color = GRAY
    p.textAlign = Paint.Align.CENTER
    p.textSize = u * 0.036f
    p.letterSpacing = 0.12f
    c.drawText(fmt("EEEE d MMMM", now).uppercase(), tx, ty + half + u * 0.08f, p)

    // weather side
    val wx = if (land) w * 0.76f else w / 2f
    val yIcon = if (land) h * 0.30f else h * 0.47f
    val sIcon = if (land) h * 0.26f else w * 0.3f
    val yTemp = if (land) h * 0.62f else h * 0.67f
    val sTemp = if (land) h * 0.2f else w * 0.24f
    val yDesc = if (land) h * 0.73f else h * 0.73f
    val yHl = if (land) h * 0.81f else h * 0.78f
    val yCity = if (land) h * 0.90f else h * 0.84f
    val sTxt = if (land) h * 0.052f else w * 0.045f
    val maxW = if (land) w * 0.36f else w * 0.86f

    val wd = weather
    if (!hasCity || wd == null) {
        pr()
        p.typeface = tfMed
        p.textAlign = Paint.Align.CENTER
        p.textSize = sTxt * 1.1f
        p.color = Palette.ALICE_BLUE
        val l1 = if (hasCity) cityLabel else "No city selected"
        val l2 = when {
            !hasCity -> "Open the app to choose one"
            !loadedOnce -> "Loading weather…"
            else -> "Weather unavailable"
        }
        c.drawText(clip(l1, maxW), wx, h * 0.46f, p)
        p.color = GRAY
        c.drawText(clip(l2, maxW), wx, h * 0.46f + sTxt * 1.8f, p)
        return
    }

    drawIcon(c, kindOf(wd.code), wd.isDay, wx, yIcon, sIcon)

    pr()
    p.typeface = tfBold
    p.textAlign = Paint.Align.CENTER
    p.color = accent
    fit(temp(wd.tempC), maxW, sTemp)
    c.drawText(temp(wd.tempC), wx, yTemp, p)

    pr()
    p.typeface = tfMed
    p.textAlign = Paint.Align.CENTER
    p.textSize = sTxt
    p.color = Palette.ALICE_BLUE
    c.drawText(clip(describe(wd.code), maxW), wx, yDesc, p)
    p.color = GRAY
    if (!wd.hiC.isNaN() && !wd.loC.isNaN()) {
        c.drawText("H:" + temp(wd.hiC) + "   L:" + temp(wd.loC), wx, yHl, p)
    }
    p.textSize = sTxt * 0.8f
    c.drawText(clip(wd.city, maxW), wx, yCity, p)
}

// ======================================================================
// 10. Productivity grid: clock + mini agenda
// ======================================================================

internal fun FlowClockView.drawGrid(c: Canvas, w: Float, h: Float, now: Long) {
    val land = w > h * 1.1f
    val ts = timeStr()
    val u = min(w, h * 1.6f)

    // clock half
    val cx = if (land) w * 0.25f else w / 2f
    val cy = if (land) h * 0.46f else h * 0.2f
    pr()
    p.typeface = digitFace(tfLight)
    p.color = accent
    p.textAlign = Paint.Align.CENTER
    fitBox(ts, if (land) w * 0.42f else w * 0.86f, if (land) h * 0.4f else h * 0.14f)
    c.drawText(ts, cx, baseAt("0", cy), p)
    val half = tb.height() / 2f
    pr()
    p.typeface = tfMed
    p.color = GRAY
    p.textAlign = Paint.Align.CENTER
    p.textSize = u * 0.036f
    p.letterSpacing = 0.12f
    c.drawText(fmt("EEEE d MMMM", now).uppercase(), cx, cy + half + u * 0.08f, p)

    // divider
    pr()
    p.style = Paint.Style.STROKE
    p.strokeWidth = max(1.5f, u * 0.002f)
    p.color = withAlpha(Palette.ALICE_BLUE, 0x30)
    if (land) c.drawLine(w * 0.5f, h * 0.12f, w * 0.5f, h * 0.88f, p)
    else c.drawLine(w * 0.08f, h * 0.42f, w * 0.92f, h * 0.42f, p)

    // agenda half
    val ax = if (land) w * 0.56f else w * 0.08f
    var ay = if (land) h * 0.2f else h * 0.5f
    val es = if (land) h * 0.06f else w * 0.05f
    val avail = if (land) w * 0.4f else w * 0.84f
    pr()
    p.typeface = tfMed
    p.color = withAlpha(Palette.ALICE_BLUE, 0x99)
    p.textSize = es * 0.8f
    p.letterSpacing = 0.25f
    c.drawText("AGENDA", ax, ay, p)
    ay += es * 2.2f

    pr()
    p.typeface = tfMed
    p.textSize = es
    if (events.isEmpty()) {
        p.color = GRAY
        c.drawText(if (calOk) "No upcoming events" else "Allow calendar access in the app", ax, ay, p)
        return
    }
    val tp = if (use24h) "HH:mm" else "h:mm a"
    val today = fmt("yyyyMMdd", now)
    for (e in events) {
        val tm = when {
            e.allDay -> "All day"
            fmt("yyyyMMdd", e.begin) == today -> fmt(tp, e.begin)
            else -> fmt("EEE $tp", e.begin)
        }
        p.color = accent
        c.drawText(tm, ax, ay, p)
        val tw = p.measureText("$tm   ")
        p.color = Color.WHITE
        c.drawText(clip(e.title, avail - tw), ax + tw, ay, p)
        ay += es * 2.0f
    }
}

// ======================================================================
// 11. Soft glow / sleep mode
// ======================================================================

internal fun FlowClockView.drawSoftGlow(c: Canvas, w: Float, h: Float, now: Long) {
    // no seconds, so redraw only once per minute
    frameMs = 60000L - now % 60000L + 50L
    val ts = timeStr()
    val warm = 0xFF9A3B2A.toInt()    // muted red-orange, gentle in a dark room
    val warmDim = 0xFF5C261B.toInt()

    pr()
    p.typeface = digitFace(tfLight)
    p.color = warm
    p.textAlign = Paint.Align.CENTER
    fitBox(ts, w * 0.8f, h * 0.55f)
    c.drawText(ts, w / 2f, baseAt("0", h * 0.46f), p)
    val half = tb.height() / 2f

    val u = min(w, h * 1.6f)
    pr()
    p.typeface = tfMed
    p.color = warmDim
    p.textAlign = Paint.Align.CENTER
    p.textSize = u * 0.034f
    p.letterSpacing = 0.2f
    c.drawText(fmt("EEEE d MMMM", now).uppercase(), w / 2f, h * 0.46f + half + u * 0.08f, p)
}

// ======================================================================
// 12. Artistic kinetic: the time drifts to a new place every few minutes
// ======================================================================

internal fun FlowClockView.drawKinetic(c: Canvas, w: Float, h: Float, now: Long) {
    val land = w > h * 1.1f
    val ts = timeStr()
    pr()
    p.typeface = digitFace(tfCond)
    p.color = accent
    p.textAlign = Paint.Align.LEFT
    fitBox(ts, if (land) w * 0.5f else w * 0.78f, h * 0.3f)
    val tw = p.measureText(ts)
    p.getTextBounds("0", 0, 1, tb)
    val gh = tb.height().toFloat()
    val textSize = p.textSize

    val slotMs = 3L * 60000L          // new position every 3 minutes
    val slot = now / slotMs
    val tt = ((now - slot * slotMs) / 1800f).coerceIn(0f, 1f)   // 1.8 s glide
    val e = tt * tt * (3f - 2f * tt)
    if (tt < 1f) frameMs = 33L

    val fx = rnd(slot * 1.7 + 0.5) + (rnd((slot - 1) * 1.7 + 0.5) - rnd(slot * 1.7 + 0.5)) * (1f - e)
    val fy = rnd(slot * 3.3 + 5.0) + (rnd((slot - 1) * 3.3 + 5.0) - rnd(slot * 3.3 + 5.0)) * (1f - e)

    val mx = w * 0.04f
    val my = h * 0.05f
    val dateRoom = h * 0.09f
    val x = mx + fx * max(0f, w - tw - 2f * mx)
    val base = my + gh + fy * max(0f, h - gh - dateRoom - 2f * my)

    pr()
    p.typeface = digitFace(tfCond)
    p.color = accent
    p.textSize = textSize
    c.drawText(ts, x, base, p)

    pr()
    p.typeface = tfMed
    p.color = GRAY
    p.textSize = h * 0.04f
    p.letterSpacing = 0.15f
    c.drawText(fmt("EEE d MMM", now).uppercase(), x, base + h * 0.07f, p)
}
