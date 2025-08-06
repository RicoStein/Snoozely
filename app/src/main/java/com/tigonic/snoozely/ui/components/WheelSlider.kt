package com.tigonic.snoozely.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.*
import com.tigonic.snoozely.ui.components.TimerCenterText

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
    wheelScale: Float = 1f
) {
    val size = 300.dp
    val stroke = 12.dp
    val handleRadius = 16.dp

    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }
    val strokePx = with(density) { stroke.toPx() }
    val handlePx = with(density) { handleRadius.toPx() }
    val wheelRadius = (sizePx - strokePx) / 2f
    val center = Offset(sizePx / 2, sizePx / 2)

    var rounds by remember { mutableStateOf(value / stepsPerCircle) }
    var angleInCircle by remember { mutableStateOf((value % stepsPerCircle) * 360f / stepsPerCircle) }
    var lastAngle by remember { mutableStateOf<Float?>(null) }

    fun calcValue(rounds: Int, angle: Float): Int {
        val minutesInCircle = ((angle / 360f) * stepsPerCircle).roundToInt()
        return (rounds * stepsPerCircle + minutesInCircle).coerceIn(minValue, maxValue)
    }

    val valueForSweep = calcValue(rounds, angleInCircle)
    val sweep = (valueForSweep * 360f / stepsPerCircle)
    val radians = Math.toRadians((sweep - 90f).toDouble())
    val handleCenter = Offset(
        x = (center.x + cos(radians) * wheelRadius).toFloat(),
        y = (center.y + sin(radians) * wheelRadius).toFloat()
    )

    if (wheelAlpha > 0f) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .scale(wheelScale)
        ) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val cx = sizePx / 2f
                                val cy = sizePx / 2f
                                val x = offset.x - cx
                                val y = offset.y - cy
                                val angle = ((Math.toDegrees(atan2(y.toDouble(), x.toDouble())) + 450) % 360).toFloat()
                                lastAngle = angle
                            },
                            onDrag = { change: PointerInputChange, _: Offset ->
                                val cx = sizePx / 2f
                                val cy = sizePx / 2f
                                val x = change.position.x - cx
                                val y = change.position.y - cy
                                val angle = ((Math.toDegrees(atan2(y.toDouble(), x.toDouble())) + 450) % 360).toFloat()

                                lastAngle?.let { prevAngle ->
                                    var delta = angle - prevAngle
                                    if (delta > 180f) delta -= 360f
                                    if (delta < -180f) delta += 360f

                                    var newAngle = angleInCircle + delta
                                    var newRounds = rounds

                                    if (newAngle >= 360f) {
                                        newAngle -= 360f
                                        newRounds++
                                    } else if (newAngle < 0f) {
                                        if (newRounds > 0) {
                                            newAngle += 360f
                                            newRounds--
                                        } else {
                                            newAngle = 0f
                                            lastAngle = angle
                                            val newValue = calcValue(newRounds, newAngle)
                                            onValueChange(newValue)
                                            return@detectDragGestures
                                        }
                                    }

                                    val newValue = calcValue(newRounds, newAngle)
                                    onValueChange(newValue)
                                    angleInCircle = newAngle
                                    rounds = newRounds
                                    lastAngle = angle
                                }
                            },
                            onDragEnd = { lastAngle = null },
                            onDragCancel = { lastAngle = null }
                        )
                    }
            ) {
                val sweepColors = listOf(
                    Color(0xFFFF2222), Color(0xFFFF531B), Color(0xFFFF851E), Color(0xFFFFB719), Color(0xFFFFEA16),
                    Color(0xFFFFFF15), Color(0xFFFFEA16), Color(0xFFFFB719), Color(0xFFFF851E), Color(0xFFFF531B), Color(0xFFFF2222)
                )
                val steps = sweepColors.size - 1
                val rotateCount = ((steps * sweep / 360f).roundToInt() + steps) % steps
                val rotatedColors = sweepColors.drop(rotateCount) + sweepColors.take(rotateCount) + sweepColors[rotateCount]

                drawCircle(
                    color = Color(0xFF22252A),
                    radius = wheelRadius,
                    center = center,
                    style = Stroke(width = strokePx)
                )
                if (valueForSweep > 0) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = rotatedColors,
                            center = center
                        ),
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                        topLeft = Offset(
                            center.x - wheelRadius,
                            center.y - wheelRadius
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            wheelRadius * 2,
                            wheelRadius * 2
                        )
                    )
                }
            }

        }

        // HANDLE (Punkt)
        Box(
            modifier = Modifier
                .size(size)
                .scale(wheelScale),
            contentAlignment = Alignment.TopStart
        ) {
            Canvas(
                modifier = Modifier
                    .size(handleRadius * 2)
                    .absoluteOffset {
                        IntOffset(
                            (handleCenter.x - handlePx).roundToInt(),
                            (handleCenter.y - handlePx).roundToInt()
                        )
                    }
            ) {
                drawCircle(
                    color = Color.White,
                    radius = handlePx,
                    center = Offset(handlePx, handlePx)
                )
            }
        }
    }
}
