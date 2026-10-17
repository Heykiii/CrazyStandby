package com.crazystandby

import android.content.Context
import android.text.format.DateFormat

object Prefs {
    private const val FILE = "crazy_standby"

    // index 0, 1, 2 are used by FlowClockView
    val STYLE_NAMES = listOf("Big time + date", "Colorful digital clock", "Analog clock + weather")

    val COLORS = intArrayOf(
        0xFFFFFFFF.toInt(), // white
        0xFF6DB5F0.toInt(), // blue
        0xFFFF6F61.toInt(), // coral
        0xFF5EE6A8.toInt(), // mint
        0xFFB388FF.toInt(), // purple
        0xFFFFC857.toInt(), // amber
        0xFFFF8AC6.toInt()  // pink
    )

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun style(c: Context) = sp(c).getInt("style", 0).coerceIn(0, STYLE_NAMES.size - 1)
    fun setStyle(c: Context, v: Int) = sp(c).edit().putInt("style", v).apply()

    fun colorIndex(c: Context) = sp(c).getInt("color", 1).coerceIn(0, COLORS.size - 1)
    fun color(c: Context) = COLORS[colorIndex(c)]
    fun setColor(c: Context, i: Int) = sp(c).edit().putInt("color", i).apply()

    fun use24h(c: Context) = sp(c).getBoolean("h24", DateFormat.is24HourFormat(c))
    fun setUse24h(c: Context, v: Boolean) = sp(c).edit().putBoolean("h24", v).apply()

    /** Screen brightness while charging, in percent (1..100). */
    fun brightness(c: Context) = sp(c).getInt("bright", 15).coerceIn(1, 100)
    fun setBrightness(c: Context, v: Int) = sp(c).edit().putInt("bright", v).apply()

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
