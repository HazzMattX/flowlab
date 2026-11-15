package com.hazzmattx.flowlab

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder

/* Foreground location service, no Google Play Services.
   Uses Android LocationManager (GPS + network) only, so it works degoogled.
   It only fires on a change of inside/outside state, not on every location fix,
   and the first fix after starting only records state, it does not fire. */
class LocationTriggerService : Service(), LocationListener {
    private var lm: LocationManager? = null
    private var batteryReceiver: android.content.BroadcastReceiver? = null
    private var dynamicReceivers = mutableListOf<android.content.BroadcastReceiver>()
    private var dynamicStarted = false
    private var appHandler: android.os.Handler? = null
    private var appRunnable: Runnable? = null
    private var lastAppPackage: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        startListening()
        startBatteryListening()
        startAppPolling()
        startDynamicReceivers()
        return START_STICKY
    }

    private fun startBatteryListening() {
        try {
            if (batteryReceiver != null) return
            batteryReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(ctx: android.content.Context?, intent: Intent?) {
                    if (intent == null) return
                    val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
                    if (level < 0 || scale <= 0) return
                    val pct = (level * 100) / scale
                    val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                    val isFull = pct >= 100 || status == android.os.BatteryManager.BATTERY_STATUS_FULL
                    val fullFlows = Store.load(this@LocationTriggerService).filter {
                        it.kind == "automation" && it.enabled && it.trigger == "battery_full"
                    }
                    for (flow in fullFlows) {
                        val key = "battery_full_state_" + flow.id
                        val prevRaw = getSharedPreferences("flowlab", Context.MODE_PRIVATE).getString(key, null)
                        val prevFull = prevRaw?.toBoolean()
                        if (prevFull == null) {
                            getSharedPreferences("flowlab", Context.MODE_PRIVATE).edit().putString(key, isFull.toString()).apply()
                            continue
                        }
                        if (prevFull == isFull) continue
                        getSharedPreferences("flowlab", Context.MODE_PRIVATE).edit().putString(key, isFull.toString()).apply()
                        if (isFull) Thread { Engine.run(this@LocationTriggerService, flow) { } }.start()
                    }
                    val flows = Store.load(this@LocationTriggerService).filter {
                        it.kind == "automation" && it.enabled && it.trigger == "battery_below"
                    }
                    for (flow in flows) {
                        val key = "battery_state_" + flow.id
                        val prevRaw = getSharedPreferences("flowlab", Context.MODE_PRIVATE).getString(key, null)
                        val prevBelow = prevRaw?.toBoolean()
                        val below = pct < flow.triggerThreshold
                        if (prevBelow == null) {
                            getSharedPreferences("flowlab", Context.MODE_PRIVATE).edit().putString(key, below.toString()).apply()
                            continue
                        }
                        if (prevBelow == below) continue
                        getSharedPreferences("flowlab", Context.MODE_PRIVATE).edit().putString(key, below.toString()).apply()
                        if (below) Thread { Engine.run(this@LocationTriggerService, flow) { } }.start()
                    }
                }
            }
            registerReceiver(batteryReceiver, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (e: Exception) {
        }
    }

    private fun startDynamicReceivers() {
        /* onStartCommand can run more than once; registering the same
           receivers again would fire each matching flow twice. */
        if (dynamicStarted) return
        dynamicStarted = true
        try {
            val screen = ScreenReceiver()
            registerReceiver(screen, android.content.IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
            })
            dynamicReceivers.add(screen)
        } catch (e: Exception) { }
        try {
            val wifi = WifiReceiver()
            registerReceiver(wifi, android.content.IntentFilter(android.net.wifi.WifiManager.NETWORK_STATE_CHANGED_ACTION))
            dynamicReceivers.add(wifi)
        } catch (e: Exception) { }
        try {
            val bt = BtReceiver()
            registerReceiver(bt, android.content.IntentFilter(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED))
            dynamicReceivers.add(bt)
        } catch (e: Exception) { }
        try {
            val headphones = HeadphonesReceiver()
            registerReceiver(headphones, android.content.IntentFilter().apply {
                addAction(Intent.ACTION_HEADSET_PLUG)
                addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
            })
            dynamicReceivers.add(headphones)
        } catch (e: Exception) { }
        try {
            val unlock = UnlockReceiver()
            registerReceiver(unlock, android.content.IntentFilter(Intent.ACTION_USER_PRESENT))
            dynamicReceivers.add(unlock)
        } catch (e: Exception) { }
    }

    private fun usageAccessGranted(): Boolean {
        return try {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName) == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) { false }
    }

    private fun startAppPolling() {
        try {
            if (appHandler != null) return
            appHandler = android.os.Handler(android.os.Looper.getMainLooper())
            appRunnable = object : Runnable {
                override fun run() {
                    try {
                        if (usageAccessGranted()) {
                            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
                            val now = System.currentTimeMillis()
                            val stats = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, now - 60000L, now)
                            val top = stats?.maxByOrNull { it.lastTimeUsed }
                            val pkg = top?.packageName
                            if (pkg != null && pkg != lastAppPackage) {
                                lastAppPackage = pkg
                                val flows = Store.load(this@LocationTriggerService).filter {
                                    it.kind == "automation" && it.enabled && it.trigger == "app_opened" && it.triggerParam == pkg
                                }
                                for (f in flows) Thread { Engine.run(this@LocationTriggerService, f) { } }.start()
                            }
                        }
                    } catch (e: Exception) { }
                    appHandler?.postDelayed(this, 20000L)
                }
            }
            appHandler?.post(appRunnable!!)
        } catch (e: Exception) { }
    }

    private fun startForegroundNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("flow_location", "Flow location", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            android.app.Notification.Builder(this, "flow_location")
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
        }
        builder.setContentTitle("Flow location automations")
            .setContentText("Checking location for your location automations")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
        startForeground(2, builder.build())
    }

    private fun startListening() {
        try {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30000L, 50f, this)
            lm?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000L, 50f, this)
            // Check last known right away, as a state-only first check.
            val last = Engine.getLastLocation(this)
            if (last != null) checkLocation(last.first, last.second)
        } catch (e: Exception) {
        }
    }

    override fun onLocationChanged(location: Location) {
        checkLocation(location.latitude, location.longitude)
    }

    private fun checkLocation(lat: Double, lng: Double) {
        val flows = Store.load(this).filter {
            it.kind == "automation" && it.enabled &&
                (it.trigger == "location_enter" || it.trigger == "location_exit")
        }
        for (flow in flows) {
            if (flow.locLat == 0.0 && flow.locLng == 0.0) continue
            val results = FloatArray(1)
            Location.distanceBetween(lat, lng, flow.locLat, flow.locLng, results)
            val inside = results[0] <= flow.locRadius
            val previous = LocationState.get(this, flow.id)
            if (previous == null) {
                LocationState.set(this, flow.id, inside)
                continue
            }
            if (previous == inside) continue
            LocationState.set(this, flow.id, inside)
            val shouldFire = (flow.trigger == "location_enter" && inside) ||
                (flow.trigger == "location_exit" && !inside)
            if (shouldFire) {
                Thread { Engine.run(this, flow) { } }.start()
            }
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    override fun onDestroy() {
        try { lm?.removeUpdates(this) } catch (e: Exception) {}
        try { if (batteryReceiver != null) unregisterReceiver(batteryReceiver) } catch (e: Exception) {}
        try { appRunnable?.let { appHandler?.removeCallbacks(it) } } catch (e: Exception) {}
        for (r in dynamicReceivers) { try { unregisterReceiver(r) } catch (e: Exception) { } }
        dynamicReceivers.clear()
        dynamicStarted = false
        super.onDestroy()
    }
}
