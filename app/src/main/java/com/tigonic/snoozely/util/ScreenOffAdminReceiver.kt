package com.tigonic.snoozely.util

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * Device-Admin-Receiver:
 * - Wird nur benötigt, wenn Features Admin-Rechte erfordern (z. B. Screen sperren).
 * - Zeigt kurze Status-Toast und loggt das Ereignis.
 */
class ScreenOffAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "ScreenOffAdminReceiver"
        private const val MSG_ENABLED = "Device Admin aktiviert"
        private const val MSG_DISABLED = "Device Admin deaktiviert"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        // Anwendungskontext nutzen (kein Leck)
        Toast.makeText(context.applicationContext, MSG_ENABLED, Toast.LENGTH_SHORT).show()
        Log.i(TAG, "onEnabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context.applicationContext, MSG_DISABLED, Toast.LENGTH_SHORT).show()
        Log.i(TAG, "onDisabled")
    }
}
