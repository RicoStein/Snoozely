package com.tigonic.snoozely.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import kotlinx.coroutines.launch

private const val TAG = "WidgetConfig"

class TimerWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Activity started.")

        if (ThemeRegistry.themes.isEmpty()) {
            registerDefaultThemes()
        }

        // Standardmäßig auf "Abgebrochen" setzen
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        Log.d(TAG, "onCreate: Received appWidgetId: $appWidgetId")

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Log.e(TAG, "onCreate: Invalid appWidgetId. Finishing activity.")
            finish()
            return
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val themeId by SettingsPreferenceHelper.getThemeMode(this).collectAsState(initial = "system")
            val useDarkFromSettings = when(themeId) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            ConfigTheme(useDark = useDarkFromSettings) {
                val scope = rememberCoroutineScope()

                WidgetWheelConfigScreen(
                    appWidgetId = appWidgetId,
                    onSave = { minutes ->
                        Log.d(TAG, "onSave: User clicked Save for widget $appWidgetId with $minutes minutes.")

                        scope.launch {
                            // 1. Dauer in die synchronen SharedPreferences speichern
                            saveWidgetDuration(applicationContext, appWidgetId, minutes)
                            Log.d(TAG, "onSave: Duration saved successfully via SharedPreferences.")

                            // 2. Manuelles Update des Widgets anstoßen, um die neue Zeit sofort anzuzeigen
                            val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
                            TimerQuickStartWidgetProvider.updateAppWidget(applicationContext, appWidgetManager, appWidgetId)

                            // 3. Das Ergebnis-Intent für den Launcher vorbereiten
                            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            setResult(Activity.RESULT_OK, resultValue)

                            // 4. Activity vollständig beenden und aus den letzten Tasks entfernen
                            finishAndRemoveTask()
                        }
                    },
                    onCancel = {
                        Log.d(TAG, "onCancel: User clicked Cancel for widget $appWidgetId.")
                        setResult(Activity.RESULT_CANCELED)
                        // Activity vollständig beenden und aus den letzten Tasks entfernen
                        finishAndRemoveTask()
                    }
                )
            }
        }
    }
}

@Composable
private fun ConfigTheme(
    useDark: Boolean,
    content: @Composable () -> Unit
) {
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
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !useDark
        }
    }

    CompositionLocalProvider(LocalExtraColors provides extras) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
private fun WidgetWheelConfigScreen(
    appWidgetId: Int,
    onSave: (Int) -> Unit,
    onCancel: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val extra = LocalExtraColors.current
    val ctx = LocalContext.current.applicationContext

    // Lese den zuletzt gespeicherten Wert für dieses Widget als Startwert
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

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.height(320.dp).fillMaxWidth()
        ) {
            WheelSlider(
                value = sliderMinutes,
                onValueChange = { value ->
                    sliderMinutes = value.coerceAtLeast(1)
                },
                minValue = 1
            )
            TimerCenterText(
                minutes = sliderMinutes,
                seconds = 0,
                showLabel = true
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Text(text = stringResource(R.string.cancel), color = cs.onSurface)
            }
            Button(
                onClick = { onSave(sliderMinutes) },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Text(text = stringResource(R.string.save), fontWeight = FontWeight.Bold, color = cs.onPrimary)
            }
        }
    }
}
