package com.tigonic.snoozely

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.navigation.compose.*
import com.tigonic.snoozely.ui.screens.HomeScreen
import com.tigonic.snoozely.ui.theme.SnoozelyTheme
import com.tigonic.snoozely.ui.screens.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnoozelyTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        var isPlaying by remember { mutableStateOf(false) }
                        HomeScreen(
                            onSettingsClick = { navController.navigate("settings") },
                            onPlayPauseClick = { isPlaying = !isPlaying },
                            isPlaying = isPlaying
                        )
                    }
                    composable("settings") {
                        // Hier wird NUR der importierte SettingsScreen benutzt!
                        SettingsScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
