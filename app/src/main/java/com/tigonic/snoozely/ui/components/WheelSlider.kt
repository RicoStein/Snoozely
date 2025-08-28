package com.tigonic.snoozely.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.tigonic.snoozely.ui.theme.LocalExtraColors
import kotlin.math.*

@Composable
fun WheelSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minValue: Int = 1,
    maxValue: Int = 1000,
    stepsPerCircle: Int = 60,
    showCenterText: Boolean = true,
    wheelAlpha: Float = 1f,
    wheelScale: Float = 1f,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val extra = LocalExtraColors.current

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

    // Interaktionsparameter
    // Drags innerhalb dieser relativen Radius-Schwelle werden ignoriert oder auf den Rand projiziert
    val innerDeadZoneR = 0.65f
    val snapEnter = 0.88f
    val snapExit = 0.82f
    // Begrenze pro Event den maximalen Winkel-Schritt stark, damit die Anzeige ruhig bleibt
    val maxDeltaPerEventDeg = 12f

    // State
    var rounds by remember { mutableStateOf(value / stepsPerCircle) }
    var angleInCircle by remember { mutableStateOf((value % stepsPerCircle) * 360f / stepsPerCircle) }
    var continuousAngleDeg by remember { mutableStateOf((value.toFloat() / stepsPerCircle) * 360f) }
    var snapMode by remember { mutableStateOf(false) }
    var lastEmitted by remember { mutableStateOf(value.coerceIn(minValue, maxValue)) }
    var ignoreDrag by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        val clamped = value.coerceIn(minValue, maxValue)
        rounds = clamped / stepsPerCircle
        angleInCircle = (clamped % stepsPerCircle) * 360f / stepsPerCircle
        continuousAngleDeg = (clamped.toFloat() / stepsPerCircle) * 360f
        lastEmitted = clamped
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
    fun projectToEdge(pos: Offset): Offset {
        // Projektiert einen Punkt radial auf den Kreisrand
        val dx = pos.x - center.x
        val dy = pos.y - center.y
        val len = hypot(dx, dy).coerceAtLeast(1e-3f)
        return Offset(center.x + dx / len * wheelRadius, center.y + dy / len * wheelRadius)
    }
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
        val steps = ((continuousAngleDeg / 360f) * stepsPerCircle)
            .roundToInt()
            .coerceIn(minValue, maxValue)
        if (steps != lastEmitted) {
            lastEmitted = steps
            rounds = steps / stepsPerCircle
            angleInCircle = ((steps % stepsPerCircle).toFloat() / stepsPerCircle) * 360f
            onValueChange(steps)
        } else {
            // Nur interne Winkel-States aktualisieren, nicht emittieren
            rounds = steps / stepsPerCircle
            angleInCircle = ((steps % stepsPerCircle).toFloat() / stepsPerCircle) * 360f
        }
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

    // Min/Max-Winkel
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
                                        val r = radialR(pos)
                                        val posEdge = if (r < innerDeadZoneR) projectToEdge(pos) else pos
                                        val finger = polarAngleFrom(posEdge)
                                        continuousAngleDeg =
                                            closestEquivalentAngle(finger, continuousAngleDeg)
                                                .coerceIn(minAngle, maxAngle)
                                        emitFromContinuous()
                                        try { this.tryAwaitRelease() } catch (_: Throwable) {}
                                    },
                                    onTap = { pos ->
                                        val r = radialR(pos)
                                        val posEdge = if (r < innerDeadZoneR) projectToEdge(pos) else pos
                                        val finger = polarAngleFrom(posEdge)
                                        continuousAngleDeg =
                                            closestEquivalentAngle(finger, continuousAngleDeg)
                                                .coerceIn(minAngle, maxAngle)
                                        emitFromContinuous()
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { pos ->
                                        val r = radialR(pos)
                                        ignoreDrag = r < innerDeadZoneR
                                        val posUse = if (ignoreDrag) projectToEdge(pos) else pos
                                        val finger = polarAngleFrom(posUse)
                                        snapMode = r >= snapEnter
                                        if (snapMode) {
                                            continuousAngleDeg =
                                                closestEquivalentAngle(finger, continuousAngleDeg)
                                                    .coerceIn(minAngle, maxAngle)
                                            emitFromContinuous()
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        val rRaw = radialR(change.position)
                                        val r = rRaw.coerceIn(0f, 1f)
                                        if (r < innerDeadZoneR) {
                                            // Inner Dead-Zone: handle bleibt stabil, aber wir projizieren für den Winkel
                                            val posEdge = projectToEdge(change.position)
                                            val finger = polarAngleFrom(posEdge)
                                            // Im Inneren schalten wir in einen sanften Snap
                                            snapMode = true
                                            continuousAngleDeg =
                                                closestEquivalentAngle(finger, continuousAngleDeg)
                                                    .coerceIn(minAngle, maxAngle)
                                            emitFromContinuous()
                                            return@detectDragGestures
                                        }

                                        // Snap-Modus je nach Radius
                                        if (!snapMode && r >= snapEnter) snapMode = true
                                        else if (snapMode && r < snapExit) snapMode = false

                                        val finger = polarAngleFrom(change.position)
                                        if (snapMode) {
                                            continuousAngleDeg =
                                                closestEquivalentAngle(finger, continuousAngleDeg)
                                        } else {
                                            val currentMod = ((continuousAngleDeg % 360f) + 360f) % 360f
                                            var delta = finger - currentMod
                                            if (delta > 180f) delta -= 360f
                                            if (delta < -180f) delta += 360f
                                            // Keine Radial-Speed-Booster mehr – ruhig halten
                                            val deltaAdj = delta.coerceIn(-maxDeltaPerEventDeg, maxDeltaPerEventDeg)
                                            continuousAngleDeg += deltaAdj
                                        }
                                        continuousAngleDeg = continuousAngleDeg.coerceIn(minAngle, maxAngle)
                                        emitFromContinuous()
                                    },
                                    onDragEnd = { ignoreDrag = false }
                                )
                            }
                    } else Modifier
                )
        ) {
            // Hintergrundring
            drawCircle(
                color = extra.wheelTrack,
                radius = wheelRadius,
                center = center,
                style = Stroke(width = strokePx)
            )

            // Fortschrittsbogen (Gradients)
            if (drawSteps > 0) {
                val base = extra.wheelGradient
                val closed = if (base.isNotEmpty() && base.first() != base.last()) {
                    base + base.first()
                } else base
                val sweepClamped = sweep.coerceAtMost(359.999f)

                withTransform({
                    rotate(degrees = -90f, pivot = center)
                }) {
                    drawArc(
                        brush = Brush.sweepGradient(colors = closed, center = center),
                        startAngle = 0f,
                        sweepAngle = sweepClamped,
                        useCenter = false,
                        style = Stroke(width = strokePx, cap = StrokeCap.Butt),
                        topLeft = Offset(center.x - wheelRadius, center.y - wheelRadius),
                        size = Size(wheelRadius * 2, wheelRadius * 2)
                    )
                }
            }

            // Handle
            drawCircle(
                color = if (enabled) extra.toggle else cs.onSurface.copy(alpha = 0.4f),
                radius = handlePx,
                center = handleCenter
            )
        }
    }
}
