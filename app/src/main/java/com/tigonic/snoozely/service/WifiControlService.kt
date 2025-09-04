package com.tigonic.snoozely.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Schaltet WLAN am Timer-Ende aus – nur auf Android <= 28 (P) möglich.
 * Auf neueren Versionen beendet sich der Service ohne Aktion.
 */
class WifiControlService : Service() {

    companion object {
        private const val TAG = "WifiControlService"

        /** Kurzlebig – from any context. */
        fun start(context: Context) {
            context.startService(Intent(context, WifiControlService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            try {
                val requested = SettingsPreferenceHelper.getWifiDisableRequested(applicationContext).first()
                if (!requested) {
                    Log.d(TAG, "Wi-Fi skip: user setting disabled")
                    stopSelf(startId)
                    return@launch
                }

                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                    Log.d(TAG, "Wi-Fi skip: API ${Build.VERSION.SDK_INT} >= 29 not supported")
                    stopSelf(startId)
                    return@launch
                }

                val wifi = getSystemService(Context.WIFI_SERVICE) as? WifiManager
                if (wifi == null) {
                    Log.w(TAG, "Wi-Fi manager not available")
                    stopSelf(startId)
                    return@launch
                }

                if (wifi.isWifiEnabled) {
                    Log.d(TAG, "Disabling Wi-Fi via WifiManager.setWifiEnabled(false)")
                    @Suppress("DEPRECATION")
                    runCatching { wifi.isWifiEnabled = false }
                        .onFailure { Log.e(TAG, "Error disabling Wi-Fi", it) }
                } else {
                    Log.d(TAG, "Wi-Fi already disabled")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error disabling Wi-Fi", t)
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
}
