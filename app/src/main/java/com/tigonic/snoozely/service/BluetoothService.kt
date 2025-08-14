package com.tigonic.snoozely.service

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Kombinierter Service für Bluetooth an/aus.
 * - Über ACTION_DISABLE_BT wird BT ausgeschaltet (sofern erlaubt)
 * - Über ACTION_TOGGLE_BT wird BT an/aus geschaltet
 */
class BluetoothService : Service() {

    companion object {
        const val ACTION_DISABLE_BT = "com.tigonic.snoozely.action.DISABLE_BT"
        const val ACTION_TOGGLE_BT  = "com.tigonic.snoozely.action.TOGGLE_BT"
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        scope.launch {
            when (action) {
                ACTION_DISABLE_BT -> setBluetooth(false)
                ACTION_TOGGLE_BT  -> toggleBluetooth()
            }
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun setBluetooth(enabled: Boolean) {
        // Nur vor Android 13 erlaubt
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
            if (enabled) adapter.enable() else adapter.disable()
        }
    }

    private fun toggleBluetooth() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
            if (adapter.isEnabled) adapter.disable() else adapter.enable()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
