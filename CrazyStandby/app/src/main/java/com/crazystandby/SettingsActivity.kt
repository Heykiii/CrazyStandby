package com.crazystandby

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
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
    private lateinit var calBtn: Button

    private fun setWindowBrightness(v: Float) {
        val lp = window.attributes
        lp.screenBrightness = v
        window.attributes = lp
    }

    private fun refreshCalBtn() {
        if (!::calBtn.isInitialized) return
        val ok = checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        calBtn.visibility = if (Prefs.style(this) == 10 && !ok) View.VISIBLE else View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = this
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val ink = Palette.GHOST_WHITE
        val dim = withAlpha(Palette.ALICE_BLUE, 0x99)
        val line = withAlpha(Palette.ALICE_BLUE, 0x33)

        fun section(t: String) = TextView(ctx).apply {
            text = t.uppercase()
            setTextColor(dim)
            textSize = 12f
            letterSpacing = 0.14f
            setPadding(0, dp(34), 0, dp(10))
        }

        fun flatButton(t: String, onClick: () -> Unit): Button {
            val b = Button(ctx)
            b.text = t
            b.isAllCaps = false
            b.setTextColor(ink)
            b.textSize = 14f
            val bg = GradientDrawable()
            bg.cornerRadius = dp(12).toFloat()
            bg.setColor(Color.TRANSPARENT)
            bg.setStroke(dp(1), line)
            b.background = bg
            b.stateListAnimator = null
            b.setOnClickListener { onClick() }
            return b
        }

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(36), dp(24), dp(48))
        }

        // list of tappable rows with a check mark (and an optional color dot)
        fun optionRows(
            names: List<String>,
            dotColors: IntArray?,
            sel: () -> Int,
            pick: (Int) -> Unit
        ): List<View> {
            val rows = ArrayList<View>()
            val checks = ArrayList<TextView>()
            fun refresh() {
                val s = sel()
                checks.forEachIndexed { i, t -> t.visibility = if (i == s) View.VISIBLE else View.INVISIBLE }
            }
            names.forEachIndexed { i, nm ->
                val row = LinearLayout(ctx)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(0, dp(12), 0, dp(12))
                if (dotColors != null) {
                    val dot = View(ctx)
                    val g = GradientDrawable()
                    g.shape = GradientDrawable.OVAL
                    g.setColor(dotColors[i])
                    g.setStroke(dp(1), line)
                    dot.background = g
                    row.addView(dot, LinearLayout.LayoutParams(dp(24), dp(24)).also { it.rightMargin = dp(16) })
                }
                val name = TextView(ctx)
                name.text = nm
                name.textSize = 16f
                name.setTextColor(ink)
                row.addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                val check = TextView(ctx)
                check.text = "✓"
                check.textSize = 16f
                check.setTextColor(Palette.HONEYDEW)
                row.addView(check)
                row.setOnClickListener {
                    pick(i)
                    refresh()
                }
                root.addView(row)
                rows.add(row)
                checks.add(check)
            }
            refresh()
            return rows
        }

        // ---- header: logo + name ----
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = ImageView(ctx)
        logo.setImageResource(R.drawable.ic_launcher_foreground)
        header.addView(logo, LinearLayout.LayoutParams(dp(76), dp(76)))
        val titles = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(TextView(ctx).apply {
            text = "Still"
            textSize = 30f
            setTextColor(ink)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        })
        titles.addView(TextView(ctx).apply {
            text = "A quiet clock while you charge"
            textSize = 13f
            setTextColor(dim)
        })
        header.addView(titles)
        root.addView(header)

        // ---- preview ----
        root.addView(section("Preview"))
        preview = FlowClockView(ctx)
        preview.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(18).toFloat())
            }
        }
        preview.clipToOutline = true
        root.addView(
            preview,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(210))
        )

        // groups of views that only matter for some layouts
        val numberViews = ArrayList<View>()
        val colorViews = ArrayList<View>()
        val neonViews = ArrayList<View>()
        val conViews = ArrayList<View>()
        val weatherViews = ArrayList<View>()
        val calViews = ArrayList<View>()
        fun applyStyleUi() {
            val st = Prefs.style(ctx)
            fun show(list: List<View>, on: Boolean) {
                list.forEach { it.visibility = if (on) View.VISIBLE else View.GONE }
            }
            show(numberViews, st != 4 && st != 5)
            show(colorViews, st != 1 && st != 4 && st != 11)
            show(neonViews, st == 4)
            show(conViews, st == 3)
            show(weatherViews, st == 2 || st == 9)
            show(calViews, st == 10)
            refreshCalBtn()
        }

        // ---- layout ----
        root.addView(section("Layout"))
        val group = RadioGroup(ctx)
        Prefs.STYLE_NAMES.forEachIndexed { i, name ->
            val rb = RadioButton(ctx)
            rb.id = 1000 + i
            rb.text = name
            rb.textSize = 16f
            rb.setTextColor(ink)
            rb.setPadding(dp(10), dp(10), 0, dp(10))
            group.addView(rb)
        }
        group.check(1000 + Prefs.style(ctx))
        group.setOnCheckedChangeListener { _, id ->
            Prefs.setStyle(ctx, id - 1000)
            applyStyleUi()
            preview.reload()
        }
        root.addView(group)

        // ---- number style (font) ----
        val numberHead = section("Number style")
        root.addView(numberHead)
        numberViews.add(numberHead)
        numberViews.addAll(
            optionRows(Prefs.NUMBER_FONT_NAMES, null, { Prefs.numberFont(ctx) }) {
                Prefs.setNumberFont(ctx, it)
                preview.reload()
            }
        )

        // ---- clock color ----
        val colorHead = section("Clock color")
        root.addView(colorHead)
        colorViews.add(colorHead)
        colorViews.addAll(
            optionRows(Prefs.COLOR_NAMES, Prefs.COLORS, { Prefs.colorIndex(ctx) }) {
                Prefs.setColor(ctx, it)
                preview.reload()
            }
        )

        // ---- neon color (wolf + moon clock) ----
        val neonHead = section("Neon color")
        root.addView(neonHead)
        neonViews.add(neonHead)
        neonViews.addAll(
            optionRows(NEON_NAMES, NEON, { Prefs.neon(ctx) }) {
                Prefs.setNeon(ctx, it)
                preview.reload()
            }
        )

        // ---- constellation (which animal) ----
        val conHead = section("Constellation")
        root.addView(conHead)
        conViews.add(conHead)
        conViews.addAll(
            optionRows(Prefs.CON_NAMES, null, { Prefs.conMode(ctx) }) {
                Prefs.setConMode(ctx, it)
                preview.reload()
            }
        )

        // ---- weather city ----
        val weatherHead = section("Weather city")
        val cityNow = TextView(ctx).apply {
            setTextColor(dim)
            textSize = 14f
            setPadding(0, 0, 0, dp(8))
        }
        fun refreshCityText() {
            cityNow.text = if (Prefs.hasCity(ctx)) "Selected: ${Prefs.cityName(ctx)}" else "No city selected"
        }
        refreshCityText()

        val input = EditText(ctx).apply {
            hint = "Type a city name"
            setHintTextColor(withAlpha(Palette.ALICE_BLUE, 0x55))
            setTextColor(ink)
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
                        item.setTextColor(ink)
                        item.setBackgroundColor(withAlpha(Palette.ALICE_BLUE, 0x14))
                        item.setPadding(dp(14), dp(14), dp(14), dp(14))
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
        val searchBtn = flatButton("Search city") { doSearch() }

        val fSwitch = Switch(ctx)
        fSwitch.text = "Fahrenheit (°F)"
        fSwitch.textSize = 16f
        fSwitch.setTextColor(ink)
        fSwitch.setPadding(0, dp(14), 0, dp(14))
        fSwitch.isChecked = Prefs.fahrenheit(ctx)
        fSwitch.setOnCheckedChangeListener { _, v ->
            Prefs.setFahrenheit(ctx, v)
            preview.reload()
        }

        val note = TextView(ctx)
        note.text = "Weather by Open-Meteo.com. No account and no location permission needed."
        note.setTextColor(withAlpha(Palette.ALICE_BLUE, 0x66))
        note.textSize = 12f

        for (v in listOf<View>(weatherHead, cityNow, input, searchBtn, results, fSwitch, note)) {
            root.addView(v)
            weatherViews.add(v)
        }

        // ---- calendar access (productivity grid) ----
        val calHead = section("Calendar")
        val calNote = TextView(ctx)
        calNote.text = "The agenda shows your next events. The app only reads them, it never changes anything."
        calNote.setTextColor(dim)
        calNote.textSize = 14f
        calNote.setPadding(0, 0, 0, dp(8))
        calBtn = flatButton("Allow calendar access") {
            requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 1)
        }
        root.addView(calHead)
        root.addView(calNote)
        root.addView(calBtn)
        calViews.add(calHead)
        calViews.add(calNote)

        // ---- brightness while charging ----
        root.addView(section("Brightness while charging"))
        val brightLabel = TextView(ctx)
        brightLabel.text = "${Prefs.brightness(ctx)}%"
        brightLabel.textSize = 22f
        brightLabel.setTextColor(ink)
        brightLabel.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        root.addView(brightLabel)
        val seek = SeekBar(ctx)
        seek.max = 99
        seek.progress = Prefs.brightness(ctx) - 1
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val pct = progress + 1
                brightLabel.text = "$pct%"
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
        root.addView(section("Options"))
        val h24 = Switch(ctx)
        h24.text = "24-hour time"
        h24.textSize = 16f
        h24.setTextColor(ink)
        h24.setPadding(0, dp(14), 0, dp(14))
        h24.isChecked = Prefs.use24h(ctx)
        h24.setOnCheckedChangeListener { _, v ->
            Prefs.setUse24h(ctx, v)
            preview.reload()
        }
        root.addView(h24)

        val saverBtn = flatButton("Open screen saver settings") {
            try {
                startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
            }
        }
        root.addView(
            saverBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(20) }
        )

        applyStyleUi()

        setContentView(ScrollView(ctx).apply {
            setBackgroundColor(Palette.EERIE_BLACK)
            addView(root)
        })
    }

    override fun onResume() {
        super.onResume()
        refreshCalBtn()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshCalBtn()
        preview.reload()
    }
}
