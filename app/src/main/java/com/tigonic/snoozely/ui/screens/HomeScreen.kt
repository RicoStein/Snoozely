package com.tigonic.snoozely.ui.screens

import android.annotation.SuppressLint
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tigonic.snoozely.R
import com.tigonic.snoozely.ads.HomeBanner
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
import androidx.compose.ui.zIndex

private const val TAG = "HomeScreenAds"
private const val TEST_BANNER = "ca-app-pub-3940256099942544/6300978111"

@SuppressLint("ContextCastToActivity")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onSettingsClick: () -> Unit,
    adsGateIsAllowed: Boolean,
    adsGateIsNonPersonalized: Boolean,
    consentResolved: Boolean,
    consentType: String,
    premium: Boolean,
    onOpenPrivacyOptions: () -> Unit,
    onRequestAdThenStart: (onAfter: () -> Unit) -> Unit
) {
    val appCtx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    val cs = MaterialTheme.colorScheme
    val extra = LocalExtraColors.current

    var showPremiumDialog by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }

    val timerMinutes by TimerPreferenceHelper.getTimer(appCtx).collectAsState(initial = 5)
    val timerStartTime by TimerPreferenceHelper.getTimerStartTime(appCtx).collectAsState(initial = 0L)
    val timerRunning by TimerPreferenceHelper.getTimerRunning(appCtx).collectAsState(initial = false)

    var sliderMinutes by rememberSaveable { mutableStateOf(timerMinutes.coerceAtLeast(1)) }
    var persistJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(timerMinutes, timerRunning) {
        if (!timerRunning) {
            sliderMinutes = timerMinutes.coerceAtLeast(1)
        }
    }

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timerRunning, timerStartTime) {
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
        animationSpec = tween(durationMillis = 300),
        label = "wheelAlpha"
    )
    val wheelScale by animateFloatAsState(
        targetValue = if (timerRunning) 0.93f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "wheelScale"
    )

    fun startTimerNow() {
        Log.d("Timer", "startTimerNow called, sliderMinutes=$sliderMinutes running=$timerRunning")
        scope.launch {
            if (!timerRunning && sliderMinutes > 0) {
                if (timerMinutes != sliderMinutes) {
                    TimerPreferenceHelper.setTimer(appCtx, sliderMinutes)
                }
                val startIntent = Intent(appCtx, TimerEngineService::class.java)
                    .setAction(TimerContracts.ACTION_START)
                    .putExtra(TimerContracts.EXTRA_MINUTES, sliderMinutes)
                appCtx.startForegroundServiceCompat(startIntent)
                TimerPreferenceHelper.startTimer(appCtx, sliderMinutes)
            }
        }
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                                scope.launch {
                                    val current = SettingsPreferenceHelper.getPremiumActive(appCtx).first()
                                    SettingsPreferenceHelper.setPremiumActive(appCtx, !current)
                                    android.widget.Toast
                                        .makeText(appCtx, if (!current) "Premium AN" else "Premium AUS", android.widget.Toast.LENGTH_SHORT)
                                        .show()
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
                                    text = {
                                        Text(
                                            if (premium) stringResource(R.string.premium_user_label)
                                            else stringResource(R.string.premium_activate_q)
                                        )
                                    },
                                    onClick = {
                                        overflowOpen = false
                                        showPremiumDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Datenschutz-Einstellungen") },
                                    onClick = {
                                        overflowOpen = false
                                        onOpenPrivacyOptions()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        // Root-Box ohne innerPadding, damit das Banner nicht hochgeschoben wird
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(cs.background)
        ) {
            // Hauptinhalt: hier innerPadding anwenden (für TopBar etc.)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(24.dp))

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
                                    TimerPreferenceHelper.setTimer(appCtx, coerced)
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
                        if (!timerRunning && sliderMinutes > 0) {
                            onRequestAdThenStart {
                                startTimerNow()
                            }
                        } else if (timerRunning) {
                            scope.launch {
                                val stopIntent = Intent(appCtx, TimerEngineService::class.java)
                                    .setAction(TimerContracts.ACTION_STOP)
                                appCtx.startForegroundServiceCompat(stopIntent)
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

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Banner exakt direkt über der System-Navigation-Bar (ohne zusätzlichen Abstand)
            if (adsGateIsAllowed) {
                // Optional: falls du Safe-Insets trotzdem berücksichtigen willst, nutze sie bewusst:
                // val bottomInset: Dp = with(LocalDensity.current) {
                //     val bottomPx = WindowInsets.safeDrawing.getBottom(this)
                //     (bottomPx / density).dp
                // }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .zIndex(1f)
                ) {
                    HomeBanner(
                        isAdsAllowed = true,
                        adUnitId = TEST_BANNER,
                        nonPersonalized = adsGateIsNonPersonalized
                    )
                }
            }

            if (showPremiumDialog) {
                PremiumPaywallDialog(
                    isPremium = premium,
                    onClose = { showPremiumDialog = false },
                    onPurchase = {
                        // Nur im Nicht-Premium-Dialog erreichbar
                        scope.launch {
                            SettingsPreferenceHelper.setPremiumActive(appCtx, true)
                        }
                        showPremiumDialog = false
                    },
                    onDonate = { amountEur ->
                        // Beispiel: Spenden-Handler – hier könntest du einen Browser öffnen oder In-App-Kauf triggern
                        // val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://deine-spenden-url.tld?amount=$amountEur"))
                        // intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        // appCtx.startActivity(intent)
                        android.widget.Toast
                            .makeText(appCtx, "Danke für deine Unterstützung: ${amountEur}€", android.widget.Toast.LENGTH_SHORT)
                            .show()
                    }
                )
            }
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
