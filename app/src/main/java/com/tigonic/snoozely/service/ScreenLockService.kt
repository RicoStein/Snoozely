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

/**
 * Sperrt das Gerät per DevicePolicyManager.lockNow(), wenn der Nutzer es in den Einstellungen
 * aktiviert hat und der Admin-Receiver aktiv ist. Kurzer, selbst-beendender Service.
 */
class ScreenLockService : Service() {

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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Hinweis: Idealerweise von einem Foreground-Service gestartet (z. B. TimerEngineService),
        // um Restriktionen zu vermeiden.
        serviceScope.launch {
            try {
                val enabled = SettingsPreferenceHelper.getScreenOff(applicationContext).first()
                if (!enabled) {
                    Log.d(TAG, "Screen lock skipped: setting is disabled.")
                    stopSelf(startId)
                    return@launch
                }

                val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                if (dpm == null) {
                    Log.w(TAG, "Screen lock skipped: DevicePolicyManager unavailable.")
                    stopSelf(startId)
                    return@launch
                }

                val admin = ComponentName(applicationContext, ScreenOffAdminReceiver::class.java)
                if (dpm.isAdminActive(admin)) {
                    Log.d(TAG, "Locking device now via DevicePolicyManager.lockNow()")
                    runCatching { dpm.lockNow() }
                        .onFailure { Log.e(TAG, "lockNow failed", it) }
                } else {
                    Log.w(TAG, "Screen lock skipped: device admin not active.")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error while trying to lock screen", t)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
