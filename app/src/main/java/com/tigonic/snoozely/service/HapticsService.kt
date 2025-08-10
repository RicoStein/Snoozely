package com.tigonic.snoozely.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.util.Log
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Kurzlebiger Service für Haptik/Sound beim Timer:
 * - Lässt sich per Intent-Actions ansteuern (Reminder, Pre-Finish, Finish)
 * - Respektiert Settings (TIMER_VIBRATE)
 * - Kapselt Vibrator/VibratorManager (API-safe)
 *
 * Aktuell: Vibrationsaufrufe sind vorbereitet, aber leicht deaktivierbar.
 * Du kannst in den when-Zweigen unten sofort Pattern/Töne setzen.
 */
class HapticsService : Service() {

    object Actions {
        /** z.B. „Noch X Minuten“ – kurzer Ping */
        const val REMINDER = "com.tigonic.snoozely.haptics.REMINDER"
        /** Optionaler Pre-Finish-Hinweis (z. B. 10s vor Ende) */
        const val PRE_FINISH = "com.tigonic.snoozely.haptics.PRE_FINISH"
        /** Timer ist zu Ende – kräftigeres Feedback */
        const val FINISH = "com.tigonic.snoozely.haptics.FINISH"
        /** Manuelles Testen aus der UI */
        const val TEST = "com.tigonic.snoozely.haptics.TEST"
    }

    object Extras {
        /** Optional: Stärke 1..255 (DEFAULT_AMPLITUDE wenn nicht gesetzt) */
        const val EXTRA_AMPLITUDE = "extra_amplitude"
        /** Optional: Dauer in ms für One-Shot */
        const val EXTRA_DURATION_MS = "extra_duration_ms"
        /** Optional: Pattern für VibrationEffect.createWaveform */
        const val EXTRA_PATTERN = "extra_pattern" // long[]
        /** Optional: Anzahl Wiederholungen bei Pattern (-1 = keine) */
        const val EXTRA_REPEAT = "extra_repeat"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            try {
                val action = intent?.action
                val hapticsEnabled = SettingsPreferenceHelper
                    .getTimerVibrate(applicationContext).first()

                if (!hapticsEnabled) {
                    Log.d(TAG, "Haptics skipped: setting disabled.")
                    stopSelf()
                    return@launch
                }

                when (action) {
                    Actions.REMINDER -> {
                        // Kurzer Hinweis. Beispiel: 60–120 ms Soft Pulse.
                        vibrateOneShot(
                            durationMs = intent.getLongExtra(Extras.EXTRA_DURATION_MS, 80L),
                            amplitude = intent.getIntExtra(Extras.EXTRA_AMPLITUDE, VibrationEffect.DEFAULT_AMPLITUDE)
                        )
                        // TODO: falls gewünscht, kurzen Ton abspielen -> playTone(Notification) vorbereiten
                    }

                    Actions.PRE_FINISH -> {
                        // Deutlichere „gleich ist Schluss“-Sequenz.
                        // Beispiel-Pattern (ms): Warte 0, vibriere 70, warte 60, vibriere 120
                        val defaultPattern = longArrayOf(0, 70, 60, 120)
                        vibratePattern(
                            pattern = intent.getLongArrayExtra(Extras.EXTRA_PATTERN) ?: defaultPattern,
                            repeat = intent.getIntExtra(Extras.EXTRA_REPEAT, -1)
                        )
                    }

                    Actions.FINISH -> {
                        // Kräftiger Abschluss. Beispiel: 200–300 ms Pulse.
                        vibrateOneShot(
                            durationMs = intent.getLongExtra(Extras.EXTRA_DURATION_MS, 220L),
                            amplitude = intent.getIntExtra(Extras.EXTRA_AMPLITUDE, VibrationEffect.DEFAULT_AMPLITUDE)
                        )
                        // TODO: optional Alarm/Beep abspielen, falls du später einen Sound-Schalter einführst.
                    }

                    Actions.TEST -> {
                        // Für UI-Tests: kurzes Pattern
                        vibratePattern(longArrayOf(0, 60, 50, 60, 50, 120), repeat = -1)
                    }

                    else -> {
                        Log.w(TAG, "Unknown action: $action")
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "HapticsService error", t)
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

    // --- Public helpers to start service from anywhere ---

    companion object {
        private const val TAG = "HapticsService"

        fun reminder(context: Context, durationMs: Long = 80L) {
            context.startService(
                Intent(context, HapticsService::class.java).apply {
                    action = Actions.REMINDER
                    putExtra(Extras.EXTRA_DURATION_MS, durationMs)
                }
            )
        }

        fun preFinish(context: Context, pattern: LongArray? = null) {
            context.startService(
                Intent(context, HapticsService::class.java).apply {
                    action = Actions.PRE_FINISH
                    if (pattern != null) putExtra(Extras.EXTRA_PATTERN, pattern)
                }
            )
        }

        fun finish(context: Context, durationMs: Long = 220L) {
            context.startService(
                Intent(context, HapticsService::class.java).apply {
                    action = Actions.FINISH
                    putExtra(Extras.EXTRA_DURATION_MS, durationMs)
                }
            )
        }

        fun test(context: Context) {
            context.startService(Intent(context, HapticsService::class.java).apply {
                action = Actions.TEST
            })
        }
    }

    // --- Intern: Vibrations-Abstraktionen ---

    private fun vibrateOneShot(durationMs: Long, amplitude: Int) {
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            VibrationEffect.createOneShot(durationMs, amplitude)
        } else {
            @Suppress("DEPRECATION")
            return vibrateLegacy(durationMs)
        }
        vibrate(effect)
    }

    private fun vibratePattern(pattern: LongArray, repeat: Int) {
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            VibrationEffect.createWaveform(pattern, repeat)
        } else {
            @Suppress("DEPRECATION")
            return vibrateLegacy(pattern, repeat)
        }
        vibrate(effect)
    }

    private fun vibrate(effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            val vib = vm.defaultVibrator
            if (!vib.hasVibrator()) return
            vib.vibrate(
                effect,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        } else {
            @Suppress("DEPRECATION")
            (getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator)
                ?.takeIf { it.hasVibrator() }
                ?.vibrate(effect)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrateLegacy(durationMs: Long) {
        (getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator)
            ?.takeIf { it.hasVibrator() }
            ?.vibrate(durationMs)
    }

    @Suppress("DEPRECATION")
    private fun vibrateLegacy(pattern: LongArray, repeat: Int) {
        (getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator)
            ?.takeIf { it.hasVibrator() }
            ?.vibrate(pattern, repeat)
    }

    // --- Optional: vorbereitet für Ton/Beep ---
    // Später kannst du hier z. B. MediaPlayer/RingtoneManager nutzen.
    // private fun playTone() { ... }
}
