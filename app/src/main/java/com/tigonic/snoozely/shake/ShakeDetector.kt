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
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Leichter, UI-freier Shake-Detector mit Live-Level.
 *
 * - strengthPercent 0..100 steuert die Schwellwertempfindlichkeit.
 * - onShake wird ausgelöst, wenn mehrere starke Peaks kurz hintereinander auftreten.
 * - level ∈ [0..1] liefert eine geglättete Intensität; fällt im Idle sanft auf 0 zurück.
 *
 * Keine Runtime-Permission nötig.
 */
class ShakeDetector(
    context: Context,
    strengthPercent: Int = 50,
    private val onShake: () -> Unit
) : SensorEventListener {

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val sm: SensorManager = context.getSystemService(SensorManager::class.java)
    private val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active

    // Öffentliches Live-Level 0..1 (für UI)
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level

    // Schwellwert (~8..20 m/s²)
    @Volatile
    private var threshold = mapPercentToThreshold(strengthPercent)

    // Shake-Erkennung (Peaks)
    private var lastShakeTs = 0L
    private var hitCount = 0

    // Für die Glättung
    private var lastMagnitude = 0f
    private var lastUpdate = 0L

    fun updateStrength(percent: Int) {
        threshold = mapPercentToThreshold(percent.coerceIn(0, 100))
    }

    fun start() {
        if (accel == null) return
        if (_active.value) return
        sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
        _active.value = true

        // sanftes Abklingen im Idle
        scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                // alle ~16 ms etwas decayern
                delay(16)
                // exponentiell gegen 0 laufen lassen
                val current = _level.value
                val decayed = current * 0.90f
                if (decayed < 0.005f) {
                    if (_level.value != 0f) _level.value = 0f
                } else {
                    _level.value = decayed
                }
                lastUpdate = now
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
        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]
        val magnitude = sqrt(ax * ax + ay * ay + az * az) // grob inkl. g

        // Normalisieren auf [0..1] relativ zu threshold..maxMag
        val maxMag = 25f                   // „sehr starkes“ Schütteln
        val norm = ((magnitude - threshold) / (maxMag - threshold))
            .coerceIn(0f, 1f)

        // leichte Glättung beim Anstieg (responsive, aber nicht zackig)
        val smooth = max(norm, _level.value * 0.85f + norm * 0.15f)
        _level.value = smooth
        lastMagnitude = magnitude

        // echte Shake-Geste (2 schnelle Peaks)
        if (magnitude > threshold) {
            val now = System.currentTimeMillis()
            if (now - lastShakeTs < 350) {
                hitCount++
                if (hitCount >= 2) {
                    hitCount = 0
                    scope.launch { onShake() }
                }
            } else {
                hitCount = 1
            }
            lastShakeTs = now
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun mapPercentToThreshold(p: Int): Float {
        val min = 8f   // sehr sensibel
        val max = 20f  // sehr streng
        return min + (p / 100f) * (max - min)
    }
}
