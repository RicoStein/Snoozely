package com.tigonic.snoozely.service

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
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
        const val ACTION_TOGGLE_BT = "com.tigonic.snoozely.action.TOGGLE_BT"
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        scope.launch {
            try {
                when (action) {
                    ACTION_DISABLE_BT -> handleDisable()
                    ACTION_TOGGLE_BT -> handleToggle()
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

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun getAdapter(): BluetoothAdapter? {
        val bm = getSystemService(BluetoothManager::class.java)
        return bm?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    }

    private fun hasBtConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 31) {
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun handleDisable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Ab Android 13 ist das programmatische Umschalten nicht mehr erlaubt
            Log.w(TAG, "Programmatic Bluetooth disable is not allowed on Android 13+")
            return
        }
        val adapter = getAdapter()
        if (adapter == null) {
            Log.w(TAG, "No BluetoothAdapter available")
            return
        }
        if (!hasBtConnectPermission()) {
            Log.w(TAG, "Missing BLUETOOTH_CONNECT permission, skipping disable()")
            return
        }
        runCatching {
            @SuppressLint("MissingPermission")
            val ok = adapter.disable()
            Log.d(TAG, "Bluetooth disable() -> $ok")
        }.onFailure {
            Log.e(TAG, "Bluetooth disable() failed", it)
        }
    }

    private fun handleToggle() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Log.w(TAG, "Programmatic Bluetooth toggle is not allowed on Android 13+")
            return
        }
        val adapter = getAdapter()
        if (adapter == null) {
            Log.w(TAG, "No BluetoothAdapter available")
            return
        }
        if (!hasBtConnectPermission()) {
            Log.w(TAG, "Missing BLUETOOTH_CONNECT permission, skipping toggle()")
            return
        }
        runCatching {
            @SuppressLint("MissingPermission")
            val enabled = adapter.isEnabled
            @SuppressLint("MissingPermission")
            val ok = if (enabled) adapter.disable() else adapter.enable()
            Log.d(TAG, "Bluetooth toggle: was=$enabled -> ok=$ok")
        }.onFailure {
            Log.e(TAG, "Bluetooth toggle failed", it)
        }
    }
}
