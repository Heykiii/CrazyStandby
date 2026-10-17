package com.crazystandby

import android.content.Context
import android.text.format.DateFormat

object Prefs {
    private const val FILE = "crazy_standby"

    // The index is the style number used by FlowClockView. Keep the order stable.
    val STYLE_NAMES = listOf(
        "Big time + date",           // 0
        "Colorful digital clock",    // 1
        "Analog clock + weather",    // 2
        "Constellation animals",     // 3
        "Neon wolf + moon clock",    // 4
        "Pixel pet companion",       // 5
        "Retro flip clock",          // 6
        "Dual ring (battery + minutes)", // 7
        "Bold modern block",         // 8
        "Ambient weather + time",    // 9
        "Productivity grid (agenda)",// 10
        "Soft glow (sleep mode)",    // 11
        "Artistic kinetic"           // 12
    )

    val NUMBER_FONT_NAMES = listOf(
        "System default",
        "Give You Glory",
        "Calligraphy (Zapfino-style)",
        "Baseball",
        "Varsity"
    )

    val CON_NAMES = listOf(
        "Auto: zodiac season",
        "Auto: day of the week",
        "Ursa Major (bear)",
        "Leo (lion)",
        "Pegasus (horse)"
    )

    // Clock colors the user can choose from (see Palette below)
    val COLORS = intArrayOf(
        Palette.HONEYDEW,
        Palette.VANILLA,
        Palette.GHOST_WHITE,
        Palette.ALICE_BLUE,
        Palette.CHOCOLATE
    )
    val COLOR_NAMES = listOf("Honeydew", "Vanilla", "Ghost White", "Alice Blue", "Chocolate")

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun style(c: Context) = sp(c).getInt("style", 0).coerceIn(0, STYLE_NAMES.size - 1)
    fun setStyle(c: Context, v: Int) = sp(c).edit().putInt("style", v).apply()

    fun colorIndex(c: Context) = sp(c).getInt("color", 0).coerceIn(0, COLORS.size - 1)
    fun color(c: Context) = COLORS[colorIndex(c)]
    fun setColor(c: Context, i: Int) = sp(c).edit().putInt("color", i).apply()

    fun use24h(c: Context) = sp(c).getBoolean("h24", DateFormat.is24HourFormat(c))
    fun setUse24h(c: Context, v: Boolean) = sp(c).edit().putBoolean("h24", v).apply()

    /** Screen brightness while charging, in percent (1..100). */
    fun brightness(c: Context) = sp(c).getInt("bright", 15).coerceIn(1, 100)
    fun setBrightness(c: Context, v: Int) = sp(c).edit().putInt("bright", v).apply()

    fun numberFont(c: Context) = sp(c).getInt("numfont", 0).coerceIn(0, NUMBER_FONT_NAMES.size - 1)
    fun setNumberFont(c: Context, v: Int) = sp(c).edit().putInt("numfont", v).apply()

    fun neon(c: Context) = sp(c).getInt("neon", 0).coerceIn(0, 2)
    fun setNeon(c: Context, v: Int) = sp(c).edit().putInt("neon", v).apply()

    fun conMode(c: Context) = sp(c).getInt("con", 0).coerceIn(0, CON_NAMES.size - 1)
    fun setConMode(c: Context, v: Int) = sp(c).edit().putInt("con", v).apply()

    // ---- weather ----
    fun fahrenheit(c: Context) = sp(c).getBoolean("fahrenheit", false)
    fun setFahrenheit(c: Context, v: Boolean) = sp(c).edit().putBoolean("fahrenheit", v).apply()

    fun cityName(c: Context): String = sp(c).getString("city_name", "") ?: ""
    fun cityLat(c: Context): Double = sp(c).getString("city_lat", "")?.toDoubleOrNull() ?: 0.0
    fun cityLon(c: Context): Double = sp(c).getString("city_lon", "")?.toDoubleOrNull() ?: 0.0
    fun hasCity(c: Context) = cityName(c).isNotEmpty() && sp(c).contains("city_lat")
    fun cityKey(c: Context): String =
        (sp(c).getString("city_lat", "") ?: "") + "," + (sp(c).getString("city_lon", "") ?: "")

    fun setCity(c: Context, name: String, lat: Double, lon: Double) =
        sp(c).edit()
            .putString("city_name", name)
            .putString("city_lat", lat.toString())
            .putString("city_lon", lon.toString())
            .apply()

    fun weatherCache(c: Context): String? = sp(c).getString("weather", null)
    fun setWeatherCache(c: Context, json: String) = sp(c).edit().putString("weather", json).apply()
}

/** The app palette. The standby screen itself stays pure black to save OLED power. */
object Palette {
    val EERIE_BLACK = 0xFF212121.toInt()   // app background
    val HONEYDEW = 0xFFF0FFF0.toInt()      // main accent
    val VANILLA = 0xFFEFF0A5.toInt()
    val GHOST_WHITE = 0xFFF8F8FF.toInt()   // main text
    val ALICE_BLUE = 0xFFF0F8FF.toInt()    // secondary text and lines
    val CHOCOLATE = 0xFFD2691E.toInt()
}

fun withAlpha(col: Int, a: Int): Int = (col and 0x00FFFFFF) or (a shl 24)
