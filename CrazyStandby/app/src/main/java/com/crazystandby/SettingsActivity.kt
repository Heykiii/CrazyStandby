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
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

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
        root.addView(label("Style"))
        val colorViews = ArrayList<View>()
        fun applyStyleUi() {
            val gradient = Prefs.style(ctx) == 1
            colorViews.forEach { it.visibility = if (gradient) View.GONE else View.VISIBLE }
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
