package com.hazzmattx.flowlab

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.UUID

class MainActivity : Activity() {
    private var currentKind = "macro"
    private val EXPORT_REQUEST = 1001
    private val IMPORT_REQUEST = 1002
    private lateinit var gridBox: GridLayout
    private var searchQuery = ""
    private lateinit var tabMacros: Button
    private lateinit var tabAutomations: Button
    private lateinit var doStatus: TextView

    private fun dp(v: Int): Int {
        return (v * resources.displayMetrics.density).toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        val wanted = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                wanted.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                wanted.add(android.Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            wanted.add(android.Manifest.permission.CAMERA)
        }
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            wanted.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (wanted.isNotEmpty()) requestPermissions(wanted.toTypedArray(), 1)
        Scheduler.scheduleAll(this)

        // FrameLayout so the + button can float at the bottom-right.
        val root = FrameLayout(this)
        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        // Top bar: title on the left, three dots on the right for global options.
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val title = TextView(this).apply {
            text = "Flow"
            textSize = 30f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val topMenuBtn = Button(this).apply {
            text = "⋮"
            textSize = 22f
            setOnClickListener { showTopMenu() }
        }
        header.addView(title)
        header.addView(topMenuBtn)

        // Two tabs at the top, above the divider line.
        val tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tabMacros = Button(this).apply {
            text = "Macros"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { currentKind = "macro"; refresh() }
        }
        tabAutomations = Button(this).apply {
            text = "Automations"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { currentKind = "automation"; refresh() }
        }
        tabRow.addView(tabMacros)
        tabRow.addView(tabAutomations)

        val divider = android.view.View(this).apply {
            setBackgroundColor(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2)).apply {
                topMargin = dp(12); bottomMargin = dp(12)
            }
        }

        doStatus = TextView(this).apply { textSize = 13f }
        val searchEdit = android.widget.EditText(this).apply {
            hint = "Search flows..."
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) { searchQuery = s?.toString() ?: ""; refresh() }
            })
        }

        // Grid, not a list: two cards per row, each card is one macro or automation.
        val scroll = ScrollView(this)
        gridBox = GridLayout(this).apply {
            columnCount = 2
            setUseDefaultMargins(true)
        }
        scroll.addView(gridBox)

        main.addView(header)
        main.addView(tabRow)
        main.addView(divider)
        main.addView(doStatus)
        main.addView(searchEdit)
        main.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(main, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // Floating + button, bottom-right.
        val fab = Button(this).apply {
            text = "+"
            isAllCaps = false
            textSize = 28f
            setOnClickListener {
                val i = Intent(this@MainActivity, EditorActivity::class.java)
                i.putExtra("kind", currentKind)
                startActivity(i)
            }
        }
        val fabParams = FrameLayout.LayoutParams(dp(64), dp(64), Gravity.BOTTOM or Gravity.END).apply {
            bottomMargin = dp(24); rightMargin = dp(24)
        }
        root.addView(fab, fabParams)

        setContentView(root)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun showTopMenu() {
        val darkLabel = if (ThemePrefs.isDark(this)) "Dark mode: On" else "Dark mode: Off"
        val items = listOf("Run log", "Device Owner setup", "Remove Device Owner", darkLabel, "Export flows", "Import flows", "Choose Quick Settings tile flow", "Grant usage access", "Grant modify settings", "About")
        SearchablePicker.show(this, "Flow", items) { which ->
            when (items[which]) {
                "Run log" -> startActivity(Intent(this, LogActivity::class.java))
                "Device Owner setup" -> showDoSetup()
                "Remove Device Owner" -> showDoRemove()
                darkLabel -> {
                    ThemePrefs.setDark(this, !ThemePrefs.isDark(this))
                    recreate()
                }
                "Export flows" -> exportFlows()
                "Import flows" -> importFlows()
                "Choose Quick Settings tile flow" -> chooseQsTileFlow()
                "Grant usage access" -> {
                    try { startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)) } catch (e: Exception) { Toast.makeText(this, "Could not open usage access settings", Toast.LENGTH_SHORT).show() }
                }
                "Grant modify settings" -> {
                    try {
                        val i = Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS)
                        i.data = android.net.Uri.parse("package:" + packageName)
                        startActivity(i)
                    } catch (e: Exception) { Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show() }
                }
                else -> AlertDialog.Builder(this)
                    .setTitle("Flow")
                    .setMessage("Macros run when you tap them. Automations run on their own.\nBoth use the same steps, the same variables, and the same engine.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun chooseQsTileFlow() {
        val flows = Store.load(this)
        if (flows.isEmpty()) { Toast.makeText(this, "No flows yet", Toast.LENGTH_SHORT).show(); return }
        SearchablePicker.show(this, "Quick Settings tile flow", flows.map { it.name + " (" + it.kind + ")" }) { which ->
            getSharedPreferences("flowlab", MODE_PRIVATE).edit().putString("qs_tile_flow", flows[which].id).apply()
            Toast.makeText(this, "Tile set to " + flows[which].name + ". Add the Flow tile in Quick Settings to use it.", Toast.LENGTH_LONG).show()
        }
    }

    private fun exportFlows() {
        try {
            val flows = Store.load(this)
            val arr = org.json.JSONArray()
            for (f in flows) arr.put(f.toJson())
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "flow-export.json")
            }
            /* Stash the JSON in a field via the intent extra path: onActivityResult
               re-reads Store, so export always reflects the current save. */
            startActivityForResult(intent, EXPORT_REQUEST)
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun importFlows() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(intent, IMPORT_REQUEST)
        } catch (e: Exception) {
            Toast.makeText(this, "Import failed: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        try {
            if (requestCode == EXPORT_REQUEST) {
                val flows = Store.load(this)
                val arr = org.json.JSONArray()
                for (f in flows) arr.put(f.toJson())
                contentResolver.openOutputStream(uri)?.use { it.write(arr.toString().toByteArray(Charsets.UTF_8)) }
                Toast.makeText(this, "Exported " + flows.size + " flows", Toast.LENGTH_SHORT).show()
            } else if (requestCode == IMPORT_REQUEST) {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                val arr = org.json.JSONArray(text)
                val all = Store.load(this).toMutableList()
                var imported = 0
                for (i in 0 until arr.length()) {
                    val f = Flow.fromJson(arr.getJSONObject(i))
                    val existingIndex = all.indexOfFirst { it.id == f.id }
                    if (existingIndex >= 0) all[existingIndex] = f else all.add(f)
                    imported++
                }
                Store.save(this, all)
                Scheduler.scheduleAll(this)
                refresh()
                Toast.makeText(this, "Imported " + imported + " flows", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "That file did not work: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun showDoSetup() {
        val text = if (isDeviceOwner(this)) {
            "Flow IS the Device Owner on this phone. Bluetooth and Wi-Fi steps will run directly.\n\nTo remove it later, use Remove Device Owner in the top menu and the ADB command it shows."
        } else {
            "Flow is NOT a Device Owner yet. This is optional, and it is not needed for normal macros.\n\nIf you want it:\n1. Install Flow, then remove all accounts from Settings > Accounts, or factory reset if the command still fails.\n2. From a computer with ADB: adb shell dpm set-device-owner com.hazzmattx.flowlab/.FlowDeviceAdmin\n3. Re-add your accounts, reopen Flow, and this status should say Device Owner: Yes.\n\nWarning: a Device Owner app is hard to uninstall normally. You remove it with ADB first, see Remove Device Owner."
        }
        AlertDialog.Builder(this).setTitle("Device Owner (optional)").setMessage(text).setPositiveButton("OK") { _, _ -> refresh() }.show()
    }

    private fun showDoRemove() {
        val text = "From a computer with ADB:\n\nadb shell dpm remove-active-admin com.hazzmattx.flowlab/.FlowDeviceAdmin\n\nThen uninstall Flow normally. If that command fails, a factory reset also clears Device Owner."
        AlertDialog.Builder(this).setTitle("Remove Device Owner").setMessage(text).setPositiveButton("OK", null).show()
    }

    private fun showCardMenu(flow: Flow) {
        /* Plain list, no search box: searching between Edit / Delete /
           Duplicate is not needed. Search stays everywhere else. */
        val items = mutableListOf<String>()
        items.add("Edit")
        items.add("Duplicate")
        items.add("Delete")
        items.add("Add home-screen icon")
        if (flow.kind == "automation") {
            items.add(if (flow.enabled) "Turn off" else "Turn on")
            items.add("Test run")
        }
        AlertDialog.Builder(this).setTitle(flow.name)
            .setItems(items.toTypedArray()) { _, which ->
            when (items[which]) {
                "Edit" -> {
                    val i = Intent(this, EditorActivity::class.java)
                    i.putExtra("flowId", flow.id)
                    startActivity(i)
                }
                "Duplicate" -> {
                    val all = Store.load(this).toMutableList()
                    val copySteps = flow.steps.map { it.copy() }.toMutableList()
                    val copy = Flow(UUID.randomUUID().toString(), flow.kind, flow.name + " copy", flow.kind != "automation", flow.trigger, flow.hour, flow.minute, copySteps, flow.locLat, flow.locLng, flow.locRadius, flow.locName, flow.triggerThreshold, flow.locAddress, flow.triggerParam)
                    all.add(copy)
                    Store.save(this, all)
                    Toast.makeText(this, "Duplicated as " + copy.name, Toast.LENGTH_SHORT).show()
                    refresh()
                }
                "Delete" -> {
                    AlertDialog.Builder(this)
                        .setTitle("Delete " + flow.name + "?")
                        .setPositiveButton("Delete") { _, _ ->
                            val all = Store.load(this).filter { it.id != flow.id }
                            Store.save(this, all)
                            refresh()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                "Turn on", "Turn off" -> {
                    val all = Store.load(this)
                    val target = all.firstOrNull { it.id == flow.id }
                    if (target != null) {
                        target.enabled = !target.enabled
                        Store.save(this, all)
                        if (target.enabled) Scheduler.scheduleAll(this)
                    }
                    refresh()
                }
                "Add home-screen icon" -> addHomeScreenIcon(flow)
                "Test run" -> runFlow(flow)
            }
        }.show()
    }

    /* Pinned launcher shortcut for one flow, distinct from the widget
       and the Quick Settings tile. Tapping the icon runs the flow. */
    private fun addHomeScreenIcon(flow: Flow) {
        if (Build.VERSION.SDK_INT < 26) {
            Toast.makeText(this, "Home-screen icons need Android 8 or newer", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val sm = getSystemService(android.content.Context.SHORTCUT_SERVICE) as android.content.pm.ShortcutManager
            if (!sm.isRequestPinShortcutSupported) {
                Toast.makeText(this, "Your launcher does not support pinned shortcuts", Toast.LENGTH_LONG).show()
                return
            }
            val intent = Intent(this, FlowShortcutActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("flowId", flow.id)
            }
            val shortcut = android.content.pm.ShortcutInfo.Builder(this, "flow_" + flow.id)
                .setShortLabel(flow.name)
                .setIcon(android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_dialog_info))
                .setIntent(intent)
                .build()
            sm.requestPinShortcut(shortcut, null)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not add a home-screen icon: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun refresh() {
        doStatus.text = "Device Owner: " + if (isDeviceOwner(this)) "Yes" else "No (optional, top ⋮ menu)"
        /* Selected tab should look selected, not faded out. Both tabs stay tappable,
           the current one is darker and bold, the other is lighter.
           Dark mode uses dark greys with white / light-grey text. */
        val dark = ThemePrefs.isDark(this)
        val selectedBg = if (dark) Color.parseColor("#616161") else Color.parseColor("#BDBDBD")
        val unselectedBg = if (dark) Color.parseColor("#303030") else Color.parseColor("#E0E0E0")
        val selectedText = if (dark) Color.WHITE else Color.BLACK
        val unselectedText = if (dark) Color.parseColor("#CCCCCC") else Color.BLACK
        tabMacros.isEnabled = true
        tabAutomations.isEnabled = true
        tabMacros.setBackgroundColor(if (currentKind == "macro") selectedBg else unselectedBg)
        tabAutomations.setBackgroundColor(if (currentKind == "automation") selectedBg else unselectedBg)
        tabMacros.setTextColor(if (currentKind == "macro") selectedText else unselectedText)
        tabAutomations.setTextColor(if (currentKind == "automation") selectedText else unselectedText)
        tabMacros.setTypeface(null, if (currentKind == "macro") android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        tabAutomations.setTypeface(null, if (currentKind == "automation") android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        gridBox.removeAllViews()
        val flows = Store.load(this).filter { it.kind == currentKind && (searchQuery.isEmpty() || it.name.lowercase().contains(searchQuery.lowercase())) }
        if (flows.isEmpty()) {
            val empty = TextView(this).apply {
                text = if (currentKind == "macro") "No macros yet. Tap + to add one."
                    else "No automations yet. Tap + to add one, pick a trigger, then add steps."
                setPadding(0, dp(24), 0, dp(24))
            }
            val params = GridLayout.LayoutParams().apply {
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 2)
                width = GridLayout.LayoutParams.MATCH_PARENT
            }
            gridBox.addView(empty, params)
            gridBox.invalidate()
            return
        }
        for (flow in flows) {
            /* Card: tapping the card itself runs it. The small ⋮ in the corner is edit/delete.
               Text is left-aligned, not all-caps, and padded away from the ⋮ button,
               so the ⋮ no longer covers the flow name. */
            val card = FrameLayout(this).apply {
                alpha = if (flow.kind == "automation" && !flow.enabled) 0.55f else 1f
            }
            /* Name-only button. Trigger, steps, and On/Off are not shown on the
               card at all; they live in the editor and the card's ⋮ menu.
               A disabled automation is still dimmed, so off is still visible. */
            val runBtn = Button(this).apply {
                text = flow.name
                textSize = 16f
                isAllCaps = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(16), dp(56), dp(16))
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(100))
                setOnClickListener { runFlow(flow) }
            }
            val dotsBtn = Button(this).apply {
                text = "⋮"
                textSize = 18f
                isAllCaps = false
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = FrameLayout.LayoutParams(dp(40), dp(40), Gravity.TOP or Gravity.END)
                setOnClickListener { showCardMenu(flow) }
            }
            card.addView(runBtn)
            card.addView(dotsBtn)
            val params = GridLayout.LayoutParams().apply {
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                width = 0
                height = dp(100)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            gridBox.addView(card, params)
        }
        gridBox.invalidate()
        gridBox.requestLayout()
    }

    private fun runFlow(flow: Flow) {
        Toast.makeText(this, "Running " + flow.name, Toast.LENGTH_SHORT).show()
        Thread { Engine.run(this, flow) { } }.start()
    }
}
