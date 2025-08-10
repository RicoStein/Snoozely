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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimerEngineService : Service() {

    companion object {
        private const val NOTIF_ID_RUNNING = 42
        private const val NOTIF_ID_REMINDER = 43
        private const val REQ_EXTEND = 2002
        private const val REQ_STOP = 2003

        @Volatile var isForeground: Boolean = false
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var tickerJob: Job? = null

    // Reminder nur einmal pro Startzeit
    @Volatile private var reminderSentForStartTime: Long = -1L

    // Gemerkter Benutzer-Startwert (verhindert Reset auf 1)
    @Volatile private var lastStartMinutesCache: Int? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Channels früh anlegen
        ensureChannel(TimerContracts.CHANNEL_RUNNING, getString(R.string.notification_channel_running), NotificationManager.IMPORTANCE_LOW)
        ensureChannel(TimerContracts.CHANNEL_REMINDER, getString(R.string.notification_channel_reminder), NotificationManager.IMPORTANCE_HIGH)

        // NICHT mehr blind in den Foreground wechseln – erst Settings prüfen
        val allowProgress = runCatching {
            val ctx = applicationContext
            val enabled = kotlinx.coroutines.runBlocking { com.tigonic.snoozely.util.SettingsPreferenceHelper.getNotificationEnabled(ctx).first() }
            val show    = kotlinx.coroutines.runBlocking { com.tigonic.snoozely.util.SettingsPreferenceHelper.getShowProgressNotification(ctx).first() }
            enabled && show
        }.getOrDefault(true)

        if (allowProgress) ensureForegroundOnce()
    }

    override fun onDestroy() {
        super.onDestroy()
        tickerJob?.cancel()
        tickerJob = null
        serviceScope.cancel()
        isForeground = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Foreground nur, wenn Progress-Notification erlaubt
        val allowProgress = runCatching {
            val ctx = applicationContext
            val enabled = kotlinx.coroutines.runBlocking { com.tigonic.snoozely.util.SettingsPreferenceHelper.getNotificationEnabled(ctx).first() }
            val show    = kotlinx.coroutines.runBlocking { com.tigonic.snoozely.util.SettingsPreferenceHelper.getShowProgressNotification(ctx).first() }
            enabled && show
        }.getOrDefault(true)

        if (allowProgress) ensureForegroundOnce() else stopForegroundCompat()

        when (intent?.action) {
            TimerContracts.ACTION_START -> {
                // interne Marker resetten
                reminderSentForStartTime = -1L

                val minutesFromIntent = intent.getIntExtra(TimerContracts.EXTRA_MINUTES, -1)

                serviceScope.launch {
                    val ctx = applicationContext
                    val minutes = if (minutesFromIntent > 0) minutesFromIntent
                    else runCatching { TimerPreferenceHelper.getTimer(ctx).first() }.getOrDefault(5)

                    // ATOMAR starten
                    TimerPreferenceHelper.startTimer(ctx, minutes)

                    // Ticker starten
                    startTickerIfNeeded()

                    // AudioFade laufen lassen
                    startServiceSafe(Intent(ctx, AudioFadeService::class.java))

                    // Erste Notification aktualisieren (nur wenn erlaubt, wird inside geprüft)
                    sendRunningUpdateNow()
                }

                return START_STICKY
            }

            TimerContracts.ACTION_EXTEND -> {
                serviceScope.launch { extendTimer() }
                return START_STICKY
            }

            TimerContracts.ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }

            TimerContracts.ACTION_NOTIFY_UPDATE,
            TimerContracts.ACTION_NOTIFY_REMINDER -> {
                return START_NOT_STICKY
            }

            else -> {
                serviceScope.launch {
                    val ctx = applicationContext
                    val running = TimerPreferenceHelper.getTimerRunning(ctx).first()
                    val start   = TimerPreferenceHelper.getTimerStartTime(ctx).first()
                    val minutes = TimerPreferenceHelper.getTimer(ctx).first()
                    if (running && start > 0L && minutes > 0) {
                        startTickerIfNeeded()
                        startServiceSafe(Intent(ctx, AudioFadeService::class.java))
                        sendRunningUpdateNow()
                    }
                }
                return START_STICKY
            }
        }
    }


    // ---- Foreground / Notification-Basis ----

    private fun ensureForegroundOnce() {
        if (isForeground) return

        val minimal = NotificationCompat.Builder(this, TimerContracts.CHANNEL_RUNNING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.notification_timer_running))
            .setContentText(getString(R.string.notification_remaining_time, "—:—"))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        try {
            startForeground(NOTIF_ID_RUNNING, minimal)
            isForeground = true
        } catch (_: Throwable) {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIF_ID_RUNNING, minimal)
            isForeground = true
        }
    }

    private fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION") stopForeground(true)
            }
        } catch (_: Throwable) { /* ignore */ }
        isForeground = false
    }

    private fun ensureChannel(id: String, name: String, importance: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)

        val existing = nm.getNotificationChannel(id)
        if (existing == null) {
            nm.createNotificationChannel(
                NotificationChannel(id, name, importance).apply {
                    description = name
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                }
            )
            return
        }

        // Falls der Channel blockiert ist oder zu niedrige Importance hat -> neu anlegen
        val blocked = existing.importance == NotificationManager.IMPORTANCE_NONE
        val tooLow = existing.importance < importance &&
                existing.importance != NotificationManager.IMPORTANCE_UNSPECIFIED
        val renamed = existing.name?.toString() != name

        if (blocked || tooLow || renamed) {
            runCatching { nm.deleteNotificationChannel(id) }
            nm.createNotificationChannel(
                NotificationChannel(id, name, importance).apply {
                    description = name
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                }
            )
        }
    }



    // ---- Ticker / Logik ----

    private fun startTickerIfNeeded() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            val nm = getSystemService(NotificationManager::class.java)

            while (isActive) {
                val ctx = applicationContext

                // Aktuellen Timer-State lesen
                val running   = TimerPreferenceHelper.getTimerRunning(ctx).first()
                val startTime = TimerPreferenceHelper.getTimerStartTime(ctx).first()
                val minutes   = TimerPreferenceHelper.getTimer(ctx).first()

                if (!running || startTime <= 0L || minutes < 1) {
                    // Falls Timer gestoppt wurde: Progress-Notif wegräumen und Foreground verlassen
                    runCatching { nm.cancel(NOTIF_ID_RUNNING) }
                    stopForegroundCompat()
                    delay(300)
                    continue
                }

                val totalMs   = minutes * 60_000L
                val now       = System.currentTimeMillis()
                val elapsed   = now - startTime
                val remaining = (totalMs - elapsed).coerceAtLeast(0)

                // Tick für UI
                sendBroadcast(Intent(TimerContracts.ACTION_TICK).apply {
                    putExtra(TimerContracts.EXTRA_TOTAL_MS, totalMs)
                    putExtra(TimerContracts.EXTRA_REMAINING_MS, remaining)
                })

                // Settings lesen
                val notificationsEnabled = runCatching {
                    SettingsPreferenceHelper.getNotificationEnabled(ctx).first()
                }.getOrDefault(true)

                val showProgress = notificationsEnabled && runCatching {
                    SettingsPreferenceHelper.getShowProgressNotification(ctx).first()
                }.getOrDefault(true)

                val showReminder = notificationsEnabled && runCatching {
                    SettingsPreferenceHelper.getShowReminderPopup(ctx).first()
                }.getOrDefault(true)

                // --- Laufende Notification + Foreground steuern ---
                if (showProgress) {
                    val channelOk = notificationsAllowedForChannel(TimerContracts.CHANNEL_RUNNING)
                    if (channelOk) {
                        if (!isForeground) ensureForegroundOnce()

                        val extendStep = runCatching {
                            SettingsPreferenceHelper.getProgressExtendMinutes(ctx).first()
                        }.getOrDefault(5).coerceAtLeast(1)

                        val notif = buildRunningNotification(remaining, totalMs, extendStep)
                        nm.notify(NOTIF_ID_RUNNING, notif)
                    } else {
                        runCatching { nm.cancel(NOTIF_ID_RUNNING) }
                        stopForegroundCompat()
                    }
                } else {
                    runCatching { nm.cancel(NOTIF_ID_RUNNING) }
                    stopForegroundCompat()
                }

                // --- Reminder EINMALIG kurz vor Ablauf ---
                if (showReminder) {
                    val reminderMin = runCatching {
                        SettingsPreferenceHelper.getReminderMinutes(ctx).first()
                    }.getOrDefault(2).coerceAtLeast(1)

                    val thresholdMs = reminderMin * 60_000L
                    // Nur einmal pro Startzeit senden
                    if (remaining <= thresholdMs && reminderSentForStartTime != startTime) {
                        reminderSentForStartTime = startTime
                        // Heads-up Reminder posten
                        startServiceSafe(
                            Intent(ctx, TimerNotificationService::class.java)
                                .setAction(TimerContracts.ACTION_NOTIFY_REMINDER)
                                .putExtra("reminderMinutes", reminderMin)
                                .putExtra(TimerContracts.EXTRA_REMAINING_MS, remaining)
                                .putExtra(TimerContracts.EXTRA_TOTAL_MS, totalMs)
                        )
                    }
                }

                // Timerende
                if (remaining <= 0L) {
                    onTimerFinished()
                    return@launch
                }

                delay(1000)
            }
        }
    }


    private fun buildRunningNotification(
        remainingMs: Long,
        totalMs: Long,
        extendStep: Int
    ): Notification {
        val minutes = (remainingMs / 1000) / 60
        val seconds = (remainingMs / 1000) % 60
        val timeText = String.format("%02d:%02d", minutes, seconds)
        val progress = if (totalMs > 0) (((totalMs - remainingMs) * 100 / totalMs).toInt()).coerceIn(0, 100) else 0

        val flags = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0) or
                PendingIntent.FLAG_CANCEL_CURRENT

        val extendPi =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                PendingIntent.getForegroundService(
                    this, REQ_EXTEND,
                    Intent(this, TimerEngineService::class.java).setAction(TimerContracts.ACTION_EXTEND),
                    flags
                )
            else
                PendingIntent.getService(
                    this, REQ_EXTEND,
                    Intent(this, TimerEngineService::class.java).setAction(TimerContracts.ACTION_EXTEND),
                    flags
                )

        val stopPi =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                PendingIntent.getForegroundService(
                    this, REQ_STOP,
                    Intent(this, TimerEngineService::class.java).setAction(TimerContracts.ACTION_STOP),
                    flags
                )
            else
                PendingIntent.getService(
                    this, REQ_STOP,
                    Intent(this, TimerEngineService::class.java).setAction(TimerContracts.ACTION_STOP),
                    flags
                )



        return NotificationCompat.Builder(this, TimerContracts.CHANNEL_RUNNING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(getString(R.string.notification_timer_running))
            .setContentText(getString(R.string.notification_remaining_time, timeText))
            .setProgress(100, progress, false)
            .addAction(android.R.drawable.ic_input_add, getString(R.string.timer_plus_x, extendStep), extendPi)
            .addAction(android.R.drawable.ic_lock_idle_alarm, getString(R.string.timer_stop), stopPi)
            .build()
    }

    private fun sendRunningUpdateNow() {
        serviceScope.launch {
            val ctx = applicationContext
            val running   = TimerPreferenceHelper.getTimerRunning(ctx).first()
            val startTime = TimerPreferenceHelper.getTimerStartTime(ctx).first()
            val minutes   = TimerPreferenceHelper.getTimer(ctx).first()
            if (!running || startTime <= 0L || minutes < 1) return@launch

            val notificationsEnabled = runCatching {
                SettingsPreferenceHelper.getNotificationEnabled(ctx).first()
            }.getOrDefault(true)
            val showProgress = runCatching {
                SettingsPreferenceHelper.getShowProgressNotification(ctx).first()
            }.getOrDefault(true)

            if (!(notificationsEnabled && showProgress)) {
                // sicherheitshalber löschen, falls gerade deaktiviert wurde
                try { getSystemService(NotificationManager::class.java).cancel(NOTIF_ID_RUNNING) } catch (_: Throwable) {}
                return@launch
            }

            val totalMs   = minutes * 60_000L
            val remaining = (totalMs - (System.currentTimeMillis() - startTime)).coerceAtLeast(0)

            startServiceSafe(
                Intent(ctx, TimerNotificationService::class.java)
                    .setAction(TimerContracts.ACTION_NOTIFY_UPDATE)
                    .putExtra(TimerContracts.EXTRA_REMAINING_MS, remaining)
                    .putExtra(TimerContracts.EXTRA_TOTAL_MS, totalMs)
            )
        }
    }


    private fun extendTimer() {
        val ctx = applicationContext
        serviceScope.launch {
            val running = TimerPreferenceHelper.getTimerRunning(ctx).first()
            val start   = TimerPreferenceHelper.getTimerStartTime(ctx).first()
            if (!running || start == 0L) return@launch

            val minutes   = TimerPreferenceHelper.getTimer(ctx).first()
            val extend    = runCatching { SettingsPreferenceHelper.getProgressExtendMinutes(ctx).first() }
                .getOrDefault(5).coerceAtLeast(1)
            val now       = System.currentTimeMillis()
            val elapsed   = now - start
            val totalMs   = minutes * 60_000L
            val remaining = (totalMs - elapsed).coerceAtLeast(0)

            val newTotal   = remaining + extend * 60_000L
            val newMinutes = (((newTotal) + elapsed) / 60_000L).toInt().coerceAtLeast(1)
            TimerPreferenceHelper.setTimer(ctx, newMinutes)
        }
    }

    private fun onTimerFinished() {
        serviceScope.launch {
            val ctx = applicationContext
            // optionales Feedback, aber nicht crashen wenn verboten
            startServiceSafe(Intent(ctx, HapticsService::class.java).setAction("END"))
            startServiceSafe(Intent(ctx, ScreenLockService::class.java))

            stopEverything()
        }
    }

    private fun stopEverything() {
        // Ticker beenden
        tickerJob?.cancel()
        tickerJob = null

        // DataStore-Stop SYNCHRON (vor stopSelf/Scope-Cancel!)
        val ctx = applicationContext
        val fallback = (lastStartMinutesCache
            ?: runCatching { kotlinx.coroutines.runBlocking { com.tigonic.snoozely.util.TimerPreferenceHelper.getTimer(ctx).first() } }
                .getOrDefault(5))
            .coerceAtLeast(1)

        // <— wichtig: blockierend, damit running=false garantiert gesetzt ist
        kotlinx.coroutines.runBlocking {
            com.tigonic.snoozely.util.TimerPreferenceHelper.stopTimer(ctx, fallback)
        }

        // Reminder-Guards zurücksetzen
        reminderSentForStartTime = -1L

        // AudioFade beenden (restauriert Lautstärke in onDestroy)
        try { stopService(Intent(applicationContext, AudioFadeService::class.java)) } catch (_: Throwable) {}

        // Notifications aufräumen + Foreground verlassen
        try {
            getSystemService(NotificationManager::class.java).apply {
                cancel(com.tigonic.snoozely.service.TimerNotificationService.NOTIFICATION_ID_RUNNING)
                cancel(com.tigonic.snoozely.service.TimerNotificationService.NOTIFICATION_ID_REMINDER)
            }
        } catch (_: Throwable) {}

        stopForegroundCompat()
        stopSelf()
    }


    private fun startServiceSafe(intent: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Als normaler Service reicht hier meist; wenn OS streng ist, fängt try/catch es ab.
                startService(intent)
            } else {
                startService(intent)
            }
        } catch (_: Throwable) { /* swallow to avoid crash */ }
    }

    private fun notificationsAllowedForChannel(id: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val nm = getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return false
        val ch = nm.getNotificationChannel(id) ?: return true
        return ch.importance != NotificationManager.IMPORTANCE_NONE
    }


    private fun flagImmutable(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
}


