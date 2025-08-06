package com.tigonic.snoozely.ui.screens

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import com.tigonic.snoozely.ui.components.TimerCenterText
import com.tigonic.snoozely.ui.components.WheelSlider
import com.tigonic.snoozely.util.TimerPreferenceHelper
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onSettingsClick: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- Settings (für Bildschirm ausschalten etc.) ---
    val screenOff by SettingsPreferenceHelper.getScreenOff(context).collectAsState(initial = false)
    val stopAudio by SettingsPreferenceHelper.getStopAudio(context).collectAsState(initial = true)

    val timerMinutes by TimerPreferenceHelper.getTimer(context).collectAsState(initial = null)
    if (timerMinutes == null) {
        // Ladeanzeige oder gar nichts anzeigen!
        Box(
            Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("Lade...", color = Color.White)
        }
        return
    }
    val timerRunning by TimerPreferenceHelper.getTimerRunning(context).collectAsState(initial = false)
    val timerStartTime by TimerPreferenceHelper.getTimerStartTime(context).collectAsState(initial = 0L)

    // --- SEKUNDEN-TICKER ---
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timerRunning, timerStartTime) {
        if (timerRunning) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    // --- REMAINING: Jetzt als Sekunden berechnen! ---
    val totalSeconds = if (timerRunning && timerStartTime > 0L) {
        val elapsedMillis = now - timerStartTime
        val elapsedSec = (elapsedMillis / 1000).toInt()
        (timerMinutes!! * 60 - elapsedSec).coerceAtLeast(0)
    } else timerMinutes!! * 60

    val remainingMinutes = totalSeconds / 60
    val remainingSeconds = totalSeconds % 60

    val wheelAlpha by animateFloatAsState(
        targetValue = if (timerRunning) 0f else 1f,
        animationSpec = tween(durationMillis = 0),
        label = "wheelAlpha"
    )
    val wheelScale by animateFloatAsState(
        targetValue = if (timerRunning) 0.93f else 1f,
        animationSpec = tween(durationMillis = 0),
        label = "wheelScale"
    )

    // --- AKTIONEN nach Ablauf des Timers ---
    var actionTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(totalSeconds, timerRunning, stopAudio) {
        // Timer ist abgelaufen!
        if (timerRunning && totalSeconds == 0 && !actionTriggered) {
            actionTriggered = true
            if (stopAudio) {
                stopMusicPlayback(context)
            }
            // Bildschirm ausschalten wäre hier ebenfalls möglich, wie zuvor besprochen
        }
        // Reset-Flag, falls Timer gestoppt oder neu gestartet wird:
        if (!timerRunning || totalSeconds > 0) {
            actionTriggered = false
        }
    }

    // --- UI ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
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
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(100.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.height(320.dp)
            ) {
                WheelSlider(
                    value = timerMinutes!!,
                    onValueChange = { value ->
                        if (!timerRunning) {
                            scope.launch { TimerPreferenceHelper.setTimer(context, value) }
                        }
                    },
                    showCenterText = !timerRunning,
                    wheelAlpha = wheelAlpha,
                    wheelScale = wheelScale
                )
                // --- MINUTEN + SEKUNDEN ---
                TimerCenterText(
                    minutes = remainingMinutes,
                    seconds = remainingSeconds
                )
            }

            Spacer(modifier = Modifier.height(60.dp))

            IconButton(
                onClick = {
                    scope.launch {
                        if (!timerRunning && timerMinutes!! > 0) {
                            TimerPreferenceHelper.startTimer(context, timerMinutes!!)
                        } else if (timerRunning) {
                            TimerPreferenceHelper.stopTimer(context, timerMinutes!!)
                        }
                    }
                },
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.White, shape = MaterialTheme.shapes.extraLarge)
            ) {
                Icon(
                    imageVector = if (timerRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (timerRunning) stringResource(R.string.pause) else stringResource(R.string.play),
                    tint = Color.Black,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
    }
}

/**
 * Holt den Audio-Fokus transient und unterbricht damit Spotify, YouTube usw.
 */
fun stopMusicPlayback(context: Context) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    // Der Callback kann leer sein, wichtig ist nur das Requesten!
    audioManager.requestAudioFocus(
        { }, // Kein spezieller Listener nötig
        AudioManager.STREAM_MUSIC,
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
    )
}
