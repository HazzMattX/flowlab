package com.hazzmattx.flowlab

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.WifiManager

/* Runs matching automations in a background thread, using goAsync so
   a Wait or HTTP step does not get cut off when onReceive returns. */
abstract class FlowReceiver : BroadcastReceiver() {
    protected fun runMatching(ctx: Context, intent: Intent, trigger: String, flowId: String?) {
        val pending = goAsync()
        Thread {
            try {
                val flows = Store.load(ctx)
                val targets = if (flowId != null) {
                    flows.filter { it.id == flowId }
                } else {
                    flows.filter { it.kind == "automation" && it.enabled && it.trigger == trigger }
                }
                for (f in targets) {
                    Engine.run(ctx, f) { }
                    when (f.trigger) {
                        "time" -> Scheduler.scheduleTime(ctx, f)
                        "time_weekday" -> Scheduler.scheduleWeekday(ctx, f)
                        "sunrise" -> Scheduler.scheduleSun(ctx, f, true)
                        "sunset" -> Scheduler.scheduleSun(ctx, f, false)
                        "interval" -> Scheduler.scheduleInterval(ctx, f)
                    }
                }
            } catch (e: Exception) {
            } finally {
                pending?.finish()
            }
        }.start()
    }
}

class TimeReceiver : FlowReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        runMatching(ctx, intent, "time", intent.getStringExtra("flowId"))
    }
}

class PowerReceiver : FlowReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val trigger = when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> "charging_on"
            Intent.ACTION_POWER_DISCONNECTED -> "charging_off"
            else -> return
        }
        runMatching(ctx, intent, trigger, null)
    }
}

class BatteryReceiver : FlowReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BATTERY_LOW) {
            runMatching(ctx, intent, "battery_low", null)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Scheduler.scheduleAll(ctx)
        }
    }
}

// Notification-with-button tap: run the flow stored in the notification.
class FlowActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val flowId = intent.getStringExtra("flowId") ?: return
        val pending = goAsync()
        Thread {
            try {
                val flow = Store.byId(ctx, flowId)
                if (flow != null) Engine.run(ctx, flow) { }
            } catch (e: Exception) {
            } finally {
                pending?.finish()
            }
        }.start()
    }
}

class WifiReceiver : FlowReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != WifiManager.NETWORK_STATE_CHANGED_ACTION) return
        val info = intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
        val connected = info?.isConnected == true
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val ssid = wm?.connectionInfo?.ssid?.trim('"') ?: ""
        val prefs = ctx.getSharedPreferences("flowlab", Context.MODE_PRIVATE)
        val prevConnected = prefs.getString("wifi_state_connected", null)?.toBoolean()
        val prevSsid = prefs.getString("wifi_state_ssid", null)
        prefs.edit().putString("wifi_state_connected", connected.toString()).putString("wifi_state_ssid", ssid).apply()
        /* Only fire on a transition, not on every network-state broadcast.
           SSID trigger: fire when the SSID changes to the wanted one, or on a
           fresh connect to that SSID (reconnecting to the same network). */
        val fireSsid = connected && ssid.isNotEmpty() &&
            (prevSsid != ssid || (prevConnected != null && prevConnected != connected))
        val trigger = if (prevConnected == null || prevConnected == connected) {
            null // no connect/disconnect transition
        } else if (connected) {
            "wifi_connected"
        } else {
            "wifi_disconnected"
        }
        if (trigger == null && !fireSsid) return
        /* One goAsync() per onReceive: goAsync() can only be called once —
           a second call returns null, and finish() on that null crashed on every Wi-Fi connect with an SSID.
           Run both the connect/disconnect flows and the SSID flows in the
           single thread owned by that one PendingResult. */
        val pending = goAsync()
        Thread {
            try {
                val flows = Store.load(ctx)
                if (trigger != null) {
                    for (f in flows.filter { it.kind == "automation" && it.enabled && it.trigger == trigger }) {
                        Engine.run(ctx, f) { }
                    }
                }
                if (fireSsid) {
                    for (f in flows.filter { it.kind == "automation" && it.enabled && it.trigger == "wifi_ssid" && it.triggerParam == ssid }) {
                        Engine.run(ctx, f) { }
                    }
                }
            } catch (e: Exception) {
            } finally { pending?.finish() }
        }.start()
    }
}

class BtReceiver : FlowReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
        val name = device.name ?: ""
        val addr = device.address ?: ""
        val pending = goAsync()
        Thread {
            try {
                val flows = Store.load(ctx).filter {
                    it.kind == "automation" && it.enabled && it.trigger == "bt_device" &&
                        (it.triggerParam.equals(name, ignoreCase = true) || it.triggerParam.equals(addr, ignoreCase = true))
                }
                for (f in flows) Engine.run(ctx, f) { }
            } catch (e: Exception) {
            } finally { pending?.finish() }
        }.start()
    }
}

class ScreenReceiver : FlowReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val trigger = when (intent.action) {
            Intent.ACTION_SCREEN_ON -> "screen_on"
            Intent.ACTION_SCREEN_OFF -> "screen_off"
            else -> return
        }
        runMatching(ctx, intent, trigger, null)
    }
}

/* Device unlocked: ACTION_USER_PRESENT fires on unlock, distinct from
   screen on. Registered dynamically in LocationTriggerService. */
class UnlockReceiver : FlowReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return
        runMatching(ctx, intent, "unlocked", null)
    }
}

/* Headphones: wired via ACTION_HEADSET_PLUG, Bluetooth audio devices
   via ACL connect/disconnect. Only fires on a change of connected
   state, like WifiReceiver. One goAsync() per onReceive, all work in
   the single thread from that call. */
class HeadphonesReceiver : FlowReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val connected: Boolean? = when (intent.action) {
            Intent.ACTION_HEADSET_PLUG -> intent.getIntExtra("state", 0) == 1
            BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                val isAudio = try {
                    device.bluetoothClass?.majorDeviceClass == android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO
                } catch (e: Exception) { false }
                if (!isAudio) return
                intent.action == BluetoothDevice.ACTION_ACL_CONNECTED
            }
            else -> return
        }
        val isConnected = connected ?: return
        val prefs = ctx.getSharedPreferences("flowlab", Context.MODE_PRIVATE)
        val prev = prefs.getString("headphones_connected", null)?.toBoolean()
        prefs.edit().putString("headphones_connected", isConnected.toString()).apply()
        if (prev == null || prev == isConnected) return
        val trigger = if (isConnected) "headphones_connected" else "headphones_disconnected"
        val pending = goAsync()
        Thread {
            try {
                val flows = Store.load(ctx).filter { it.kind == "automation" && it.enabled && it.trigger == trigger }
                for (f in flows) Engine.run(ctx, f) { }
            } catch (e: Exception) {
            } finally { pending?.finish() }
        }.start()
    }
}
