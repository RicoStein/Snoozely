package com.tigonic.snoozely.service

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import kotlinx.coroutines.*

/**
 * Kurzlebiger Service, der (wo erlaubt) Bluetooth ausschaltet.
 * Hinweis: Ab neueren Android-Versionen ist das für Drittanbieter-Apps nicht mehr zulässig.
 * Wir begrenzen das Feature deshalb auf API <= 30 (Android 11).
 */
class BluetoothToggleService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            try {
                if (!isSupported()) {
                    Log.w(TAG, "Bluetooth disable not supported on this API level")
                    stopSelf()
                    return@launch
                }

                val mgr = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val adapter: BluetoothAdapter? = mgr.adapter
                if (adapter == null) {
                    Log.w(TAG, "BluetoothAdapter is null")
                    stopSelf()
                    return@launch
                }

                // Ausschalten (sofern eingeschaltet)
                if (adapter.isEnabled) {
                    // Achtung: funktioniert nur bis API 30 für Drittanbieter-Apps
                    @Suppress("MissingPermission")
                    adapter.disable()
                    Log.d(TAG, "Bluetooth disable() called")
                } else {
                    Log.d(TAG, "Bluetooth already disabled")
                }

                // Optional: Zustand merken (falls du das später nutzen willst)
                SettingsPreferenceHelper.setBluetoothDisableRequested(applicationContext, true)
            } catch (t: Throwable) {
                Log.e(TAG, "Error disabling Bluetooth", t)
            } finally {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun isSupported(): Boolean {
        // Realistisch für Drittanbieter-Apps: nur bis Android 11 (API 30).
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.R
    }

    companion object {
        private const val TAG = "BluetoothToggleService"

        fun start(context: Context) {
            context.startService(Intent(context, BluetoothToggleService::class.java))
        }
    }
}
