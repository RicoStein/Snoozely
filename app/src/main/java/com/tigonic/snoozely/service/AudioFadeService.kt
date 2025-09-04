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

class AudioFadeService : Service() {

    companion object {
        private const val TAG = "AudioFadeService"
        // Bestehende Actions weiterverwenden
        const val ACTION_FADE_AND_STOP = "com.tigonic.snoozely.action.FADE_AND_STOP" // hier: Fade ONLY (ohne Stop)
        const val ACTION_CANCEL_FADE = "com.tigonic.snoozely.action.CANCEL_FADE"
        const val ACTION_FADE_FINALIZE = "com.tigonic.snoozely.action.FADE_FINALIZE" // Pause + Restore
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
                // Bedeutet hier: Fade ONLY (nicht pausieren); Pause erfolgt bei Timer-Ende via ACTION_FADE_FINALIZE
                serviceScope.launch { startFadeOnly() }
            }
            ACTION_CANCEL_FADE -> {
                cancelFadeAndRampUp()
            }
            ACTION_FADE_FINALIZE -> {
                serviceScope.launch { finalizePauseAndRestore() }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun startFadeOnly() {
        if (isFading || fadeJob?.isActive == true) return
        // Laufendes Hochregeln stoppen
        rampUpJob?.cancel()

        val fadeDurationSec = runCatching {
            SettingsPreferenceHelper.getFadeOut(applicationContext).first().roundToInt()
        }.getOrDefault(30).coerceAtLeast(0)

        // Ursprungslautstärke nur einmal merken
        if (originalVolume < 0) {
            originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        }

        val startVol = (originalVolume.takeIf { it >= 0 } ?: audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
            .coerceAtLeast(0)

        isFading = true
        fadeJob = serviceScope.launch {
            try {
                if (fadeDurationSec > 0 && startVol > 0) {
                    val steps = startVol
                    val totalMs = fadeDurationSec * 1000L
                    val delayPerStep = (totalMs / steps).coerceAtLeast(10L)
                    for (vol in startVol downTo 0) {
                        setVolume(vol)
                        delay(delayPerStep)
                        if (!isActive) return@launch
                    }
                } else {
                    setVolume(0)
                }
                // Wichtig: hier NICHT pausieren und NICHT Lautstärke zurücksetzen.
                // Der Service bleibt aktiv, damit originalVolume erhalten bleibt, bis FINALIZE oder CANCEL kommt.
                Log.d(TAG, "Fade finished (no pause). Waiting for finalize or cancel.")
            } catch (t: Throwable) {
                Log.e(TAG, "Error during fade", t)
            } finally {
                isFading = false
                // Service bewusst NICHT beenden, wir warten auf FINALIZE/CANCEL.
            }
        }
    }

    private fun cancelFadeAndRampUp() {
        // Fade abbrechen
        runCatching { fadeJob?.cancel() }
        fadeJob = null
        isFading = false

        val target = originalVolume.takeIf { it >= 0 }
            ?: audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        // Sanftes Hochregeln (400–800ms)
        rampUpJob?.cancel()
        rampUpJob = serviceScope.launch {
            try {
                val durationMs = 600L
                val steps = (target - current).coerceAtLeast(1)
                val stepDelay = if (steps > 0) (durationMs / steps) else 0L
                for (vol in current..target) {
                    setVolume(vol)
                    if (stepDelay > 0) delay(stepDelay)
                    if (!isActive) return@launch
                }
            } catch (_: Throwable) { /* ignore */ }
            finally {
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
        // Ramp-Up abbrechen, falls noch aktiv (wir steuern gleich gezielt)
        runCatching { rampUpJob?.cancelAndJoin() }
        rampUpJob = null

        try {
            // Falls keine Ursprungslautstärke gemerkt war, aktuellen Wert sichern bevor wir auf 0 setzen
            var backupOriginal = originalVolume
            if (backupOriginal < 0) {
                backupOriginal = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            }

            // Ton auf 0, dann Pause, dann wiederherstellen
            setVolume(0)
            kotlinx.coroutines.delay(500L)
            sendMediaPauseCommand()
            kotlinx.coroutines.delay(500L)
            setVolume(backupOriginal)
            Log.d(TAG, "Finalize done: media paused, volume restored to $backupOriginal")
        } catch (t: Throwable) {
            Log.e(TAG, "Finalize error", t)
        } finally {
            originalVolume = -1
            stopSelf()
        }
    }

    private fun setVolume(volume: Int) {
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume.coerceAtLeast(0), 0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set volume", e)
        }
    }

    private fun sendMediaPauseCommand() {
        try {
            val downEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
            val upEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to send media pause command", t)
        }
    }

    override fun onDestroy() {
        runCatching { fadeJob?.cancel() }
        runCatching { rampUpJob?.cancel() }
        fadeJob = null
        rampUpJob = null
        isFading = false
        // Kein automatisches Restore hier (wird durch CANCEL oder FINALIZE gemacht)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
