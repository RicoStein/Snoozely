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
import androidx.compose.ui.graphics.Color
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
    minValue: Int = 1,          // Minimum (Minuten)
    maxValue: Int = 1000,       // Maximum (Minuten)
    stepsPerCircle: Int = 60,   // 60 Schritte = 60 Minuten pro Kreis
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
    val innerDeadZoneR = 0.65f
    val snapEnter = 0.88f
    val snapExit = 0.82f
    val maxDeltaPerEventDeg = 12f

    // Winkel-Grenzen (entsprechend min/max Minuten)
    val minAngle = (minValue.toFloat() / stepsPerCircle) * 360f // 1 min -> 6°
    val maxAngle = (maxValue.toFloat() / stepsPerCircle) * 360f

    // State
    var rounds by remember { mutableIntStateOf(value / stepsPerCircle) }
    var angleInCircle by remember { mutableStateOf((value % stepsPerCircle) * 360f / stepsPerCircle) }
    var continuousAngleDeg by remember {
        mutableFloatStateOf(((value.toFloat() / stepsPerCircle) * 360f).coerceIn(minAngle, maxAngle))
    }
    var snapMode by remember { mutableStateOf(false) }
    var lastEmitted by remember { mutableIntStateOf(value.coerceIn(minValue, maxValue)) }
    var ignoreDrag by remember { mutableStateOf(false) }

    LaunchedEffect(value, minAngle, maxAngle) {
        val clamped = value.coerceIn(minValue, maxValue)
        rounds = clamped / stepsPerCircle
        angleInCircle = (clamped % stepsPerCircle) * 360f / stepsPerCircle
        continuousAngleDeg = ((clamped.toFloat() / stepsPerCircle) * 360f).coerceIn(minAngle, maxAngle)
        lastEmitted = clamped
    }

    // Helpers
    fun polarAngleFrom(offset: Offset): Float {
        val x = offset.x - center.x
        val y = offset.y - center.y
        // 0° = oben (12 Uhr), clockwise
        return (((Math.toDegrees(atan2(y.toDouble(), x.toDouble())) + 450.0) % 360.0).toFloat())
    }
    fun radialR(pos: Offset): Float {
        val dx = pos.x - center.x
        val dy = pos.y - center.y
        return (hypot(dx, dy) / wheelRadius).coerceIn(0f, 1f)
    }
    fun projectToEdge(pos: Offset): Offset {
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
            rounds = steps / stepsPerCircle
            angleInCircle = ((steps % stepsPerCircle).toFloat() / stepsPerCircle) * 360f
        }
        return steps
    }

    // Zeichnungswinkel: kontinuierlich, aber hart auf min/max geklemmt (kein < 1 Minute)
    val sweepExact = continuousAngleDeg.coerceIn(minAngle, maxAngle)
    val normalizedAngle = ((continuousAngleDeg % 360f) + 360f) % 360f

    // Kopfzentrum
    val radians = Math.toRadians((normalizedAngle - 90f).toDouble())
    val handleCenter = Offset(
        x = (center.x + cos(radians) * wheelRadius).toFloat(),
        y = (center.y + sin(radians) * wheelRadius).toFloat()
    )

    if (wheelAlpha <= 0f) return

    // Farben für Verlauf: Blau -> helleres Blau -> Türkis -> leicht helles Grün
    val headDark = Color(0xFF1E88E5)   // Blau (Kopf)
    val blueLight = Color(0xFF64B5F6)  // helleres Blau
    val turquoise = Color(0xFF26C6DA)  // Türkis
    val greenLight = Color(0xFF4AC3AB) // leicht helles Grün

    // Kurzer Cap direkt hinter dem Kopf (ca. 4° ≈ < 1 Minute)
    val capAngleDeg = min(sweepExact, 4f)

    // Ausrichtung: 0° zeigt in Kopfrichtung (oben)
    val rotateToHead = normalizedAngle - 90f

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
                                        continuousAngleDeg = closestEquivalentAngle(finger, continuousAngleDeg)
                                            .coerceIn(minAngle, maxAngle) // clamp erzwingt >= 1 min
                                        emitFromContinuous()
                                        try { this.tryAwaitRelease() } catch (_: Throwable) {}
                                    },
                                    onTap = { pos ->
                                        val r = radialR(pos)
                                        val posEdge = if (r < innerDeadZoneR) projectToEdge(pos) else pos
                                        val finger = polarAngleFrom(posEdge)
                                        continuousAngleDeg = closestEquivalentAngle(finger, continuousAngleDeg)
                                            .coerceIn(minAngle, maxAngle) // clamp erzwingt >= 1 min
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
                                            continuousAngleDeg = closestEquivalentAngle(finger, continuousAngleDeg)
                                                .coerceIn(minAngle, maxAngle)
                                            emitFromContinuous()
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        val rRaw = radialR(change.position)
                                        val r = rRaw.coerceIn(0f, 1f)
                                        if (r < innerDeadZoneR) {
                                            val posEdge = projectToEdge(change.position)
                                            val finger = polarAngleFrom(posEdge)
                                            snapMode = true
                                            continuousAngleDeg = closestEquivalentAngle(finger, continuousAngleDeg)
                                                .coerceIn(minAngle, maxAngle) // clamp
                                            emitFromContinuous()
                                            return@detectDragGestures
                                        }

                                        if (!snapMode && r >= snapEnter) snapMode = true
                                        else if (snapMode && r < snapExit) snapMode = false

                                        val finger = polarAngleFrom(change.position)
                                        if (snapMode) {
                                            continuousAngleDeg = closestEquivalentAngle(finger, continuousAngleDeg)
                                        } else {
                                            val currentMod = ((continuousAngleDeg % 360f) + 360f) % 360f
                                            var delta = finger - currentMod
                                            if (delta > 180f) delta -= 360f
                                            if (delta < -180f) delta += 360f
                                            val deltaAdj = delta.coerceIn(-maxDeltaPerEventDeg, maxDeltaPerEventDeg)
                                            continuousAngleDeg += deltaAdj
                                        }
                                        // Harter Clamp auf min/max – verhindert < 1 Minute
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

            if (sweepExact > 0f) {
                val sweepClamped = sweepExact.coerceIn(minAngle, 359.999f)

                withTransform({
                    rotate(degrees = rotateToHead, pivot = center)
                }) {
                    // Verlauf hinter dem Kopf: [startTrail .. 360)
                    val startTrail = (360f - sweepClamped) % 360f
                    val tailLen = (sweepClamped - capAngleDeg).coerceAtLeast(0f)

                    // Farbverlauf (Blau -> helleres Blau -> Türkis -> leicht helles Grün)
                    val stops = buildList {
                        add(0.00f to headDark) // gegenüberliegende Seite dunkel (Artefakte vermeiden)
                        val s = startTrail / 360f
                        if (tailLen > 0f) {
                            add(s to greenLight)                                        // Schwanz: grün
                            add(((startTrail + tailLen * 0.40f) / 360f) to turquoise)   // -> türkis
                            add(((startTrail + tailLen * 0.70f) / 360f) to blueLight)   // -> helleres blau
                            add(((startTrail + tailLen * 0.98f) / 360f) to headDark)    // -> nahe Kopf abdunkeln
                        } else {
                            add(s to headDark)
                        }
                        add(1.00f to headDark) // am Kopf dunkel
                    }.sortedBy { it.first }.toTypedArray()

                    val brush = Brush.sweepGradient(
                        colorStops = stops,
                        center = center
                    )

                    // Verlaufsspur
                    drawArc(
                        brush = brush,
                        startAngle = startTrail,
                        sweepAngle = sweepClamped,
                        useCenter = false,
                        style = Stroke(width = strokePx, cap = StrokeCap.Butt),
                        topLeft = Offset(center.x - wheelRadius, center.y - wheelRadius),
                        size = Size(wheelRadius * 2, wheelRadius * 2)
                    )

                    // Kurzer dunkler Cap direkt hinter dem Kopf
                    if (capAngleDeg > 0f) {
                        val capStart = (360f - capAngleDeg) % 360f
                        drawArc(
                            color = headDark,
                            startAngle = capStart,
                            sweepAngle = capAngleDeg,
                            useCenter = false,
                            style = Stroke(width = strokePx, cap = StrokeCap.Butt),
                            topLeft = Offset(center.x - wheelRadius, center.y - wheelRadius),
                            size = Size(wheelRadius * 2, wheelRadius * 2)
                        )
                    }
                }
            }

            // Kopf (immer dunkel)
            drawCircle(
                color = headDark,
                radius = handlePx,
                center = handleCenter
            )
        }
    }
}
