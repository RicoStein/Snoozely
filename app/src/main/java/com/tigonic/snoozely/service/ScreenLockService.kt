package com.tigonic.snoozely.service

import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.tigonic.snoozely.util.ScreenOffAdminReceiver
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ScreenLockService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Hinweis: Idealerweise wird dieser Service von einem Foreground-Service
        // (z. B. TimerEngineService) gestartet, damit es keine Background-Service
        // Restriktionen auf neueren Android-Versionen gibt.
        serviceScope.launch {
            try {
                val ctx = applicationContext
                val enabled = SettingsPreferenceHelper.getScreenOff(ctx).first()
                if (!enabled) {
                    Log.d(TAG, "Screen lock skipped: setting is disabled.")
                    stopSelf()
                    return@launch
                }

                val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val admin = ComponentName(ctx, ScreenOffAdminReceiver::class.java)

                if (dpm.isAdminActive(admin)) {
                    Log.d(TAG, "Locking device now via DevicePolicyManager.lockNow()")
                    dpm.lockNow()
                } else {
                    Log.w(TAG, "Screen lock skipped: device admin not active.")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error while trying to lock screen", t)
            } finally {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ScreenLockService"

        /**
         * Startet den Service „sicher“. Am besten aus einem bereits laufenden
         * Foreground-Service heraus aufrufen (z. B. beim Timer-Ende).
         */
        fun start(context: Context) {
            // absichtlich startService(), da dieser Service sehr kurz lebt
            context.startService(Intent(context, ScreenLockService::class.java))
        }
    }
}
