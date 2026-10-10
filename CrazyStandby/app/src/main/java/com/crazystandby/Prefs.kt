package com.crazystandby

import android.content.Context
import android.text.format.DateFormat

object Prefs {
    private const val FILE = "crazy_standby"

    val STYLE_NAMES = listOf(
        "Aurora glow",
        "Flip clock",
        "Split digits",
        "Big time + date",
        "Calendar + events",
        "Overlap digits",
        "Battery ring"
    )

    val COLORS = intArrayOf(
        0xFFFFFFFF.toInt(), // white
        0xFF7EB0F7.toInt(), // blue
        0xFFFF6F61.toInt(), // coral
        0xFF5EE6A8.toInt(), // mint
        0xFFB388FF.toInt(), // purple
        0xFFFFC857.toInt(), // amber
        0xFFFF8AC6.toInt()  // pink
    )

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun style(c: Context) = sp(c).getInt("style", 1).coerceIn(0, STYLE_NAMES.size - 1)
    fun setStyle(c: Context, v: Int) = sp(c).edit().putInt("style", v).apply()

    fun colorIndex(c: Context) = sp(c).getInt("color", 1).coerceIn(0, COLORS.size - 1)
    fun color(c: Context) = COLORS[colorIndex(c)]
    fun setColor(c: Context, i: Int) = sp(c).edit().putInt("color", i).apply()

    fun use24h(c: Context) = sp(c).getBoolean("h24", DateFormat.is24HourFormat(c))
    fun setUse24h(c: Context, v: Boolean) = sp(c).edit().putBoolean("h24", v).apply()

    fun dimAtNight(c: Context) = sp(c).getBoolean("dim", true)
    fun setDimAtNight(c: Context, v: Boolean) = sp(c).edit().putBoolean("dim", v).apply()
}
