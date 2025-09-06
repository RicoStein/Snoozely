package com.tigonic.snoozely.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.IBinder
import android.util.Log
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/**
 * Service zum sanften Herunterregeln (Fade) der Medienlautstärke und
 * finalem Pausieren der Wiedergabe mit sauberem Restore.
 *
 * Actions:
 * - ACTION_FADE_AND_STOP: startet NUR den Fade (kein Pause/Restore)
 * - ACTION_CANCEL_FADE  : bricht Fade ab und regelt Lautstärke sanft wieder hoch
 * - ACTION_FADE_FINALIZE: setzt Volume=0 -> (kleine Warte) -> Pause -> (kleine Warte) -> Volume Restore
 */
class AudioFadeService : Service() {

    companion object {
        private const val TAG = "AudioFadeService"

        // Bestehende IDs beibehalten (kompatibel zu Aufrufern)
        const val ACTION_FADE_AND_STOP = "com.tigonic.snoozely.action.FADE_AND_STOP" // Fade ONLY
        const val ACTION_CANCEL_FADE   = "com.tigonic.snoozely.action.CANCEL_FADE"
        const val ACTION_FADE_FINALIZE = "com.tigonic.snoozely.action.FADE_FINALIZE" // Pause + Restore

        private const val RAMP_UP_MS_DEFAULT = 600L
        private const val FINALIZE_DELAY_BEFORE_PAUSE_MS = 500L
        private const val FINALIZE_DELAY_AFTER_PAUSE_MS  = 500L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var audioManager: AudioManager

    @Volatile private var fadeJob: Job? = null
    @Volatile private var rampUpJob: Job? = null
    @Volatile private var isFading: Boolean = false
    @Volatile private var originalVolume: Int = -1

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_FADE_AND_STOP -> {
                // Fade ONLY; Finalisierung erfolgt separat (siehe FINALIZE)
                val windowOverride = intent.getLongExtra("fadeWindowMs", -1L)
                serviceScope.launch { startFadeOnly(windowOverride) }
            }
            ACTION_CANCEL_FADE -> {
                cancelFadeAndRampUp()
            }
            ACTION_FADE_FINALIZE -> {
                serviceScope.launch { finalizePauseAndRestore() }
            }
            else -> {
                Log.d(TAG, "Unknown action=${intent?.action}")
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun startFadeOnly(windowOverrideMs: Long = -1L) {
        if (isFading || fadeJob?.isActive == true) return
        rampUpJob?.cancel()

        val fadeDurationSec = runCatching {
            SettingsPreferenceHelper.getFadeOut(applicationContext).first().roundToInt()
        }.getOrDefault(30).coerceAtLeast(0)

        // Ursprungslautstärke nur einmal merken
        if (originalVolume < 0) {
            originalVolume = getCurrentVolume()
        }
        val startVol = (originalVolume.takeIf { it >= 0 } ?: getCurrentVolume()).coerceAtLeast(0)

        isFading = true
        fadeJob = serviceScope.launch {
            try {
                val configuredTotalMs = fadeDurationSec * 1000L
                // NEU: Wenn ein Fenster übergeben wurde, in dieses Fenster einpassen
                val totalMs = if (windowOverrideMs > 0) {
                    windowOverrideMs.coerceAtMost(configuredTotalMs)
                } else {
                    configuredTotalMs
                }

                if (totalMs > 0 && startVol > 0) {
                    val steps = startVol
                    val delayPerStep = (totalMs / steps).coerceAtLeast(10L)
                    for (vol in startVol downTo 0) {
                        ensureActive()
                        setVolume(vol)
                        if (vol > 0) delay(delayPerStep)
                    }
                } else {
                    setVolume(0)
                }
                // Warten auf FINALIZE/CANCEL; kein Pause/Restore hier
            } catch (t: Throwable) {
                // logging …
            } finally {
                isFading = false
            }
        }
    }

    private fun cancelFadeAndRampUp() {
        // Fade abbrechen
        runCatching { fadeJob?.cancel() }
        fadeJob = null
        isFading = false

        val target = originalVolume.takeIf { it >= 0 } ?: getCurrentVolume()
        val current = getCurrentVolume()

        // Sanftes Hochregeln
        rampUpJob?.cancel()
        rampUpJob = serviceScope.launch {
            try {
                val durationMs = RAMP_UP_MS_DEFAULT
                val steps = (target - current).coerceAtLeast(1)
                val stepDelay = if (steps > 0) (durationMs / steps) else 0L
                for (vol in current..target) {
                    ensureActive()
                    setVolume(vol)
                    if (stepDelay > 0) delay(stepDelay)
                }
            } catch (_: Throwable) {
                // ignore
            } finally {
                // Reset State
                originalVolume = -1
                stopSelf()
            }
        }
    }

    private suspend fun finalizePauseAndRestore() {
        // Sicherstellen, dass kein Fade mehr läuft
        runCatching { fadeJob?.cancelAndJoin() }
        fadeJob = null
        isFading = false
        // Laufendes Ramp-Up abbrechen, falls aktiv
        runCatching { rampUpJob?.cancelAndJoin() }
        rampUpJob = null

        try {
            // Falls keine Ursprungslautstärke gemerkt war, aktuellen Wert sichern bevor wir auf 0 setzen
            var backupOriginal = originalVolume
            if (backupOriginal < 0) {
                backupOriginal = getCurrentVolume()
            }

            // 1) Lautstärke auf 0
            setVolume(0)
            // 2) kurze Wartezeit: ältere Player pausieren asynchron
            delay(FINALIZE_DELAY_BEFORE_PAUSE_MS)
            // 3) Pause senden
            sendMediaPauseCommand()
            // 4) erneut kurz warten, bis Pause „greift“
            delay(FINALIZE_DELAY_AFTER_PAUSE_MS)
            // 5) Lautstärke wiederherstellen
            setVolume(backupOriginal)
            Log.d(TAG, "Finalize done: media paused, volume restored to $backupOriginal")
        } catch (t: Throwable) {
            Log.e(TAG, "Finalize error", t)
        } finally {
            originalVolume = -1
            stopSelf()
        }
    }

    private fun getCurrentVolume(): Int =
        try { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) } catch (_: Throwable) { 0 }

    private fun setVolume(volume: Int) {
        try {
            val v = volume.coerceAtLeast(0)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set volume", e)
        }
    }

    private fun sendMediaPauseCommand() {
        try {
            // Bevorzugt: explizites Pause-Event
            val downPause = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
            val upPause   = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,   android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
            audioManager.dispatchMediaKeyEvent(downPause)
            audioManager.dispatchMediaKeyEvent(upPause)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed MEDIA_PAUSE, try PLAY_PAUSE fallback", t)
            // Fallback für Player, die nur Play/Pause-Toggle verstehen
            runCatching {
                val down = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                val up   = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,   android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                audioManager.dispatchMediaKeyEvent(down)
                audioManager.dispatchMediaKeyEvent(up)
            }
        }
    }

    override fun onDestroy() {
        runCatching { fadeJob?.cancel() }
        runCatching { rampUpJob?.cancel() }
        fadeJob = null
        rampUpJob = null
        isFading = false
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
