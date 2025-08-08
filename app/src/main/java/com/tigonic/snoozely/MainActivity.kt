package com.tigonic.snoozely

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tigonic.snoozely.ui.screens.HomeScreen
import com.tigonic.snoozely.ui.screens.SettingsScreen
import com.tigonic.snoozely.ui.theme.SnoozelyTheme
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.tigonic.snoozely.util.TimerPreferenceHelper
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import com.tigonic.snoozely.service.updateNotification

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnoozelyTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)  // <-- Hintergrund fixen!
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
                            HomeScreen(
                                onSettingsClick = { navController.navigate("settings") }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            val context = applicationContext
            val timerRunning = TimerPreferenceHelper.getTimerRunning(context).first()
            val timerMinutes = TimerPreferenceHelper.getTimer(context).first()
            val timerStartTime = TimerPreferenceHelper.getTimerStartTime(context).first()
            val notificationEnabled = SettingsPreferenceHelper.getNotificationEnabled(context).first()
            if (timerRunning && timerMinutes > 0 && timerStartTime > 0L && notificationEnabled) {
                val now = System.currentTimeMillis()
                val totalMs = timerMinutes * 60_000L
                val elapsedMs = now - timerStartTime
                val remainingMs = (totalMs - elapsedMs).coerceAtLeast(0)
                updateNotification(context, remainingMs, totalMs)
            }
        }
    }
}
