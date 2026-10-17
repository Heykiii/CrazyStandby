package com.crazystandby

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors

/** Temperatures are always stored in Celsius; the UI converts if needed. */
data class WeatherData(
    val key: String,
    val city: String,
    val tempC: Double,
    val code: Int,          // WMO weather code
    val isDay: Boolean,
    val hiC: Double,        // NaN if unknown
    val loC: Double,        // NaN if unknown
    val updatedAt: Long
)

data class CityHit(val title: String, val name: String, val lat: Double, val lon: Double)

/**
 * Weather from Open-Meteo (https://open-meteo.com). No API key is needed for
 * non-commercial use. Network work runs on one background thread; results are
 * cached in SharedPreferences and delivered on the main thread.
 */
object WeatherRepo {
    private const val MAX_AGE_MS = 30L * 60L * 1000L   // refresh at most every 30 min
    private const val RETRY_MS = 5L * 60L * 1000L      // after a failure wait 5 min

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var inFlight = false
    @Volatile private var lastAttempt = 0L

    /** Last successful result for the selected city, or null. */
    fun cached(ctx: Context): WeatherData? {
        val s = Prefs.weatherCache(ctx) ?: return null
        return try {
            val o = JSONObject(s)
            val d = WeatherData(
                o.getString("key"), o.getString("city"), o.getDouble("t"), o.getInt("code"),
                o.getBoolean("day"), o.optDouble("hi", Double.NaN), o.optDouble("lo", Double.NaN),
                o.getLong("at")
            )
            if (d.key == Prefs.cityKey(ctx)) d else null
        } catch (e: Exception) {
            null
        }
    }

    private fun save(ctx: Context, d: WeatherData) {
        val o = JSONObject()
        o.put("key", d.key)
        o.put("city", d.city)
        o.put("t", d.tempC)
        o.put("code", d.code)
        o.put("day", d.isDay)
        if (!d.hiC.isNaN()) o.put("hi", d.hiC)
        if (!d.loC.isNaN()) o.put("lo", d.loC)
        o.put("at", d.updatedAt)
        Prefs.setWeatherCache(ctx, o.toString())
    }

    /**
     * Starts a refresh only if the cache is old (or [force]) and we are not in a
     * failure back-off. Returns true if a request was started; [onDone] then runs
     * on the main thread with the new data, or null if the request failed.
     */
    fun refreshIfNeeded(ctx: Context, force: Boolean, onDone: (WeatherData?) -> Unit): Boolean {
        val app = ctx.applicationContext
        if (!Prefs.hasCity(app) || inFlight) return false
        val now = System.currentTimeMillis()
        if (!force) {
            val cur = cached(app)
            val fresh = cur != null && now >= cur.updatedAt && now - cur.updatedAt < MAX_AGE_MS
            if (fresh) return false
            if (now - lastAttempt < RETRY_MS) return false
        }
        inFlight = true
        lastAttempt = now
        val key = Prefs.cityKey(app)
        val name = Prefs.cityName(app)
        val lat = Prefs.cityLat(app)
        val lon = Prefs.cityLon(app)
        io.execute {
            var result: WeatherData? = null
            try {
                result = fetch(key, name, lat, lon)
                save(app, result)
            } catch (e: Exception) {
                result = null   // network error, API error or bad data: keep the old cache
            }
            inFlight = false
            main.post { onDone(result) }
        }
        return true
    }

    /** City search for the settings screen. Callback gets null on failure. */
    fun searchCity(query: String, cb: (List<CityHit>?) -> Unit) {
        io.execute {
            var out: List<CityHit>? = null
            try {
                val url = "https://geocoding-api.open-meteo.com/v1/search?name=" +
                    URLEncoder.encode(query.trim(), "UTF-8") + "&count=6&language=en&format=json"
                val arr = JSONObject(httpGet(url)).optJSONArray("results")
                val list = ArrayList<CityHit>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val r = arr.getJSONObject(i)
                        val name = r.getString("name")
                        val title = listOf(name, r.optString("admin1", ""), r.optString("country", ""))
                            .filter { it.isNotEmpty() }
                            .joinToString(", ")
                        list.add(CityHit(title, name, r.getDouble("latitude"), r.getDouble("longitude")))
                    }
                }
                out = list
            } catch (e: Exception) {
                out = null
            }
            main.post { cb(out) }
        }
    }

    private fun fetch(key: String, name: String, lat: Double, lon: Double): WeatherData {
        val url = String.format(
            Locale.ROOT,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,weather_code,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1",
            lat, lon
        )
        val o = JSONObject(httpGet(url))
        val cur = o.getJSONObject("current")
        val daily = o.optJSONObject("daily")
        val hi = daily?.optJSONArray("temperature_2m_max")?.optDouble(0, Double.NaN) ?: Double.NaN
        val lo = daily?.optJSONArray("temperature_2m_min")?.optDouble(0, Double.NaN) ?: Double.NaN
        return WeatherData(
            key, name,
            cur.getDouble("temperature_2m"),
            cur.getInt("weather_code"),
            cur.optInt("is_day", 1) == 1,
            hi, lo,
            System.currentTimeMillis()
        )
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "CrazyStandby/1.0")
            if (conn.responseCode != 200) throw IOException("HTTP " + conn.responseCode)
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
