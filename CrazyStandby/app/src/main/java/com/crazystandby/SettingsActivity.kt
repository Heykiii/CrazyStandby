package com.crazystandby

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private lateinit var preview: FlowClockView

    private fun setWindowBrightness(v: Float) {
        val lp = window.attributes
        lp.screenBrightness = v
        window.attributes = lp
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = this
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        fun label(t: String) = TextView(ctx).apply {
            text = t
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(22), 0, dp(8))
        }

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(40))
        }

        root.addView(TextView(ctx).apply {
            text = "Crazy Standby"
            textSize = 26f
            setTextColor(Color.WHITE)
        })

        // ---- preview ----
        root.addView(label("Preview"))
        preview = FlowClockView(ctx)
        root.addView(
            preview,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(210))
        )

        // ---- style ----
        root.addView(label("Layout"))
        val colorViews = ArrayList<View>()     // only for "Big time + date"
        val weatherViews = ArrayList<View>()   // only for "Analog clock + weather"
        fun applyStyleUi() {
            val st = Prefs.style(ctx)
            colorViews.forEach { it.visibility = if (st == 0) View.VISIBLE else View.GONE }
            weatherViews.forEach { it.visibility = if (st == 2) View.VISIBLE else View.GONE }
        }
        val group = RadioGroup(ctx)
        Prefs.STYLE_NAMES.forEachIndexed { i, name ->
            val rb = RadioButton(ctx)
            rb.id = 1000 + i
            rb.text = name
            rb.textSize = 16f
            rb.setTextColor(Color.WHITE)
            group.addView(rb)
        }
        group.check(1000 + Prefs.style(ctx))
        group.setOnCheckedChangeListener { _, id ->
            Prefs.setStyle(ctx, id - 1000)
            applyStyleUi()
            preview.reload()
        }
        root.addView(group)

        // ---- color (only for the Big time style) ----
        val colorLabel = label("Color")
        root.addView(colorLabel)
        val swatches = ArrayList<View>()
        fun refreshSwatches() {
            val sel = Prefs.colorIndex(ctx)
            swatches.forEachIndexed { i, v ->
                val g = GradientDrawable()
                g.shape = GradientDrawable.OVAL
                g.setColor(Prefs.COLORS[i])
                if (i == sel) g.setStroke(dp(3), Color.WHITE) else g.setStroke(dp(1), 0x66FFFFFF)
                v.background = g
            }
        }
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        Prefs.COLORS.forEachIndexed { i, _ ->
            val v = View(ctx)
            val lp = LinearLayout.LayoutParams(dp(38), dp(38))
            lp.rightMargin = dp(8)
            v.setOnClickListener {
                Prefs.setColor(ctx, i)
                refreshSwatches()
                preview.reload()
            }
            row.addView(v, lp)
            swatches.add(v)
        }
        refreshSwatches()
        val colorScroll = HorizontalScrollView(ctx).apply { addView(row) }
        root.addView(colorScroll)
        colorViews.add(colorLabel)
        colorViews.add(colorScroll)

        // ---- weather city (only for the Analog + weather layout) ----
        val weatherLabel = label("Weather city")
        val cityNow = TextView(ctx).apply {
            setTextColor(0xFFBBBBBB.toInt())
            textSize = 14f
            setPadding(0, 0, 0, dp(8))
        }
        fun refreshCityText() {
            cityNow.text = if (Prefs.hasCity(ctx)) "Selected: ${Prefs.cityName(ctx)}" else "No city selected"
        }
        refreshCityText()

        val input = EditText(ctx).apply {
            hint = "Type a city name"
            setHintTextColor(0xFF777777.toInt())
            setTextColor(Color.WHITE)
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        val results = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        fun doSearch() {
            val q = input.text.toString().trim()
            if (q.length < 2) return
            results.removeAllViews()
            WeatherRepo.searchCity(q) { hits ->
                results.removeAllViews()
                if (hits == null) {
                    Toast.makeText(ctx, "Search failed. Check your internet connection.", Toast.LENGTH_LONG).show()
                } else if (hits.isEmpty()) {
                    Toast.makeText(ctx, "No city found", Toast.LENGTH_SHORT).show()
                } else {
                    hits.forEach { hit ->
                        val item = TextView(ctx)
                        item.text = hit.title
                        item.textSize = 16f
                        item.setTextColor(Color.WHITE)
                        item.setBackgroundColor(0xFF1A1A1C.toInt())
                        item.setPadding(dp(12), dp(14), dp(12), dp(14))
                        item.setOnClickListener {
                            Prefs.setCity(ctx, hit.name, hit.lat, hit.lon)
                            results.removeAllViews()
                            refreshCityText()
                            preview.reload(true)   // fetch the new city right away
                        }
                        val lp = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        lp.bottomMargin = dp(2)
                        results.addView(item, lp)
                    }
                }
            }
        }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch()
                true
            } else {
                false
            }
        }
        val searchBtn = Button(ctx)
        searchBtn.text = "Search city"
        searchBtn.setOnClickListener { doSearch() }

        val fSwitch = Switch(ctx)
        fSwitch.text = "Use Fahrenheit (°F)"
        fSwitch.setTextColor(Color.WHITE)
        fSwitch.isChecked = Prefs.fahrenheit(ctx)
        fSwitch.setOnCheckedChangeListener { _, v ->
            Prefs.setFahrenheit(ctx, v)
            preview.reload()
        }

        val note = TextView(ctx)
        note.text = "Weather data: Open-Meteo.com. No account or location permission needed."
        note.setTextColor(0xFF777777.toInt())
        note.textSize = 12f
        note.setPadding(0, dp(8), 0, 0)

        for (v in listOf<View>(weatherLabel, cityNow, input, searchBtn, results, fSwitch, note)) {
            root.addView(v)
            weatherViews.add(v)
        }
        applyStyleUi()

        // ---- brightness while charging ----
        val brightLabel = label("Brightness while charging: ${Prefs.brightness(ctx)}%")
        root.addView(brightLabel)
        val seek = SeekBar(ctx)
        seek.max = 99
        seek.progress = Prefs.brightness(ctx) - 1
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val pct = progress + 1
                brightLabel.text = "Brightness while charging: $pct%"
                if (fromUser) {
                    Prefs.setBrightness(ctx, pct)
                    setWindowBrightness(pct / 100f)   // live demo on this screen
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}

            override fun onStopTrackingTouch(sb: SeekBar?) {
                setWindowBrightness(-1f)              // back to normal brightness
            }
        })
        root.addView(seek)

        // ---- options ----
        root.addView(label("Options"))
        root.addView(Switch(ctx).apply {
            text = "24-hour time"
            setTextColor(Color.WHITE)
            isChecked = Prefs.use24h(ctx)
            setOnCheckedChangeListener { _, v ->
                Prefs.setUse24h(ctx, v)
                preview.reload()
            }
        })

        val saverBtn = Button(ctx)
        saverBtn.text = "Open screen saver settings"
        saverBtn.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
            }
        }
        root.addView(saverBtn)

        setContentView(ScrollView(ctx).apply {
            setBackgroundColor(Color.BLACK)
            addView(root)
        })
    }
}
