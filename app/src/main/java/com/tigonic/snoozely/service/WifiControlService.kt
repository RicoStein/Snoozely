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
 * Auf neueren Versionen macht der Service nichts.
 */
class WifiControlService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            try {
                val ctx = applicationContext
                val requested = SettingsPreferenceHelper.getWifiDisableRequested(ctx).first()

                if (!requested) {
                    Log.d(TAG, "Wi-Fi skip: user setting disabled")
                    stopSelf(); return@launch
                }

                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                    Log.d(TAG, "Wi-Fi skip: API ${Build.VERSION.SDK_INT} >= 29 not supported")
                    stopSelf(); return@launch
                }

                val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                if (wifi.isWifiEnabled) {
                    Log.d(TAG, "Disabling Wi-Fi via WifiManager.setWifiEnabled(false)")
                    @Suppress("DEPRECATION")
                    wifi.isWifiEnabled = false
                } else {
                    Log.d(TAG, "Wi-Fi already disabled")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error disabling Wi-Fi", t)
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

    companion object {
        private const val TAG = "WifiControlService"
        /** Kurzlebig – from any context. */
        fun start(context: Context) {
            context.startService(Intent(context, WifiControlService::class.java))
        }
    }
}
