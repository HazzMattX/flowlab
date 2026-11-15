package com.hazzmattx.flowlab

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

/* Quick Settings tile: tapping runs the flow chosen in Flow's top menu
   ("Choose Quick Settings tile flow", stored as qs_tile_flow). If no
   flow is chosen, tapping opens Flow instead. */
class FlowTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val flowId = getSharedPreferences("flowlab", MODE_PRIVATE).getString("qs_tile_flow", null)
        val flow = flowId?.let { Store.byId(this, it) }
        tile.label = flow?.name ?: "Flow"
        tile.state = if (flow != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val flowId = getSharedPreferences("flowlab", MODE_PRIVATE).getString("qs_tile_flow", null)
        val flow = flowId?.let { Store.byId(this, it) }
        if (flow == null) {
            val intent = Intent(this, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            startActivityAndCollapse(intent)
            return
        }
        Toast.makeText(this, "Running " + flow.name, Toast.LENGTH_SHORT).show()
        Thread { Engine.run(applicationContext, flow) { } }.start()
    }
}
