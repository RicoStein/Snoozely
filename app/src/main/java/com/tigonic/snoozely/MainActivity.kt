package com.tigonic.snoozely

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import com.tigonic.snoozely.ui.screens.startForegroundServiceCompat

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Der Zustand für die Paywall wird auf Activity-Ebene gehalten.
    private var showPaywall by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().apply {
            setKeepOnScreenCondition {
                viewModel.isLoading.value
            }
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Verarbeite das Intent, das die Activity gestartet hat.
        // Das 'intent'-Property der Activity ist von Natur aus nullable (Intent?).
        handleIntent(intent)

        registerDefaultThemes()
        lifecycleScope.launch { maybeStartEngineIfTimerRunning() }

        setContent {
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
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(200)) },
                        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) },
                        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(200)) }
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
                            onClose = { showPaywall = false },
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

    /**
     * Korrekte Signatur: 'intent' ist hier NON-NULLABLE (Intent).
     * Diese Methode wird aufgerufen, wenn die Activity bereits läuft und ein neues Intent empfängt.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Das non-nullable Intent wird an die Hilfsfunktion weitergegeben.
        handleIntent(intent)
    }

    /**
     * Hilfsfunktion, die ein NULLABLE Intent verarbeiten kann, da `activity.intent` nullable ist.
     */
    private fun handleIntent(intent: Intent?) {
        // Die sichere ?.let-Prüfung verhindert Fehler, falls das Intent null ist.
        intent?.let {
            if (it.getBooleanExtra("showPaywall", false)) {
                this.showPaywall = true
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
