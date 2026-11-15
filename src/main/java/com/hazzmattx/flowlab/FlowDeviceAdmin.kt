package com.hazzmattx.flowlab

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/* Device Admin receiver for the optional Device Owner mode.
   This class alone does NOT make Flow a Device Owner. That role is
   only granted by provisioning, e.g.:
     adb shell dpm set-device-owner com.hazzmattx.flowlab/.FlowDeviceAdmin
   Until that is done, Flow is a regular app and DO-gated steps log
   that Device Owner is required instead of silently failing. */
class FlowDeviceAdmin : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }
}
