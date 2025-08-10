package com.tigonic.snoozely.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tigonic.snoozely.R
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import com.tigonic.snoozely.util.TimerPreferenceHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Reagiert auf Events der TimerEngine:
 * - ACTION_NOTIFY_UPDATE    → laufende Statusbar-Notification mit Fortschritt
 * - ACTION_NOTIFY_REMINDER  → Heads-up Reminder kurz vor Ablauf
 * - ACTION_EXTEND / ACTION_STOP (Buttons) → leitet an Engine weiter
 */
class TimerNotificationService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannelsIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Channels sicherstellen (auch wenn onCreate schon lief – doppelt schadet nicht)
        createChannelsIfNeeded()

        val action = intent?.action

        when (action) {
            // Fortschrittsanzeige – an "Benachrichtigung AN" gekoppelt
            TimerContracts.ACTION_NOTIFY_UPDATE -> {
                val notificationsEnabled = runBlocking {
                    SettingsPreferenceHelper.getNotificationEnabled(applicationContext).first()
                }
                val showProgress = notificationsEnabled && runBlocking {
                    SettingsPreferenceHelper.getShowProgressNotification(applicationContext).first()
                }
                if (!showProgress) {
                    val nm = getSystemService(NotificationManager::class.java)
                    runCatching { nm.cancel(NOTIFICATION_ID_RUNNING) }
                    stopSelf()
                    return START_NOT_STICKY
                }

                var remainingMs = intent.getLongExtra(TimerContracts.EXTRA_REMAINING_MS, -1L)
                var totalMs     = intent.getLongExtra(TimerContracts.EXTRA_TOTAL_MS, -1L)

                if (remainingMs < 0 || totalMs < 0) {
                    runBlocking {
                        val minutes = SettingsPreferenceHelper
                            .getProgressExtendMinutes(applicationContext).first() // nicht zwingend, nur Beispiel
                        val m = TimerPreferenceHelper.getTimer(applicationContext).first()
                        val s = TimerPreferenceHelper.getTimerStartTime(applicationContext).first()
                        if (m > 0 && s > 0L) {
                            totalMs = m * 60_000L
                            remainingMs = (totalMs - (System.currentTimeMillis() - s)).coerceAtLeast(0)
                        }
                    }
                }

                if (totalMs > 0) showRunningNotification(remainingMs.coerceAtLeast(0), totalMs)
            }

            // Reminder – an "Benachrichtigung AN" UND "Reminder vor Ablauf AN" gekoppelt
            TimerContracts.ACTION_NOTIFY_REMINDER -> {
                val notificationsEnabled = runBlocking {
                    SettingsPreferenceHelper.getNotificationEnabled(applicationContext).first()
                }
                val allowReminder = notificationsEnabled && runBlocking {
                    SettingsPreferenceHelper.getShowReminderPopup(applicationContext).first()
                }
                if (!allowReminder) {
                    val nm = getSystemService(NotificationManager::class.java)
                    runCatching { nm.cancel(NOTIFICATION_ID_REMINDER) }
                    stopSelf()
                    return START_NOT_STICKY
                }

                // Reminder-Minuten aus Extra oder Settings
                val fromExtra = intent?.getIntExtra("reminderMinutes", -1) ?: -1
                val minutes = if (fromExtra > 0) fromExtra else runBlocking {
                    SettingsPreferenceHelper.getReminderMinutes(applicationContext).first()
                }

                showHeadsUpReminder(minutes)
            }

            TimerContracts.ACTION_EXTEND -> {
                startService(Intent(this, TimerEngineService::class.java).setAction(TimerContracts.ACTION_EXTEND))
            }

            TimerContracts.ACTION_STOP -> {
                startService(Intent(this, TimerEngineService::class.java).setAction(TimerContracts.ACTION_STOP))
                val nm = getSystemService(NotificationManager::class.java)
                runCatching { nm.cancel(NOTIFICATION_ID_RUNNING) }
                runCatching { nm.cancel(NOTIFICATION_ID_REMINDER) }
                stopSelf()
            }

            else -> Unit
        }

        return START_NOT_STICKY
    }




    // --- Laufende Timer-Notification (Statusleiste) ---

    private fun showRunningNotification(remainingMs: Long, totalMs: Long) {
        android.util.Log.d("TimerNotif", "showRunningNotification: remaining=$remainingMs total=$totalMs")
        val minutes = (remainingMs / 1000) / 60
        val seconds = (remainingMs / 1000) % 60
        val timeText = String.format("%02d:%02d", minutes, seconds)
        val progress = if (totalMs > 0)
            (((totalMs - remainingMs) * 100 / totalMs).toInt()).coerceIn(0, 100)
        else 0

        val extendStep = runBlocking {
            SettingsPreferenceHelper.getProgressExtendMinutes(this@TimerNotificationService).first()
        }

        val notif = NotificationCompat.Builder(this, TimerContracts.CHANNEL_RUNNING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.notification_timer_running))
            .setContentText(getString(R.string.notification_remaining_time, timeText))
            .setOngoing(true)
            .setProgress(100, progress, false)
            .setContentIntent(pendingOpenApp())           // <— NEU
            .addAction(
                android.R.drawable.ic_input_add,
                getString(R.string.timer_plus_x, extendStep),
                pendingExtend()                           // <— NEU (statt inline)
            )
            .addAction(
                android.R.drawable.ic_lock_idle_alarm,
                getString(R.string.timer_stop),
                pendingStop()                             // <— NEU (statt inline)
            )
            .build()

        notify(NOTIFICATION_ID_RUNNING, notif)
    }


    // --- Heads-up Reminder kurz vor Ablauf ---

    private fun showHeadsUpReminder(remainingMin: Int) {
        val extendMinutes = runBlocking {
            SettingsPreferenceHelper.getProgressExtendMinutes(this@TimerNotificationService).first()
        }

        val body = getString(R.string.reminder_popup_message)

        val notif = NotificationCompat.Builder(this, TimerContracts.CHANNEL_REMINDER)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.reminder_popup_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingOpenApp())           // <— NEU
            .addAction(
                android.R.drawable.ic_input_add,
                getString(R.string.timer_plus_x, extendMinutes),
                pendingExtend()                           // <— NEU (statt inline)
            )
            .addAction(
                android.R.drawable.ic_lock_idle_alarm,
                getString(R.string.timer_stop),
                pendingStop()                             // <— NEU (statt inline)
            )
            .setTimeoutAfter(10_000)
            .build()

        notify(NOTIFICATION_ID_REMINDER, notif)
    }


    // --- Channels / Utils ---

    private fun createChannelsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)

        fun upsert(id: String, name: String, imp: Int, apply: NotificationChannel.() -> Unit = {}) {
            val existing = nm.getNotificationChannel(id)
            if (existing == null) {
                nm.createNotificationChannel(NotificationChannel(id, name, imp).apply(apply))
                return
            }
            val blocked = existing.importance == NotificationManager.IMPORTANCE_NONE
            val tooLow = existing.importance < imp &&
                    existing.importance != NotificationManager.IMPORTANCE_UNSPECIFIED
            val renamed = existing.name?.toString() != name
            if (blocked || tooLow || renamed) {
                runCatching { nm.deleteNotificationChannel(id) }
                nm.createNotificationChannel(NotificationChannel(id, name, imp).apply(apply))
            }
        }

        upsert(
            TimerContracts.CHANNEL_RUNNING,
            "Timer läuft",
            NotificationManager.IMPORTANCE_LOW
        ) {
            description = "Fortschritt und verbleibende Zeit in der Statusleiste"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }

        upsert(
            TimerContracts.CHANNEL_REMINDER,
            "Timer-Reminder",
            NotificationManager.IMPORTANCE_HIGH
        ) {
            description = "Heads-up Hinweis kurz vor Ablauf"
            enableVibration(true)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
    }



    private fun notify(id: Int, notification: Notification) {
        android.util.Log.d("TimerNotif", "notify: id=$id")
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(id, notification)
    }

    private fun flagImmutable(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    companion object {
        private const val REQ_EXTEND = 2002
        private const val REQ_STOP = 2003

        const val NOTIFICATION_ID_RUNNING = 42
        const val NOTIFICATION_ID_REMINDER = 43
    }

    private fun pendingStop(): PendingIntent {
        val i = Intent(this, TimerEngineService::class.java).setAction(TimerContracts.ACTION_STOP)
        val flags = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0) or
                PendingIntent.FLAG_CANCEL_CURRENT

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            PendingIntent.getForegroundService(this, 2003, i, flags)
        else
            PendingIntent.getService(this, 2003, i, flags)
    }

    private fun pendingExtend(): PendingIntent {
        val i = Intent(this, TimerEngineService::class.java).setAction(TimerContracts.ACTION_EXTEND)
        val flags = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0) or
                PendingIntent.FLAG_CANCEL_CURRENT

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            PendingIntent.getForegroundService(this, 2002, i, flags)
        else
            PendingIntent.getService(this, 2002, i, flags)
    }

    private fun pendingOpenApp(): PendingIntent {
        val i = Intent(this, com.tigonic.snoozely.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val flags = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0) or
                PendingIntent.FLAG_CANCEL_CURRENT

        return PendingIntent.getActivity(this, /*REQ*/ 1001, i, flags)
    }

}
