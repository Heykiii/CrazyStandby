package com.crazystandby

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
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
import android.widget.Switch
import android.widget.TextView

class SettingsActivity : Activity() {

    private lateinit var preview: FlowClockView
    private lateinit var calBtn: Button

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

        // ---- live preview ----
        root.addView(label("Preview"))
        preview = FlowClockView(ctx)
        root.addView(
            preview,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(210))
        )

        // ---- style ----
        root.addView(label("Style"))
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
            preview.reload()
        }
        root.addView(group)

        // ---- color ----
        root.addView(label("Color"))
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
        root.addView(HorizontalScrollView(ctx).apply { addView(row) })

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
        root.addView(Switch(ctx).apply {
            text = "Dim screen at night (23:00 - 06:00)"
            setTextColor(Color.WHITE)
            isChecked = Prefs.dimAtNight(ctx)
            setOnCheckedChangeListener { _, v -> Prefs.setDimAtNight(ctx, v) }
        })

        calBtn = Button(ctx)
        calBtn.text = "Allow calendar access (for Calendar style)"
        calBtn.setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 1)
        }
        root.addView(calBtn)

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

    override fun onResume() {
        super.onResume()
        updateCalBtn()
    }

    private fun updateCalBtn() {
        val ok = checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        calBtn.visibility = if (ok) View.GONE else View.VISIBLE
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateCalBtn()
        preview.reload()
    }
}
