package com.tigonic.snoozely.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tigonic.snoozely.R
import com.tigonic.snoozely.service.TimerContracts.ACTION_TICK
import com.tigonic.snoozely.service.TimerContracts.EXTRA_REMAINING_MS
import com.tigonic.snoozely.service.TimerContracts.EXTRA_TOTAL_MS
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Service, der die Medienlautstärke abhängig von der verbleibenden Zeit
 * des Sleep-Timers linear herunterfährt.
 *
 * Logik:
 * - Hört auf ACTION_TICK (Engine-Broadcast mit REMAINING/TOTAL).
 * - Wenn STOP_AUDIO aktiviert und FADE_OUT > 0:
 *      - Bei remainingSec <= fadeOutSec → Fade aktiv:
 *          volume = originalVolume * (remainingSec / fadeOutSec)
 *      - Bei Verlängerung (remainingSec > fadeOutSec) → Fade abbrechen + Volume restore.
 * - Bei Service-Stopp oder Abschalten der Funktion → Volume restore.
 */
class AudioFadeService : Service() {

    companion object {
        private const val TAG = "AudioFadeService"

        // Optionaler Foreground während der aktiven Fade-Phase:
        private const val FADE_CHANNEL_ID = "audio_fade"
        private const val FADE_NOTIFICATION_ID = 1007
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Audio
    private lateinit var audioManager: AudioManager
    private val stream = AudioManager.STREAM_MUSIC
    private var originalVolume: Int? = null
    private var fadingActive: Boolean = false

    // Settings-Cache (wird bei jedem Tick aktualisiert)
    @Volatile private var stopAudioEnabled: Boolean = true
    @Volatile private var fadeOutSec: Int = 30 // Default, wird aus Settings gelesen

    // Receiver für Ticks aus dem Engine-Service
    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_TICK) return
            val totalMs = intent.getLongExtra(EXTRA_TOTAL_MS, 0L)
            val remainingMs = intent.getLongExtra(EXTRA_REMAINING_MS, 0L)
            onTick(totalMs, remainingMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        registerReceivers()
        // Channel für optionalen Foreground (nur wenn du ihn aktivierst)
        createFadeChannelIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Service lebt im Hintergrund und reagiert auf Ticks.
        return START_STICKY
    }

    override fun onDestroy() {
        // Sicherheitshalber Lautstärke zurücksetzen, falls wir mitten im Fade sind.
        restoreVolumeIfNeeded()
        unregisterReceivers()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---- Core ----

    private fun onTick(totalMs: Long, remainingMs: Long) {
        scope.launch {
            // Settings aktuell lesen (billig genug für jeden Tick)
            stopAudioEnabled = SettingsPreferenceHelper.getStopAudio(applicationContext).first()
            val fadeOut = SettingsPreferenceHelper.getFadeOut(applicationContext).first()
            fadeOutSec = max(0, fadeOut.roundToInt()) // float (Sekunden) → int

            if (!stopAudioEnabled || fadeOutSec <= 0 || totalMs <= 0L) {
                // Funktion deaktiviert → sicherstellen, dass kein Fade aktiv bleibt
                if (fadingActive) {
                    restoreVolumeIfNeeded()
                    stopForegroundIfRunning()
                }
                return@launch
            }

            val remainingSec = max(0L, remainingMs / 1000L).toInt()

            if (remainingSec <= fadeOutSec) {
                // Fade-Fenster erreicht → linear reduzieren
                if (!fadingActive) {
                    // Start der Fade-Phase → Ausgangslautstärke einmalig puffern
                    originalVolume = audioManager.getStreamVolume(stream).coerceAtLeast(0)
                    fadingActive = true
                    // Optional: Foreground, falls du es brauchst:
                    // startForegroundWithTinyNotification()
                }

                val base = (originalVolume ?: audioManager.getStreamVolume(stream)).coerceAtLeast(0)
                // Verhältnis 0..1
                val ratio: Double = remainingSec.toDouble() / max(1, fadeOutSec).toDouble()
                // mindestens 0, maximal base
                val target = (base * ratio).let { ceil(it).toInt() }.coerceIn(0, base)

                setStreamVolumeSafe(target)
            } else {
                // Außerhalb des Fade-Fensters → ggf. abbrechen & Lautstärke zurück
                if (fadingActive) {
                    restoreVolumeIfNeeded()
                    stopForegroundIfRunning()
                }
            }
        }
    }

    // ---- Helpers ----

    private fun setStreamVolumeSafe(vol: Int) {
        try {
            val clamped = vol.coerceIn(0, audioManager.getStreamMaxVolume(stream))
            if (audioManager.getStreamVolume(stream) != clamped) {
                audioManager.setStreamVolume(stream, clamped, 0)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "setStreamVolume failed: $t")
        }
    }

    private fun restoreVolumeIfNeeded() {
        val base = originalVolume
        if (base != null) {
            setStreamVolumeSafe(base)
        }
        originalVolume = null
        fadingActive = false
    }

    // ---- Broadcast-Handling ----

    private fun registerReceivers() {
        val f = IntentFilter().apply { addAction(ACTION_TICK) }
        registerReceiver(tickReceiver, f)
    }

    private fun unregisterReceivers() {
        try {
            unregisterReceiver(tickReceiver)
        } catch (_: Throwable) { /* ignore */ }
    }

    // ---- (Optional) Foreground während des echten Fades ----

    private fun createFadeChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(FADE_CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        FADE_CHANNEL_ID,
                        "Audio Fade",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Aktiver Audio-Fade während des Sleep-Timers"
                        setShowBadge(false)
                        lockscreenVisibility = Notification.VISIBILITY_SECRET
                    }
                )
            }
        }
    }

    private fun startForegroundWithTinyNotification() {
        val notif = NotificationCompat.Builder(this, FADE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.fade_out_running)) // z. B. „Audio wird ausgeblendet…“
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        startForeground(FADE_NOTIFICATION_ID, notif)
    }

    private fun stopForegroundIfRunning() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Throwable) { /* ignore */ }
    }
}
