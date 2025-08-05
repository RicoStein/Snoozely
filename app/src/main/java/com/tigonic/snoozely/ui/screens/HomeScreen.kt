package com.tigonic.snoozely.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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

    val minutesFromStorage by TimerPreferenceHelper.getTimer(context).collectAsState(initial = null)
    var initialMinutes by remember { mutableStateOf(0) }
    var isInitialized by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var runningMinutes by remember { mutableStateOf(0) }

    LaunchedEffect(minutesFromStorage) {
        if (minutesFromStorage != null && !isInitialized) {
            initialMinutes = minutesFromStorage!!
            runningMinutes = minutesFromStorage!!
            isInitialized = true
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            var remaining = initialMinutes
            runningMinutes = remaining
            while (remaining > 0 && isPlaying) {
                delay(1000)
                remaining--
                runningMinutes = remaining
            }
            if (remaining == 0) {
                isPlaying = false
                runningMinutes = initialMinutes
            }
        } else {
            runningMinutes = initialMinutes
        }
    }

    if (!isInitialized) {
        Box(
            Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) { Text("Lade...", color = Color.White) }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Timerblock/Fading außerhalb der Column!
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 96.dp) // evtl. anpassen!
                .height(300.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = !isPlaying,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                WheelSlider(
                    value = initialMinutes,
                    onValueChange = { value ->
                        initialMinutes = value
                        runningMinutes = value
                        scope.launch { TimerPreferenceHelper.setTimer(context, value) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    showCenterText = true
                )
            }
            if (isPlaying) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = runningMinutes.toString(),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.displayLarge
                    )
                    Text(
                        text = stringResource(R.string.minutes),
                        color = Color(0xAAFFFFFF),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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

            Spacer(modifier = Modifier.height(340.dp)) // genug Platz für TimerBlock oben!

            IconButton(
                onClick = {
                    if (!isPlaying && initialMinutes > 0) {
                        isPlaying = true
                    } else if (isPlaying) {
                        isPlaying = false
                    }
                },
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
