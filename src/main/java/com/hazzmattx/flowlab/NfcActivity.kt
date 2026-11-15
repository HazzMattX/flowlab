package com.hazzmattx.flowlab

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.Toast

/* NFC tag tapped: runs enabled automations with the nfc_tag trigger.
   A flow can bind to one tag by putting that tag's ID in its trigger
   field; a blank trigger field means any tag runs it. No tag writing,
   no tag registration UI: the tapped tag's ID is written to the Run
   log, so it can be copied into a flow's trigger field to bind it.
   Reached via the TAG_DISCOVERED intent-filter in the manifest, so the
   system may show a chooser if another app also handles NFC tags. */
class NfcActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        val tagId = tag?.id?.joinToString("") { "%02x".format(it) } ?: ""
        val flows = try {
            Store.load(this).filter { f ->
                f.kind == "automation" && f.enabled && f.trigger == "nfc_tag" &&
                    (f.triggerParam.isEmpty() || f.triggerParam.equals(tagId, ignoreCase = true))
            }
        } catch (e: Exception) { emptyList() }
        LogStore.add(this, "NFC tag tapped" + (if (tagId.isNotEmpty()) ", tag ID " + tagId else "") + ": " + flows.size + " matching automation(s)")
        if (flows.isEmpty()) {
            Toast.makeText(this, "NFC tag tapped, no Flow automation for this tag", Toast.LENGTH_SHORT).show()
        } else {
            for (f in flows) Thread { Engine.run(applicationContext, f) { } }.start()
        }
        finish()
    }
}
