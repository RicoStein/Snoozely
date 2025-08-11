package com.tigonic.snoozely.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tigonic.snoozely.R
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShakeStrengthScreen(onBack: () -> Unit) {
    val appCtx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    // 0..100 (0 = sehr sensibel, 100 = sehr starkes Schütteln nötig)
    val strengthPref by SettingsPreferenceHelper
        .getShakeStrength(appCtx)
        .collectAsState(initial = 50)

    var strength by remember(strengthPref) { mutableStateOf(strengthPref) }

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
            // Visual: zentraler vertikaler Balken mit Verlauf + Rahmen
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // Hintergrund-„Thermometer“
                Canvas(modifier = Modifier
                    .width(54.dp)
                    .fillMaxHeight(0.82f)
                ) {
                    val barWidth = size.width
                    val barHeight = size.height

                    // Rand
                    drawRoundRect(
                        color = Color(0xFF2B2B2B),
                        size = size,
                        style = Stroke(width = 2.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx())
                    )
                    // Füll-Verlauf (unten warm -> oben kühl)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFFE000), // gelb
                                Color(0xFF7CD458), // grün
                                Color(0xFF0AB1A4)  // türkis
                            )
                        ),
                        size = androidx.compose.ui.geometry.Size(barWidth - 4.dp.toPx(), barHeight - 4.dp.toPx()),
                        topLeft = androidx.compose.ui.geometry.Offset(2.dp.toPx(), 2.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                    )
                }

                // Vertikaler Slider (Material3 Slider gedreht)
                // - Wir drehen den Slider um -90°, sodass er vertikal bedienbar ist.
                // - Wert 0 = unten, 100 = oben.
                Slider(
                    value = strength.toFloat(),
                    onValueChange = { v ->
                        strength = v.toInt().coerceIn(0, 100)
                        // Sofort speichern, damit andere Komponenten (Detector) live reagieren
                        scope.launch { SettingsPreferenceHelper.setShakeStrength(appCtx, strength) }
                    },
                    valueRange = 0f..100f,
                    steps = 99,
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent,
                        thumbColor = Color.White
                    ),
                    modifier = Modifier
                        .height(54.dp)         // nach Rotation wird das zur Breite
                        .fillMaxHeight(0.82f)  // nach Rotation wird das zur Höhe
                        .rotate(-90f)          // macht aus horizontal → vertikal
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
                text = stringResource(R.string.shake_strength_hint), // „Handy schütteln … testen“
                color = Color(0xFF9E9E9E),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
