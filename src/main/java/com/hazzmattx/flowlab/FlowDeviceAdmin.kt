package com.hazzmattx.flowlab

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/* Device Admin receiver for Device Owner mode. This class alone does not
   grant the role; provisioning does. Until then, DO-gated steps log
   that Device Owner is required instead of failing silently. */
class FlowDeviceAdmin : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }
}
