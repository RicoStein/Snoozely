package com.tigonic.snoozely

import android.content.Intent
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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tigonic.snoozely.service.TimerEngineService
import com.tigonic.snoozely.ui.NotificationSettingsScreen
import com.tigonic.snoozely.ui.components.PremiumPaywallDialog
import com.tigonic.snoozely.ui.screens.HomeScreen
import com.tigonic.snoozely.ui.screens.SettingsScreen
import com.tigonic.snoozely.ui.screens.ShakeExtendSettingsScreen
import com.tigonic.snoozely.ui.screens.ShakeStrengthScreen
import com.tigonic.snoozely.ui.theme.SnoozelyTheme
import com.tigonic.snoozely.ui.theme.registerDefaultThemes
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import com.tigonic.snoozely.util.TimerPreferenceHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import com.tigonic.snoozely.ui.screens.startForegroundServiceCompat

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Theme-Presets registrieren (einmalig)
        registerDefaultThemes()

        lifecycleScope.launch { maybeStartEngineIfTimerRunning() }

        val initialShowPaywall = intent?.getBooleanExtra("showPaywall", false) == true

        setContent {
            val themeId by SettingsPreferenceHelper.getThemeMode(this).collectAsState(initial = "system")
            val dynamic by SettingsPreferenceHelper.getThemeDynamic(this).collectAsState(initial = true)

            SnoozelyTheme(themeId = themeId, dynamicColor = dynamic) {

                var showPaywall by remember { mutableStateOf(initialShowPaywall) }

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

                    if (showPaywall) {
                        PremiumPaywallDialog(
                            onClose = {
                                showPaywall = false
                                // Nutzer bleibt in der App; Home -> Homescreen
                            },
                            onPurchase = {
                                lifecycleScope.launch {
                                    SettingsPreferenceHelper.setPremiumActive(applicationContext, true)
                                }
                                showPaywall = false
                            }
                        )
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
            val intent = Intent(ctx, TimerEngineService::class.java)
            ctx.startForegroundServiceCompat(intent)
        }
    }
}
