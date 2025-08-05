package com.tigonic.snoozely.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tigonic.snoozely.R

@Composable
fun TimerCenterText(
    minutes: Int,
    seconds: Int = 0
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = String.format("%02d:%02d", minutes, seconds),
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.displayLarge,
            color = Color.White
        )
        Text(
            text = stringResource(R.string.minutes), // Optional, du kannst das jetzt auch ausblenden!
            fontWeight = FontWeight.Normal,
            color = Color(0xAAFFFFFF),
            style = MaterialTheme.typography.titleMedium,
            letterSpacing = 2.sp
        )
    }
}

