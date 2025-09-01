package com.tigonic.snoozely.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.tigonic.snoozely.R
import com.tigonic.snoozely.ui.components.TimerCenterText
import com.tigonic.snoozely.ui.components.WheelSlider
import com.tigonic.snoozely.ui.theme.*

private const val TAG = "WidgetConfig"

@Composable
private fun ConfigTheme(useDark: Boolean, content: @Composable () -> Unit) {
    val themeId = if (useDark) "dark" else "light"
    val spec = ThemeRegistry.byId(themeId)

    val colorScheme = when {
        spec != null && useDark -> spec.dark ?: darkColorScheme()
        spec != null && !useDark -> spec.light ?: lightColorScheme()
        useDark -> darkColorScheme()
        else -> lightColorScheme()
    }
    val extras = when {
        spec != null && useDark -> spec.extraDark
        spec != null && !useDark -> spec.extraLight
        else -> LocalExtraColors.current
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !useDark
        }
    }

    CompositionLocalProvider(LocalExtraColors provides extras) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}

/**
 * UI: Minuten per Wheel auswählen, Speichern/Abbrechen.
 */
@Composable
private fun WidgetWheelConfigScreen(
    appWidgetId: Int,
    onSave: (Int) -> Unit,
    onCancel: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val extra = LocalExtraColors.current
    val ctx = LocalContext.current.applicationContext

    val initialMinutes = remember { getWidgetDuration(ctx, appWidgetId, 15) }
    var sliderMinutes by remember { mutableStateOf(initialMinutes) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.SpaceAround,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = extra.menu,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.widget_config_title),
                style = MaterialTheme.typography.titleMedium,
                color = cs.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }

        Box(contentAlignment = Alignment.Center, modifier = Modifier.height(320.dp).fillMaxWidth()) {
            WheelSlider(value = sliderMinutes, onValueChange = { sliderMinutes = it.coerceAtLeast(1) }, minValue = 1)
            TimerCenterText(minutes = sliderMinutes, seconds = 0, showLabel = true)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(onClick = onCancel, modifier = Modifier.weight(1f).height(52.dp), shape = MaterialTheme.shapes.extraLarge) {
                Text(text = stringResource(R.string.cancel), color = cs.onSurface)
            }
            Button(onClick = { onSave(sliderMinutes) }, modifier = Modifier.weight(1f).height(52.dp), shape = MaterialTheme.shapes.extraLarge) {
                Text(text = stringResource(R.string.save), fontWeight = FontWeight.Bold, color = cs.onPrimary)
            }
        }
    }
}
