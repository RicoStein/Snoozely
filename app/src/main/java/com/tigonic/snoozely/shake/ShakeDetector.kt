package com.tigonic.snoozely.shake

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.sqrt

/**
 * UI-freier Shake-Detector mit Live-Level und internem Cooldown.
 *
 * - strengthPercent 0..100 steuert die Schwellwertempfindlichkeit.
 * - onShake wird ausgelöst, wenn mehrere starke Peaks kurz hintereinander auftreten.
 * - level ∈ [0..1] liefert eine geglättete Intensität (für UI), fällt im Idle sanft auf 0 zurück.
 * - cooldownMs: Sperrzeit nach einem Treffer.
 */
class ShakeDetector(
    context: Context,
    strengthPercent: Int = 50,
    private val onShake: () -> Unit,
    private val cooldownMs: Long = 3000L,
    private val hitsToTrigger: Int = 2,   // Preview: 1, Service: 2
) : SensorEventListener {

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val sm: SensorManager = context.getSystemService(SensorManager::class.java)
    private val accel: Sensor? =
        sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Für Accelerometer-Fallback: grobe Schätzung der Gravitation (Low-Pass)
    private var gX = 0f; private var gY = 0f; private var gZ = 0f
    private val lpAlpha = 0.8f

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active

    // Öffentliches Live-Level [0..1] zum Visualisieren
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level

    // Empfindlichkeit (m/s²)
    @Volatile
    private var threshold = mapPercentToThreshold(strengthPercent)

    // Peak-Erkennung
    private var lastShakeTs = 0L
    private var hitCount = 0

    // Interner Feuercooldown
    @Volatile
    private var coolUntil = 0L

    fun updateStrength(percent: Int) {
        threshold = mapPercentToThreshold(percent.coerceIn(0, 100))
    }

    fun start() {
        if (accel == null) return
        if (_active.value) return
        sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
        _active.value = true

        // sanftes Abklingen im Idle (~60 FPS)
        scope.launch {
            while (isActive) {
                delay(16)
                val decayed = _level.value * 0.90f
                _level.value = if (decayed < 0.005f) 0f else decayed
            }
        }
    }

    fun stop() {
        if (!_active.value) return
        sm.unregisterListener(this)
        _active.value = false
        _level.value = 0f
        scope.cancel()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val usingLinear = accel?.type == Sensor.TYPE_LINEAR_ACCELERATION

        val ax: Float
        val ay: Float
        val az: Float

        if (usingLinear) {
            // Linear Acceleration enthält bereits keine Gravitation
            ax = event.values[0]; ay = event.values[1]; az = event.values[2]
        } else {
            // Fallback: Gravitation grob herausfiltern
            gX = lpAlpha * gX + (1 - lpAlpha) * event.values[0]
            gY = lpAlpha * gY + (1 - lpAlpha) * event.values[1]
            gZ = lpAlpha * gZ + (1 - lpAlpha) * event.values[2]
            ax = event.values[0] - gX
            ay = event.values[1] - gY
            az = event.values[2] - gZ
        }

        // sqrt erwartet Double
        val magnitude = sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat()

        // Level fürs UI (Über-Schwelle normiert)
        val maxMag = 25f
        val norm = ((magnitude - threshold) / (maxMag - threshold)).coerceIn(0f, 1f)
        val smooth = max(norm, _level.value * 0.85f + norm * 0.15f)
        _level.value = smooth

        if (magnitude > threshold) {
            val now = System.currentTimeMillis()
            if (now >= coolUntil) {
                // 1) Peak zählen (<= 350 ms = gleiche Geste)
                hitCount = if (now - lastShakeTs <= 350) hitCount + 1 else 1
                lastShakeTs = now

                // 2) Nach dem Zählen auslösen
                if (hitCount >= hitsToTrigger) {
                    hitCount = 0
                    coolUntil = now + cooldownMs
                    scope.launch { onShake() }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun mapPercentToThreshold(p: Int): Float {
        val min = 8f   // sehr sensibel
        val max = 20f  // sehr streng
        return min + (p / 100f) * (max - min)
    }
}
