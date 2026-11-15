package com.hazzmattx.flowlab

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/* Target of a pinned home-screen shortcut. Runs the flow and finishes
   at once; it never shows a UI of its own. */
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
