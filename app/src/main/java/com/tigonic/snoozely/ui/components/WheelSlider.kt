package com.tigonic.snoozely.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*
import com.tigonic.snoozely.R
import androidx.compose.ui.res.stringResource

@Composable
fun WheelSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minValue: Int = 0,
    maxValue: Int = 600,
    stepsPerCircle: Int = 60
) {
    val size = 300.dp
    val stroke = 12.dp

    var rounds by remember { mutableStateOf(value / stepsPerCircle) }
    var angleInCircle by remember { mutableStateOf((value % stepsPerCircle) * 360f / stepsPerCircle) }
    var lastAngle by remember { mutableStateOf<Float?>(null) }

    fun calcValue(rounds: Int, angle: Float): Int {
        val minutesInCircle = ((angle / 360f) * stepsPerCircle).roundToInt()
        return (rounds * stepsPerCircle + minutesInCircle).coerceIn(minValue, maxValue)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        Canvas(
            modifier = Modifier
                .size(size)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val center = this.size.width / 2f
                            val x = offset.x - center
                            val y = offset.y - center
                            val angle = ((Math.toDegrees(atan2(y.toDouble(), x.toDouble())) + 450) % 360).toFloat()
                            lastAngle = angle
                        },
                        onDrag = { change: PointerInputChange, _: Offset ->
                            val center = this.size.width / 2f
                            val x = change.position.x - center
                            val y = change.position.y - center
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
                        onDragEnd = {
                            lastAngle = null
                        },
                        onDragCancel = {
                            lastAngle = null
                        }
                    )
                }
        ) {
            // Schöner Verlauf: Rot → Orange → Gelb → Weiß → Rot
            val sweepColors = listOf(
                Color(0xFFFF2222), Color(0xFFFF531B), Color(0xFFFF851E), Color(0xFFFFB719), Color(0xFFFFEA16),
                Color(0xFFFFFF15), Color(0xFFFFEA16), Color(0xFFFFB719), Color(0xFFFF851E), Color(0xFFFF531B), Color(0xFFFF2222)
            )
            val valueForSweep = calcValue(rounds, angleInCircle)
            val sweep = (valueForSweep * 360f / stepsPerCircle)
            val center = Offset(this.size.width / 2, this.size.height / 2)

// "Mitziehender" Verlauf: Farbliste drehen!
            val steps = sweepColors.size - 1
            val rotateCount = ((steps * sweep / 360f).roundToInt() + steps) % steps
            val rotatedColors = sweepColors.drop(rotateCount) + sweepColors.take(rotateCount) + sweepColors[rotateCount]

            if (valueForSweep > 0) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = rotatedColors,
                        center = center
                    ),
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = stroke.toPx(), cap = StrokeCap.Round)
                )
            }

            // Handle-Position exakt auf Rand
            val radians = Math.toRadians((sweep - 90f).toDouble())
            val radius = (size.toPx() - stroke.toPx()) / 2 + stroke.toPx() / 2 - 1.dp.toPx()
            val handleCenter = Offset(
                x = (center.x + cos(radians) * radius).toFloat(),
                y = (center.y + sin(radians) * radius).toFloat()
            )
            drawCircle(
                color = Color.White,
                radius = 16.dp.toPx(),
                center = handleCenter
            )
        }

        // Minutenanzeige in der Mitte
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val valueForText = calcValue(rounds, angleInCircle)
            Text(
                text = valueForText.toString(),
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.displayLarge,
                color = Color.White
            )
            Text(
                text = stringResource(R.string.minutes),
                fontWeight = FontWeight.Normal,
                color = Color(0xAAFFFFFF),
                style = MaterialTheme.typography.titleMedium,
                letterSpacing = 2.sp
            )
        }
    }
}
