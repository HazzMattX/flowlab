package com.hazzmattx.flowlab

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import android.widget.Toast

/* Home screen widget: one widget runs one macro (or automation) when tapped.
   Adding a widget opens WidgetConfigActivity to pick which flow it runs.
   The mapping appWidgetId -> flowId lives in SharedPreferences, so it
   survives a restart, and deleting the widget clears its mapping. */
class FlowWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateWidget(ctx, manager, id)
    }

    override fun onDeleted(ctx: Context, ids: IntArray) {
        val prefs = ctx.getSharedPreferences("flowlab", Context.MODE_PRIVATE)
        val edit = prefs.edit()
        for (id in ids) edit.remove("widget_flow_" + id)
        edit.apply()
    }

    companion object {
        fun updateWidget(ctx: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val prefs = ctx.getSharedPreferences("flowlab", Context.MODE_PRIVATE)
            val flowId = prefs.getString("widget_flow_" + appWidgetId, null)
            val flow = flowId?.let { Store.byId(ctx, it) }
            val views = RemoteViews(ctx.packageName, R.layout.flow_widget)
            if (flow != null) {
                views.setTextViewText(R.id.widget_button, flow.name)
                val intent = Intent(ctx, WidgetRunReceiver::class.java).apply {
                    action = "com.hazzmattx.flowlab.RUN_WIDGET"
                    putExtra("flowId", flow.id)
                }
                val pi = PendingIntent.getBroadcast(ctx, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.widget_button, pi)
            } else {
                views.setTextViewText(R.id.widget_button, "Open Flow to pick a macro")
                val intent = Intent(ctx, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                val pi = PendingIntent.getActivity(ctx, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.widget_button, pi)
            }
            manager.updateAppWidget(appWidgetId, views)
        }
    }
}

class WidgetRunReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != "com.hazzmattx.flowlab.RUN_WIDGET") return
        val pending = goAsync()
        Thread {
            try {
                val flow = intent.getStringExtra("flowId")?.let { Store.byId(ctx.applicationContext, it) }
                if (flow != null) {
                    Engine.run(ctx.applicationContext, flow) { }
                }
            } catch (e: Exception) {
            } finally {
                pending?.finish()
            }
        }.start()
    }
}

class WidgetConfigActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        // Default to cancelled if the user backs out without picking.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        root.addView(TextView(this).apply { text = "Choose a flow for this widget"; textSize = 22f })
        root.addView(TextView(this).apply { text = "Tapping the widget on your home screen will run the flow you pick here."; textSize = 14f })
        val flows = Store.load(this)
        if (flows.isEmpty()) {
            root.addView(TextView(this).apply { text = "No flows yet. Create one in Flow first, then add the widget again." })
        } else {
            val pickBtn = Button(this).apply {
                text = "Choose a flow..."
                setOnClickListener {
                    SearchablePicker.show(this@WidgetConfigActivity, "Choose a flow for this widget", flows.map { it.name + " (" + it.kind + ")" }) { which ->
                        val flow = flows[which]
                        getSharedPreferences("flowlab", Context.MODE_PRIVATE).edit().putString("widget_flow_" + appWidgetId, flow.id).apply()
                        val manager = AppWidgetManager.getInstance(this@WidgetConfigActivity)
                        FlowWidgetProvider.updateWidget(this@WidgetConfigActivity, manager, appWidgetId)
                        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        setResult(RESULT_OK, result)
                        Toast.makeText(this@WidgetConfigActivity, "Widget set to " + flow.name, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
            root.addView(pickBtn)
        }
        setContentView(root)
    }
}
