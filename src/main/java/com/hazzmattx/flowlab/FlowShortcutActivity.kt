package com.hazzmattx.flowlab

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/* Target of a pinned home-screen shortcut ("Add home-screen icon" in a
   flow's card menu). Runs the flow and finishes immediately; it never
   shows a UI of its own. Distinct from the widget and the QS tile. */
class FlowShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val flowId = intent?.getStringExtra("flowId")
        val flow = flowId?.let { Store.byId(this, it) }
        if (flow == null) {
            Toast.makeText(this, "That flow no longer exists", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Running " + flow.name, Toast.LENGTH_SHORT).show()
            Thread { Engine.run(applicationContext, flow) { } }.start()
        }
        finish()
    }
}
