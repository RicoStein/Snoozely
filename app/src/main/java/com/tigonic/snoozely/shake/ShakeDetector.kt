package com.tigonic.snoozely.shake

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max
import kotlin.math.sqrt

/**
 * UI-freier Shake-Detector:
 * - Verwendet TYPE_LINEAR_ACCELERATION (Fallback: Accelerometer mit einfachem Low-Pass).
 * - strengthPercent (0..100) wird 1:1 auf einen m/s²-Schwellenwert gemappt (minThr..maxThr).
 * - onShake wird bei ausreichend starken Peaks innerhalb kurzer Zeitfenster ausgelöst.
 * - magnitudeNorm: absolute Magnitude [0..1] für Visualisierung.
 * - thresholdNorm: aktuell verwendete Schwelle [0..1] (damit UI exakt dieselbe Linie zeichnen kann).
 */
class ShakeDetector(
    context: Context,
    strengthPercent: Int = 50,
    private val onShake: () -> Unit,
    private val cooldownMs: Long = 3000L,
    private val hitsToTrigger: Int = 1,
    private val overFactor: Float = 1.0f
) : SensorEventListener {

    // Sensor
    private val sm: SensorManager = context.getSystemService(SensorManager::class.java)
    private val accel: Sensor? =
        sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Fallback-Entfernung der Gravitation (low-pass)
    private var gX = 0f; private var gY = 0f; private var gZ = 0f
    private val lpAlpha = 0.8f

    // Coroutine-Scope
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Live-States
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active

    private val _magnitudeNorm = MutableStateFlow(0f)
    val magnitudeNorm: StateFlow<Float> = _magnitudeNorm

    private val _thresholdNorm = MutableStateFlow(0f)
    val thresholdNorm: StateFlow<Float> = _thresholdNorm

    // Mapping-Konstanten
    private val minThr = 8f   // sehr sensibel
    private val maxThr = 50f  // sehr streng
    private val maxMag = 25f  // Skalierungsgrundlage für 0..1 Visualisierung

    // Aktuelle Schwelle in m/s²
    @Volatile private var threshold = mapPercentToThreshold(strengthPercent)

    // Peak-Logik
    private var lastShakeTs = 0L
    private var hitCount = 0
    private val peakWindowMs = 250L
    private val rearmRatio = 0.70f
    private var canCountPeak = true
    @Volatile private var coolUntil = 0L

    fun start() {
        if (accel == null) return
        if (_active.value) return
        sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
        _active.value = true
        _thresholdNorm.value = (threshold / maxMag).coerceIn(0f, 1f)

        // sanfter Decay für UI-Level (~60 FPS)
        scope.launch {
            while (isActive) {
                delay(16)
                val decayed = _magnitudeNorm.value * 0.92f
                _magnitudeNorm.value = if (decayed < 0.004f) 0f else decayed
            }
        }
    }

    fun stop() {
        if (!_active.value) return
        sm.unregisterListener(this)
        _active.value = false
        _magnitudeNorm.value = 0f
        scope.coroutineContext.cancelChildren()
    }

    fun updateStrength(percent: Int) {
        threshold = mapPercentToThreshold(percent.coerceIn(0, 100))
        _thresholdNorm.value = (threshold / maxMag).coerceIn(0f, 1f)
    }

    override fun onSensorChanged(event: android.hardware.SensorEvent?) {
        event ?: return
        val usingLinear = (accel?.type == Sensor.TYPE_LINEAR_ACCELERATION)

        val ax: Float; val ay: Float; val az: Float
        if (usingLinear) {
            ax = event.values[0]; ay = event.values[1]; az = event.values[2]
        } else {
            gX = lpAlpha * gX + (1 - lpAlpha) * event.values[0]
            gY = lpAlpha * gY + (1 - lpAlpha) * event.values[1]
            gZ = lpAlpha * gZ + (1 - lpAlpha) * event.values[2]
            ax = event.values[0] - gX
            ay = event.values[1] - gY
            az = event.values[2] - gZ
        }

        val magnitude = sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat()

        // absolute Magnitude 0..1 (für Visualisierung)
        _magnitudeNorm.value = (magnitude / maxMag).coerceIn(0f, 1f)

        // Trigger-Logik
        val effective = threshold * overFactor
        val rearm = effective * rearmRatio
        val now = System.currentTimeMillis()

        if (canCountPeak && magnitude >= effective) {
            if (now >= coolUntil) {
                hitCount = if (now - lastShakeTs <= peakWindowMs) hitCount + 1 else 1
                lastShakeTs = now

                if (hitCount >= hitsToTrigger) {
                    hitCount = 0
                    coolUntil = now + cooldownMs
                    scope.launch { onShake() }
                }
            }
            canCountPeak = false
        } else if (magnitude < rearm) {
            // Re-Arm sobald unter Hysterese
            canCountPeak = true
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private val curveGamma = 1.8f
    private fun mapPercentToThreshold(p: Int): Float {
        val t = (p.coerceIn(0, 100) / 100f).toDouble()
        val curved = Math.pow(t, curveGamma.toDouble()).toFloat()
        return minThr + (maxThr - minThr) * curved
    }
}
