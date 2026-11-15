package com.hazzmattx.flowlab

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.graphics.Color
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.Button
import android.widget.EditText
import android.widget.MultiAutoCompleteTextView
import android.widget.ScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.UUID

class EditorActivity : Activity() {
    private lateinit var nameEdit: EditText
    private lateinit var triggerButton: Button
    private var selectedTriggerValue = "time"
    private lateinit var timeBox: LinearLayout
    private lateinit var hourEdit: EditText
    private lateinit var minuteEdit: EditText
    private lateinit var locBox: LinearLayout
    private lateinit var locNameEdit: EditText
    private lateinit var locAddressEdit: EditText
    private lateinit var locLatEdit: EditText
    private lateinit var locLngEdit: EditText
    private lateinit var locRadiusEdit: EditText
    private lateinit var thresholdBox: LinearLayout
    private lateinit var thresholdEdit: EditText
    private lateinit var triggerParamBox: LinearLayout
    private lateinit var triggerParamEdit: EditText
    private lateinit var weekdayBox: LinearLayout
    private lateinit var intervalBox: LinearLayout
    private lateinit var intervalEdit: EditText
    private lateinit var intervalUnitBtn: Button
    private var selectedIntervalUnit = "minutes"
    private lateinit var appTriggerBtn: Button
    private val selectedWeekdays = mutableSetOf<Int>()
    private var selectedAppPackage = ""
    private var selectedAppLabel = ""
    private lateinit var stepsBox: LinearLayout
    private lateinit var logView: TextView
    private var flowId: String? = null
    private var kind = "macro"
    private val steps = mutableListOf<Step>()
    /* Collapsed If/While blocks, by start index.
       Editor session only, not saved with the flow. */
    private val collapsedBlocks = mutableSetOf<Int>()

    fun builtins(): List<String> {
        return listOf("time", "date", "hour", "minute", "battery", "charging", "flashlight", "wifi", "bluetooth", "ringer", "dnd", "nfc", "location", "airplane", "vibrate_ringing")
    }

    fun variableColor(): Int {
        return if (ThemePrefs.isDark(this)) Color.parseColor("#64B5F6") else Color.parseColor("#1565C0")
    }

    fun userVariableColor(): Int {
        return if (ThemePrefs.isDark(this)) Color.parseColor("#81C784") else Color.parseColor("#2E7D32")
    }

    fun conditionalColor(): Int {
        return if (ThemePrefs.isDark(this)) Color.parseColor("#BA68C8") else Color.parseColor("#7B1FA2")
    }

    /* ${name} tokens are drawn in the variable colour, so they stand out from plain words. */
    fun colorVariableTokens(text: String, step: Step? = null): android.text.SpannableStringBuilder {
        val builder = android.text.SpannableStringBuilder(text)
        val tokenRegex = Regex("\\$\\{[^}]+\\}")
        for (m in tokenRegex.findAll(text)) {
            builder.setSpan(ForegroundColorSpan(variableColor()), m.range.first, m.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (step != null) {
            val varNames = mutableListOf<String>()
            when (step.type) {
                "if", "elseif", "while", "set_var", "ask_input", "get_time", "stop_if", "choose_list", "get_clipboard" -> if (step.p1.isNotEmpty()) varNames.add(step.p1)
                "get_location" -> { if (step.p1.isNotEmpty()) varNames.add(step.p1); if (step.p2.isNotEmpty()) varNames.add(step.p2) }
                "http_get", "get_value" -> if (step.p2.isNotEmpty()) varNames.add(step.p2)
                "random_number" -> if (step.op.isNotEmpty()) varNames.add(step.op)
                "file_text" -> if (step.p1 == "read" && step.op.isNotEmpty()) varNames.add(step.op)
            }
            for (name in varNames) {
                val wordRegex = Regex("\\b" + Regex.escape(name) + "\\b")
                for (m in wordRegex.findAll(text)) {
                    // Skip words already inside a ${} token, they are coloured above.
                    val insideToken = tokenRegex.findAll(text).any { m.range.first >= it.range.first && m.range.last <= it.range.last }
                    if (!insideToken) builder.setSpan(ForegroundColorSpan(variableColor()), m.range.first, m.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        /* Conditional keywords in purple bold, longest phrases first,
           so "If" inside "Else If" is not coloured twice. */
        val colouredRanges = mutableListOf<IntRange>()
        for (phrase in listOf("Else If", "End If", "End While", "End Repeat", "Repeat", "While", "If", "Else")) {
            val phraseRegex = Regex("\\b" + Regex.escape(phrase) + "\\b")
            for (m in phraseRegex.findAll(text)) {
                val range = m.range
                if (colouredRanges.any { it.first <= range.last && range.first <= it.last }) continue
                colouredRanges.add(range)
                builder.setSpan(ForegroundColorSpan(conditionalColor()), range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(StyleSpan(android.graphics.Typeface.BOLD), range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return builder
    }

    fun attachVariableHighlight(edit: EditText) {
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s == null) return
                val spans = s.getSpans(0, s.length, ForegroundColorSpan::class.java)
                for (span in spans) s.removeSpan(span)
                val tokenRegex = Regex("\\$\\{[^}]+\\}")
                for (m in tokenRegex.findAll(s.toString())) {
                    s.setSpan(ForegroundColorSpan(variableColor()), m.range.first, m.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        })
    }

    fun variableSuggestions(): List<String> {
        val builtins = builtins()
        val created = mutableSetOf<String>()
        fun collect(list: List<Step>) {
            for (s in list) {
                when (s.type) {
                    "set_var", "ask_input", "get_time", "stop_if", "if", "elseif", "while", "var_op", "choose_list", "get_clipboard" -> if (s.p1.isNotEmpty()) created.add(s.p1)
                    "get_location" -> { if (s.p1.isNotEmpty()) created.add(s.p1); if (s.p2.isNotEmpty()) created.add(s.p2) }
                    "http_get", "get_value" -> if (s.p2.isNotEmpty()) created.add(s.p2)
                    "http_post", "random_number" -> if (s.op.isNotEmpty()) created.add(s.op)
                    "file_text" -> if (s.p1 == "read" && s.op.isNotEmpty()) created.add(s.op)
                }
            }
        }
        collect(steps)
        try {
            for (f in Store.load(this)) collect(f.steps)
        } catch (e: Exception) {
        }
        return (builtins + created.sorted()).distinct()
    }

    /* Variable-name fields: nothing pops up while typing; Pick variable
       replaces the whole field. Free-text fields trigger on typing ${.
       Built-ins show in blue, your variables in green. */
    private fun showColoredVariableDialog(edit: EditText, insertAsToken: Boolean, title: String) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 16) }
        val scroll = ScrollView(this).apply { addView(box) }
        var dialog: AlertDialog? = null
        fun addSection(header: String, names: List<String>, colour: Int) {
            if (names.isEmpty()) return
            box.addView(TextView(this).apply { text = header; textSize = 16f; setTypeface(null, android.graphics.Typeface.BOLD) })
            for (name in names) {
                val btn = Button(this).apply {
                    text = name
                    setTextColor(colour)
                    setOnClickListener {
                        if (insertAsToken) {
                            val insert = "\${" + name + "}"
                            val cursor = edit.selectionStart.coerceIn(0, edit.text.length)
                            edit.text.insert(cursor, insert)
                            edit.setSelection((cursor + insert.length).coerceAtMost(edit.text.length))
                        } else {
                            edit.setText(name)
                            edit.setSelection(name.length)
                        }
                        dialog?.dismiss()
                    }
                }
                box.addView(btn)
            }
        }
        val all = variableSuggestions()
        val built = builtins()
        addSection("Built-in variables", built.filter { it in all }, variableColor())
        addSection("Your variables", all.filter { it !in built }.sorted(), userVariableColor())
        dialog = AlertDialog.Builder(this).setTitle(title).setView(scroll).setNegativeButton("Cancel", null).create()
        dialog.show()
    }

    private fun showVariableNamePicker(edit: EditText) {
        showColoredVariableDialog(edit, false, "Pick a variable")
    }

    private fun pickVariableButton(edit: EditText): Button {
        return Button(this).apply {
            text = "Pick variable..."
            setOnClickListener { showVariableNamePicker(edit) }
        }
    }

    /* Autocomplete for free text: the token is whatever follows an unclosed
       ${, so typing "Battery is ${b" offers battery, bluetooth, and so on. */
    private class VariableTokenizer : MultiAutoCompleteTextView.Tokenizer {
        override fun findTokenStart(text: CharSequence, cursor: Int): Int {
            var i = cursor
            while (i > 1) {
                if (text[i - 2] == '$' && text[i - 1] == '{') return i
                if (text[i - 1] == '}') return cursor
                i--
            }
            return cursor
        }

        override fun findTokenEnd(text: CharSequence, cursor: Int): Int {
            var i = cursor
            while (i < text.length) {
                if (text[i] == '}') return i + 1
                i++
            }
            return text.length
        }

        override fun terminateToken(text: CharSequence): CharSequence {
            val s = text.toString()
            return if (s.endsWith("}")) s else s + "}"
        }
    }

    private fun codingEdit(hint: String): MultiAutoCompleteTextView {
        val view = MultiAutoCompleteTextView(this)
        view.hint = hint
        view.threshold = 1
        view.setAdapter(android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, variableSuggestions()))
        view.setTokenizer(VariableTokenizer())
        attachVariableHighlight(view)
        return view
    }

    /* Variable picker: opens the same variable list as autocomplete and
       inserts ${name} at the cursor, so you don't have to type the syntax. */
    private fun showVariablePicker(edit: EditText) {
        showColoredVariableDialog(edit, true, "Insert variable")
    }

    private fun insertVariableButton(edit: EditText): Button {
        return Button(this).apply {
            text = "Insert variable..."
            setOnClickListener { showVariablePicker(edit) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        flowId = intent.getStringExtra("flowId")
        val existing = flowId?.let { Store.byId(this, it) }
        if (existing != null) {
            kind = existing.kind
            steps.addAll(existing.steps)
        } else {
            val rawKind = intent.getStringExtra("kind") ?: "macro"
            kind = if (rawKind == "shortcut") "macro" else rawKind
        }
        if (kind == "macro") selectedTriggerValue = "none" else selectedTriggerValue = existing?.trigger ?: "time"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        val title = TextView(this).apply {
            text = if (existing == null) {
                if (kind == "macro") "New macro" else "New automation"
            } else "Edit " + existing.name
            textSize = 24f
        }
        val kindNote = TextView(this).apply {
            text = if (kind == "macro") {
                "Macro: runs only when you tap Run. Steps below are the shared step library."
            } else {
                "Automation: runs on its own when its trigger fires. Steps below are the same shared step library as macros."
            }
            textSize = 14f
        }
        nameEdit = EditText(this).apply {
            hint = "Name"
            setText(existing?.name ?: "")
        }
        root.addView(title)
        root.addView(kindNote)
        root.addView(nameEdit)

        timeBox = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        hourEdit = EditText(this).apply {
            hint = "Hour 0-23"; setText((existing?.hour ?: 9).toString())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        minuteEdit = EditText(this).apply {
            hint = "Minute 0-59"; setText((existing?.minute ?: 0).toString())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        timeBox.addView(hourEdit); timeBox.addView(minuteEdit)
        locBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        locNameEdit = EditText(this).apply { hint = "Internal label, never shown on card"; setText(existing?.locName ?: "") }
        locAddressEdit = EditText(this).apply { hint = "Address for lookup only, never shown on card"; setText(existing?.locAddress ?: "") }
        locLatEdit = EditText(this).apply { hint = "Latitude, e.g. 47.6062"; setText(if ((existing?.locLat ?: 0.0) != 0.0) (existing?.locLat ?: 0.0).toString() else "") }
        locLngEdit = EditText(this).apply { hint = "Longitude, e.g. -122.3321"; setText(if ((existing?.locLng ?: 0.0) != 0.0) (existing?.locLng ?: 0.0).toString() else "") }
        locRadiusEdit = EditText(this).apply { hint = "Radius in metres, e.g. 200"; setText((existing?.locRadius ?: 200).toString()) }
        val lookupBtn = Button(this).apply {
            text = "Look up address"
            setOnClickListener { lookupAddress() }
        }
        locBox.addView(locNameEdit); locBox.addView(locAddressEdit); locBox.addView(locLatEdit); locBox.addView(locLngEdit); locBox.addView(locRadiusEdit); locBox.addView(lookupBtn)
        thresholdBox = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        thresholdEdit = EditText(this).apply { hint = "Battery % threshold, e.g. 20"; setText((existing?.triggerThreshold ?: 20).toString()) }
        thresholdBox.addView(thresholdEdit)
        triggerParamBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        triggerParamEdit = EditText(this).apply { hint = "Value"; setText(existing?.triggerParam ?: "") }
        triggerParamBox.addView(triggerParamEdit)
        weekdayBox = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val dayNames = listOf("Mon" to java.util.Calendar.MONDAY, "Tue" to java.util.Calendar.TUESDAY, "Wed" to java.util.Calendar.WEDNESDAY, "Thu" to java.util.Calendar.THURSDAY, "Fri" to java.util.Calendar.FRIDAY, "Sat" to java.util.Calendar.SATURDAY, "Sun" to java.util.Calendar.SUNDAY)
        val existingDays = (existing?.triggerParam ?: "").split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        if (existingDays.isEmpty()) selectedWeekdays.addAll(listOf(java.util.Calendar.MONDAY, java.util.Calendar.TUESDAY, java.util.Calendar.WEDNESDAY, java.util.Calendar.THURSDAY, java.util.Calendar.FRIDAY)) else selectedWeekdays.addAll(existingDays)
        for ((label, day) in dayNames) {
            val btn = Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener {
                    if (selectedWeekdays.contains(day)) selectedWeekdays.remove(day) else selectedWeekdays.add(day)
                    updateWeekdayButtons()
                }
            }
            btn.tag = day
            weekdayBox.addView(btn)
        }
        intervalBox = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val existingInterval = (existing?.triggerParam ?: "").split(":")
        if (existing?.trigger == "interval") {
            selectedIntervalUnit = if (existingInterval.getOrNull(1) == "hours") "hours" else "minutes"
        }
        intervalEdit = EditText(this).apply {
            hint = "Every how many?"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(if (existing?.trigger == "interval") (existingInterval.getOrNull(0) ?: "30") else "30")
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        intervalUnitBtn = Button(this).apply {
            text = selectedIntervalUnit.replaceFirstChar { it.uppercase() }
            setOnClickListener {
                selectedIntervalUnit = if (selectedIntervalUnit == "minutes") "hours" else "minutes"
                text = selectedIntervalUnit.replaceFirstChar { it.uppercase() }
            }
        }
        intervalBox.addView(intervalEdit); intervalBox.addView(intervalUnitBtn)
        appTriggerBtn = Button(this).apply {
            text = "Choose app..."
            setOnClickListener { showAppTriggerPicker() }
        }
        if (existing?.trigger == "app_opened") { selectedAppPackage = existing.triggerParam; selectedAppLabel = existing.triggerParam }
        updateWeekdayButtons()

        if (kind == "automation") {
            val trigLabel = TextView(this).apply { text = "Trigger"; textSize = 16f }
            root.addView(trigLabel)
            triggerButton = Button(this).apply {
                text = triggerLabel(selectedTriggerValue)
                setOnClickListener { showTriggerPicker() }
            }
            root.addView(triggerButton)
            root.addView(timeBox)
            root.addView(locBox)
            root.addView(thresholdBox)
            root.addView(triggerParamBox)
            root.addView(weekdayBox)
            root.addView(intervalBox)
            root.addView(appTriggerBtn)
            updateTriggerVisibility()
        }

        val stepsLabel = TextView(this).apply { text = "Steps (shared library)"; textSize = 16f }
        root.addView(stepsLabel)
        val scroll = ScrollView(this)
        stepsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(stepsBox)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val addBtn = Button(this).apply {
            text = "+ Add step"
            setOnClickListener { showStepPicker() }
        }
        val saveBtn = Button(this).apply {
            text = "Save"
            setOnClickListener { saveAndFinish() }
        }
        val testBtn = Button(this).apply {
            text = "Test run"
            setOnClickListener { testRun() }
        }
        logView = TextView(this).apply { text = "" }
        root.addView(addBtn)
        root.addView(saveBtn)
        root.addView(testBtn)
        root.addView(logView)
        setContentView(root)
        refreshSteps()
    }

    private fun updateWeekdayButtons() {
        for (i in 0 until weekdayBox.childCount) {
            val btn = weekdayBox.getChildAt(i) as? Button ?: continue
            val day = btn.tag as? Int ?: continue
            btn.setBackgroundColor(if (selectedWeekdays.contains(day)) android.graphics.Color.parseColor("#64B5F6") else android.graphics.Color.TRANSPARENT)
        }
    }

    private fun showAppTriggerPicker() {
        val mainIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val apps = packageManager.queryIntentActivities(mainIntent, 0)
            .map { it.loadLabel(packageManager).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
        if (apps.isEmpty()) { Toast.makeText(this, "No launchable apps found", Toast.LENGTH_SHORT).show(); return }
        SearchablePicker.show(this, "App opens", apps.map { it.first }) { which ->
            selectedAppPackage = apps[which].second
            selectedAppLabel = apps[which].first
            appTriggerBtn.text = "App: " + apps[which].first
            triggerParamEdit.setText(selectedAppPackage)
        }
    }

    private fun updateTriggerVisibility() {
        val selected = selectedTrigger()
        timeBox.visibility = if (selected == "time" || selected == "time_weekday") android.view.View.VISIBLE else android.view.View.GONE
        locBox.visibility = if (selected == "location_enter" || selected == "location_exit") android.view.View.VISIBLE else android.view.View.GONE
        thresholdBox.visibility = if (selected == "battery_below") android.view.View.VISIBLE else android.view.View.GONE
        triggerParamBox.visibility = if (selected == "wifi_ssid" || selected == "bt_device" || selected == "nfc_tag") android.view.View.VISIBLE else android.view.View.GONE
        triggerParamEdit.hint = when (selected) {
            "wifi_ssid" -> "Wi-Fi network name (SSID), exactly as shown"
            "bt_device" -> "Bluetooth device name, or its address"
            "nfc_tag" -> "Tag ID (optional, leave blank for any tag, see Run log after a tap)"
            else -> "Value"
        }
        weekdayBox.visibility = if (selected == "time_weekday") android.view.View.VISIBLE else android.view.View.GONE
        intervalBox.visibility = if (selected == "interval") android.view.View.VISIBLE else android.view.View.GONE
        appTriggerBtn.visibility = if (selected == "app_opened") android.view.View.VISIBLE else android.view.View.GONE
        if (selected == "app_opened" && selectedAppPackage.isNotEmpty()) appTriggerBtn.text = "App: " + (if (selectedAppLabel.isNotEmpty()) selectedAppLabel else selectedAppPackage)
    }

    private fun lookupAddress() {
        val query = locAddressEdit.text.toString()
        if (query.isEmpty()) {
            Toast.makeText(this, "Type an address in the address field first", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Looking up address...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val url = java.net.URL("https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + encoded)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.setRequestProperty("User-Agent", "Flow/1.4 (contact: local user)")
                conn.connectTimeout = 10000; conn.readTimeout = 10000
                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val arr = org.json.JSONArray(text)
                if (arr.length() == 0) {
                    runOnUiThread { Toast.makeText(this, "No address found, enter latitude and longitude manually", Toast.LENGTH_LONG).show() }
                    return@Thread
                }
                val first = arr.getJSONObject(0)
                val lat = first.optString("lat", ""); val lng = first.optString("lon", "")
                val display = first.optString("display_name", "")
                runOnUiThread {
                    locLatEdit.setText(lat); locLngEdit.setText(lng)
                    Toast.makeText(this, "Found location, coordinates filled in", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Lookup failed: " + (e.message ?: ""), Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun triggerOptions(): List<Pair<String, String>> {
        return listOf(
            "time" to "Time of day",
            "charging_on" to "Charging starts",
            "charging_off" to "Charging stops",
            "battery_low" to "Battery low",
            "battery_below" to "Battery falls below %",
            "location_enter" to "Enter a location",
            "location_exit" to "Leave a location",
            "wifi_connected" to "Wi-Fi connects",
            "wifi_disconnected" to "Wi-Fi disconnects",
            "wifi_ssid" to "Wi-Fi connects to a network...",
            "bt_device" to "Bluetooth device connects...",
            "time_weekday" to "Time of day, on weekdays...",
            "battery_full" to "Battery is full",
            "screen_on" to "Screen turns on",
            "screen_off" to "Screen turns off",
            "app_opened" to "An app opens...",
            "sunrise" to "Sunrise",
            "sunset" to "Sunset",
            "headphones_connected" to "Headphones connect",
            "headphones_disconnected" to "Headphones disconnect",
            "unlocked" to "Device is unlocked",
            "nfc_tag" to "NFC tag is tapped...",
            "interval" to "Every X minutes / hours..."
        )
    }

    private fun triggerLabel(value: String): String {
        return triggerOptions().firstOrNull { it.first == value }?.second ?: "Choose trigger"
    }

    private fun showTriggerPicker() {
        val options = triggerOptions()
        SearchablePicker.show(this, "Choose trigger", options.map { it.second }) { which ->
            selectedTriggerValue = options[which].first
            if (::triggerButton.isInitialized) triggerButton.text = options[which].second
            updateTriggerVisibility()
        }
    }

    private fun selectedTrigger(): String {
        if (kind != "automation") return "none"
        return selectedTriggerValue
    }

    private fun startStepDrag(view: android.view.View, index: Int) {
        val data = android.content.ClipData.newPlainText("step", index.toString())
        val shadow = android.view.View.DragShadowBuilder(view)
        view.startDragAndDrop(data, shadow, null, 0)
    }

    private fun refreshSteps() {
        stepsBox.removeAllViews()
        if (steps.isEmpty()) {
            stepsBox.addView(TextView(this).apply { text = "No steps yet. Add one." })
            return
        }
        stepsBox.addView(TextView(this).apply { text = "Tap an If or While header to collapse or expand its branch. Long-press a step and drag it to move it." })
        var depth = 0
        // Tree matching: start index -> matching End index, for collapse.
        val matchEnd = mutableMapOf<Int, Int>()
        val openStackForMatch = mutableListOf<Int>()
        for ((i, s) in steps.withIndex()) {
            if (s.type in IF_TYPES || s.type == "while" || s.type == "repeat") openStackForMatch.add(i)
            else if (s.type == "end_if" || s.type == "end_while" || s.type == "end_repeat") {
                if (openStackForMatch.isNotEmpty()) matchEnd[openStackForMatch.removeAt(openStackForMatch.size - 1)] = i
            }
        }
        var skipUntil = -1
        /* Stack of open If conditions, so each step knows which statement it belongs to. */
        val openIfs = mutableListOf<String>()
        for ((index, step) in steps.withIndex()) {
            if (index < skipUntil) continue
            if (step.type == "else" || step.type == "elseif" || step.type == "end_if" || step.type == "end_while" || step.type == "end_repeat") {
                depth = maxOf(0, depth - 1)
                if ((step.type == "end_if" || step.type == "end_while" || step.type == "end_repeat") && openIfs.isNotEmpty()) openIfs.removeAt(openIfs.size - 1)
            }
            val indent = "│ ".repeat(depth)
            val isBranchHeader = step.type in IF_TYPES || step.type == "while" || step.type == "repeat"
            val isCollapsed = isBranchHeader && collapsedBlocks.contains(index)
            val treePrefix = when {
                isBranchHeader -> if (isCollapsed) "▶ " else "▼ "
                step.type == "else" || step.type == "elseif" -> "├ "
                step.type == "end_if" || step.type == "end_while" || step.type == "end_repeat" -> "└ "
                depth > 0 -> "├ "
                else -> ""
            }
            val scope = if (step.type in IF_TYPES || step.type == "while" || step.type == "repeat" || step.type == "else" || step.type == "elseif" || step.type == "end_if" || step.type == "end_while" || step.type == "end_repeat") {
                ""
            } else if (openIfs.isNotEmpty()) {
                "  [in: " + openIfs.last() + "]"
            } else {
                ""
            }
            if (step.type in IF_TYPES || step.type == "while" || step.type == "repeat") {
                depth++
                openIfs.add(stepLabel(step))
            } else if (step.type == "else") {
                depth++
            } else if (step.type == "elseif") {
                depth++
                if (openIfs.isNotEmpty()) openIfs[openIfs.size - 1] = stepLabel(step) else openIfs.add(stepLabel(step))
            }
            if (isCollapsed) {
                /* Collapse: undo the push above, hide through the matching End, show the header only. */
                depth = maxOf(0, depth - 1)
                if (openIfs.isNotEmpty()) openIfs.removeAt(openIfs.size - 1)
                skipUntil = (matchEnd[index] ?: index) + 1
            }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val targetIndex = index
            val label = TextView(this).apply {
                text = colorVariableTokens((index + 1).toString() + ". " + indent + treePrefix + stepLabel(step) + scope + (if (step.disabled) "  (disabled)" else "") + (if (isCollapsed) "  (collapsed, tap to expand)" else ""), step)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                if (step.disabled) alpha = 0.45f
                if (step.type == "comment") {
                    setTypeface(null, android.graphics.Typeface.ITALIC)
                    alpha = 0.7f
                }
            }
            if (isBranchHeader) {
                label.setOnClickListener {
                    if (collapsedBlocks.contains(index)) collapsedBlocks.remove(index) else collapsedBlocks.add(index)
                    refreshSteps()
                }
            }
            val handle = TextView(this).apply {
                text = "≡ "
                textSize = 20f
            }
            val del = Button(this).apply {
                text = "X"
                setOnClickListener { steps.removeAt(index); collapsedBlocks.clear(); refreshSteps() }
            }
            val stepMenu = Button(this).apply {
                text = "⋮"
                setOnClickListener { showStepMenu(index) }
            }
            row.addView(handle); row.addView(label); row.addView(stepMenu); row.addView(del)
            /* Long-press and drag: drop in the top half moves above the row, bottom half below it. */
            row.setOnLongClickListener { v -> startStepDrag(v, targetIndex); true }
            handle.setOnLongClickListener { v -> startStepDrag(row, targetIndex); true }
            label.setOnLongClickListener { v -> startStepDrag(row, targetIndex); true }
            row.setOnDragListener { v, event ->
                when (event.action) {
                    android.view.DragEvent.ACTION_DRAG_STARTED -> true
                    android.view.DragEvent.ACTION_DRAG_ENTERED -> {
                        v.setBackgroundColor(android.graphics.Color.parseColor("#444444"))
                        true
                    }
                    android.view.DragEvent.ACTION_DRAG_EXITED, android.view.DragEvent.ACTION_DRAG_ENDED -> {
                        v.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        true
                    }
                    android.view.DragEvent.ACTION_DROP -> {
                        val source = event.clipData.getItemAt(0).text.toString().toIntOrNull()
                        if (source != null && source != targetIndex) {
                            var insertAt = if (event.y < v.height / 2) targetIndex else targetIndex + 1
                            if (source < insertAt) insertAt--
                            val moved = steps.removeAt(source)
                            steps.add(insertAt.coerceIn(0, steps.size), moved)
                            collapsedBlocks.clear()
                        }
                        v.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        refreshSteps()
                        true
                    }
                    else -> true
                }
            }
            stepsBox.addView(row)
        }
        if (depth > 0) {
            stepsBox.addView(TextView(this).apply {
                text = "⚠ " + depth + " If / While / Repeat block(s) not closed. Add End If, End While, or End Repeat after the last step in each block, or the steps after it stay inside that block. For a toggle, use Else for the opposite branch, not a second If, because a second If re-checks the state after the first branch already changed it."
                setTextColor(android.graphics.Color.parseColor("#FF8A65"))
            })
        }
    }

    private fun showStepMenu(index: Int) {
        if (index !in steps.indices) return
        val step = steps[index]
        val items = arrayOf("Duplicate step", if (step.disabled) "Enable step" else "Disable step")
        AlertDialog.Builder(this).setTitle(stepLabel(step))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        steps.add(index + 1, step.copy())
                        collapsedBlocks.clear()
                        refreshSteps()
                    }
                    1 -> {
                        steps[index] = step.copy(disabled = !step.disabled)
                        refreshSteps()
                    }
                }
            }.show()
    }

    private fun showAppPicker() {
        val mainIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val apps = packageManager.queryIntentActivities(mainIntent, 0)
            .map { it.loadLabel(packageManager).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
        if (apps.isEmpty()) {
            Toast.makeText(this, "No launchable apps found", Toast.LENGTH_SHORT).show()
            return
        }
        SearchablePicker.show(this, "Open app", apps.map { it.first }) { which ->
            val app = apps[which]
            steps.add(Step("open_app", app.second, app.first))
            refreshSteps()
        }
    }

    private fun showStepPicker() {
        /* Step picker: conditional steps in purple bold. The search field filters the list live. */
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 16) }
        val search = EditText(this).apply { hint = "Search steps..." }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(box) }
        root.addView(search)
        root.addView(scroll)
        var dialog: AlertDialog? = null
        val conditionalTypes = setOf("if", "elseif", "else", "endif", "while", "endwhile", "repeat", "endrepeat")
        fun rebuild(query: String) {
            box.removeAllViews()
            for (type in STEP_TYPES) {
                val title = stepTypeTitle(type)
                if (query.isNotEmpty() && !title.lowercase().contains(query.lowercase()) && !type.lowercase().contains(query.lowercase())) continue
                val btn = Button(this).apply {
                    text = title
                    if (type in conditionalTypes) {
                        setTextColor(conditionalColor())
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }
                    setOnClickListener { dialog?.dismiss(); onStepTypePicked(type) }
                }
                box.addView(btn)
            }
            if (box.childCount == 0) box.addView(TextView(this).apply { text = "No matches." })
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { rebuild(s?.toString() ?: "") }
        })
        rebuild("")
        dialog = AlertDialog.Builder(this).setTitle("Add step").setView(root).setNegativeButton("Cancel", null).create()
        dialog.show()
    }

    private fun onStepTypePicked(type: String) {
                if (type in NO_INPUT_STEPS) {
                    steps.add(Step(type, "", ""))
                    refreshSteps()
                } else if (type == "if" || type == "elseif" || type == "while") {
                    showIfInputs(type)
                } else if (type == "open_app") {
                    showAppPicker()
                } else if (type == "set_flashlight" || type == "set_dnd" || type == "set_bluetooth" || type == "set_wifi") {
                    AlertDialog.Builder(this).setTitle(stepTypeTitle(type))
                        .setItems(arrayOf("On", "Off")) { _, i ->
                            steps.add(Step(type, if (i == 0) "on" else "off", ""))
                            refreshSteps()
                        }.show()
                } else if (type == "set_ringer") {
                    AlertDialog.Builder(this).setTitle(stepTypeTitle(type))
                        .setItems(arrayOf("Normal", "Vibrate", "Silent")) { _, i ->
                            steps.add(Step(type, arrayOf("normal", "vibrate", "silent")[i], ""))
                            refreshSteps()
                        }.show()
                } else if (type == "set_vibrate_ringing") {
                    AlertDialog.Builder(this).setTitle(stepTypeTitle(type))
                        .setItems(arrayOf("On", "Off")) { _, i ->
                            steps.add(Step(type, if (i == 0) "on" else "off", ""))
                            refreshSteps()
                        }.show()
                } else if (type == "repeat") {
                    val e = EditText(this).apply { hint = "Times to repeat, e.g. 3 (max 100)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
                    AlertDialog.Builder(this).setTitle(stepTypeTitle(type)).setView(e)
                        .setPositiveButton("Add") { _, _ ->
                            steps.add(Step(type, e.text.toString().ifEmpty { "3" }, ""))
                            refreshSteps()
                        }.setNegativeButton("Cancel", null).show()
                } else if (type == "run_flow") {
                    val flows = Store.load(this)
                    if (flows.isEmpty()) { Toast.makeText(this, "No flows yet", Toast.LENGTH_SHORT).show() }
                    else SearchablePicker.show(this, "Run another flow", flows.map { it.name + " (" + it.kind + ")" }) { which ->
                        steps.add(Step(type, flows[which].id, flows[which].name))
                        refreshSteps()
                    }
                } else if (type == "var_op") {
                    showVarOpInputs()
                } else if (type == "http_post") {
                    showHttpPostInputs()
                } else if (type == "notify_buttons") {
                    showNotifyButtonInputs()
                } else if (type == "play_sound") {
                    SearchablePicker.show(this, "Play a sound", listOf("Notification", "Alarm", "Ringtone")) { which ->
                        val kind = listOf("notification", "alarm", "ringtone")[which]
                        val e = EditText(this).apply { hint = "Seconds, 1-30, default 3"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
                        AlertDialog.Builder(this).setTitle("Play " + kind + " sound").setView(e)
                            .setPositiveButton("Add") { _, _ ->
                                steps.add(Step(type, kind, e.text.toString().ifEmpty { "3" }))
                                refreshSteps()
                            }.setNegativeButton("Cancel", null).show()
                    }
                } else if (type == "set_volume") {
                    SearchablePicker.show(this, "Volume stream", listOf("Media", "Ring", "Alarm")) { which ->
                        val stream = listOf("media", "ring", "alarm")[which]
                        val e = EditText(this).apply { hint = "Volume 0-100"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
                        AlertDialog.Builder(this).setTitle("Set " + stream + " volume").setView(e)
                            .setPositiveButton("Add") { _, _ ->
                                steps.add(Step(type, stream, e.text.toString().ifEmpty { "50" }))
                                refreshSteps()
                            }.setNegativeButton("Cancel", null).show()
                    }
                } else if (type == "set_screen_timeout") {
                    SearchablePicker.show(this, "Screen timeout", listOf("15 seconds", "30 seconds", "60 seconds", "120 seconds", "300 seconds", "600 seconds")) { which ->
                        steps.add(Step(type, listOf("15", "30", "60", "120", "300", "600")[which], ""))
                        refreshSteps()
                    }
                } else if (type == "open_panel") {
                    SearchablePicker.show(this, stepTypeTitle(type), listOf("Wi-Fi", "Internet", "NFC", "Volume")) { i ->
                        steps.add(Step(type, arrayOf("wifi", "internet", "nfc", "volume")[i], ""))
                        refreshSteps()
                    }
                } else if (type == "open_settings") {
                    SearchablePicker.show(this, stepTypeTitle(type), listOf("Wi-Fi", "Bluetooth", "Location", "Hotspot")) { i ->
                        steps.add(Step(type, arrayOf("wifi", "bluetooth", "location", "tethering")[i], ""))
                        refreshSteps()
                    }
                } else if (type == "choose_list") {
                    showChooseListInputs()
                } else if (type == "random_number") {
                    showRandomNumberInputs()
                } else if (type == "send_text_call") {
                    showSendTextCallInputs()
                } else if (type == "get_value") {
                    showGetValueInputs()
                } else if (type == "set_alarm_timer") {
                    showAlarmTimerInputs()
                } else if (type == "file_text") {
                    showFileTextInputs()
                } else {
                    showStepInputs(type)
                }
    }

    private fun showChooseListInputs() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 16) }
        val varEdit = EditText(this).apply { hint = "Variable name to store the pick in"; setTextColor(variableColor()) }
        val promptEdit = codingEdit("Title for the dialog (optional)")
        val optionsEdit = EditText(this).apply {
            hint = "Options, one per line"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
        }
        box.addView(varEdit); box.addView(pickVariableButton(varEdit)); box.addView(promptEdit); box.addView(insertVariableButton(promptEdit)); box.addView(optionsEdit)
        AlertDialog.Builder(this).setTitle("Choose from list").setView(box)
            .setPositiveButton("Add") { _, _ ->
                if (varEdit.text.toString().isEmpty()) { Toast.makeText(this, "Fill in the variable name", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                steps.add(Step("choose_list", varEdit.text.toString(), optionsEdit.text.toString(), promptEdit.text.toString()))
                refreshSteps()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showRandomNumberInputs() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 16) }
        val minEdit = EditText(this).apply { hint = "Minimum, e.g. 1"; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED }
        val maxEdit = EditText(this).apply { hint = "Maximum, e.g. 100"; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED }
        val varEdit = EditText(this).apply { hint = "Variable name to store the number in"; setTextColor(variableColor()) }
        box.addView(minEdit); box.addView(maxEdit); box.addView(varEdit); box.addView(pickVariableButton(varEdit))
        AlertDialog.Builder(this).setTitle("Random number").setView(box)
            .setPositiveButton("Add") { _, _ ->
                if (varEdit.text.toString().isEmpty()) { Toast.makeText(this, "Fill in the variable name", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                steps.add(Step("random_number", minEdit.text.toString().ifEmpty { "1" }, maxEdit.text.toString().ifEmpty { "100" }, varEdit.text.toString()))
                refreshSteps()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showSendTextCallInputs() {
        SearchablePicker.show(this, "Send text / make call", listOf("Send a text", "Make a call")) { which ->
            val mode = if (which == 1) "call" else "text"
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 16) }
            val numberEdit = codingEdit("Phone number (type \${ to pick a variable)")
            numberEdit.inputType = android.text.InputType.TYPE_CLASS_PHONE
            box.addView(numberEdit); box.addView(insertVariableButton(numberEdit))
            var messageEdit: EditText? = null
            if (mode == "text") {
                messageEdit = codingEdit("Message text (type \${ to pick a variable)")
                box.addView(messageEdit); box.addView(insertVariableButton(messageEdit))
            }
            AlertDialog.Builder(this).setTitle(if (mode == "call") "Make a call" else "Send a text").setView(box)
                .setPositiveButton("Add") { _, _ ->
                    steps.add(Step("send_text_call", mode, numberEdit.text.toString(), messageEdit?.text?.toString() ?: ""))
                    refreshSteps()
                }.setNegativeButton("Cancel", null).show()
        }
    }

    private fun showGetValueInputs() {
        SearchablePicker.show(this, "Get current value", listOf("Brightness percent", "Volume percent", "Wi-Fi network name")) { which ->
            val whichValue = listOf("brightness", "volume", "ssid")[which]
            fun finishWith(stream: String) {
                val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 16) }
                val varEdit = EditText(this).apply { hint = "Variable name to store it in"; setTextColor(variableColor()) }
                box.addView(varEdit); box.addView(pickVariableButton(varEdit))
                AlertDialog.Builder(this).setTitle("Get " + whichValue).setView(box)
                    .setPositiveButton("Add") { _, _ ->
                        if (varEdit.text.toString().isEmpty()) { Toast.makeText(this, "Fill in the variable name", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                        steps.add(Step("get_value", whichValue, varEdit.text.toString(), stream))
                        refreshSteps()
                    }.setNegativeButton("Cancel", null).show()
            }
            if (whichValue == "volume") {
                SearchablePicker.show(this, "Volume stream", listOf("Media", "Ring", "Alarm")) { i ->
                    finishWith(listOf("media", "ring", "alarm")[i])
                }
            } else {
                finishWith("")
            }
        }
    }

    private fun showAlarmTimerInputs() {
        SearchablePicker.show(this, "Set alarm or timer", listOf("Alarm", "Timer")) { which ->
            val mode = if (which == 1) "timer" else "alarm"
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 16) }
            var hourEdit: EditText? = null
            var minuteEdit: EditText? = null
            var secondsEdit: EditText? = null
            if (mode == "alarm") {
                hourEdit = EditText(this).apply { hint = "Hour 0-23"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
                minuteEdit = EditText(this).apply { hint = "Minute 0-59"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
                box.addView(hourEdit); box.addView(minuteEdit)
            } else {
                secondsEdit = EditText(this).apply { hint = "Seconds, e.g. 300"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
                box.addView(secondsEdit)
            }
            val labelEdit = codingEdit("Label (optional, type \${ to pick a variable)")
            box.addView(labelEdit); box.addView(insertVariableButton(labelEdit))
            AlertDialog.Builder(this).setTitle(if (mode == "timer") "Set timer" else "Set alarm").setView(box)
                .setPositiveButton("Add") { _, _ ->
                    val p2 = if (mode == "timer") {
                        secondsEdit?.text?.toString()?.ifEmpty { "60" } ?: "60"
                    } else {
                        (hourEdit?.text?.toString()?.ifEmpty { "7" } ?: "7") + ":" + (minuteEdit?.text?.toString()?.ifEmpty { "0" } ?: "0")
                    }
                    steps.add(Step("set_alarm_timer", mode, p2, labelEdit.text.toString()))
                    refreshSteps()
                }.setNegativeButton("Cancel", null).show()
        }
    }

    private fun showFileTextInputs() {
        SearchablePicker.show(this, "Save / read a text file", listOf("Save text to a file", "Read a file into a variable")) { which ->
            val mode = if (which == 1) "read" else "save"
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 16) }
            val nameEdit = EditText(this).apply { hint = "File name, e.g. notes.txt (stored inside Flow)" }
            box.addView(nameEdit)
            var valueEdit: EditText? = null
            if (mode == "save") {
                valueEdit = codingEdit("Text to save (type \${ to pick a variable)")
                box.addView(valueEdit); box.addView(insertVariableButton(valueEdit))
            } else {
                valueEdit = EditText(this).apply { hint = "Variable name to store the file contents in"; setTextColor(variableColor()) }
                box.addView(valueEdit); box.addView(pickVariableButton(valueEdit))
            }
            AlertDialog.Builder(this).setTitle(if (mode == "read") "Read a text file" else "Save a text file").setView(box)
                .setPositiveButton("Add") { _, _ ->
                    if (nameEdit.text.toString().isEmpty()) { Toast.makeText(this, "Fill in the file name", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                    steps.add(Step("file_text", mode, nameEdit.text.toString(), valueEdit?.text?.toString() ?: ""))
                    refreshSteps()
                }.setNegativeButton("Cancel", null).show()
        }
    }

    private fun showVarOpInputs() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 16) }
        val varEdit = EditText(this).apply { hint = "Variable name"; setTextColor(variableColor()) }
        val ops = arrayOf("add", "subtract", "multiply", "divide", "uppercase", "lowercase", "append", "prepend", "length")
        val spinner = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@EditorActivity, android.R.layout.simple_spinner_dropdown_item, ops)
        }
        val valueEdit = codingEdit("Operand / text (not needed for uppercase, lowercase, length)")
        box.addView(varEdit); box.addView(pickVariableButton(varEdit)); box.addView(spinner); box.addView(valueEdit); box.addView(insertVariableButton(valueEdit))
        AlertDialog.Builder(this).setTitle("Variable operation").setView(box)
            .setPositiveButton("Add") { _, _ ->
                if (varEdit.text.toString().isEmpty()) { Toast.makeText(this, "Fill in the variable name", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                steps.add(Step("var_op", varEdit.text.toString(), valueEdit.text.toString(), spinner.selectedItem as? String ?: "add"))
                refreshSteps()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showHttpPostInputs() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 16) }
        val urlEdit = codingEdit("URL")
        val bodyEdit = codingEdit("Body (type \${ to pick a variable)")
        val respEdit = EditText(this).apply { hint = "Variable name to store response in"; setTextColor(variableColor()) }
        box.addView(urlEdit); box.addView(insertVariableButton(urlEdit)); box.addView(bodyEdit); box.addView(insertVariableButton(bodyEdit)); box.addView(respEdit); box.addView(pickVariableButton(respEdit))
        AlertDialog.Builder(this).setTitle("HTTP POST").setView(box)
            .setPositiveButton("Add") { _, _ ->
                steps.add(Step("http_post", urlEdit.text.toString(), bodyEdit.text.toString(), respEdit.text.toString()))
                refreshSteps()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showNotifyButtonInputs() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 16) }
        val textEdit = codingEdit("Notification text (type \${ to pick a variable)")
        val labelEdit = EditText(this).apply { hint = "Button label, e.g. Run" }
        var pickedFlow: Flow? = null
        val flowBtn = Button(this).apply {
            text = "Choose flow to run when tapped..."
            setOnClickListener {
                val flows = Store.load(this@EditorActivity)
                SearchablePicker.show(this@EditorActivity, "Flow to run", flows.map { it.name + " (" + it.kind + ")" }) { which ->
                    pickedFlow = flows[which]
                    text = "Flow: " + flows[which].name
                }
            }
        }
        box.addView(textEdit); box.addView(insertVariableButton(textEdit)); box.addView(labelEdit); box.addView(flowBtn)
        AlertDialog.Builder(this).setTitle("Notification with button").setView(box)
            .setPositiveButton("Add") { _, _ ->
                steps.add(Step("notify_buttons", textEdit.text.toString(), labelEdit.text.toString(), pickedFlow?.id ?: ""))
                refreshSteps()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showIfInputs(stepType: String = "if") {
        /* One If / While step, with the operator as a field inside it.
           Tap Pick variable to choose one; the list does not pop up on its own. */
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 16) }
        val varEdit = EditText(this).apply { hint = "Variable, e.g. battery, charging, flashlight, wifi, or your own variable"; setTextColor(variableColor()) }
        val spinner = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@EditorActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("==", "!=", "contains", ">", "<", "<=", ">="))
        }
        val valueEdit = codingEdit("Value, e.g. 20, 07:00, or true, or type \${ for a variable")
        box.addView(varEdit); box.addView(pickVariableButton(varEdit)); box.addView(spinner); box.addView(valueEdit); box.addView(insertVariableButton(valueEdit))
        AlertDialog.Builder(this)
            .setTitle(if (stepType == "elseif") "Else If..." else if (stepType == "while") "While... (repeats until false, max 100 times)" else "If...")
            .setView(box)
            .setPositiveButton("Add") { _, _ ->
                val v = varEdit.text.toString()
                if (v.isEmpty()) {
                    Toast.makeText(this, "Fill in the variable name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val op = spinner.selectedItem as? String ?: "=="
                steps.add(Step(stepType, v, valueEdit.text.toString(), op))
                refreshSteps()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showStepInputs(type: String) {
        // p1Label / p2Label per type, null p2 means single-field step.
        val (p1Label, p2Label) = when (type) {
            "message" -> "Message text (type \${ to pick a variable)" to null
            "notify" -> "Notification text (type \${ to pick a variable)" to null
            "vibrate" -> "Duration in ms, e.g. 300" to null
            "wait" -> "Seconds, e.g. 2" to null
            "open_url" -> "URL, e.g. https://example.com" to null
            "set_var" -> "Variable name, e.g. myname. Built-ins you can read: time, date, hour, minute, battery, charging" to "Value, can use \${other} to copy another variable"
            "ask_input" -> "Variable name to store the answer in, e.g. myname" to "Prompt to show, e.g. What is your name?"
            "speak" -> "Text to speak (type \${ to pick a variable)" to null
            "get_time" -> "Variable name to store time in" to null
            "get_location" -> "Variable name to store latitude in" to "Variable name to store longitude in"
            "http_get" -> "URL" to "Variable name to store result in"
            "stop_if" -> "Variable name" to "Stop unless it equals this"
            "set_brightness" -> "Brightness 0-100" to null
            "set_volume" -> "Stream: media, ring, or alarm" to "Volume 0-100"
            "set_screen_timeout" -> "Seconds, e.g. 60" to null
            "copy_clipboard" -> "Text to copy (type \${ to pick a variable)" to null
            "share_text" -> "Text to share (type \${ to pick a variable)" to null
            "flash_blink" -> "Blink count, max 10" to "Interval in ms, e.g. 500"
            "get_clipboard" -> "Variable name to store the clipboard text in" to null
            "comment" -> "Comment text, shown in the editor only" to null
            else -> "Value" to null
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 16) }
        /* Triggers are manual, never automatic on a regular word: Pick variable
           for name fields, ${ plus an Insert button for free-text fields. */
        val isVarName1 = type == "set_var" || type == "ask_input" || type == "get_time" || type == "stop_if" || type == "get_location" || type == "get_clipboard"
        val e1: EditText = if (type == "message" || type == "notify" || type == "speak" || type == "open_url" || type == "http_get" || type == "copy_clipboard" || type == "share_text") codingEdit(p1Label) else EditText(this).apply { hint = p1Label }
        if (isVarName1) e1.setTextColor(variableColor())
        box.addView(e1)
        if (isVarName1) box.addView(pickVariableButton(e1))
        if (type == "message" || type == "notify" || type == "speak" || type == "open_url" || type == "http_get" || type == "copy_clipboard" || type == "share_text") box.addView(insertVariableButton(e1))
        var e2: EditText? = null
        if (p2Label != null) {
            val isVarName2 = type == "get_location" || type == "http_get"
            e2 = if (type == "set_var" || type == "ask_input") codingEdit(p2Label) else EditText(this).apply { hint = p2Label }
            if (isVarName2) e2.setTextColor(variableColor())
            box.addView(e2)
            if (isVarName2) box.addView(pickVariableButton(e2!!))
            if (type == "set_var" || type == "ask_input") box.addView(insertVariableButton(e2!!))
        }
        AlertDialog.Builder(this)
            .setTitle(stepTypeTitle(type))
            .setView(box)
            .setPositiveButton("Add") { _, _ ->
                val p1 = e1.text.toString()
                val p2 = e2?.text.toString() ?: ""
                if (p1.isEmpty() && type != "message" && type != "notify" && type != "comment") {
                    Toast.makeText(this, "Fill in the first field", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                steps.add(Step(type, p1, p2))
                refreshSteps()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun buildFlow(): Flow {
        val name = nameEdit.text.toString().ifEmpty { "Untitled" }
        val id = flowId ?: UUID.randomUUID().toString()
        val trigger = selectedTrigger()
        val hour = if (::hourEdit.isInitialized) hourEdit.text.toString().toIntOrNull() ?: 9 else 9
        val minute = if (::minuteEdit.isInitialized) minuteEdit.text.toString().toIntOrNull() ?: 0 else 0
        val locLat = if (::locLatEdit.isInitialized) locLatEdit.text.toString().toDoubleOrNull() ?: 0.0 else 0.0
        val locLng = if (::locLngEdit.isInitialized) locLngEdit.text.toString().toDoubleOrNull() ?: 0.0 else 0.0
        val locRadius = if (::locRadiusEdit.isInitialized) locRadiusEdit.text.toString().toIntOrNull() ?: 200 else 200
        val locName = if (::locNameEdit.isInitialized) locNameEdit.text.toString() else ""
        val threshold = if (::thresholdEdit.isInitialized) thresholdEdit.text.toString().toIntOrNull() ?: 20 else 20
        val locAddress = if (::locAddressEdit.isInitialized) locAddressEdit.text.toString() else ""
        val triggerParam = when (trigger) {
            "time_weekday" -> selectedWeekdays.sorted().joinToString(",")
            "app_opened" -> selectedAppPackage
            "wifi_ssid", "bt_device", "nfc_tag" -> if (::triggerParamEdit.isInitialized) triggerParamEdit.text.toString() else ""
            "interval" -> (if (::intervalEdit.isInitialized) intervalEdit.text.toString().toIntOrNull() ?: 30 else 30).toString() + ":" + selectedIntervalUnit
            else -> ""
        }
        return Flow(id, kind, name, true, trigger, hour, minute, steps.toMutableList(), locLat, locLng, locRadius, locName, threshold, locAddress, triggerParam)
    }

    private fun saveAndFinish() {
        val flow = buildFlow()
        // Preserve enabled state for an existing automation.
        val all = Store.load(this)
        val old = all.firstOrNull { it.id == flow.id }
        if (old != null) flow.enabled = old.enabled
        val updated = all.filter { it.id != flow.id }.toMutableList()
        updated.add(flow)
        Store.save(this, updated)
        if (flow.kind == "automation" && flow.enabled) {
            Scheduler.scheduleAll(this)
        }
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun testRun() {
        val flow = buildFlow()
        Toast.makeText(this, "Test running " + flow.name, Toast.LENGTH_SHORT).show()
        Thread {
            Engine.run(this, flow) { line ->
                runOnUiThread { logView.text = line + "\n" + logView.text }
            }
        }.start()
    }
}
