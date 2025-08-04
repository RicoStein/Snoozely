package com.tigonic.snoozely.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tigonic.snoozely.ui.components.WheelSlider

@Composable
fun HomeScreen(
    onSettingsClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    isPlaying: Boolean
) {
    var minutes by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TopBar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SleepTimer",
                    fontSize = MaterialTheme.typography.headlineLarge.fontSize,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Einstellungen",
                        tint = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(40.dp))

            // WheelSlider
            WheelSlider(
                value = minutes,
                onValueChange = { minutes = it },
                modifier = Modifier.padding(vertical = 24.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Play/Pause Button
            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.White, shape = MaterialTheme.shapes.extraLarge)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
    }
}
