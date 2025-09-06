package com.tigonic.snoozely.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
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
import com.tigonic.snoozely.shake.ShakeDetector
import kotlin.math.roundToInt

/**
 * TimerEngineService
 *
 * Verantwortlich für:
 * - Start/Stop/Extend/Reduce Logik des Sleeptimers
 * - Periodische Ticks (1 Hz) mit Berechnung der Restzeit
 * - Triggern von Audio-Fade, Haptics/Screen-Lock am Ende
 * - Anzeigen/Aktualisieren der Fortschrittsbenachrichtigung
 * - Broadcast der Ticks an UI/Widgets
 *
 * Wichtig:
 * - Läuft im Vordergrund (Low-Importance-Channel), sobald Progress-Notifications erlaubt sind.
 * - Widget-Updates betreffen ausschließlich das 3x1 Control-Widget.
 */
class TimerEngineService : Service() {

    companion object {
        private const val TAG = "TimerService"
        private const val NOTIF_ID_RUNNING = 42
        private const val NOTIF_ID_REMINDER = 43
        private const val REQ_EXTEND = 2002
        private const val REQ_STOP = 2003
        private const val SHAKE_COOLDOWN_MS = 3000L

        @Volatile var isForeground: Boolean = false
    }

    private var shakeDetector: ShakeDetector? = null
    @Volatile private var shakeCooldownUntil: Long = 0L
    private var shakeRingtone: Ringtone? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var tickerJob: Job? = null
    @Volatile private var reminderSentForStartTime: Long = -1L
    @Volatile private var lastStartMinutesCache: Int? = null

    // Audio-Fade Status
    @Volatile private var fadeStarted: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(
            TimerContracts.CHANNEL_RUNNING,
            getString(R.string.notification_channel_running),
            NotificationManager.IMPORTANCE_LOW
        )
        ensureChannel(
            TimerContracts.CHANNEL_REMINDER,
            getString(R.string.notification_channel_reminder),
            NotificationManager.IMPORTANCE_HIGH
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        tickerJob?.cancel()
        tickerJob = null
        serviceScope.cancel()
        isForeground = false
        stopShakeDetectorAndSound()
        fadeStarted = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val allowProgress = runCatching {
            val ctx = applicationContext
            val enabled = kotlinx.coroutines.runBlocking { SettingsPreferenceHelper.getNotificationEnabled(ctx).first() }
            val show    = kotlinx.coroutines.runBlocking { SettingsPreferenceHelper.getShowProgressNotification(ctx).first() }
            enabled && show
        }.getOrDefault(true)

        if (allowProgress) ensureForegroundOnce() else stopForegroundCompat()

        when (intent?.action) {
            TimerContracts.ACTION_START -> {
                reminderSentForStartTime = -1L
                val minutesFromIntent = intent.getIntExtra(TimerContracts.EXTRA_MINUTES, -1)
                serviceScope.launch {
                    val ctx = applicationContext
                    val minutes = if (minutesFromIntent > 0) minutesFromIntent
                    else runCatching { TimerPreferenceHelper.getTimer(ctx).first() }.getOrDefault(5)
                    lastStartMinutesCache = minutes
                    TimerPreferenceHelper.startTimer(ctx, minutes)
                    fadeStarted = false // reset fade state
                    startTickerIfNeeded()
                    // AudioFadeService wird erst beim Fade-Zeitpunkt gestartet
                    sendRunningUpdateNow()
                    requestWidgetUpdate()
                }
                return START_REDELIVER_INTENT
            }
            TimerContracts.ACTION_EXTEND -> {
                serviceScope.launch { extendTimer() }
                return START_REDELIVER_INTENT
            }
            TimerContracts.ACTION_REDUCE -> {
                serviceScope.launch { reduceTimer() }
                return START_REDELIVER_INTENT
            }
            TimerContracts.ACTION_STOP -> {
                // WICHTIG: nicht blockierend; alles in Coroutine erledigen
                serviceScope.launch { stopEverything(timerFinished = false) }
                return START_NOT_STICKY
            }
            else -> {
                serviceScope.launch {
                    val ctx = applicationContext
                    val running = TimerPreferenceHelper.getTimerRunning(ctx).first()
                    if (running) startTickerIfNeeded()
                }
                return START_REDELIVER_INTENT
            }
        }
    }

    /** Startet eine minimale Vordergrund-Notification, falls noch nicht aktiv. */
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
            // Fallback: Notification ohne echten FGS, um Abstürze zu verhindern
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID_RUNNING, minimal)
            isForeground = true
        }
    }

    private fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
            else @Suppress("DEPRECATION") stopForeground(true)
        } catch (_: Throwable) { /* ignore */ }
        isForeground = false
    }

    /** Notification-Channel sicherstellen (Neu-Anlage bei Namens-/Importance-Änderung). */
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

    /** Startet den Ticker-Loop (1 Hz), berechnet Restzeit, triggert Fade/Notifs/Widget-Update. */
    private fun startTickerIfNeeded() {
        if (tickerJob?.isActive == true) return
        tickerJob = serviceScope.launch {
            while (isActive) {
                try {
                    val ctx = applicationContext
                    val running   = TimerPreferenceHelper.getTimerRunning(ctx).first()
                    val startTime = TimerPreferenceHelper.getTimerStartTime(ctx).first()
                    val minutes   = TimerPreferenceHelper.getTimer(ctx).first()

                    if (!running || startTime <= 0L || minutes < 1) {
                        stopEverything(timerFinished = false)
                        return@launch
                    }

                    // Foreground-State nach Settings ausrichten
                    refreshForegroundBySettings()

                    // Shake-Detector je nach Einstellung aktivieren
                    val shakeEnabled = SettingsPreferenceHelper.getShakeEnabled(ctx).first()
                    if (shakeEnabled) {
                        val shakeStrength = SettingsPreferenceHelper.getShakeStrength(ctx).first()
                        ensureServiceShakeDetector(true, shakeStrength)
                    } else {
                        ensureServiceShakeDetector(false, 0)
                    }

                    val totalMs = minutes * 60_000L
                    val now = System.currentTimeMillis()
                    val elapsed = now - startTime
                    val remaining = (totalMs - elapsed).coerceAtLeast(0)

                    val stopAudio = runCatching { SettingsPreferenceHelper.getStopAudio(ctx).first() }.getOrDefault(true)
                    val fadeSec = runCatching { SettingsPreferenceHelper.getFadeOut(ctx).first().roundToInt() }.getOrDefault(30)
                    val thresholdMs = (fadeSec.coerceAtLeast(0) * 1000L)

                    if (stopAudio && fadeSec > 0) {
                        if (remaining <= thresholdMs && !fadeStarted) {
                            // NEU: verbleibende Zeit an den Fade übergeben
                            val fadeWindow = remaining.coerceAtMost(thresholdMs)
                            startServiceSafe(
                                Intent(ctx, AudioFadeService::class.java)
                                    .setAction(AudioFadeService.ACTION_FADE_AND_STOP)
                                    .putExtra("fadeWindowMs", fadeWindow) // <— NEU
                            )
                            fadeStarted = true
                        } else if (fadeStarted && remaining > thresholdMs) {
                            startServiceSafe(
                                Intent(ctx, AudioFadeService::class.java).setAction(AudioFadeService.ACTION_CANCEL_FADE)
                            )
                            fadeStarted = false
                        }
                    } else {
                        // Stop-Audio deaktiviert -> laufenden Fade abbrechen
                        if (fadeStarted) {
                            startServiceSafe(
                                Intent(ctx, AudioFadeService::class.java).setAction(AudioFadeService.ACTION_CANCEL_FADE)
                            )
                            fadeStarted = false
                        }
                    }

                    // App-weite Tick-Broadcasts (z. B. für UI)
                    sendBroadcast(Intent(TimerContracts.ACTION_TICK).apply {
                        putExtra(TimerContracts.EXTRA_TOTAL_MS, totalMs)
                        putExtra(TimerContracts.EXTRA_REMAINING_MS, remaining)
                    })

                    // Benachrichtigung + Widget updaten
                    sendRunningUpdateNow()
                    requestWidgetUpdate()

                    if (remaining <= 0L) {
                        onTimerFinished()
                        return@launch
                    }

                    // Reminder prüfen und ggf. senden
                    val notificationsEnabled = runCatching {
                        SettingsPreferenceHelper.getNotificationEnabled(ctx).first()
                    }.getOrDefault(false)

                    val showReminder = runCatching {
                        SettingsPreferenceHelper.getShowReminderPopup(ctx).first()
                    }.getOrDefault(false)

                    if (notificationsEnabled && showReminder && remaining > 0L) {
                        val reminderMin = runCatching {
                            SettingsPreferenceHelper.getReminderMinutes(ctx).first()
                        }.getOrDefault(5).coerceAtLeast(1)

                        val reminderThresholdMs = reminderMin * 60_000L

                        // Optional: Re-Arm, wenn wieder deutlich über der Schwelle
                        if (reminderSentForStartTime == startTime && remaining > reminderThresholdMs + 5_000L) {
                            reminderSentForStartTime = -1L
                        }

                        if (remaining <= reminderThresholdMs &&
                            reminderSentForStartTime != startTime &&
                            notificationsAllowedForChannel(TimerContracts.CHANNEL_REMINDER)
                        ) {
                            startServiceSafe(
                                Intent(ctx, TimerNotificationService::class.java)
                                    .setAction(TimerContracts.ACTION_NOTIFY_REMINDER)
                                    .putExtra("reminderMinutes", reminderMin)
                            )
                            reminderSentForStartTime = startTime
                        }
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Ticker iteration failed", t)
                } finally {
                    delay(1000)
                }
            }
        }
    }

    /** Startet/aktualisiert ShakeDetector im Service-Kontext. */
    private fun ensureServiceShakeDetector(enabled: Boolean, strength: Int) {
        if (enabled) {
            if (shakeDetector == null) {
                Log.d(TAG, "Creating and starting service ShakeDetector (strength: $strength%)")
                shakeDetector = ShakeDetector(
                    context = applicationContext,
                    strengthPercent = strength,
                    onShake = { serviceScope.launch { onShakeTriggered() } },
                    cooldownMs = SHAKE_COOLDOWN_MS,
                    hitsToTrigger = 1,
                    overFactor = 1.0f
                ).also { it.start() }
            } else {
                shakeDetector?.updateStrength(strength)
            }
        } else {
            stopShakeDetectorAndSound()
        }
    }

    private suspend fun onShakeTriggered() {
        val ctx = applicationContext
        val now = System.currentTimeMillis()
        if (now < shakeCooldownUntil) return
        val running = TimerPreferenceHelper.getTimerRunning(ctx).first()
        val enabled = SettingsPreferenceHelper.getShakeEnabled(ctx).first()
        if (!running || !enabled) return

        // Aktivierungsfenster prüfen
        val mode = SettingsPreferenceHelper.getShakeActivationMode(ctx).first()
        val delayMin = SettingsPreferenceHelper.getShakeActivationDelayMinutes(ctx).first()
        val start = TimerPreferenceHelper.getTimerStartTime(ctx).first()
        val elapsedMin = ((now - start) / 60_000L).toInt()
        val isActive = when (mode) {
            "after_start" -> elapsedMin >= delayMin
            else -> true
        }
        if (!isActive) return

        val extendBy = SettingsPreferenceHelper.getShakeExtendMinutes(ctx).first()
        extendTimerBy(extendBy)

        // Audiofeedback
        val soundMode = SettingsPreferenceHelper.getShakeSoundMode(ctx).first()
        val uri  = SettingsPreferenceHelper.getShakeRingtone(ctx).first()
        playShakeFeedback(soundMode, uri)

        shakeCooldownUntil = now + SHAKE_COOLDOWN_MS
    }

    private suspend fun extendTimerBy(extendMin: Int) {
        val ctx = applicationContext
        val running = TimerPreferenceHelper.getTimerRunning(ctx).first()
        val start   = TimerPreferenceHelper.getTimerStartTime(ctx).first()
        if (!running || start == 0L) return

        val minutes = TimerPreferenceHelper.getTimer(ctx).first()
        val now       = System.currentTimeMillis()
        val elapsed   = now - start
        val totalMs   = minutes * 60_000L
        val remaining = (totalMs - elapsed).coerceAtLeast(0)
        val newTotal   = remaining + extendMin * 60_000L
        val newMinutes = (((newTotal) + elapsed) / 60_000L).toInt().coerceAtLeast(1)
        TimerPreferenceHelper.setTimer(ctx, newMinutes)
        requestWidgetUpdate()
    }

    private fun playShakeFeedback(mode: String, ringtoneUri: String) {
        when (mode) {
            "vibrate" -> {
                try {
                    val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val vm = getSystemService(VibratorManager::class.java)
                        vm?.defaultVibrator
                    } else {
                        @Suppress("DEPRECATION")
                        getSystemService(Vibrator::class.java)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val effect = VibrationEffect.createOneShot(600L, VibrationEffect.DEFAULT_AMPLITUDE)
                        val audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                        vibrator?.vibrate(effect, audioAttributes)
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(600L)
                    }
                } catch (_: Throwable) { /* ignore */ }
            }
            "silent" -> { /* no-op */ }
            else -> {
                try {
                    shakeRingtone?.stop()
                    if (ringtoneUri.isBlank()) return
                    val uri = Uri.parse(ringtoneUri)
                    val r = RingtoneManager.getRingtone(this, uri)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        r.audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    }
                    shakeRingtone = r
                    r.play()
                } catch (_: Throwable) { }
            }
        }
    }

    private fun stopShakeDetectorAndSound() {
        if (shakeDetector != null) {
            Log.d(TAG, "Stopping service ShakeDetector")
            try { shakeDetector?.stop() } catch (_: Throwable) {}
            shakeDetector = null
        }

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            wakeLock = null
            Log.d(TAG, "WakeLock released")
        }

        runCatching { shakeRingtone?.stop() }
        shakeRingtone = null
    }

    private fun onTimerFinished() {
        serviceScope.launch {
            val ctx = applicationContext
            // ZUERST: Audio sauber pausieren und Volume restaurieren
            startServiceSafe(Intent(ctx, AudioFadeService::class.java).setAction(AudioFadeService.ACTION_FADE_FINALIZE))
            // DANN: Haptics / Screen lock
            startServiceSafe(Intent(ctx, HapticsService::class.java).setAction("END"))
            startServiceSafe(Intent(ctx, ScreenLockService::class.java))
            // Aufräumen
            stopEverything(timerFinished = true)
        }
    }

    private suspend fun stopEverything(timerFinished: Boolean) {
        tickerJob?.cancel()
        tickerJob = null
        val ctx = applicationContext

        val base = runCatching { TimerPreferenceHelper.getTimerUserBase(ctx).first() }.getOrDefault(0)
        val cached = lastStartMinutesCache
        val current = runCatching { TimerPreferenceHelper.getTimer(ctx).first() }.getOrDefault(5)
        val fallback = (if (base > 0) base else (cached ?: current)).coerceAtLeast(1)

        runCatching { TimerPreferenceHelper.stopTimer(ctx, fallback) }

        // Audio-Fade aufräumen
        if (!timerFinished) {
            // Manuell gestoppt: Fade abbrechen und Lautstärke wiederherstellen
            runCatching { startServiceSafe(Intent(applicationContext, AudioFadeService::class.java).setAction(AudioFadeService.ACTION_CANCEL_FADE)) }
            runCatching { stopService(Intent(applicationContext, AudioFadeService::class.java)) }
        }
        fadeStarted = false

        runCatching {
            getSystemService(NotificationManager::class.java).apply {
                cancel(TimerNotificationService.NOTIFICATION_ID_RUNNING)
                cancel(TimerNotificationService.NOTIFICATION_ID_REMINDER)
            }
        }

        disableBluetooth()
        disableWifi()
        stopShakeDetectorAndSound()
        requestWidgetUpdate()
        stopForegroundCompat()
        stopSelf()
    }

    /** Baut und liefert die laufende Fortschritts-Notification. */
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
        val reducePi =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                PendingIntent.getForegroundService(
                    this, 2004,
                    Intent(this, TimerEngineService::class.java).setAction(TimerContracts.ACTION_REDUCE),
                    flags
                )
            else
                PendingIntent.getService(
                    this, 2004,
                    Intent(this, TimerEngineService::class.java).setAction(TimerContracts.ACTION_REDUCE),
                    flags
                )
        return NotificationCompat.Builder(this, TimerContracts.CHANNEL_RUNNING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(getString(R.string.notification_timer_running))
            .setContentText(getString(R.string.notification_remaining_time, timeText))
            .setProgress(100, progress, false)
            .addAction(android.R.drawable.ic_media_previous, getString(R.string.timer_minus_x, extendStep), reducePi)
            .addAction(android.R.drawable.ic_lock_idle_alarm, getString(R.string.timer_stop), stopPi)
            .addAction(android.R.drawable.ic_input_add, getString(R.string.timer_plus_x, extendStep), extendPi)
            .build()
    }

    /** Aktualisiert die Fortschritts-Notification sofort. */
    private fun sendRunningUpdateNow() {
        serviceScope.launch {
            val ctx = applicationContext
            val running   = TimerPreferenceHelper.getTimerRunning(ctx).first()
            val startTime = TimerPreferenceHelper.getTimerStartTime(ctx).first()
            val minutes   = TimerPreferenceHelper.getTimer(ctx).first()
            if (!running || startTime <= 0L || minutes < 1) return@launch

            val notificationsEnabled = runCatching { SettingsPreferenceHelper.getNotificationEnabled(ctx).first() }.getOrDefault(true)
            val showProgress = runCatching { SettingsPreferenceHelper.getShowProgressNotification(ctx).first() }.getOrDefault(true)
            if (!(notificationsEnabled && showProgress)) {
                stopForegroundCompat()
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

    private suspend fun extendTimer() {
        val ctx = applicationContext
        val running = TimerPreferenceHelper.getTimerRunning(ctx).first()
        val start   = TimerPreferenceHelper.getTimerStartTime(ctx).first()
        if (!running || start == 0L) return
        val minutes   = TimerPreferenceHelper.getTimer(ctx).first()
        val extend    = runCatching { SettingsPreferenceHelper.getProgressExtendMinutes(ctx).first() }.getOrDefault(5).coerceAtLeast(1)
        val now       = System.currentTimeMillis()
        val elapsed   = now - start
        val totalMs   = minutes * 60_000L
        val remaining = (totalMs - elapsed).coerceAtLeast(0)
        val newTotal   = remaining + extend * 60_000L
        val newMinutes = (((newTotal) + elapsed) / 60_000L).toInt().coerceAtLeast(1)
        TimerPreferenceHelper.setTimer(ctx, newMinutes)
    }

    private suspend fun reduceTimer() {
        val ctx = applicationContext
        val running = TimerPreferenceHelper.getTimerRunning(ctx).first()
        val start   = TimerPreferenceHelper.getTimerStartTime(ctx).first()
        if (!running || start == 0L) return
        val minutes   = TimerPreferenceHelper.getTimer(ctx).first()
        val step      = runCatching { SettingsPreferenceHelper.getProgressExtendMinutes(ctx).first() }.getOrDefault(5).coerceAtLeast(1)
        val now       = System.currentTimeMillis()
        val elapsed   = now - start
        val totalMs   = minutes * 60_000L
        val remaining = (totalMs - elapsed).coerceAtLeast(0)
        val newTotalMs = (remaining - step * 60_000L).coerceAtLeast(60_000L)
        val newMinutes = (((newTotalMs) + elapsed) / 60_000L).toInt().coerceAtLeast(1)
        TimerPreferenceHelper.setTimer(ctx, newMinutes)
        requestWidgetUpdate()
    }

    private suspend fun disableBluetooth() {
        runCatching {
            val ctx = applicationContext
            val requested = SettingsPreferenceHelper.getBluetoothDisableRequested(ctx).first()
            if (requested && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                val intent = Intent(ctx, BluetoothService::class.java).setAction(BluetoothService.ACTION_DISABLE_BT)
                ctx.startService(intent)
            }
        }
    }

    private suspend fun disableWifi() {
        runCatching {
            val ctx = applicationContext
            val requested = SettingsPreferenceHelper.getWifiDisableRequested(ctx).first()
            if (requested && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                WifiControlService.start(ctx)
            }
        }
    }

    /** Startet einen Service „sicher“ (schluckt Exceptions, kein FGS-Zwang). */
    private fun startServiceSafe(intent: Intent) {
        try {
            startService(intent)
        } catch (_: Throwable) { /* swallow to avoid crash */ }
    }

    private fun notificationsAllowedForChannel(id: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val nm = getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return false
        val ch = nm.getNotificationChannel(id) ?: return true
        return ch.importance != NotificationManager.IMPORTANCE_NONE
    }

    private suspend fun refreshForegroundBySettings() {
        val ctx = applicationContext
        val notificationsEnabled = runCatching {
            SettingsPreferenceHelper.getNotificationEnabled(ctx).first()
        }.getOrDefault(false)
        val showProgress = runCatching {
            SettingsPreferenceHelper.getShowProgressNotification(ctx).first()
        }.getOrDefault(false)

        val allowProgress = notificationsEnabled && showProgress

        if (allowProgress) {
            // Falls nicht schon FG: Foreground sicherstellen
            ensureForegroundOnce()
        } else {
            // Sofort aus Foreground aussteigen und sichtbare Notification weg
            stopForegroundCompat()
            // Fallback: cancel
            try {
                getSystemService(NotificationManager::class.java)
                    .cancel(TimerNotificationService.NOTIFICATION_ID_RUNNING)
            } catch (_: Throwable) {}
        }
    }

    /**
     * Aktualisiert NUR das 3x1-„TimerControlWidget“.
     */
    private fun requestWidgetUpdate() {
        val ctx = applicationContext
        val awm = AppWidgetManager.getInstance(ctx)
        val ids = awm.getAppWidgetIds(
            ComponentName(ctx, com.tigonic.snoozely.widget.TimerControlWidgetProvider::class.java)
        )
        if (ids.isNotEmpty()) {
            val intent = Intent(ctx, com.tigonic.snoozely.widget.TimerControlWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            sendBroadcast(intent)
        }
    }

    private fun flagImmutable(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
}
