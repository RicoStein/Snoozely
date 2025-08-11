package com.tigonic.snoozely.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import com.tigonic.snoozely.R
import com.tigonic.snoozely.util.SettingsPreferenceHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShakeStrengthScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val strength by SettingsPreferenceHelper.getShakeStrength(ctx).collectAsState(initial = 50)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shake_strength)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF101010),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
        containerColor = Color(0xFF101010),
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Visualisierung der Schüttelkraft (Platzhalter)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(width = 48.dp, height = 520.dp)) {
                    drawRect(Color(0x22222222), size = size)
                    // Optional: Live-Schüttelintensität darstellen
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.shake_strength), color = Color.White)
            Text("${strength}%", color = Color.Gray)

            Slider(
                value = strength.toFloat(),
                onValueChange = { v ->
                    scope.launch {
                        SettingsPreferenceHelper.setShakeStrength(ctx, v.toInt())
                    }
                },
                valueRange = 0f..100f,
                steps = 99,
                colors = SliderDefaults.colors(
                    activeTrackColor = Color(0xFF7F7FFF),
                    inactiveTrackColor = Color(0x33444444),
                    thumbColor = Color(0xFF7F7FFF),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
