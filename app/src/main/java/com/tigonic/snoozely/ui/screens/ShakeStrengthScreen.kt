package com.tigonic.snoozely.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tigonic.snoozely.R
import com.tigonic.snoozely.shake.ShakeDetector
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShakeStrengthScreen(onBack: () -> Unit) {
    val appCtx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    // gespeicherter Schwellenwert (0..100)
    val saved by SettingsPreferenceHelper
        .getShakeStrength(appCtx)
        .collectAsState(initial = 50)
    var strength by remember(saved) { mutableStateOf(saved) }

    // Detector: triggert nichts im Screen, liefert nur Live-Level
    val detector = remember {
        ShakeDetector(
            context = appCtx,
            strengthPercent = strength,
            onShake = {} // hier keine Aktion nötig; Screen ist nur Visualisierung
        )
    }
    LaunchedEffect(strength) { detector.updateStrength(strength) }
    DisposableEffect(Unit) {
        detector.start()
        onDispose { detector.stop() }
    }

    // Live-Level 0..1 aus dem Detector (mit kleiner UI-Glättung)
    val rawLevel by detector.level.collectAsState(0f)
    val uiLevel by animateFloatAsState(
        targetValue = rawLevel,
        animationSpec = androidx.compose.animation.core.spring(stiffness = 400f),
        label = "shakeLevelAnim"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shake_strength)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF101010),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF101010)
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Visual: senkrechter Balken – grau im Idle, farbig je nach Level
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val barWidth = 54.dp
                val barCorner = 10.dp
                val innerPad = 2.dp

                Canvas(
                    modifier = Modifier
                        .width(barWidth)
                        .fillMaxHeight(0.82f)
                ) {
                    val W = size.width
                    val H = size.height

                    // Rahmen
                    drawRoundRect(
                        color = Color(0xFF2B2B2B),
                        size = size,
                        style = Stroke(width = 2.dp.toPx()),
                        cornerRadius = CornerRadius(barCorner.toPx())
                    )

                    // Hintergrund (grau)
                    val innerSize = Size(W - innerPad.toPx() * 2, H - innerPad.toPx() * 2)
                    val innerTopLeft = Offset(innerPad.toPx(), innerPad.toPx())
                    drawRoundRect(
                        color = Color(0xFF2A2A2A),
                        size = innerSize,
                        topLeft = innerTopLeft,
                        cornerRadius = CornerRadius((barCorner - 2.dp).toPx())
                    )

                    // Füllstand nur, wenn Bewegung (> ~0.02)
                    if (uiLevel > 0.02f) {
                        val fillHeight = innerSize.height * uiLevel.coerceIn(0f, 1f)
                        val top = innerTopLeft.y + innerSize.height - fillHeight

                        // Verlauf: unten (stark) warm → oben (schwächer) kühler
                        val brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFFE000), // gelb
                                Color(0xFF7CD458), // grün
                                Color(0xFF0AB1A4)  // türkis
                            ),
                            startY = top,
                            endY = innerTopLeft.y + innerSize.height
                        )

                        drawRoundRect(
                            brush = brush,
                            topLeft = Offset(innerTopLeft.x, top),
                            size = Size(innerSize.width, fillHeight),
                            cornerRadius = CornerRadius((barCorner - 2.dp).toPx())
                        )
                    }
                }

                // Vertikaler Slider für die Empfindlichkeit (0..100)
                Slider(
                    value = strength.toFloat(),
                    onValueChange = { v ->
                        val s = v.toInt().coerceIn(0, 100)
                        strength = s
                        scope.launch { SettingsPreferenceHelper.setShakeStrength(appCtx, s) }
                        detector.updateStrength(s)
                    },
                    valueRange = 0f..100f,
                    steps = 99,
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent,
                        thumbColor = Color.White
                    ),
                    modifier = Modifier
                        .height(54.dp)        // nach Rotation: Breite
                        .fillMaxHeight(0.82f) // nach Rotation: Höhe
                        .rotate(-90f)
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.shake_strength),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${strength}%",
                color = Color(0xFFBDBDBD),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.shake_strength_hint),
                color = Color(0xFF9E9E9E),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
