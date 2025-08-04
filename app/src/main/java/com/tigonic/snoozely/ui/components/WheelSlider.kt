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

    // Interne States für Umdrehungen und Winkel im Kreis
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
                                        // **Hier die Lösung:** Drag-Startpunkt auf aktuellen Winkel setzen
                                        lastAngle = angle
                                        // Sofort Wert setzen und abbrechen (damit alles synchron bleibt)
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
            // Hintergrund-Kreis
            drawCircle(
                color = Color(0xFF222222),
                style = Stroke(width = stroke.toPx())
            )
            // Fortschritts-Bogen (fortlaufender Verlauf)
            val valueForSweep = calcValue(rounds, angleInCircle)
            if (valueForSweep > 0) {
                val sweep = (valueForSweep * 360f / stepsPerCircle)
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color(0xFF7F7FFF),
                            Color(0xFF77E8FF),
                            Color(0xFF77FFA2),
                            Color(0xFFFFD84C),
                            Color(0xFFFF7F7F),
                            Color(0xFF7F7FFF)
                        )
                    ),
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = stroke.toPx(), cap = StrokeCap.Round)
                )
            }
            // Handle-Position immer sichtbar (auch bei 0 Minuten, dann oben)
            val sweep = (valueForSweep * 360f / stepsPerCircle)
            val radians = Math.toRadians((sweep - 90f).toDouble())
            val radius = (size.toPx() - stroke.toPx()) / 2
            val center = Offset(this.size.width / 2, this.size.height / 2)
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
                text = "MINUTEN",
                fontWeight = FontWeight.Normal,
                color = Color(0xAAFFFFFF),
                style = MaterialTheme.typography.titleMedium,
                letterSpacing = 2.sp
            )
        }
    }
}
