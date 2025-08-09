package com.tigonic.snoozely.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
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
import com.tigonic.snoozely.service.stopNotification
import com.tigonic.snoozely.service.updateNotification
import com.tigonic.snoozely.ui.components.TimerCenterText
import com.tigonic.snoozely.ui.components.WheelSlider
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import com.tigonic.snoozely.util.TimerPreferenceHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "HomeScreenDebug"

@Composable
fun HomeScreen(
    onSettingsClick: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    // Nur noch das, was der HomeScreen wirklich braucht:
    val notificationEnabled by SettingsPreferenceHelper
        .getNotificationEnabled(context).collectAsState(initial = false)

    // Timer-States (Anzeige + Start/Stop):
    val timerMinutes by TimerPreferenceHelper.getTimer(context).collectAsState(initial = 0)
    val timerStartTime by TimerPreferenceHelper.getTimerStartTime(context).collectAsState(initial = 0L)
    val timerRunning by TimerPreferenceHelper.getTimerRunning(context).collectAsState(initial = false)

    // UI State: letzter vom User gesetzter Wert
    var lastUserSetValue by remember { mutableStateOf(timerMinutes) }

    // Ladeanzeige solange Timer nicht geladen
    if (timerMinutes < 1) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.appLoading),
                color = Color.White,
            )
        }
        return
    }

    // Sekundenticker nur für die UI-Anzeige (keine Logik!)
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timerRunning, timerStartTime) {
        Log.d(TAG, "LaunchedEffect Sekundenticker: timerRunning=$timerRunning, timerStartTime=$timerStartTime")
        if (timerRunning && timerStartTime > 0L) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    val startTimeValid = timerRunning && timerStartTime > 0L
    val elapsedMillis = if (startTimeValid) now - timerStartTime else 0L
    val elapsedSec = (elapsedMillis / 1000).toInt()

    val totalSeconds = when {
        !timerRunning -> timerMinutes * 60
        !startTimeValid -> timerMinutes * 60
        elapsedSec < 0 -> timerMinutes * 60
        else -> (timerMinutes * 60 - elapsedSec).coerceAtLeast(0)
    }

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

    LaunchedEffect(timerMinutes) {
        if (!timerRunning) {
            lastUserSetValue = timerMinutes
        }
    }

    // Foreground-Notification (Fortschrittsanzeige) synchron halten – reine UI Sync, keine Logik
    LaunchedEffect(timerRunning, notificationEnabled, timerMinutes, timerStartTime) {
        Log.d(TAG, "LaunchedEffect Notification: running=$timerRunning, notif=$notificationEnabled, minutes=$timerMinutes, start=$timerStartTime")
        if (timerRunning && notificationEnabled && timerStartTime > 0L && timerMinutes > 0) {
            val totalMs = timerMinutes * 60_000L
            val elapsedMs = System.currentTimeMillis() - timerStartTime
            val remainingMs = (totalMs - elapsedMs).coerceAtLeast(0)
            updateNotification(context, remainingMs, totalMs)
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
                    value = timerMinutes,
                    onValueChange = { value ->
                        if (!timerRunning && value >= 1) {
                            lastUserSetValue = value
                            scope.launch { TimerPreferenceHelper.setTimer(context, value) }
                        }
                    },
                    minValue = 1,
                    showCenterText = !timerRunning,
                    wheelAlpha = wheelAlpha,
                    wheelScale = wheelScale
                )

                TimerCenterText(
                    minutes = remainingMinutes,
                    seconds = remainingSeconds
                )
            }

            Spacer(modifier = Modifier.height(60.dp))

            IconButton(
                onClick = {
                    scope.launch {
                        if (!timerRunning && timerMinutes > 0) {
                            Log.d(TAG, "Play pressed: startTimer = $timerMinutes min")
                            // Start im Datenspeicher markieren
                            TimerPreferenceHelper.startTimer(context, timerMinutes)
                            // warten bis StartTime gesetzt
                            while (TimerPreferenceHelper.getTimerStartTime(context).first() == 0L) {
                                delay(10)
                            }
                            // Fortschritts-Notification starten (Service übernimmt Ticker & Ende)
                            if (notificationEnabled && hasNotificationPermission(context)) {
                                val totalMs = timerMinutes * 60_000L
                                val start = TimerPreferenceHelper.getTimerStartTime(context).first()
                                val elapsedMs = System.currentTimeMillis() - start
                                val remainingMs = (totalMs - elapsedMs).coerceAtLeast(0)
                                updateNotification(context, remainingMs, totalMs)
                            }
                        } else if (timerRunning) {
                            Log.d(TAG, "Pause pressed: stopTimer, restore = $lastUserSetValue")
                            TimerPreferenceHelper.stopTimer(context, lastUserSetValue)
                            stopNotification(context)
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

fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}
