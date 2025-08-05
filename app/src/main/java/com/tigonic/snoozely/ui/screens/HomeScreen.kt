package com.tigonic.snoozely.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tigonic.snoozely.R
import com.tigonic.snoozely.ui.components.WheelSlider
import com.tigonic.snoozely.util.TimerPreferenceHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onSettingsClick: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 1. DataStore liefert initial null, solange noch nicht geladen!
    val minutesFromStorage by TimerPreferenceHelper.getTimer(context).collectAsState(initial = null)

    // 2. Lokale States für Anzeige & Ablauf
    var minutes by remember { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var initialMinutes by remember { mutableStateOf(0) }
    var isInitialized by remember { mutableStateOf(false) }

    // 3. Initialisiere erst, wenn Wert aus Storage geladen ist!
    LaunchedEffect(minutesFromStorage) {
        if (minutesFromStorage != null && !isInitialized) {
            minutes = minutesFromStorage!!
            initialMinutes = minutesFromStorage!!
            isInitialized = true
        }
    }

    // 4. Ladeanzeige, solange DataStore noch lädt!
    if (!isInitialized) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("Lade...", color = Color.White)
        }
        return
    }

    // 5. Timer-Logik: Start/Stop
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            var remainingTime = minutes
            while (remainingTime > 0 && isPlaying) {
                delay(1000)
                remainingTime--
                minutes = remainingTime
            }
            if (remainingTime == 0) {
                isPlaying = false
                minutes = initialMinutes
            }
        } else {
            // Auf gespeicherten Wert zurück (z.B. bei Pause/Stop)
            minutes = initialMinutes
        }
    }

    // 6. UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TopBar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = MaterialTheme.typography.headlineLarge.fontSize,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.settings),
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // WheelSlider (setzt neuen Wert, speichert im DataStore)
            WheelSlider(
                value = minutes,
                onValueChange = {
                    minutes = it
                    initialMinutes = it
                    scope.launch {
                        TimerPreferenceHelper.setTimer(context, it)
                    }
                },
                modifier = Modifier.padding(vertical = 24.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            IconButton(
                onClick = { isPlaying = !isPlaying },
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.White, shape = MaterialTheme.shapes.extraLarge)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                    tint = Color.Black,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
    }
}
