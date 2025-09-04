package com.tigonic.snoozely.service

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Kurzlebiger Service zum Schalten von Bluetooth.
 * - ACTION_DISABLE_BT: Schaltet Bluetooth aus (nur < Android 13 erlaubt)
 * - ACTION_TOGGLE_BT : Wechselt den Zustand (nur < Android 13 erlaubt)
 *
 * Der Service beendet sich nach der Aktion selbst (START_NOT_STICKY).
 */
class BluetoothService : Service() {

    companion object {
        private const val TAG = "BluetoothService"
        const val ACTION_DISABLE_BT = "com.tigonic.snoozely.action.DISABLE_BT"
        const val ACTION_TOGGLE_BT  = "com.tigonic.snoozely.action.TOGGLE_BT"
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        scope.launch {
            try {
                when (action) {
                    ACTION_DISABLE_BT -> applyBluetooth(enabled = false)
                    ACTION_TOGGLE_BT  -> toggleBluetooth()
                    else -> Log.d(TAG, "No-op: unknown or null action=$action")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Bluetooth action failed", t)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun applyBluetooth(enabled: Boolean) {
        // Ab Android 13 (TIRAMISU) ist das programmatic enable/disable nicht mehr erlaubt
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, "Skipping: programmatic BT toggle not allowed on Android 13+")
            return
        }
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: run {
            Log.d(TAG, "No BluetoothAdapter available")
            return
        }
        val ok = if (enabled) adapter.enable() else adapter.disable()
        Log.d(TAG, "applyBluetooth(enabled=$enabled) -> $ok")
    }

    private fun toggleBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, "Skipping: programmatic BT toggle not allowed on Android 13+")
            return
        }
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: run {
            Log.d(TAG, "No BluetoothAdapter available")
            return
        }
        val target = !adapter.isEnabled
        val ok = if (target) adapter.enable() else adapter.disable()
        Log.d(TAG, "toggleBluetooth -> target=$target result=$ok")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
