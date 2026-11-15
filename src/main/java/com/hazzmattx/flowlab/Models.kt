package com.hazzmattx.flowlab

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/* One step, the shared building block. p1 and p2 mean different things
   per type; disabled steps are skipped without being deleted. */
data class Step(val type: String, val p1: String, val p2: String, val op: String = "", val disabled: Boolean = false) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("type", type); o.put("p1", p1); o.put("p2", p2); o.put("op", op); o.put("disabled", disabled)
        return o
    }
    companion object {
        fun fromJson(o: JSONObject): Step {
            val raw = o.optString("type", "message")
            val p1 = o.optString("p1", ""); val p2 = o.optString("p2", "")
            val disabled = o.optBoolean("disabled", false)
            return Step(raw, p1, p2, o.optString("op", ""), disabled)
        }
    }
}

fun stepLabel(s: Step): String {
    return when (s.type) {
        "message" -> "Show message: " + s.p1
        "notify" -> "Send notification: " + s.p1
        "vibrate" -> "Vibrate " + s.p1 + " ms"
        "wait" -> "Wait " + s.p1 + " seconds"
        "open_url" -> "Open URL: " + s.p1
        "open_app" -> "Open app " + (if (s.p2.isNotEmpty()) s.p2 else s.p1)
        "set_var" -> "Set variable " + s.p1 + " = " + s.p2
        "ask_input" -> "Ask for input into " + s.p1 + (if (s.p2.isNotEmpty()) " (" + s.p2 + ")" else "")
        "speak" -> "Speak: " + s.p1
        "get_time" -> "Get current time into " + s.p1
        "http_get" -> "HTTP GET " + s.p1 + " into " + s.p2
        "stop_if" -> "Stop if " + s.p1 + " is not " + s.p2
        "bt_on" -> "Bluetooth on (Device Owner)"
        "bt_off" -> "Bluetooth off (Device Owner)"
        "flash_on" -> "Flashlight on"
        "flash_off" -> "Flashlight off"
        "dnd_on" -> "Do Not Disturb on"
        "dnd_off" -> "Do Not Disturb off"
        "panel_wifi" -> "Open Wi-Fi panel"
        "panel_internet" -> "Open Internet panel (Wi-Fi / mobile data / airplane)"
        "panel_nfc" -> "Open NFC panel"
        "panel_volume" -> "Open Volume panel"
        "open_wifi_settings" -> "Open Wi-Fi settings"
        "open_bt_settings" -> "Open Bluetooth settings"
        "open_location_settings" -> "Open Location settings"
        "open_tether_settings" -> "Open Hotspot / tethering settings"
        "if" -> "If " + s.p1 + " " + s.op + " " + s.p2
        "elseif" -> "Else If " + s.p1 + " " + s.op + " " + s.p2
        "set_flashlight" -> "Set flashlight " + s.p1
        "toggle_flashlight" -> "Toggle flashlight"
        "set_dnd" -> "Set Do Not Disturb " + s.p1
        "set_bluetooth" -> "Set Bluetooth " + s.p1 + " (Device Owner)"
        "set_wifi" -> "Set Wi-Fi " + s.p1 + " (Device Owner)"
        "set_ringer" -> "Set ringer mode " + s.p1
        "set_vibrate_ringing" -> "Set vibrate when ringing " + s.p1
        "open_panel" -> "Open " + s.p1 + " panel"
        "open_settings" -> "Open " + s.p1 + " settings"
        "get_location" -> "Get location into " + s.p1 + " / " + s.p2
        "if_eq" -> "If " + s.p1 + " = " + s.p2
        "if_ne" -> "If " + s.p1 + " is not " + s.p2
        "if_contains" -> "If " + s.p1 + " contains " + s.p2
        "if_gt" -> "If " + s.p1 + " > " + s.p2
        "if_lt" -> "If " + s.p1 + " < " + s.p2
        "else" -> "Else"
        "end_if" -> "End If"
        "while" -> "While " + s.p1 + " " + s.op + " " + s.p2
        "end_while" -> "End While"
        "set_brightness" -> "Set brightness " + s.p1 + "%"
        "set_volume" -> "Set " + s.p1 + " volume " + s.p2 + "%"
        "set_screen_timeout" -> "Set screen timeout " + s.p1 + "s"
        "copy_clipboard" -> "Copy to clipboard: " + s.p1
        "share_text" -> "Share text: " + s.p1
        "var_op" -> "Variable " + s.p1 + " " + s.op + " " + s.p2
        "repeat" -> "Repeat " + s.p1 + " times"
        "end_repeat" -> "End Repeat"
        "run_flow" -> "Run flow " + (if (s.p2.isNotEmpty()) s.p2 else s.p1)
        "http_post" -> "HTTP POST " + s.p1 + " into " + s.op
        "notify_buttons" -> "Notify with button: " + s.p1 + " [" + s.p2 + "]"
        "play_sound" -> "Play " + s.p1 + " sound for " + s.p2 + "s"
        "flash_blink" -> "Blink flashlight " + s.p1 + " times"
        "choose_list" -> "Choose from list into " + s.p1
        "get_clipboard" -> "Get clipboard into " + s.p1
        "random_number" -> "Random number " + s.p1 + "-" + s.p2 + " into " + s.op
        "comment" -> "Comment: " + s.p1
        "send_text_call" -> if (s.p1 == "call") "Call " + s.p2 else "Text " + s.p2 + (if (s.op.isNotEmpty()) ": " + s.op else "")
        "get_value" -> "Get " + s.p1 + (if (s.p1 == "volume") " (" + s.op + ")" else "") + " into " + s.p2
        "set_alarm_timer" -> if (s.p1 == "timer") "Set timer " + s.p2 + "s" else "Set alarm " + s.p2
        "file_text" -> if (s.p1 == "read") "Read file " + s.p2 + " into " + s.op else "Save text to file " + s.p2
        else -> s.type
    }
}

val STEP_TYPES = listOf(
    "message", "notify", "vibrate", "wait", "open_url", "open_app", "ask_input", "speak",
    "set_var", "get_time", "get_location", "http_get", "stop_if",
    "if", "elseif", "else", "end_if", "while", "end_while",
    "set_flashlight", "toggle_flashlight", "set_dnd", "set_bluetooth", "set_wifi", "set_ringer", "set_vibrate_ringing",
    "open_panel", "open_settings",
    "set_brightness", "set_volume", "set_screen_timeout", "copy_clipboard", "share_text", "var_op",
    "repeat", "end_repeat", "run_flow", "http_post", "notify_buttons", "play_sound", "flash_blink",
    "choose_list", "get_clipboard", "random_number", "comment",
    "send_text_call", "get_value", "set_alarm_timer", "file_text"
)

val IF_TYPES = setOf("if", "if_eq", "if_ne", "if_contains", "if_gt", "if_lt")

// Steps that need no input at all: tapping them in the picker adds them directly.
val NO_INPUT_STEPS = setOf(
    "else", "end_if", "end_while", "end_repeat", "toggle_flashlight"
)

fun stepTypeTitle(t: String): String {
    return when (t) {
        "message" -> "Show message"
        "notify" -> "Send notification"
        "vibrate" -> "Vibrate"
        "wait" -> "Wait"
        "open_url" -> "Open URL / app link"
        "open_app" -> "Open app..."
        "ask_input" -> "Ask for input..."
        "speak" -> "Speak text (TTS)..."
        "set_var" -> "Set variable"
        "get_time" -> "Get current time"
        "http_get" -> "HTTP GET (web API)"
        "stop_if" -> "Stop if variable is not..."
        "if" -> "If... (choose variable, operator, value inside)"
        "elseif" -> "Else If... (choose variable, operator, value inside)"
        "get_location" -> "Get current location into variables"
        "set_flashlight" -> "Set flashlight..."
        "toggle_flashlight" -> "Toggle flashlight"
        "set_dnd" -> "Set Do Not Disturb..."
        "set_bluetooth" -> "Set Bluetooth... (Device Owner only)"
        "set_wifi" -> "Set Wi-Fi... (Device Owner only)"
        "set_ringer" -> "Set ringer mode..."
        "set_vibrate_ringing" -> "Set vibrate when ringing..."
        "open_panel" -> "Open settings panel..."
        "open_settings" -> "Open settings page..."
        "if_eq" -> "If variable equals..."
        "if_ne" -> "If variable is not..."
        "if_contains" -> "If variable contains..."
        "if_gt" -> "If variable is greater than..."
        "if_lt" -> "If variable is less than..."
        "else" -> "Else"
        "end_if" -> "End If"
        "while" -> "While... (choose variable, operator, value inside, repeats until false)"
        "end_while" -> "End While"
        "set_brightness" -> "Set brightness..."
        "set_volume" -> "Set volume..."
        "set_screen_timeout" -> "Set screen timeout..."
        "copy_clipboard" -> "Copy to clipboard..."
        "share_text" -> "Share text..."
        "var_op" -> "Variable operation..."
        "repeat" -> "Repeat... (run these steps N times)"
        "end_repeat" -> "End Repeat"
        "run_flow" -> "Run another flow..."
        "http_post" -> "HTTP POST (web API)..."
        "notify_buttons" -> "Notification with button..."
        "play_sound" -> "Play a sound..."
        "flash_blink" -> "Blink flashlight..."
        "choose_list" -> "Choose from list..."
        "get_clipboard" -> "Get clipboard into a variable..."
        "random_number" -> "Random number..."
        "comment" -> "Comment (does nothing, labels a section)..."
        "send_text_call" -> "Send text / make call..."
        "get_value" -> "Get current value..."
        "set_alarm_timer" -> "Set alarm or timer..."
        "file_text" -> "Save / read a text file..."
        "bt_on" -> "Bluetooth on (Device Owner only)"
        "bt_off" -> "Bluetooth off (Device Owner only)"
        "flash_on" -> "Flashlight on"
        "flash_off" -> "Flashlight off"
        "dnd_on" -> "Do Not Disturb on"
        "dnd_off" -> "Do Not Disturb off"
        "panel_wifi" -> "Open Wi-Fi panel"
        "panel_internet" -> "Open Internet panel"
        "panel_nfc" -> "Open NFC panel"
        "panel_volume" -> "Open Volume panel"
        "open_wifi_settings" -> "Open Wi-Fi settings"
        "open_bt_settings" -> "Open Bluetooth settings"
        "open_location_settings" -> "Open Location settings"
        "open_tether_settings" -> "Open Hotspot settings"
        else -> t
    }
}

fun isDeviceOwner(ctx: Context): Boolean {
    return try {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        dpm.isDeviceOwnerApp(ctx.packageName)
    } catch (e: Exception) {
        false
    }
}

/* kind is "macro" or "automation". Both use the same Step list and Engine;
   only the way they start differs. Old "shortcut" flows load as "macro". */
data class Flow(
    val id: String,
    val kind: String,
    var name: String,
    var enabled: Boolean,
    var trigger: String,
    var hour: Int,
    var minute: Int,
    val steps: MutableList<Step>,
    var locLat: Double = 0.0,
    var locLng: Double = 0.0,
    var locRadius: Int = 200,
    var locName: String = "",
    var triggerThreshold: Int = 20,
    var locAddress: String = "",
    var triggerParam: String = ""
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id); o.put("kind", kind); o.put("name", name)
        o.put("enabled", enabled); o.put("trigger", trigger)
        o.put("hour", hour); o.put("minute", minute)
        o.put("locLat", locLat); o.put("locLng", locLng); o.put("locRadius", locRadius)
        o.put("locName", locName); o.put("triggerThreshold", triggerThreshold)
        o.put("locAddress", locAddress); o.put("triggerParam", triggerParam)
        val arr = JSONArray()
        for (s in steps) arr.put(s.toJson())
        o.put("steps", arr)
        return o
    }
    companion object {
        fun fromJson(o: JSONObject): Flow {
            val steps = mutableListOf<Step>()
            val arr = o.optJSONArray("steps")
            if (arr != null) {
                for (i in 0 until arr.length()) steps.add(Step.fromJson(arr.getJSONObject(i)))
            }
            val rawKind = o.optString("kind", "macro")
            val kind = if (rawKind == "shortcut") "macro" else rawKind
            /* Old flows stored the lookup address in locName, which showed on the card.
               If locName looks like a full address, move it to locAddress so the card
               stays generic. */
            var rawLocName = o.optString("locName", "")
            var rawLocAddress = o.optString("locAddress", "")
            val looksLikeAddress = rawLocAddress.isEmpty() && (
                rawLocName.length > 25 ||
                (rawLocName.any { it.isDigit() } && rawLocName.contains(","))
            )
            if (looksLikeAddress) {
                rawLocAddress = rawLocName
                rawLocName = ""
            }
            return Flow(
                o.optString("id", UUID.randomUUID().toString()),
                kind,
                o.optString("name", "Untitled"),
                o.optBoolean("enabled", true),
                o.optString("trigger", "time"),
                o.optInt("hour", 9),
                o.optInt("minute", 0),
                steps,
                o.optDouble("locLat", 0.0),
                o.optDouble("locLng", 0.0),
                o.optInt("locRadius", 200),
                rawLocName,
                o.optInt("triggerThreshold", 20),
                rawLocAddress,
                o.optString("triggerParam", "")
            )
        }
    }
}

object LogStore {
    private const val PREFS = "flowlab"
    private const val KEY = "run_log_json"
    fun add(ctx: Context, line: String) {
        try {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val arr = try { JSONArray(prefs.getString(KEY, "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
            arr.put(line)
            while (arr.length() > 200) arr.remove(0)
            prefs.edit().putString(KEY, arr.toString()).apply()
        } catch (e: Exception) { }
    }
    fun load(ctx: Context): List<String> {
        return try {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val arr = JSONArray(prefs.getString(KEY, "[]") ?: "[]")
            val out = mutableListOf<String>()
            for (i in 0 until arr.length()) out.add(arr.optString(i, ""))
            out
        } catch (e: Exception) { emptyList() }
    }
    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, "[]").apply()
    }
}

object Store {
    private const val PREFS = "flowlab"
    private const val KEY = "flows_json"

    fun load(ctx: Context): MutableList<Flow> {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null)
        if (raw.isNullOrEmpty()) {
            val samples = sampleFlows()
            save(ctx, samples)
            return samples
        }
        val out = mutableListOf<Flow>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) out.add(Flow.fromJson(arr.getJSONObject(i)))
        } catch (e: Exception) {
            // Corrupt save: fall back to samples rather than crashing.
            return sampleFlows()
        }
        return out
    }

    fun save(ctx: Context, flows: List<Flow>) {
        val arr = JSONArray()
        for (f in flows) arr.put(f.toJson())
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    fun byId(ctx: Context, id: String): Flow? {
        return load(ctx).firstOrNull { it.id == id }
    }

    private fun sampleFlows(): MutableList<Flow> {
        val macroSample = Flow(
            UUID.randomUUID().toString(), "macro", "Morning check", true, "none", 9, 0,
            mutableListOf(
                Step("get_time", "time", ""),
                Step("message", "Good morning, it is \${time}", ""),
                Step("vibrate", "300", "")
            )
        )
        val ifSample = Flow(
            UUID.randomUUID().toString(), "macro", "Battery check", true, "none", 9, 0,
            mutableListOf(
                Step("if", "battery", "20", "<"),
                Step("notify", "Battery is below 20%", ""),
                Step("else", "", ""),
                Step("message", "Battery is fine", ""),
                Step("end_if", "", "")
            )
        )
        val automation = Flow(
            UUID.randomUUID().toString(), "automation", "Charging started", false, "charging_on", 9, 0,
            mutableListOf(
                Step("notify", "Charger connected", ""),
                Step("message", "Charging started", "")
            )
        )
        return mutableListOf(macroSample, ifSample, automation)
    }
}

object ThemePrefs {
    fun isDark(ctx: Context): Boolean {
        return ctx.getSharedPreferences("flowlab", Context.MODE_PRIVATE).getBoolean("dark_mode", false)
    }
    fun setDark(ctx: Context, dark: Boolean) {
        ctx.getSharedPreferences("flowlab", Context.MODE_PRIVATE).edit().putBoolean("dark_mode", dark).apply()
    }
    fun apply(ctx: Context) {
        // Must be called before super.onCreate / setContentView in an activity.
        if (isDark(ctx)) {
            (ctx as? android.app.Activity)?.setTheme(android.R.style.Theme_Material_NoActionBar)
        } else {
            (ctx as? android.app.Activity)?.setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        }
    }
}

object Engine {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun substitute(text: String, vars: Map<String, String>): String {
        var out = text
        val regex = Regex("\\$\\{([^}]+)\\}")
        out = regex.replace(out) { m -> vars[m.groupValues[1]] ?: "" }
        return out
    }

    /* Speak the text, waiting until it finishes or 60 seconds pass, then shut
       the engine down. With no TTS engine on the device, fail fast. */
    private fun speakText(ctx: Context, text: String): Boolean {
        if (text.isEmpty()) return false
        return try {
            val initLatch = CountDownLatch(1)
            var tts: android.speech.tts.TextToSpeech? = null
            val doneLatch = CountDownLatch(1)
            var initOk = false
            tts = android.speech.tts.TextToSpeech(ctx.applicationContext, { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    initOk = true
                    tts?.language = Locale.US
                    tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(id: String?) {}
                        override fun onDone(id: String?) { doneLatch.countDown() }
                        override fun onError(id: String?) { doneLatch.countDown() }
                    })
                    tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "flow-speak")
                } else {
                    doneLatch.countDown()
                }
                initLatch.countDown()
            })
            initLatch.await(10, TimeUnit.SECONDS)
            if (!initOk) {
                try { tts?.shutdown() } catch (e: Exception) { }
                return false
            }
            val finished = doneLatch.await(60, TimeUnit.SECONDS)
            try { tts?.stop(); tts?.shutdown() } catch (e: Exception) { }
            finished
        } catch (e: Exception) {
            false
        }
    }

    // Runs on the calling thread. Callers run this on a background thread.
    fun run(ctx: Context, flow: Flow, onLog: (String) -> Unit) {
        run(ctx, flow, onLog, null, 0)
    }

    /* Run another flow: the child starts with a copy of the parent's variables
       and returns its own, so the parent sees what the child set. Depth is capped. */
    fun run(ctx: Context, flow: Flow, onLog: (String) -> Unit, initialVars: Map<String, String>?, depth: Int): MutableMap<String, String> {
        val emit: (String) -> Unit = { line -> try { LogStore.add(ctx, line) } catch (e: Exception) { }; onLog(line) }
        val onLog = emit
        val vars = mutableMapOf<String, String>()
        // Built-in variables, available to If conditions without a prior step.
        val nowDate = Date()
        vars["time"] = SimpleDateFormat("HH:mm", Locale.US).format(nowDate)
        vars["date"] = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(nowDate)
        vars["hour"] = SimpleDateFormat("HH", Locale.US).format(nowDate)
        vars["minute"] = SimpleDateFormat("mm", Locale.US).format(nowDate)
        /* Flashlight is a built-in too, so If can branch on it. The value is
           "on"/"off", from the last state Flow set. */
        vars["flashlight"] = if (ctx.getSharedPreferences("flowlab", Context.MODE_PRIVATE).getBoolean("flashlight_on", false)) "on" else "off"
        /* Other toggle states a regular app can read. A missing permission leaves
           that variable unset instead of breaking the run. Values are on/off,
           except ringer: normal/vibrate/silent. */
        try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            vars["ringer"] = when (am.ringerMode) {
                android.media.AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
                android.media.AudioManager.RINGER_MODE_SILENT -> "silent"
                else -> "normal"
            }
        } catch (e: Exception) { }
        try {
            val bm = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
            vars["bluetooth"] = if (bm.adapter?.isEnabled == true) "on" else "off"
        } catch (e: Exception) { }
        try {
            @Suppress("DEPRECATION")
            val wm = ctx.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            vars["wifi"] = if (wm.isWifiEnabled) "on" else "off"
        } catch (e: Exception) { }
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            vars["dnd"] = if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE) "on" else "off"
        } catch (e: Exception) { }
        try {
            val nfc = android.nfc.NfcAdapter.getDefaultAdapter(ctx)
            if (nfc != null) vars["nfc"] = if (nfc.isEnabled) "on" else "off"
        } catch (e: Exception) { }
        try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            vars["location"] = if (lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) "on" else "off"
        } catch (e: Exception) { }
        try {
            vars["airplane"] = if (android.provider.Settings.Global.getInt(ctx.contentResolver, android.provider.Settings.Global.AIRPLANE_MODE_ON, 0) == 1) "on" else "off"
        } catch (e: Exception) { }
        try {
            vars["vibrate_ringing"] = if (android.provider.Settings.System.getInt(ctx.contentResolver, "vibrate_when_ringing", 0) == 1) "on" else "off"
        } catch (e: Exception) { }
        if (initialVars != null) vars.putAll(initialVars)
        try {
            val batt = ctx.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (batt != null) {
                val level = batt.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = batt.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
                if (level >= 0 && scale > 0) vars["battery"] = ((level * 100) / scale).toString()
                val status = batt.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                vars["charging"] = (status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL).toString()
            }
        } catch (e: Exception) {
        }
        onLog("Running: " + flow.name)
        /* If-stack: each frame holds the active branch and whether any branch
           in the chain has matched. Once one matches, later Else If branches
           are skipped and Else runs only if none did. Nesting works because
           the active check ANDs all frames together. */
        class IfFrame(var currentActive: Boolean, var matched: Boolean)
        val stack = mutableListOf<IfFrame>()
        /* While-stack: each frame remembers where its While step lives, so End
           While can jump back and re-check. Capped at 100 iterations. */
        class WhileFrame(var startIndex: Int, var count: Int)
        val whileStack = mutableListOf<WhileFrame>()
        class RepeatFrame(var startIndex: Int, var total: Int, var done: Int)
        val repeatStack = mutableListOf<RepeatFrame>()
        fun skipToEndRepeat(from: Int): Int {
            var depth = 1
            var i = from + 1
            while (i < flow.steps.size && depth > 0) {
                if (flow.steps[i].type == "repeat") depth++
                if (flow.steps[i].type == "end_repeat") depth--
                i++
            }
            return i
        }
        fun skipToEndWhile(from: Int): Int {
            var depth = 1
            var i = from + 1
            while (i < flow.steps.size && depth > 0) {
                if (flow.steps[i].type == "while") depth++
                if (flow.steps[i].type == "end_while") depth--
                i++
            }
            return i
        }
        fun isActive(): Boolean {
            var active = true
            for (f in stack) active = active && f.currentActive
            return active
        }
        fun parseTimeMinutes(s: String): Int? {
            val m = Regex("""^(\d{1,2}):(\d{2})$""").matchEntire(s.trim()) ?: return null
            val h = m.groupValues[1].toIntOrNull() ?: return null
            val min = m.groupValues[2].toIntOrNull() ?: return null
            if (h !in 0..23 || min !in 0..59) return null
            return h * 60 + min
        }
        fun evalCondition(step: Step): Boolean {
            val actual = vars[step.p1] ?: ""
            val expected = substitute(step.p2, vars)
            val op = when {
                step.op.isNotEmpty() -> step.op
                step.type == "if_eq" -> "=="
                step.type == "if_ne" -> "!="
                step.type == "if_contains" -> "contains"
                step.type == "if_gt" -> ">"
                else -> "<"
            }
            /* HH:mm values are text, not numbers. If both sides look like HH:mm,
               compare minutes since midnight, so time > 18:00 works. */
            val actualMinutes = parseTimeMinutes(actual)
            val expectedMinutes = parseTimeMinutes(expected)
            if (actualMinutes != null && expectedMinutes != null) {
                return when (op) {
                    "==" -> actualMinutes == expectedMinutes
                    "!=" -> actualMinutes != expectedMinutes
                    ">" -> actualMinutes > expectedMinutes
                    "<" -> actualMinutes < expectedMinutes
                    "<=" -> actualMinutes <= expectedMinutes
                    ">=" -> actualMinutes >= expectedMinutes
                    "contains" -> actual.contains(expected)
                    else -> false
                }
            }
            return when (op) {
                "==" -> actual == expected
                "!=" -> actual != expected
                "contains" -> actual.contains(expected)
                ">" -> (actual.toDoubleOrNull() ?: 0.0) > (expected.toDoubleOrNull() ?: 0.0)
                "<=" -> (actual.toDoubleOrNull() ?: 0.0) <= (expected.toDoubleOrNull() ?: 0.0)
                ">=" -> (actual.toDoubleOrNull() ?: 0.0) >= (expected.toDoubleOrNull() ?: 0.0)
                else -> (actual.toDoubleOrNull() ?: 0.0) < (expected.toDoubleOrNull() ?: 0.0)
            }
        }
        var index = 0
        while (index < flow.steps.size) {
            val step = flow.steps[index]
            try {
                if (step.type == "while") {
                    if (!isActive()) {
                        index = skipToEndWhile(index)
                        continue
                    }
                    val existing = whileStack.lastOrNull()
                    if (existing != null && existing.startIndex == index) {
                        // Looping back: re-check, do not push a second frame.
                        if (evalCondition(step)) {
                            onLog("Step " + (index + 1) + ": While " + step.p1 + " (" + (vars[step.p1] ?: "") + ") " + step.op + " " + substitute(step.p2, vars) + " = true, looping")
                            index++
                            continue
                        } else {
                            whileStack.removeAt(whileStack.size - 1)
                            onLog("Step " + (index + 1) + ": While condition now false, exiting loop")
                            index = skipToEndWhile(index)
                            continue
                        }
                    } else {
                        val result = evalCondition(step)
                        onLog("Step " + (index + 1) + ": While " + step.p1 + " (" + (vars[step.p1] ?: "") + ") " + step.op + " " + substitute(step.p2, vars) + " = " + result)
                        if (result) {
                            whileStack.add(WhileFrame(index, 0))
                            index++
                            continue
                        } else {
                            index = skipToEndWhile(index)
                            continue
                        }
                    }
                }
                if (step.type == "end_while") {
                    if (whileStack.isEmpty()) {
                        onLog("Step " + (index + 1) + ": End While without While, ignored")
                        index++
                        continue
                    }
                    val frame = whileStack.last()
                    frame.count++
                    if (frame.count >= 100) {
                        whileStack.removeAt(whileStack.size - 1)
                        onLog("Step " + (index + 1) + ": While loop stopped after 100 iterations to prevent an infinite loop")
                        index++
                        continue
                    } else {
                        index = frame.startIndex
                        continue
                    }
                }
                if (step.type == "repeat") {
                    if (!isActive()) { index = skipToEndRepeat(index); continue }
                    val existing = repeatStack.lastOrNull()
                    if (existing != null && existing.startIndex == index) { index++; continue }
                    val total = (substitute(step.p1, vars).toIntOrNull() ?: 0).coerceIn(0, 100)
                    if (total <= 0) { index = skipToEndRepeat(index); continue }
                    repeatStack.add(RepeatFrame(index, total, 0))
                    onLog("Step " + (index + 1) + ": Repeat " + total + " times")
                    index++; continue
                }
                if (step.type == "end_repeat") {
                    if (repeatStack.isEmpty()) { onLog("Step " + (index + 1) + ": End Repeat without Repeat, ignored"); index++; continue }
                    val frame = repeatStack.last()
                    frame.done++
                    if (frame.done < frame.total) { index = frame.startIndex; continue }
                    repeatStack.removeAt(repeatStack.size - 1)
                    index++; continue
                }
                if (step.type in IF_TYPES) {
                    val result = evalCondition(step)
                    stack.add(IfFrame(result, result))
                    onLog("Step " + (index + 1) + ": If " + step.p1 + " (" + (vars[step.p1] ?: "") + ") " + (if (step.op.isNotEmpty()) step.op else step.type) + " " + substitute(step.p2, vars) + " = " + result)
                    index++
                    continue
                }
                if (step.type == "elseif") {
                    if (stack.isEmpty()) {
                        onLog("Step " + (index + 1) + ": Else If without If, ignored")
                    } else {
                        val frame = stack.last()
                        if (frame.matched) {
                            frame.currentActive = false
                            onLog("Step " + (index + 1) + ": Else If skipped, an earlier branch already matched")
                        } else {
                            val result = evalCondition(step)
                            frame.currentActive = result
                            frame.matched = result
                            onLog("Step " + (index + 1) + ": Else If " + step.p1 + " (" + (vars[step.p1] ?: "") + ") " + step.op + " " + substitute(step.p2, vars) + " = " + result)
                        }
                    }
                    index++
                    continue
                }
                if (step.type == "else") {
                    if (stack.isEmpty()) {
                        onLog("Step " + (index + 1) + ": Else without If, ignored")
                    } else {
                        val frame = stack.last()
                        frame.currentActive = !frame.matched
                        frame.matched = true
                    }
                    index++
                    continue
                }
                if (step.type == "end_if") {
                    if (stack.isNotEmpty()) stack.removeAt(stack.size - 1) else onLog("Step " + (index + 1) + ": End If without If, ignored")
                    index++
                    continue
                }
                if (!isActive()) {
                    index++
                    continue
                }
                /* Disabled steps are skipped. Block headers are handled above, so
                   disabling one does not break matching for the steps around it. */
                if (step.disabled) {
                    onLog("Step " + (index + 1) + ": skipped, step is disabled")
                    index++
                    continue
                }
                when (step.type) {
                    "message" -> {
                        val text = substitute(step.p1, vars)
                        mainHandler.post { Toast.makeText(ctx, text, Toast.LENGTH_LONG).show() }
                        onLog("Step " + (index + 1) + ": message: " + text)
                    }
                    "notify" -> {
                        val text = substitute(step.p1, vars)
                        showNotification(ctx, flow.name, text)
                        onLog("Step " + (index + 1) + ": notification: " + text)
                    }
                    "vibrate" -> {
                        val ms = step.p1.toLongOrNull() ?: 300L
                        val vib = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                        if (Build.VERSION.SDK_INT >= 26) {
                            vib?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vib?.vibrate(ms)
                        }
                        onLog("Step " + (index + 1) + ": vibrated " + ms + " ms")
                    }
                    "wait" -> {
                        val sec = step.p1.toLongOrNull() ?: 1L
                        Thread.sleep(sec * 1000L)
                        onLog("Step " + (index + 1) + ": waited " + sec + "s")
                    }
                    "open_url" -> {
                        /* Spaces in a substituted URL are encoded; plain URLs are unchanged. */
                        val url = substitute(step.p1, vars).replace(" ", "%20")
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(intent)
                        onLog("Step " + (index + 1) + ": opened " + url)
                    }
                    "open_app" -> {
                        val pkg = substitute(step.p1, vars)
                        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(intent)
                            onLog("Step " + (index + 1) + ": opened app " + (if (step.p2.isNotEmpty()) step.p2 else pkg))
                        } else {
                            onLog("Step " + (index + 1) + ": could not open app " + pkg + ", not installed or no launch activity")
                        }
                    }
                    "set_var" -> {
                        vars[step.p1] = substitute(step.p2, vars)
                        onLog("Step " + (index + 1) + ": " + step.p1 + " = " + vars[step.p1])
                    }
                    "ask_input" -> {
                        val varName = step.p1
                        val prompt = substitute(step.p2, vars)
                        val activity = ctx as? android.app.Activity
                        if (activity == null) {
                            onLog("Step " + (index + 1) + ": Ask for input skipped, Flow is not in the foreground, so no dialog can be shown. Variable " + varName + " left empty.")
                        } else {
                            val latch = CountDownLatch(1)
                            var answer = ""
                            mainHandler.post {
                                try {
                                    val edit = android.widget.EditText(activity)
                                    android.app.AlertDialog.Builder(activity)
                                        .setTitle(if (prompt.isNotEmpty()) prompt else "Enter a value")
                                        .setView(edit)
                                        .setPositiveButton("OK") { _, _ -> answer = edit.text.toString(); latch.countDown() }
                                        .setNegativeButton("Cancel") { _, _ -> latch.countDown() }
                                        .setOnCancelListener { latch.countDown() }
                                        .show()
                                } catch (e: Exception) {
                                    latch.countDown()
                                }
                            }
                            latch.await(5, TimeUnit.MINUTES)
                            vars[varName] = answer
                            onLog("Step " + (index + 1) + ": asked for input, " + varName + " = " + answer)
                        }
                    }
                    "speak" -> {
                        val text = substitute(step.p1, vars)
                        val ok = speakText(ctx, text)
                        onLog("Step " + (index + 1) + ": spoke \"" + text + "\", result=" + ok)
                    }
                    "get_time" -> {
                        val now = SimpleDateFormat("HH:mm", Locale.US).format(Date())
                        vars[step.p1] = now
                        onLog("Step " + (index + 1) + ": " + step.p1 + " = " + now)
                    }
                    "http_get" -> {
                        val urlText = substitute(step.p1, vars)
                        val body = httpGet(urlText)
                        vars[step.p2] = body
                        onLog("Step " + (index + 1) + ": HTTP GET " + urlText + " -> " + body.length + " chars into " + step.p2)
                    }
                    "stop_if" -> {
                        val actual = vars[step.p1] ?: ""
                        if (actual != step.p2) {
                            onLog("Stopped: " + step.p1 + " is '" + actual + "', not '" + step.p2 + "'")
                            return vars
                        }
                        onLog("Step " + (index + 1) + ": condition passed")
                    }
                    "bt_on", "bt_off" -> {
                        if (!isDeviceOwner(ctx)) {
                            onLog("Step " + (index + 1) + ": Bluetooth toggle needs Device Owner. Open the Device Owner section in Flow to set it up, or use the Bluetooth settings step instead.")
                        } else {
                            val ok = setBluetooth(ctx, step.type == "bt_on")
                            onLog("Step " + (index + 1) + ": Bluetooth " + (if (step.type == "bt_on") "on" else "off") + " requested, result=" + ok)
                        }
                    }
                    "flash_on", "flash_off" -> {
                        val ok = setFlashlight(ctx, step.type == "flash_on")
                        onLog("Step " + (index + 1) + ": Flashlight " + (if (step.type == "flash_on") "on" else "off") + ", result=" + ok)
                    }
                    "dnd_on", "dnd_off" -> {
                        val ok = setDnd(ctx, step.type == "dnd_on")
                        onLog("Step " + (index + 1) + ": Do Not Disturb " + (if (step.type == "dnd_on") "on" else "off") + ", result=" + ok)
                    }
                    "panel_wifi", "panel_internet", "panel_nfc", "panel_volume" -> {
                        val action = when (step.type) {
                            "panel_wifi" -> android.provider.Settings.Panel.ACTION_WIFI
                            "panel_internet" -> android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY
                            "panel_nfc" -> android.provider.Settings.Panel.ACTION_NFC
                            else -> android.provider.Settings.Panel.ACTION_VOLUME
                        }
                        val intent = Intent(action)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(intent)
                        onLog("Step " + (index + 1) + ": opened " + step.type + " panel, user taps the toggle there")
                    }
                    "open_wifi_settings", "open_bt_settings", "open_location_settings", "open_tether_settings" -> {
                        val action = when (step.type) {
                            "open_wifi_settings" -> android.provider.Settings.ACTION_WIFI_SETTINGS
                            "open_bt_settings" -> android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
                            "open_location_settings" -> android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS
                            else -> "android.settings.TETHER_SETTINGS"
                        }
                        val intent = Intent(action)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(intent)
                        onLog("Step " + (index + 1) + ": opened " + step.type)
                    }
                    "set_bluetooth" -> {
                        if (!isDeviceOwner(ctx)) {
                            onLog("Step " + (index + 1) + ": Bluetooth toggle needs Device Owner, see Device Owner setup, or use Bluetooth settings step instead.")
                        } else {
                            val ok = setBluetooth(ctx, step.p1 == "on")
                            if (ok) vars["bluetooth"] = step.p1
                            onLog("Step " + (index + 1) + ": Bluetooth " + step.p1 + ", result=" + ok)
                        }
                    }
                    "set_wifi" -> {
                        if (!isDeviceOwner(ctx)) {
                            onLog("Step " + (index + 1) + ": Wi-Fi toggle needs Device Owner, see Device Owner setup, or use the Internet panel step instead.")
                        } else {
                            val ok = setWifi(ctx, step.p1 == "on")
                            if (ok) vars["wifi"] = step.p1
                            onLog("Step " + (index + 1) + ": Wi-Fi " + step.p1 + ", result=" + ok)
                        }
                    }
                    "set_flashlight" -> {
                        val ok = setFlashlight(ctx, step.p1 == "on")
                        if (ok) {
                            ctx.getSharedPreferences("flowlab", Context.MODE_PRIVATE).edit().putBoolean("flashlight_on", step.p1 == "on").apply()
                            vars["flashlight"] = step.p1
                        }
                        onLog("Step " + (index + 1) + ": Flashlight " + step.p1 + ", result=" + ok)
                    }
                    "toggle_flashlight" -> {
                        /* Android has no "is torch on?" query, so Flow remembers the last state
                           it set and flips that. The on/off step above keeps this in sync. */
                        val prefs = ctx.getSharedPreferences("flowlab", Context.MODE_PRIVATE)
                        val next = !prefs.getBoolean("flashlight_on", false)
                        val ok = setFlashlight(ctx, next)
                        if (ok) {
                            prefs.edit().putBoolean("flashlight_on", next).apply()
                            vars["flashlight"] = if (next) "on" else "off"
                        }
                        onLog("Step " + (index + 1) + ": Flashlight toggled to " + (if (next) "on" else "off") + ", result=" + ok)
                    }
                    "set_dnd" -> {
                        val ok = setDnd(ctx, step.p1 == "on")
                        if (ok) vars["dnd"] = step.p1
                        onLog("Step " + (index + 1) + ": Do Not Disturb " + step.p1 + ", result=" + ok)
                    }
                    "set_ringer" -> {
                        val ok = setRinger(ctx, step.p1)
                        if (ok) vars["ringer"] = step.p1
                        onLog("Step " + (index + 1) + ": Ringer mode " + step.p1 + ", result=" + ok)
                    }
                    "set_vibrate_ringing" -> {
                        val ok = setVibrateRinging(ctx, step.p1 == "on")
                        if (ok) vars["vibrate_ringing"] = step.p1
                        onLog("Step " + (index + 1) + ": Vibrate when ringing " + step.p1 + ", result=" + ok)
                    }
                    "open_panel" -> {
                        val action = when (step.p1) {
                            "wifi" -> android.provider.Settings.Panel.ACTION_WIFI
                            "internet" -> android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY
                            "nfc" -> android.provider.Settings.Panel.ACTION_NFC
                            else -> android.provider.Settings.Panel.ACTION_VOLUME
                        }
                        val intent = Intent(action)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(intent)
                        onLog("Step " + (index + 1) + ": opened " + step.p1 + " panel, user taps the toggle there. Note: Wi-Fi off cannot be done silently by a regular app, the panel is the closest step.")
                    }
                    "open_settings" -> {
                        val action = when (step.p1) {
                            "wifi" -> android.provider.Settings.ACTION_WIFI_SETTINGS
                            "bluetooth" -> android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
                            "location" -> android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS
                            else -> "android.settings.TETHER_SETTINGS"
                        }
                        val intent = Intent(action)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(intent)
                        onLog("Step " + (index + 1) + ": opened " + step.p1 + " settings")
                    }
                    "get_location" -> {
                        val loc = getLastLocation(ctx)
                        if (loc != null) {
                            vars[step.p1] = loc.first.toString()
                            vars[step.p2] = loc.second.toString()
                            onLog("Step " + (index + 1) + ": location saved into " + step.p1 + " / " + step.p2)
                        } else {
                            onLog("Step " + (index + 1) + ": no last known location, grant location permission and try again")
                        }
                    }
                    "set_brightness" -> {
                        val pct = (substitute(step.p1, vars).toIntOrNull() ?: 50).coerceIn(0, 100)
                        if (!android.provider.Settings.System.canWrite(ctx)) {
                            val intent = Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS)
                            intent.data = android.net.Uri.parse("package:" + ctx.packageName)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try { ctx.startActivity(intent) } catch (e: Exception) { }
                            onLog("Step " + (index + 1) + ": Brightness needs Modify settings permission, opened settings, grant it and run again")
                        } else {
                            android.provider.Settings.System.putInt(ctx.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                            android.provider.Settings.System.putInt(ctx.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, (pct * 255) / 100)
                            onLog("Step " + (index + 1) + ": Brightness set to " + pct + "%")
                        }
                    }
                    "set_volume" -> {
                        val pct = (substitute(step.p2, vars).toIntOrNull() ?: 50).coerceIn(0, 100)
                        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                        val stream = when (step.p1.lowercase()) {
                            "ring" -> android.media.AudioManager.STREAM_RING
                            "alarm" -> android.media.AudioManager.STREAM_ALARM
                            else -> android.media.AudioManager.STREAM_MUSIC
                        }
                        val max = am.getStreamMaxVolume(stream)
                        am.setStreamVolume(stream, (pct * max) / 100, 0)
                        onLog("Step " + (index + 1) + ": " + step.p1 + " volume set to " + pct + "%")
                    }
                    "set_screen_timeout" -> {
                        val sec = (substitute(step.p1, vars).toIntOrNull() ?: 60).coerceIn(5, 3600)
                        if (!android.provider.Settings.System.canWrite(ctx)) {
                            val intent = Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS)
                            intent.data = android.net.Uri.parse("package:" + ctx.packageName)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try { ctx.startActivity(intent) } catch (e: Exception) { }
                            onLog("Step " + (index + 1) + ": Screen timeout needs Modify settings permission, opened settings, grant it and run again")
                        } else {
                            android.provider.Settings.System.putInt(ctx.contentResolver, android.provider.Settings.System.SCREEN_OFF_TIMEOUT, sec * 1000)
                            onLog("Step " + (index + 1) + ": Screen timeout set to " + sec + "s")
                        }
                    }
                    "copy_clipboard" -> {
                        val text = substitute(step.p1, vars)
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Flow", text))
                        onLog("Step " + (index + 1) + ": Copied to clipboard")
                    }
                    "share_text" -> {
                        val text = substitute(step.p1, vars)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        val chooser = Intent.createChooser(intent, "Share from Flow")
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(chooser)
                        onLog("Step " + (index + 1) + ": Share sheet opened")
                    }
                    "var_op" -> {
                        val target = step.p1
                        val operand = substitute(step.p2, vars)
                        val current = vars[target] ?: ""
                        val result = when (step.op) {
                            "add" -> fmtNum((current.toDoubleOrNull() ?: 0.0) + (operand.toDoubleOrNull() ?: 0.0))
                            "subtract" -> fmtNum((current.toDoubleOrNull() ?: 0.0) - (operand.toDoubleOrNull() ?: 0.0))
                            "multiply" -> fmtNum((current.toDoubleOrNull() ?: 0.0) * (operand.toDoubleOrNull() ?: 0.0))
                            "divide" -> { val d = operand.toDoubleOrNull() ?: 0.0; if (d == 0.0) "0" else fmtNum((current.toDoubleOrNull() ?: 0.0) / d) }
                            "uppercase" -> current.uppercase()
                            "lowercase" -> current.lowercase()
                            "append" -> current + operand
                            "prepend" -> operand + current
                            "length" -> current.length.toString()
                            else -> operand
                        }
                        vars[target] = result
                        onLog("Step " + (index + 1) + ": " + target + " " + step.op + " = " + result)
                    }
                    "run_flow" -> {
                        val target = Store.byId(ctx, step.p1)
                        if (target == null) {
                            onLog("Step " + (index + 1) + ": Run flow failed, flow not found")
                        } else if (depth >= 3) {
                            onLog("Step " + (index + 1) + ": Run flow skipped, already 3 flows deep")
                        } else {
                            val childVars = run(ctx, target, onLog, vars, depth + 1)
                            vars.putAll(childVars)
                            onLog("Step " + (index + 1) + ": Ran flow " + target.name)
                        }
                    }
                    "http_post" -> {
                        val urlText = substitute(step.p1, vars)
                        val body = substitute(step.p2, vars)
                        val resp = httpPost(urlText, body)
                        if (step.op.isNotEmpty()) vars[step.op] = resp
                        onLog("Step " + (index + 1) + ": HTTP POST " + urlText + " -> " + resp.length + " chars" + (if (step.op.isNotEmpty()) " into " + step.op else ""))
                    }
                    "notify_buttons" -> {
                        val text = substitute(step.p1, vars)
                        showNotificationWithButton(ctx, flow.name, text, substitute(step.p2, vars), step.op)
                        onLog("Step " + (index + 1) + ": notification with button: " + text)
                    }
                    "play_sound" -> {
                        val secs = (substitute(step.p2, vars).toIntOrNull() ?: 3).coerceIn(1, 30)
                        val type = when (step.p1.lowercase()) {
                            "alarm" -> android.media.RingtoneManager.TYPE_ALARM
                            "ringtone" -> android.media.RingtoneManager.TYPE_RINGTONE
                            else -> android.media.RingtoneManager.TYPE_NOTIFICATION
                        }
                        try {
                            val uri = android.media.RingtoneManager.getDefaultUri(type)
                            val rt = android.media.RingtoneManager.getRingtone(ctx, uri)
                            rt?.play()
                            Thread.sleep(secs * 1000L)
                            try { rt?.stop() } catch (e: Exception) { }
                            onLog("Step " + (index + 1) + ": Played " + step.p1 + " sound for " + secs + "s")
                        } catch (e: Exception) {
                            onLog("Step " + (index + 1) + ": Play sound failed: " + (e.message ?: ""))
                        }
                    }
                    "flash_blink" -> {
                        val count = (substitute(step.p1, vars).toIntOrNull() ?: 3).coerceIn(1, 10)
                        val interval = (substitute(step.p2, vars).toLongOrNull() ?: 500L).coerceIn(100L, 5000L)
                        val prefs = ctx.getSharedPreferences("flowlab", Context.MODE_PRIVATE)
                        val prev = prefs.getBoolean("flashlight_on", false)
                        for (i in 0 until count) {
                            setFlashlight(ctx, true); Thread.sleep(interval)
                            setFlashlight(ctx, false); Thread.sleep(interval)
                        }
                        setFlashlight(ctx, prev)
                        prefs.edit().putBoolean("flashlight_on", prev).apply()
                        vars["flashlight"] = if (prev) "on" else "off"
                        onLog("Step " + (index + 1) + ": Blinked flashlight " + count + " times")
                    }
                    "comment" -> {
                        // No-op by design: labels a section in the editor.
                        onLog("Step " + (index + 1) + ": Comment")
                    }
                    "choose_list" -> {
                        /* A dialog needs an Activity, so background runs skip this step instead
                           of crashing, same as Ask for input. */
                        val activity = ctx as? android.app.Activity
                        val options = step.p2.lines().map { substitute(it, vars) }.filter { it.isNotEmpty() }
                        if (options.isEmpty()) {
                            onLog("Step " + (index + 1) + ": Choose from list skipped, no options defined")
                        } else if (activity == null) {
                            onLog("Step " + (index + 1) + ": Choose from list skipped, Flow is not in the foreground, so no dialog can be shown. Variable " + step.p1 + " left empty.")
                        } else {
                            val latch = CountDownLatch(1)
                            var picked = ""
                            mainHandler.post {
                                try {
                                    android.app.AlertDialog.Builder(activity)
                                        .setTitle(if (step.op.isNotEmpty()) substitute(step.op, vars) else "Choose one")
                                        .setItems(options.toTypedArray()) { _, which -> picked = options[which]; latch.countDown() }
                                        .setOnCancelListener { latch.countDown() }
                                        .show()
                                } catch (e: Exception) {
                                    latch.countDown()
                                }
                            }
                            latch.await(5, TimeUnit.MINUTES)
                            vars[step.p1] = picked
                            onLog("Step " + (index + 1) + ": chose \"" + picked + "\" into " + step.p1)
                        }
                    }
                    "get_clipboard" -> {
                        /* Android 10+ only lets the foreground app read the clipboard, so a
                           background run may get nothing. That is logged, not a failure. */
                        val text = try {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = cm.primaryClip
                            if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(ctx)?.toString() ?: "" else ""
                        } catch (e: Exception) { "" }
                        vars[step.p1] = text
                        if (text.isEmpty()) {
                            onLog("Step " + (index + 1) + ": Clipboard was empty or unavailable, " + step.p1 + " left empty")
                        } else {
                            onLog("Step " + (index + 1) + ": Clipboard read into " + step.p1)
                        }
                    }
                    "random_number" -> {
                        var lo = substitute(step.p1, vars).toIntOrNull() ?: 1
                        var hi = substitute(step.p2, vars).toIntOrNull() ?: 100
                        if (lo > hi) { val tmp = lo; lo = hi; hi = tmp }
                        val value = kotlin.random.Random.nextInt(lo, hi + 1)
                        vars[step.op] = value.toString()
                        onLog("Step " + (index + 1) + ": Random number " + value + " into " + step.op)
                    }
                    "send_text_call" -> {
                        val number = substitute(step.p2, vars)
                        try {
                            if (step.p1 == "call") {
                                val intent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + number))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                ctx.startActivity(intent)
                                onLog("Step " + (index + 1) + ": Dialer opened for " + number + ", confirm the call there")
                            } else {
                                val intent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:" + number))
                                intent.putExtra("sms_body", substitute(step.op, vars))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                ctx.startActivity(intent)
                                onLog("Step " + (index + 1) + ": Messages opened for " + number + ", confirm sending there")
                            }
                        } catch (e: Exception) {
                            onLog("Step " + (index + 1) + ": Could not open the " + (if (step.p1 == "call") "dialer" else "messaging app") + ": " + (e.message ?: ""))
                        }
                    }
                    "get_value" -> {
                        val value = when (step.p1) {
                            "brightness" -> {
                                try {
                                    val raw = android.provider.Settings.System.getInt(ctx.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, 128)
                                    ((raw * 100) / 255).toString()
                                } catch (e: Exception) { "" }
                            }
                            "volume" -> {
                                try {
                                    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                                    val stream = when (step.op.lowercase()) {
                                        "ring" -> android.media.AudioManager.STREAM_RING
                                        "alarm" -> android.media.AudioManager.STREAM_ALARM
                                        else -> android.media.AudioManager.STREAM_MUSIC
                                    }
                                    val max = am.getStreamMaxVolume(stream)
                                    if (max > 0) ((am.getStreamVolume(stream) * 100) / max).toString() else ""
                                } catch (e: Exception) { "" }
                            }
                            else -> { // ssid
                                try {
                                    @Suppress("DEPRECATION")
                                    val wm = ctx.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                                    val ssid = wm.connectionInfo?.ssid?.trim('"') ?: ""
                                    if (ssid.isEmpty() || ssid == "<unknown ssid>") "" else ssid
                                } catch (e: Exception) { "" }
                            }
                        }
                        vars[step.p2] = value
                        onLog("Step " + (index + 1) + ": " + step.p1 + " = " + value + " into " + step.p2)
                    }
                    "set_alarm_timer" -> {
                        try {
                            if (step.p1 == "timer") {
                                val secs = (substitute(step.p2, vars).toIntOrNull() ?: 60).coerceIn(1, 86400)
                                val intent = Intent(android.provider.AlarmClock.ACTION_SET_TIMER)
                                    .putExtra(android.provider.AlarmClock.EXTRA_LENGTH, secs)
                                    .putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, substitute(step.op, vars))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                ctx.startActivity(intent)
                                onLog("Step " + (index + 1) + ": Timer opened in Clock for " + secs + "s, confirm it there")
                            } else {
                                val parts = substitute(step.p2, vars).split(":")
                                val hour = (parts.getOrNull(0)?.toIntOrNull() ?: 7).coerceIn(0, 23)
                                val minute = (parts.getOrNull(1)?.toIntOrNull() ?: 0).coerceIn(0, 59)
                                val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM)
                                    .putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
                                    .putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
                                    .putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, substitute(step.op, vars))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                ctx.startActivity(intent)
                                onLog("Step " + (index + 1) + ": Alarm opened in Clock for " + String.format(Locale.US, "%02d:%02d", hour, minute) + ", confirm it there")
                            }
                        } catch (e: Exception) {
                            onLog("Step " + (index + 1) + ": Could not open Clock: " + (e.message ?: ""))
                        }
                    }
                    "file_text" -> {
                        /* Internal storage only: Flow's own filesDir, no picker, no permission.
                           The name is flattened so a step cannot escape into other folders. */
                        val name = step.p2.substringAfterLast("/").substringAfterLast("\\").trim()
                        if (name.isEmpty() || name == "." || name == "..") {
                            onLog("Step " + (index + 1) + ": File step needs a file name")
                        } else {
                            val file = java.io.File(ctx.filesDir, name)
                            if (step.p1 == "read") {
                                if (file.exists()) {
                                    val content = try { file.readText() } catch (e: Exception) { "" }
                                    vars[step.op] = content
                                    onLog("Step " + (index + 1) + ": Read file " + name + " into " + step.op)
                                } else {
                                    vars[step.op] = ""
                                    onLog("Step " + (index + 1) + ": File " + name + " does not exist yet, " + step.op + " left empty")
                                }
                            } else {
                                try {
                                    file.writeText(substitute(step.op, vars))
                                    onLog("Step " + (index + 1) + ": Saved text to file " + name)
                                } catch (e: Exception) {
                                    onLog("Step " + (index + 1) + ": Could not save file " + name + ": " + (e.message ?: ""))
                                }
                            }
                        }
                    }
                }
                index++
            } catch (e: Exception) {
                onLog("Step " + (index + 1) + " failed: " + (e.message ?: e.toString()))
                index++
            }
        }
        if (stack.isNotEmpty()) onLog("Warning: " + stack.size + " If block(s) left without End If")
        if (whileStack.isNotEmpty()) onLog("Warning: " + whileStack.size + " While block(s) left without End While")
        if (repeatStack.isNotEmpty()) onLog("Warning: " + repeatStack.size + " Repeat block(s) left without End Repeat")
        onLog("Done: " + flow.name)
        return vars
    }

    private fun setWifi(ctx: Context, on: Boolean): Boolean {
        return try {
            val wm = ctx.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            @Suppress("DEPRECATION")
            wm.setWifiEnabled(on)
        } catch (e: Exception) {
            false
        }
    }

    private fun setBluetooth(ctx: Context, on: Boolean): Boolean {
        return try {
            val manager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
            val adapter = manager.adapter ?: return false
            if (on) adapter.enable() else adapter.disable()
        } catch (e: Exception) {
            false
        }
    }

    private fun setFlashlight(ctx: Context, on: Boolean): Boolean {
        return try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val id = cm.cameraIdList.firstOrNull() ?: return false
            cm.setTorchMode(id, on)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun setDnd(ctx: Context, on: Boolean): Boolean {
        return try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.isNotificationPolicyAccessGranted) {
                val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
                return false
            }
            nm.setInterruptionFilter(
                if (on) NotificationManager.INTERRUPTION_FILTER_NONE
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun setRinger(ctx: Context, mode: String): Boolean {
        return try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val value = when (mode) {
                "vibrate" -> android.media.AudioManager.RINGER_MODE_VIBRATE
                "silent" -> android.media.AudioManager.RINGER_MODE_SILENT
                else -> android.media.AudioManager.RINGER_MODE_NORMAL
            }
            am.ringerMode = value
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun setVibrateRinging(ctx: Context, on: Boolean): Boolean {
        return try {
            if (!android.provider.Settings.System.canWrite(ctx)) {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS)
                intent.data = android.net.Uri.parse("package:" + ctx.packageName)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
                return false
            }
            android.provider.Settings.System.putInt(ctx.contentResolver, "vibrate_when_ringing", if (on) 1 else 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getLastLocation(ctx: Context): Pair<Double, Double>? {
        return try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val providers = listOf(android.location.LocationManager.GPS_PROVIDER, android.location.LocationManager.NETWORK_PROVIDER)
            for (p in providers) {
                try {
                    val loc = lm.getLastKnownLocation(p)
                    if (loc != null) return Pair(loc.latitude, loc.longitude)
                } catch (e: Exception) {
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun httpGet(urlText: String): String {
        val conn = URL(urlText).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        return try {
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line)
                if (sb.length > 2000) break
            }
            reader.close()
            sb.toString()
        } finally {
            conn.disconnect()
        }
    }

    private fun fmtNum(d: Double): String {
        return if (d == kotlin.math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()
    }

    private fun httpPost(urlText: String, body: String): String {
        return try {
            val conn = URL(urlText).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line)
                if (sb.length > 2000) break
            }
            reader.close()
            conn.disconnect()
            sb.toString()
        } catch (e: Exception) {
            ""
        }
    }

    private fun showNotificationWithButton(ctx: Context, title: String, text: String, label: String, flowId: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("flow", "Flow", NotificationManager.IMPORTANCE_DEFAULT)
            nm.createNotificationChannel(channel)
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(ctx, "flow") else @Suppress("DEPRECATION") android.app.Notification.Builder(ctx)
        builder.setContentTitle(title).setContentText(text).setSmallIcon(android.R.drawable.ic_dialog_info)
        if (flowId.isNotEmpty() && label.isNotEmpty()) {
            val intent = Intent(ctx, FlowActionReceiver::class.java).apply {
                action = "com.hazzmattx.flowlab.FLOW_ACTION"
                putExtra("flowId", flowId)
            }
            val pi = PendingIntent.getBroadcast(ctx, flowId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(android.app.Notification.Action.Builder(null, label, pi).build())
        }
        try { nm.notify((System.currentTimeMillis() % 100000).toInt(), builder.build()) } catch (e: Exception) { }
    }

    private fun showNotification(ctx: Context, title: String, text: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("flow", "Flow", NotificationManager.IMPORTANCE_DEFAULT)
            nm.createNotificationChannel(channel)
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            android.app.Notification.Builder(ctx, "flow")
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(ctx)
        }
        builder.setContentTitle(title).setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
        try {
            nm.notify((System.currentTimeMillis() % 100000).toInt(), builder.build())
        } catch (e: Exception) {
            mainHandler.post { Toast.makeText(ctx, text, Toast.LENGTH_LONG).show() }
        }
    }
}

object LocationState {
    fun get(ctx: Context, flowId: String): Boolean? {
        val raw = ctx.getSharedPreferences("flowlab", Context.MODE_PRIVATE).getString("loc_state_" + flowId, null)
        return raw?.toBoolean()
    }
    fun set(ctx: Context, flowId: String, inside: Boolean) {
        ctx.getSharedPreferences("flowlab", Context.MODE_PRIVATE).edit().putString("loc_state_" + flowId, inside.toString()).apply()
    }
}

object Scheduler {
    fun scheduleAll(ctx: Context) {
        val flows = Store.load(ctx)
        for (f in flows) {
            if (f.kind != "automation" || !f.enabled) continue
            when (f.trigger) {
                "time" -> scheduleTime(ctx, f)
                "time_weekday" -> scheduleWeekday(ctx, f)
                "sunrise" -> scheduleSun(ctx, f, true)
                "sunset" -> scheduleSun(ctx, f, false)
                "interval" -> scheduleInterval(ctx, f)
            }
        }
        if (flows.any { it.kind == "automation" && it.enabled && (it.trigger == "location_enter" || it.trigger == "location_exit" || it.trigger == "battery_below" || it.trigger == "battery_full" || it.trigger == "app_opened" || it.trigger == "wifi_connected" || it.trigger == "wifi_disconnected" || it.trigger == "wifi_ssid" || it.trigger == "bt_device" || it.trigger == "screen_on" || it.trigger == "screen_off" || it.trigger == "headphones_connected" || it.trigger == "headphones_disconnected" || it.trigger == "unlocked") }) {
            startLocationService(ctx)
        }
    }

    fun scheduleWeekday(ctx: Context, flow: Flow) {
        val wanted = flow.triggerParam.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
            .ifEmpty { setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY) }
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, flow.hour)
        cal.set(Calendar.MINUTE, flow.minute)
        cal.set(Calendar.SECOND, 0)
        var guard = 0
        while ((cal.timeInMillis <= System.currentTimeMillis() || cal.get(Calendar.DAY_OF_WEEK) !in wanted) && guard < 10) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            guard++
        }
        scheduleAlarm(ctx, flow, cal.timeInMillis, flow.id.hashCode())
    }

    private fun scheduleAlarm(ctx: Context, flow: Flow, whenMs: Long, requestCode: Int) {
        val intent = Intent(ctx, TimeReceiver::class.java)
        intent.putExtra("flowId", flow.id)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(ctx, requestCode, intent, flags)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
            }
        } catch (e: Exception) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
        }
    }

    fun scheduleSun(ctx: Context, flow: Flow, sunrise: Boolean) {
        val loc = Engine.getLastLocation(ctx) ?: Pair(47.6, -122.3)
        var cal = solarCalendar(loc.first, loc.second, sunrise, Calendar.getInstance())
        if (cal == null || cal.timeInMillis <= System.currentTimeMillis()) {
            val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
            cal = solarCalendar(loc.first, loc.second, sunrise, tomorrow)
        }
        if (cal == null) {
            cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, if (sunrise) 6 else 18)
            cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
            if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        scheduleAlarm(ctx, flow, cal!!.timeInMillis, flow.id.hashCode() + if (sunrise) 1001 else 1002)
    }

    /* Sunrise/sunset time for the given date, via NOAA-style solar
       position. Null when the sun never rises or sets that day. */
    fun solarCalendar(lat: Double, lng: Double, sunrise: Boolean, date: Calendar): Calendar? {
        return try {
            val n = date.get(Calendar.DAY_OF_YEAR)
            val lngHour = lng / 15.0
            val t = n + ((if (sunrise) 6.0 else 18.0) - lngHour) / 24.0
            val m = (0.9856 * t) - 3.289
            var l = m + (1.916 * Math.sin(Math.toRadians(m))) + (0.020 * Math.sin(Math.toRadians(2 * m))) + 282.634
            l = ((l % 360.0) + 360.0) % 360.0
            var ra = Math.toDegrees(Math.atan(0.91764 * Math.tan(Math.toRadians(l))))
            ra = ((ra % 360.0) + 360.0) % 360.0
            val lQuadrant = (Math.floor(l / 90.0) * 90.0)
            val raQuadrant = (Math.floor(ra / 90.0) * 90.0)
            ra += lQuadrant - raQuadrant
            ra /= 15.0
            val sinDec = 0.39782 * Math.sin(Math.toRadians(l))
            val cosDec = Math.cos(Math.asin(sinDec))
            val cosH = (Math.cos(Math.toRadians(90.833)) - (sinDec * Math.sin(Math.toRadians(lat)))) / (cosDec * Math.cos(Math.toRadians(lat)))
            if (cosH > 1.0 || cosH < -1.0) return null
            var h = Math.toDegrees(Math.acos(cosH))
            if (sunrise) h = 360.0 - h
            h /= 15.0
            val t2 = h + ra - (0.06571 * t) - 6.622
            var ut = t2 - lngHour
            ut = ((ut % 24.0) + 24.0) % 24.0
            // Local time: UTC offset from the device timezone at that date.
            val zone = java.util.TimeZone.getDefault()
            val offsetHours = zone.getOffset(date.timeInMillis) / 3600000.0
            var local = ut + offsetHours
            local = ((local % 24.0) + 24.0) % 24.0
            val out = date.clone() as Calendar
            out.set(Calendar.HOUR_OF_DAY, local.toInt())
            out.set(Calendar.MINUTE, ((local - local.toInt()) * 60).toInt())
            out.set(Calendar.SECOND, 0)
            out
        } catch (e: Exception) { null }
    }

    fun startLocationService(ctx: Context) {
        try {
            val intent = Intent(ctx, LocationTriggerService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(intent) else ctx.startService(intent)
        } catch (e: Exception) {
        }
    }

    /* Interval trigger: "<number>:minutes" or "<number>:hours", default 30
       minutes. A one-shot alarm, rescheduled after each firing by
       TimeReceiver. Timing is not exact; battery optimization can delay it. */
    fun scheduleInterval(ctx: Context, flow: Flow) {
        val parts = flow.triggerParam.split(":")
        val n = (parts.getOrNull(0)?.toLongOrNull() ?: 30L).coerceAtLeast(1L)
        val minutes = if (parts.getOrNull(1) == "hours") n * 60L else n
        val whenMs = System.currentTimeMillis() + minutes * 60L * 1000L
        scheduleAlarm(ctx, flow, whenMs, flow.id.hashCode() + 2001)
    }

    fun scheduleTime(ctx: Context, flow: Flow) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, flow.hour)
        cal.set(Calendar.MINUTE, flow.minute)
        cal.set(Calendar.SECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        val intent = Intent(ctx, TimeReceiver::class.java)
        intent.putExtra("flowId", flow.id)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(ctx, flow.id.hashCode(), intent, flags)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            }
        } catch (e: Exception) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    }
}
