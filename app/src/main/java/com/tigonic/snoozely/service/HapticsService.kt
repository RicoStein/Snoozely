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
 * Kurzlebiger Service für haptisches Feedback zum Timer.
 * - Respektiert das Setting „TIMER_VIBRATE“
 * - Unterstützt einfache One-Shot-Vibrationen und Patterns
 * - Beendet sich nach Ausführung selbst (START_NOT_STICKY)
 */
class HapticsService : Service() {

    object Actions {
        const val REMINDER   = "com.tigonic.snoozely.haptics.REMINDER"    // kurzer Hinweis
        const val PRE_FINISH = "com.tigonic.snoozely.haptics.PRE_FINISH"  // Sequenz „gleich Schluss“
        const val FINISH     = "com.tigonic.snoozely.haptics.FINISH"      // kräftiger Abschluss
        const val TEST       = "com.tigonic.snoozely.haptics.TEST"        // Test aus UI
    }

    object Extras {
        const val EXTRA_AMPLITUDE   = "extra_amplitude"     // 1..255, DEFAULT_AMPLITUDE wenn nicht gesetzt
        const val EXTRA_DURATION_MS = "extra_duration_ms"   // Dauer in ms für One-Shot
        const val EXTRA_PATTERN     = "extra_pattern"       // long[] für Waveform
        const val EXTRA_REPEAT      = "extra_repeat"        // Anzahl Wiederholungen (-1 = keine)
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
                        vibrateOneShot(
                            durationMs = intent?.getLongExtra(Extras.EXTRA_DURATION_MS, 80L) ?: 80L,
                            amplitude  = intent?.getIntExtra(Extras.EXTRA_AMPLITUDE, VibrationEffect.DEFAULT_AMPLITUDE)
                                ?: VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    }
                    Actions.PRE_FINISH -> {
                        val defaultPattern = longArrayOf(0, 70, 60, 120)
                        vibratePattern(
                            pattern = intent?.getLongArrayExtra(Extras.EXTRA_PATTERN) ?: defaultPattern,
                            repeat  = intent?.getIntExtra(Extras.EXTRA_REPEAT, -1) ?: -1
                        )
                    }
                    Actions.FINISH -> {
                        vibrateOneShot(
                            durationMs = intent?.getLongExtra(Extras.EXTRA_DURATION_MS, 220L) ?: 220L,
                            amplitude  = intent?.getIntExtra(Extras.EXTRA_AMPLITUDE, VibrationEffect.DEFAULT_AMPLITUDE)
                                ?: VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    }
                    Actions.TEST -> {
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

    companion object {
        private const val TAG = "HapticsService"

        // Convenience-Starter aus beliebigem Kontext
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

    // --- Vibrations-Abstraktionen ---

    private fun vibrateOneShot(durationMs: Long, amplitude: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrateEffect(VibrationEffect.createOneShot(durationMs, amplitude))
        } else {
            @Suppress("DEPRECATION")
            (getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator)
                ?.takeIf { it.hasVibrator() }
                ?.vibrate(durationMs)
        }
    }

    private fun vibratePattern(pattern: LongArray, repeat: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrateEffect(VibrationEffect.createWaveform(pattern, repeat))
        } else {
            @Suppress("DEPRECATION")
            (getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator)
                ?.takeIf { it.hasVibrator() }
                ?.vibrate(pattern, repeat)
        }
    }

    private fun vibrateEffect(effect: VibrationEffect) {
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
}
