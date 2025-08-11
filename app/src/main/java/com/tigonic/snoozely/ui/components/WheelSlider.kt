package com.tigonic.snoozely.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun WheelSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minValue: Int = 1,
    maxValue: Int = 600,
    stepsPerCircle: Int = 60,
    showCenterText: Boolean = true,
    wheelAlpha: Float = 1f,
    wheelScale: Float = 1f,
    enabled: Boolean = true,
) {
    // Maße
    val size = 350.dp
    val stroke = 12.dp
    val handleRadius = 16.dp

    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }
    val strokePx = with(density) { stroke.toPx() }
    val handlePx = with(density) { handleRadius.toPx() }

    val wheelRadius = (sizePx - strokePx) / 2f
    val center = Offset(sizePx / 2f, sizePx / 2f)

    // State
    var rounds by remember { mutableStateOf(value / stepsPerCircle) }
    var angleInCircle by remember { mutableStateOf((value % stepsPerCircle) * 360f / stepsPerCircle) }
    var continuousAngleDeg by remember { mutableStateOf((value.toFloat() / stepsPerCircle) * 360f) }
    var snapMode by remember { mutableStateOf(false) }
    val snapEnter = 0.88f
    val snapExit = 0.82f

    LaunchedEffect(value) {
        val clamped = value.coerceIn(minValue, maxValue)
        rounds = clamped / stepsPerCircle
        angleInCircle = (clamped % stepsPerCircle) * 360f / stepsPerCircle
        continuousAngleDeg = (clamped.toFloat() / stepsPerCircle) * 360f
    }

    // Helpers
    fun polarAngleFrom(offset: Offset): Float {
        val x = offset.x - center.x
        val y = offset.y - center.y
        return (((Math.toDegrees(atan2(y.toDouble(), x.toDouble())) + 450.0) % 360.0).toFloat())
    }

    fun radialR(pos: Offset): Float {
        val dx = pos.x - center.x
        val dy = pos.y - center.y
        return (hypot(dx, dy) / wheelRadius).coerceIn(0f, 1f)
    }

    fun radialSpeed(r: Float, maxBoost: Float = 3f): Float =
        1f + (1f - r) * (maxBoost - 1f)

    fun closestEquivalentAngle(target: Float, continuous: Float): Float {
        val base = floor((continuous - target) / 360f)
        val c1 = target + base * 360f
        val c2 = c1 + 360f
        val c0 = c1 - 360f
        val d0 = abs(continuous - c0)
        val d1 = abs(continuous - c1)
        val d2 = abs(continuous - c2)
        return when {
            d0 <= d1 && d0 <= d2 -> c0
            d1 <= d0 && d1 <= d2 -> c1
            else -> c2
        }
    }

    fun emitFromContinuous(): Int {
        val steps = ((continuousAngleDeg / 360f) * stepsPerCircle).roundToInt()
            .coerceIn(minValue, maxValue)
        rounds = steps / stepsPerCircle
        angleInCircle = ((steps % stepsPerCircle).toFloat() / stepsPerCircle) * 360f
        onValueChange(steps)
        return steps
    }

    val drawSteps = ((continuousAngleDeg / 360f) * stepsPerCircle).roundToInt()
        .coerceIn(minValue, maxValue)
    val sweep = (drawSteps.toFloat() / stepsPerCircle) * 360f
    val normalizedAngle = ((continuousAngleDeg % 360f) + 360f) % 360f
    val radians = Math.toRadians((normalizedAngle - 90f).toDouble())
    val handleCenter = Offset(
        x = (center.x + cos(radians) * wheelRadius).toFloat(),
        y = (center.y + sin(radians) * wheelRadius).toFloat()
    )

    // GEÄNDERT: Min/Max-Winkel zentral definieren
    val minAngle = (minValue.toFloat() / stepsPerCircle) * 360f
    val maxAngle = (maxValue.toFloat() / stepsPerCircle) * 360f

    if (wheelAlpha <= 0f) return

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .scale(wheelScale)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (enabled) {
                        Modifier
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = { pos ->
                                        val finger = polarAngleFrom(pos)
                                        continuousAngleDeg =
                                            closestEquivalentAngle(finger, continuousAngleDeg)
                                        // GEÄNDERT: Begrenzung für Klick hinzufügen
                                        continuousAngleDeg = continuousAngleDeg.coerceIn(minAngle, maxAngle)
                                        emitFromContinuous()
                                        try {
                                            this.tryAwaitRelease()
                                        } catch (_: Throwable) {
                                        }
                                    },
                                    onTap = { pos ->
                                        val finger = polarAngleFrom(pos)
                                        continuousAngleDeg =
                                            closestEquivalentAngle(finger, continuousAngleDeg)
                                        // GEÄNDERT: Begrenzung für Klick hinzufügen
                                        continuousAngleDeg = continuousAngleDeg.coerceIn(minAngle, maxAngle)
                                        emitFromContinuous()
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { pos ->
                                        val r = radialR(pos)
                                        val finger = polarAngleFrom(pos)
                                        snapMode = r >= snapEnter
                                        if (snapMode) {
                                            continuousAngleDeg =
                                                closestEquivalentAngle(finger, continuousAngleDeg)
                                            // GEÄNDERT: Begrenzung auch hier anwenden
                                            continuousAngleDeg = continuousAngleDeg.coerceIn(minAngle, maxAngle)
                                            emitFromContinuous()
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        val r = radialR(change.position)
                                        val finger = polarAngleFrom(change.position)

                                        if (!snapMode && r >= snapEnter) {
                                            snapMode = true
                                        } else if (snapMode && r < snapExit) {
                                            snapMode = false
                                        }

                                        if (snapMode) {
                                            continuousAngleDeg = closestEquivalentAngle(finger, continuousAngleDeg)
                                        } else {
                                            val currentMod = ((continuousAngleDeg % 360f) + 360f) % 360f
                                            var delta = finger - currentMod
                                            if (delta > 180f) delta -= 360f
                                            if (delta < -180f) delta += 360f
                                            val speed = radialSpeed(r)
                                            val deltaAdj = (delta * speed).coerceIn(-45f, 45f)
                                            continuousAngleDeg += deltaAdj
                                        }

                                        continuousAngleDeg = continuousAngleDeg.coerceIn(minAngle, maxAngle)
                                        emitFromContinuous()
                                    },
                                    onDragEnd = {
                                    }
                                )
                            }
                    } else Modifier
                )
        ) {
            // Hintergrundring
            drawCircle(
                color = Color(0xFF22252A),
                radius = wheelRadius,
                center = center,
                style = Stroke(width = strokePx)
            )

            // Fortschrittsbogen
            if (drawSteps > 0) {
                val sweepColors = listOf(
                    Color(0xFFFF2222), Color(0xFFFF531B), Color(0xFFFF851E),
                    Color(0xFFFFB719), Color(0xFFFFEA16), Color(0xFFFFFF15),
                    Color(0xFFFFEA16), Color(0xFFFFB719), Color(0xFFFF851E),
                    Color(0xFFFF531B), Color(0xFFFF2222)
                )
                val steps = sweepColors.size - 1
                val rotateCount =
                    ((steps * sweep / 360f).roundToInt() + steps) % steps
                val rotatedColors =
                    sweepColors.drop(rotateCount) + sweepColors.take(rotateCount) + sweepColors[rotateCount]

                drawArc(
                    brush = Brush.sweepGradient(colors = rotatedColors, center = center),
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = strokePx, cap = StrokeCap.Butt),
                    topLeft = Offset(center.x - wheelRadius, center.y - wheelRadius),
                    size = Size(wheelRadius * 2, wheelRadius * 2)
                )
            }

            // Handle im selben Canvas
            drawCircle(
                color = if (enabled) Color.White else Color(0x66FFFFFF),
                radius = handlePx,
                center = handleCenter
            )
        }
    }
}