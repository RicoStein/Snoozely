package com.tigonic.snoozely.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.util.Locale
import com.tigonic.snoozely.R
import androidx.compose.ui.res.stringResource
import com.tigonic.snoozely.ui.theme.LocalExtraColors

@Composable
fun TimerCenterText(
    minutes: Int,
    seconds: Int = 0,
    showLabel: Boolean = true
) {
    val extra = LocalExtraColors.current
    val timeText = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics { contentDescription = timeText }
    ) {
        Text(
            text = timeText,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.displayLarge,
            color = extra.heading, // markante Akzentfarbe aus Theme
            fontFamily = FontFamily.Monospace
        )
        if (showLabel) {
            Text(
                text = stringResource(R.string.minutes),
                fontWeight = FontWeight.Normal,
                color = extra.infoText, // dezente Infofarbe aus Theme
                style = MaterialTheme.typography.titleMedium,
                letterSpacing = 2.sp
            )
        }
    }
}

