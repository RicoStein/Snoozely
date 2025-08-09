package com.tigonic.snoozely.ui.screens

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
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
import androidx.compose.material3.*
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
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import com.tigonic.snoozely.util.TimerPreferenceHelper
import com.tigonic.snoozely.util.ScreenOffAdminReceiver
import com.tigonic.snoozely.service.updateNotification
import com.tigonic.snoozely.service.stopNotification
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

    // Einstellungen
    val screenOff by SettingsPreferenceHelper.getScreenOff(context).collectAsState(initial = false)
    val stopAudio by SettingsPreferenceHelper.getStopAudio(context).collectAsState(initial = true)
    val fadeOut by SettingsPreferenceHelper.getFadeOut(context).collectAsState(initial = 30f)
    val notificationEnabled by SettingsPreferenceHelper.getNotificationEnabled(context).collectAsState(initial = false)

    // Reminder/Verlängerung
    val showReminderPopup by SettingsPreferenceHelper.getShowReminderPopup(context).collectAsState(initial = false)
    val reminderMinutes by SettingsPreferenceHelper.getReminderMinutes(context).collectAsState(initial = 5)
    val progressExtendMinutes by SettingsPreferenceHelper.getProgressExtendMinutes(context).collectAsState(initial = 5)

    // Timer-States
    val timerMinutes by TimerPreferenceHelper.getTimer(context).collectAsState(initial = 0)
    val timerStartTime by TimerPreferenceHelper.getTimerStartTime(context).collectAsState(initial = 0L)
    val timerRunning by TimerPreferenceHelper.getTimerRunning(context).collectAsState(initial = false)

    // UI States
    var lastUserSetValue by remember { mutableStateOf(timerMinutes) }
    var fadeOutStarted by remember { mutableStateOf(false) }
    var timerWasFinished by remember { mutableStateOf(false) }

    // Ladeanzeige solange Timer nicht geladen
    if (timerMinutes < 1) {
        Box(
            Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.appLoading),
                color = Color.White,
            )
        }
        return
    }

    // Sekundenticker für UI (notifikationsunabhängig)
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timerRunning, timerStartTime) {
        Log.d(TAG, "LaunchedEffect Sekundenticker: timerRunning=$timerRunning, timerStartTime=$timerStartTime")
        if (timerRunning && timerStartTime > 0L) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1000)
                Log.d(TAG, "Sekunde getickt: now=$now")
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

    // --- Notification immer synchronisieren ---
    LaunchedEffect(timerRunning, notificationEnabled, timerMinutes, timerStartTime) {
        Log.d(TAG, "LaunchedEffect Notification: timerRunning=$timerRunning, notificationEnabled=$notificationEnabled, timerMinutes=$timerMinutes, timerStartTime=$timerStartTime")
        if (timerRunning && notificationEnabled && timerStartTime > 0L && timerMinutes > 0) {
            val totalMs = timerMinutes * 60_000L
            val now = System.currentTimeMillis()
            val elapsedMs = now - timerStartTime
            val remainingMs = (totalMs - elapsedMs).coerceAtLeast(0)
            updateNotification(context, remainingMs, totalMs)
        }
    }

    // --- Timer-Ende-Logik ---
    LaunchedEffect(totalSeconds, timerRunning, screenOff, stopAudio, fadeOut) {
        Log.d(TAG, "LaunchedEffect Timer-Ende: totalSeconds=$totalSeconds, timerRunning=$timerRunning")
        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, ScreenOffAdminReceiver::class.java)

        if (timerRunning && stopAudio && !fadeOutStarted && fadeOut > 0 && totalSeconds == fadeOut.toInt()) {
            fadeOutStarted = true
            scope.launch { fadeOutMusic(context, fadeOut.toInt()) }
        }
        if (timerRunning && totalSeconds == 0 && !timerWasFinished) {
            fadeOutStarted = false
            timerWasFinished = true

            if (stopAudio) stopMusicPlayback(context)
            if (screenOff && devicePolicyManager.isAdminActive(adminComponent)) {
                devicePolicyManager.lockNow()
            }
            // Timer zurücksetzen (kurzes Delay für Animation)
            scope.launch {
                delay(500L)
                TimerPreferenceHelper.stopTimer(context, lastUserSetValue)
            }
        }
        if (!timerRunning || totalSeconds > fadeOut.toInt()) {
            fadeOutStarted = false
        }
        if (!timerRunning && timerWasFinished) {
            timerWasFinished = false
        }
    }

    // --- UI wie gehabt ---
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
                            Log.d(TAG, "Play pressed: startTimer mit Minuten = $timerMinutes")
                            TimerPreferenceHelper.startTimer(context, timerMinutes)
                            while (TimerPreferenceHelper.getTimerStartTime(context).first() == 0L) {
                                delay(10)
                            }
                            Log.d(TAG, "timerStartTime jetzt: ${TimerPreferenceHelper.getTimerStartTime(context).first()}")
                            if (notificationEnabled && hasNotificationPermission(context)) {
                                val totalMs = timerMinutes * 60_000L
                                val timerStart = TimerPreferenceHelper.getTimerStartTime(context).first()
                                val elapsedMs = System.currentTimeMillis() - timerStart
                                val remainingMs = (totalMs - elapsedMs).coerceAtLeast(0)
                                updateNotification(context, remainingMs, totalMs)
                            }
                        } else if (timerRunning) {
                            Log.d(TAG, "Stop pressed: stopTimer, Wert = $lastUserSetValue")
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

/**
 * Fadet die Lautstärke über fadeOutSec Sekunden auf 0 herunter und stoppt dann die Musik.
 */
suspend fun fadeOutMusic(context: Context, fadeOutSec: Int) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val stream = AudioManager.STREAM_MUSIC
    val originalVolume = audioManager.getStreamVolume(stream)
    val steps = (fadeOutSec * 10).coerceAtLeast(1)

    for (i in steps downTo 1) {
        val newVolume = (originalVolume * i) / steps
        audioManager.setStreamVolume(stream, newVolume, 0)
        delay(100L)
    }
    // Die Musik wird nach Ablauf des Timers gestoppt!
}

/**
 * Holt den Audio-Fokus transient und unterbricht damit Spotify, YouTube usw.
 */
fun stopMusicPlayback(context: Context) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    audioManager.requestAudioFocus(
        { }, // Kein spezieller Listener nötig
        AudioManager.STREAM_MUSIC,
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
    )
}

fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}
