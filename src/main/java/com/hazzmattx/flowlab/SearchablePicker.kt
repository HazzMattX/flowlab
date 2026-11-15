package com.hazzmattx.flowlab

import android.app.AlertDialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView

/* Reusable searchable list dialog, used by MainActivity menus, the
   EditorActivity pickers, and WidgetConfigActivity. */
object SearchablePicker {
    fun show(ctx: Context, title: String, items: List<String>, onPick: (Int) -> Unit) {
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 16) }
        val search = EditText(ctx).apply { hint = "Search..." }
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(ctx).apply { addView(box) }
        root.addView(search)
        root.addView(scroll)
        var dialog: AlertDialog? = null
        fun rebuild(query: String) {
            box.removeAllViews()
            for ((i, item) in items.withIndex()) {
                if (query.isNotEmpty() && !item.lowercase().contains(query.lowercase())) continue
                val btn = Button(ctx).apply {
                    text = item
                    setOnClickListener { dialog?.dismiss(); onPick(i) }
                }
                box.addView(btn)
            }
            if (box.childCount == 0) {
                val empty = android.widget.TextView(ctx).apply { text = "No matches." }
                box.addView(empty)
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { rebuild(s?.toString() ?: "") }
        })
        rebuild("")
        dialog = AlertDialog.Builder(ctx).setTitle(title).setView(root).setNegativeButton("Cancel", null).create()
        dialog.show()
    }
}
