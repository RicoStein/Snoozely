package com.tigonic.snoozely.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tigonic.snoozely.R
import com.tigonic.snoozely.service.TimerContracts
import com.tigonic.snoozely.service.TimerEngineService
import com.tigonic.snoozely.ui.components.PremiumPaywallDialog
import com.tigonic.snoozely.ui.components.TimerCenterText
import com.tigonic.snoozely.ui.components.WheelSlider
import com.tigonic.snoozely.ui.theme.LocalExtraColors
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import com.tigonic.snoozely.util.TimerPreferenceHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "HomeScreenDebug"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onSettingsClick: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    // Theme-System
    val cs = MaterialTheme.colorScheme
    val extra = LocalExtraColors.current

    // Premium Dialog State
    var showPremiumDialog by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }

    // Live-States aus DataStore
    val timerMinutes by TimerPreferenceHelper.getTimer(context).collectAsState(initial = 5)
    val timerStartTime by TimerPreferenceHelper.getTimerStartTime(context).collectAsState(initial = 0L)
    val timerRunning by TimerPreferenceHelper.getTimerRunning(context).collectAsState(initial = false)

    // Eigener UI-State für Slider + Debounce-Job
    var sliderMinutes by rememberSaveable { mutableStateOf(timerMinutes.coerceAtLeast(1)) }
    var persistJob by remember { mutableStateOf<Job?>(null) }

    // Slider nur synchronisieren, wenn kein Timer läuft
    LaunchedEffect(timerMinutes, timerRunning) {
        if (!timerRunning) {
            sliderMinutes = timerMinutes.coerceAtLeast(1)
        }
    }

    // Sekundenticker nur für die UI-Anzeige
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timerRunning, timerStartTime) {
        Log.d(TAG, "UI tick start: running=$timerRunning, startTime=$timerStartTime")
        if (timerRunning && timerStartTime > 0L) {
            while (isActive) {
                now = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    val startTimeValid = timerRunning && timerStartTime > 0L
    val elapsedMillis: Long = if (startTimeValid) now - timerStartTime else 0L
    val elapsedSec: Int = (elapsedMillis / 1000L).toInt()

    val totalSeconds: Int = when {
        !timerRunning -> sliderMinutes * 60
        !startTimeValid -> timerMinutes * 60
        elapsedSec < 0 -> timerMinutes * 60
        else -> (timerMinutes * 60 - elapsedSec).coerceAtLeast(0)
    }

    val remainingMinutes: Int = totalSeconds / 60
    val remainingSeconds: Int = totalSeconds % 60

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

    // --- UI ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
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
                    color = extra.menu,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = {
                            // Toggle Premium schnell
                            scope.launch {
                                val current = SettingsPreferenceHelper.getPremiumActive(context).first()
                                SettingsPreferenceHelper.setPremiumActive(context, !current)
                                val msg = if (!current) "Premium AN" else "Premium AUS"
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings),
                            tint = extra.menu
                        )
                    }
                    Box {
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.more),
                                tint = extra.menu
                            )
                        }
                        DropdownMenu(
                            expanded = overflowOpen,
                            onDismissRequest = { overflowOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.premium_activate_q)) },
                                onClick = {
                                    overflowOpen = false
                                    showPremiumDialog = true
                                }
                            )
                        }
                    }
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
                    value = sliderMinutes,
                    onValueChange = { value ->
                        if (!timerRunning) {
                            val coerced = value.coerceAtLeast(1)
                            sliderMinutes = coerced
                            persistJob?.cancel()
                            persistJob = scope.launch {
                                delay(250)
                                TimerPreferenceHelper.setTimer(context, coerced)
                            }
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
                        if (!timerRunning && sliderMinutes > 0) {
                            if (timerMinutes != sliderMinutes) {
                                TimerPreferenceHelper.setTimer(context, sliderMinutes)
                            }
                            val startIntent = Intent(
                                context,
                                TimerEngineService::class.java
                            ).setAction(TimerContracts.ACTION_START)
                                .putExtra(TimerContracts.EXTRA_MINUTES, sliderMinutes)

                            context.startForegroundServiceCompat(startIntent)
                            TimerPreferenceHelper.startTimer(context, sliderMinutes)
                        } else if (timerRunning) {
                            val stopIntent = Intent(context, TimerEngineService::class.java)
                                .setAction(TimerContracts.ACTION_STOP)
                            context.startForegroundServiceCompat(stopIntent)
                        }
                    }
                },
                modifier = Modifier
                    .size(72.dp)
                    .background(cs.primary, shape = MaterialTheme.shapes.extraLarge)
            ) {
                Icon(
                    imageVector = if (timerRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (timerRunning) stringResource(R.string.pause) else stringResource(R.string.play),
                    tint = cs.onPrimary,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        // Premium Paywall Dialog
        if (showPremiumDialog) {
            PremiumPaywallDialog(
                onClose = { showPremiumDialog = false },
                onPurchase = {
                    // TODO: replace with Billing-Flow
                    // Platzhalter: Premium lokal setzen
                    scope.launch {
                        SettingsPreferenceHelper.setPremiumActive(context, true)
                    }
                    showPremiumDialog = false
                }
            )
        }
    }
}

/** Startet nur als Foreground-Service, wenn Progress-Notifications erlaubt sind. */
fun Context.startForegroundServiceCompat(intent: Intent) {
    val action = intent.action
    val baseShould =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                (action == TimerContracts.ACTION_START ||
                        action == TimerContracts.ACTION_STOP ||
                        action == TimerContracts.ACTION_EXTEND)

    if (!baseShould) {
        startService(intent)
        return
    }

    val notificationsEnabled = kotlinx.coroutines.runBlocking {
        com.tigonic.snoozely.util.SettingsPreferenceHelper
            .getNotificationEnabled(this@startForegroundServiceCompat).first()
    }
    val showProgress = kotlinx.coroutines.runBlocking {
        com.tigonic.snoozely.util.SettingsPreferenceHelper
            .getShowProgressNotification(this@startForegroundServiceCompat).first()
    }

    if (notificationsEnabled && showProgress) {
        startForegroundService(intent)
    } else {
        startService(intent)
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
