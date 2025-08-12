package com.tigonic.snoozely

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tigonic.snoozely.ui.screens.HomeScreen
import com.tigonic.snoozely.ui.screens.SettingsScreen
import com.tigonic.snoozely.ui.theme.SnoozelyTheme
import com.tigonic.snoozely.util.TimerPreferenceHelper
import com.tigonic.snoozely.service.TimerEngineService
import com.tigonic.snoozely.service.TimerContracts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.tigonic.snoozely.ui.screens.ShakeExtendSettingsScreen
import com.tigonic.snoozely.ui.screens.ShakeStrengthScreen
import com.tigonic.snoozely.ui.NotificationSettingsScreen
import com.tigonic.snoozely.ui.theme.registerDefaultThemes
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Engine ggf. wieder starten (wenn Timer schon lief)
        lifecycleScope.launch { maybeStartEngineIfTimerRunning() }

        // POST_NOTIFICATIONS anfragen (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        // Theme-Presets registrieren (einmalig)
        registerDefaultThemes()

        setContent {
            // Theme-Settings REAKTIV lesen → UI wechselt sofort beim Umschalten
            val themeId by SettingsPreferenceHelper.getThemeMode(this).collectAsState(initial = "system")
            val dynamic by SettingsPreferenceHelper.getThemeDynamic(this).collectAsState(initial = true)

            SnoozelyTheme(themeId = themeId, dynamicColor = dynamic) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        enterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { fullWidth -> fullWidth },
                                animationSpec = tween(300)
                            )
                        },
                        exitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { fullWidth -> -fullWidth },
                                animationSpec = tween(200)
                            )
                        },
                        popEnterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { fullWidth -> -fullWidth },
                                animationSpec = tween(300)
                            )
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { fullWidth -> fullWidth },
                                animationSpec = tween(200)
                            )
                        }
                    ) {
                        composable("home") {
                            HomeScreen(onSettingsClick = { navController.navigate("settings") })
                        }
                        composable("settings") {
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                onNavigateShakeSettings = { navController.navigate("settings/shake") },
                                onNavigateNotificationSettings = { navController.navigate("settings/notifications") }
                            )
                        }
                        composable("settings/notifications") {
                            NotificationSettingsScreen(onBack = { navController.popBackStack() })
                        }
                        composable("settings/shake") {
                            ShakeExtendSettingsScreen(
                                onBack = { navController.popBackStack() },
                                onNavigateShakeStrength = { navController.navigate("settings/shake/strength") },
                                onPickSound = { /* handled inside screen */ }
                            )
                        }
                        composable("settings/shake/strength") {
                            ShakeStrengthScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }

    private suspend fun maybeStartEngineIfTimerRunning() {
        val ctx = applicationContext
        val running = TimerPreferenceHelper.getTimerRunning(ctx).first()
        val start = TimerPreferenceHelper.getTimerStartTime(ctx).first()
        val minutes = TimerPreferenceHelper.getTimer(ctx).first()

        if (running && start > 0L && minutes > 0) {
            val intent = Intent(ctx, TimerEngineService::class.java).apply {
                action = TimerContracts.ACTION_START
            }
            ctx.startForegroundServiceCompat(intent)
        }
    }

    /** Startet nur für ACTION_START/STOP/EXTEND als Foreground-Service (O+), sonst normal. */
    /** Startet nur als Foreground-Service, wenn Progress-Notifications erlaubt sind. */
    fun Context.startForegroundServiceCompat(intent: Intent) {
        val action = intent.action
        val baseShould =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    (action == TimerContracts.ACTION_START ||
                            action == TimerContracts.ACTION_STOP  ||
                            action == TimerContracts.ACTION_EXTEND)

        if (!baseShould) {
            startService(intent); return
        }

        val notificationsEnabled = kotlinx.coroutines.runBlocking {
            SettingsPreferenceHelper.getNotificationEnabled(this@startForegroundServiceCompat).first()
        }
        val showProgress = kotlinx.coroutines.runBlocking {
            SettingsPreferenceHelper.getShowProgressNotification(this@startForegroundServiceCompat).first()
        }

        if (notificationsEnabled && showProgress) startForegroundService(intent) else startService(intent)
    }
}
