package com.hazzmattx.flowlab

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/* Run log screen: shows the one shared log for every flow, newest first,
   from the stored entries (kept to the latest 200). Clear wipes the
   stored log; Back closes the screen. */
class LogActivity : Activity() {
    private lateinit var logText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        root.addView(TextView(this).apply { text = "Run log"; textSize = 24f })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val clearBtn = Button(this).apply {
            text = "Clear"
            setOnClickListener { LogStore.clear(this@LogActivity); refresh() }
        }
        val backBtn = Button(this).apply {
            text = "Back"
            setOnClickListener { finish() }
        }
        row.addView(clearBtn)
        row.addView(backBtn)
        root.addView(row)
        val scroll = ScrollView(this)
        logText = TextView(this).apply { textSize = 13f }
        scroll.addView(logText)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        refresh()
    }

    private fun refresh() {
        val entries = LogStore.load(this)
        logText.text = if (entries.isEmpty()) "Nothing run yet." else entries.reversed().joinToString("\n")
    }
}
