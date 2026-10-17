package com.crazystandby

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ======================================================================
// 3. Constellation animals
// ======================================================================

/** conMode: 0 = by zodiac season, 1 = by day of week, 2..4 = fixed animal */
internal fun FlowClockView.pickConstellation(): Art.Con {
    val idx = when (conMode) {
        0 -> {
            val m = cal.get(Calendar.MONTH)          // 0 = January
            val d = cal.get(Calendar.DAY_OF_MONTH)
            when {
                (m == 6 && d >= 23) || (m == 7 && d <= 22) -> 1                       // Leo season
                (m == 7 && d >= 23) || m in 8..10 || (m == 11 && d <= 22) -> 2        // Pegasus (autumn sky)
                else -> 0                                                             // Ursa Major
            }
        }
        1 -> (cal.get(Calendar.DAY_OF_WEEK) - 1) % 3
        else -> (conMode - 2).coerceIn(0, 2)
    }
    return when (idx) {
        1 -> Art.LION
        2 -> Art.HORSE
        else -> Art.BEAR
    }
}

internal fun FlowClockView.drawConstellation(c: Canvas, w: Float, h: Float, now: Long) {
    frameMs = 66L
    val land = w > h * 1.1f
    val con = pickConstellation()

    // a few very faint background stars
    pr()
    for (i in 0 until 36) {
        val sx = rnd(i * 12.9898 + 1.0) * w
        val sy = rnd(i * 78.233 + 2.0) * h
        p.color = withAlpha(Color.WHITE, 0x1C + (rnd(i * 3.1) * 0x30).toInt())
        c.drawCircle(sx, sy, min(w, h) * 0.003f * (1f + rnd(i * 5.7)), p)
    }

    // constellation box (aspect 1.4 : 1)
    val boxW: Float
    val left: Float
    val top: Float
    if (land) {
        boxW = min(w * 0.5f, h * 0.72f * 1.4f)
        left = w * 0.04f + (w * 0.5f - boxW) / 2f
        top = (h - boxW / 1.4f) / 2f
    } else {
        boxW = w * 0.88f
        left = w * 0.06f
        top = h * 0.07f
    }
    val boxH = boxW / 1.4f
    val n = con.stars.size / 2

    // thin neon lines
    pr()
    p.style = Paint.Style.STROKE
    p.strokeCap = Paint.Cap.ROUND
    p.strokeWidth = max(1.5f, boxW * 0.004f)
    p.color = withAlpha(accent, 0x70)
    for (e in 0 until con.edges.size / 2) {
        val a = con.edges[e * 2]
        val b = con.edges[e * 2 + 1]
        c.drawLine(
            left + con.stars[a * 2] * boxW, top + con.stars[a * 2 + 1] * boxH,
            left + con.stars[b * 2] * boxW, top + con.stars[b * 2 + 1] * boxH, p
        )
    }

    // stars pulse once per second, as a soft ripple from star to star
    val f = (now % 1000L) / 1000f
    pr()
    for (i in 0 until n) {
        val sx = left + con.stars[i * 2] * boxW
        val sy = top + con.stars[i * 2 + 1] * boxH
        val pulse = 0.5f + 0.5f * sin(2.0 * PI * (f - i * 0.06)).toFloat()
        val r = boxW * 0.011f * (1f + 0.35f * pulse)
        p.color = withAlpha(accent, (0x18 + 0x22 * pulse).toInt())
        c.drawCircle(sx, sy, r * 3.2f, p)
        p.color = Color.WHITE
        c.drawCircle(sx, sy, r, p)
    }

    // time + date + constellation name
    val cx = if (land) w * 0.76f else w / 2f
    val cy = if (land) h * 0.46f else h * 0.68f
    pr()
    p.typeface = digitFace(tfLight)
    p.color = accent
    p.textAlign = Paint.Align.CENTER
    fitBox(timeStr(), if (land) w * 0.36f else w * 0.84f, if (land) h * 0.3f else h * 0.12f)
    c.drawText(timeStr(), cx, baseAt("0", cy), p)
    val half = tb.height() / 2f

    val u = min(w, h * 1.6f)
    pr()
    p.typeface = tfMed
    p.color = 0xFF8E8E93.toInt()
    p.textAlign = Paint.Align.CENTER
    p.textSize = u * 0.034f
    p.letterSpacing = 0.12f
    c.drawText(fmt("EEEE d MMMM", now).uppercase(), cx, cy + half + u * 0.075f, p)
    p.color = withAlpha(accent, 0xA0)
    p.letterSpacing = 0.3f
    p.textSize = u * 0.028f
    c.drawText(con.name.uppercase(), cx, cy + half + u * 0.14f, p)
}

// ======================================================================
// 4. Cyberpunk neon: moon clock + wolf silhouette
// ======================================================================

internal val NEON = intArrayOf(0xFF00F0FF.toInt(), 0xFFFF2BD6.toInt(), 0xFFB6FF00.toInt())
internal val NEON_NAMES = listOf("Cyan", "Magenta", "Lime")

internal fun FlowClockView.drawNeonWolf(c: Canvas, w: Float, h: Float, now: Long) {
    frameMs = 66L
    val land = w > h * 1.1f
    val neon = NEON[neonIdx.coerceIn(0, 2)]
    val neon2 = NEON[(neonIdx + 1) % 3]

    val mr: Float
    val mx: Float
    val my: Float
    val wolfH: Float
    val wolfX: Float
    if (land) {
        mr = h * 0.34f; mx = w * 0.62f; my = h * 0.44f
        wolfH = h * 0.52f
        wolfX = (mx - mr - w * 0.06f) - 0.69f * wolfH   // snout points at the moon
    } else {
        mr = w * 0.40f; mx = w * 0.5f; my = h * 0.34f
        wolfH = w * 0.62f
        wolfX = w * 0.04f
    }
    val baseY = h * 0.97f

    drawMoonClock(c, mx, my, mr, neon, neon2)

    // dark ground + wolf shadow with a thin neon edge
    val sc = wolfH / 100f
    val ox = wolfX
    val oy = baseY - wolfH
    val path = Path()
    var i = 0
    while (i < Art.WOLF.size) {
        val x = ox + Art.WOLF[i] * sc
        val y = oy + Art.WOLF[i + 1] * sc
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        i += 2
    }
    path.close()

    pr()
    p.color = 0xFF0A0A10.toInt()
    c.drawRect(0f, baseY, w, h, p)
    c.drawPath(path, p)

    pr()
    p.style = Paint.Style.STROKE
    p.strokeJoin = Paint.Join.ROUND
    p.strokeWidth = max(2f, h * 0.0035f)
    p.color = withAlpha(neon, 0xB0)
    c.drawPath(path, p)
    p.color = withAlpha(neon, 0x66)
    c.drawLine(0f, baseY, w, baseY, p)
}

private fun FlowClockView.drawMoonClock(
    c: Canvas, mx: Float, my: Float, mr: Float, neon: Int, neon2: Int
) {
    // moon body with faint craters
    pr()
    p.color = 0xFF0B0B12.toInt()
    c.drawCircle(mx, my, mr, p)
    p.color = withAlpha(neon, 0x12)
    c.drawCircle(mx - mr * 0.35f, my - mr * 0.30f, mr * 0.16f, p)
    c.drawCircle(mx + mr * 0.30f, my - mr * 0.10f, mr * 0.11f, p)
    c.drawCircle(mx - mr * 0.10f, my + mr * 0.38f, mr * 0.13f, p)

    // glowing rim
    val sw = mr * 0.022f
    pr()
    p.style = Paint.Style.STROKE
    val glowA = intArrayOf(0x10, 0x18, 0x28)
    for (k in 0..2) {
        p.strokeWidth = sw * (6f - k * 2f)
        p.color = withAlpha(neon, glowA[k])
        c.drawCircle(mx, my, mr, p)
    }
    p.strokeWidth = sw
    p.color = neon
    c.drawCircle(mx, my, mr, p)

    // 12 tick marks
    pr()
    p.style = Paint.Style.STROKE
    p.strokeCap = Paint.Cap.ROUND
    for (i in 0 until 12) {
        val a = Math.toRadians(i * 30.0)
        val sa = sin(a).toFloat()
        val ca = cos(a).toFloat()
        val long = i % 3 == 0
        val r1 = mr * 0.92f
        val r2 = r1 - mr * (if (long) 0.11f else 0.06f)
        p.strokeWidth = mr * (if (long) 0.022f else 0.012f)
        p.color = withAlpha(neon, if (long) 0xFF else 0xA0)
        c.drawLine(mx + r1 * sa, my - r1 * ca, mx + r2 * sa, my - r2 * ca, p)
    }

    // hands
    val sec = cal.get(Calendar.SECOND) + cal.get(Calendar.MILLISECOND) / 1000f
    val minF = cal.get(Calendar.MINUTE) + sec / 60f
    val hrF = (cal.get(Calendar.HOUR) % 12) + minF / 60f
    hand(c, mx, my, hrF * 30f, mr * 0.50f, mr * 0.040f, neon, 0f)
    hand(c, mx, my, minF * 6f, mr * 0.76f, mr * 0.026f, neon, 0f)
    hand(c, mx, my, sec * 6f, mr * 0.86f, mr * 0.010f, neon2, mr * 0.12f)
    pr()
    p.color = neon
    c.drawCircle(mx, my, mr * 0.035f, p)
}

// ======================================================================
// 5. Pixel pet companion
// ======================================================================

private fun FlowClockView.pixDigits(
    c: Canvas, s: String, x: Int, y: Int, px: Int, col: Int, colonOn: Boolean
) {
    pr()
    p.isAntiAlias = false
    p.color = col
    var cx = x
    for (ch in s) {
        if (ch == ':') {
            if (colonOn) {
                c.drawRect(cx.toFloat(), (y + 2 * px).toFloat(), (cx + px).toFloat(), (y + 3 * px).toFloat(), p)
                c.drawRect(cx.toFloat(), (y + 4 * px).toFloat(), (cx + px).toFloat(), (y + 5 * px).toFloat(), p)
            }
            cx += 2 * px
        } else {
            val g = Art.DIGITS[ch - '0']
            for (r in 0 until 7) {
                for (k in 0 until 5) {
                    if (g[r][k] == '1') {
                        c.drawRect(
                            (cx + k * px).toFloat(), (y + r * px).toFloat(),
                            (cx + (k + 1) * px).toFloat(), (y + (r + 1) * px).toFloat(), p
                        )
                    }
                }
            }
            cx += 6 * px
        }
    }
}

private fun FlowClockView.sprite(
    c: Canvas, rows: Array<String>, x: Int, y: Int, px: Int, color: (Char) -> Int
) {
    pr()
    p.isAntiAlias = false
    for (r in rows.indices) {
        for (k in rows[r].indices) {
            val col = color(rows[r][k])
            if (col != 0) {
                p.color = col
                c.drawRect(
                    (x + k * px).toFloat(), (y + r * px).toFloat(),
                    (x + (k + 1) * px).toFloat(), (y + (r + 1) * px).toFloat(), p
                )
            }
        }
    }
}

internal fun FlowClockView.drawPixelPet(c: Canvas, w: Float, h: Float, now: Long) {
    frameMs = 200L
    val land = w > h * 1.1f
    val sec = now / 1000L

    // chunky retro clock above the pet
    val ts = timeStr2()
    val ps = (if (land) min(h * 0.052f, w * 0.6f / 25f) else w * 0.8f / 25f).toInt().coerceAtLeast(2)
    val clockW = 25 * ps
    val clockX = ((w - clockW) / 2f).toInt()
    val clockY = (if (land) h * 0.12f else h * 0.30f).toInt()
    pixDigits(c, ts, clockX + ps / 2, clockY + ps / 2, ps, darken(accent, 0.65f), true)   // shadow
    pixDigits(c, ts, clockX, clockY, ps, accent, sec % 2L == 0L)

    // pet + food bowl
    val pp = (if (land) min(h * 0.36f / 15f, w * 0.28f / 16f) else w * 0.032f).toInt().coerceAtLeast(2)
    val groupW = 28 * pp
    val gx = ((w - groupW) / 2f).toInt()
    val baseY = (if (land) h * 0.90f else h * 0.80f).toInt()
    val petTop = baseY - 15 * pp
    val odd = sec % 2L == 1L
    val blink = sec % 7L == 6L
    val body = accent

    val rows = Art.CAT.mapIndexed { r, line ->
        if (blink && r == 4) line.replace('E', 'X').replace('W', 'X') else line
    }.toTypedArray()

    // breathing: the head moves down by one pixel every other second
    val head = rows.copyOfRange(0, 9)
    val rest = rows.copyOfRange(9, rows.size)
    val eye = if (fastCharging) Palette.VANILLA else Color.BLACK
    val colorOf: (Char) -> Int = { ch ->
        when (ch) {
            'X' -> body
            'x' -> darken(body, 0.45f)
            'w' -> darken(body, 0.14f)
            'W' -> Color.WHITE
            'E' -> eye
            'P' -> 0xFFFF8AC6.toInt()
            else -> 0
        }
    }
    sprite(c, rest, gx, petTop + 9 * pp, pp, colorOf)
    sprite(c, head, gx, petTop + (if (odd) pp else 0), pp, colorOf)

    // food bowl: fills up with the battery level
    val bx = gx + 20 * pp
    val by = baseY - 6 * pp
    sprite(c, Art.BOWL, bx, by, pp) { ch -> if (ch == 'X') 0xFFC8C8D2.toInt() else 0 }
    val fillRows = Math.ceil(battLevel / 100.0 * 3.0).toInt().coerceIn(0, 3)
    pr()
    p.isAntiAlias = false
    p.color = Palette.VANILLA
    for (k in 0 until fillRows) {
        val ry = by + (3 - k) * pp
        c.drawRect((bx + pp).toFloat(), ry.toFloat(), (bx + 7 * pp).toFloat(), (ry + pp).toFloat(), p)
    }
    if (battLevel >= 96) {
        c.drawRect((bx + 2 * pp).toFloat(), (by - pp).toFloat(), (bx + 6 * pp).toFloat(), by.toFloat(), p)
    }

    // ground line
    pr()
    p.isAntiAlias = false
    p.color = withAlpha(accent, 0x40)
    c.drawRect((gx - 4 * pp).toFloat(), baseY.toFloat(), (gx + groupW + 4 * pp).toFloat(), (baseY + max(2, pp / 2)).toFloat(), p)

    // electric sparks while fast charging
    if (fastCharging) {
        val t = now / 200L
        val bp = max(2, (pp * 0.75f).toInt())
        val spots = arrayOf(intArrayOf(-6, 2), intArrayOf(-5, 9), intArrayOf(17, 0))
        for (k in spots.indices) {
            if ((t + k) % 3L == 0L) continue
            val col = if ((t + k) % 2L == 0L) Palette.VANILLA else Color.WHITE
            sprite(c, Art.BOLT, gx + spots[k][0] * pp, petTop + spots[k][1] * pp, bp) { ch ->
                if (ch == 'X') col else 0
            }
        }
        pr()
        p.typeface = tfMed
        p.textAlign = Paint.Align.CENTER
        p.color = withAlpha(Palette.VANILLA, 0xB0)
        p.letterSpacing = 0.25f
        p.textSize = ps * 1.1f
        c.drawText("FAST CHARGING", w / 2f, baseY + pp * 3f, p)
    }
}
