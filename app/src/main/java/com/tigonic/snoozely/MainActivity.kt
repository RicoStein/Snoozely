package com.tigonic.snoozely

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.tigonic.snoozely.ui.screens.HomeScreen
import com.tigonic.snoozely.ui.theme.SnoozelyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnoozelyTheme {
                // Scaffold für Statusbar/Insets, falls gewünscht
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // HomeScreen mit State und Dummy-Aktionen
                    var isPlaying by remember { mutableStateOf(false) }

                    HomeScreen(
                        onSettingsClick = { /* TODO: Öffne Einstellungen */ },
                        onPlayPauseClick = { isPlaying = !isPlaying },
                        isPlaying = isPlaying
                    )
                }
            }
        }
    }
}
