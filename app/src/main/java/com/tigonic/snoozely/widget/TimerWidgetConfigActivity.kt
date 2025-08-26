package com.tigonic.snoozely.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
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
import androidx.lifecycle.lifecycleScope
import com.tigonic.snoozely.MainActivity
import com.tigonic.snoozely.R
import com.tigonic.snoozely.ui.components.TimerCenterText
import com.tigonic.snoozely.ui.components.WheelSlider
import com.tigonic.snoozely.ui.theme.*
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "WidgetConfig"

class TimerWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Activity started.")

        // Standardmäßig auf "Abgebrochen" setzen, falls etwas schiefgeht
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Log.e(TAG, "onCreate: Invalid appWidgetId. Finishing activity.")
            finish()
            return
        }

        // --- START: PREMIUM-PRÜFUNG ---
        lifecycleScope.launch {
            val isPremium = SettingsPreferenceHelper.getPremiumActive(applicationContext).first()
            if (!isPremium) {
                Log.d(TAG, "Premium not active. Redirecting to MainActivity with Paywall.")
                // App starten und Paywall zeigen
                val intent = Intent(applicationContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("showPaywall", true)
                }
                startActivity(intent)

                // Informieren, warum das Widget nicht hinzugefügt wurde
                Toast.makeText(applicationContext, getString(R.string.premium_feature_widget), Toast.LENGTH_LONG).show()

                // Konfigurations-Activity beenden
                finish()
                return@launch
            }

            // Wenn Premium aktiv ist, fahre normal fort
            setupContent()
        }
        // --- ENDE: PREMIUM-PRÜFUNG ---
    }

    private fun setupContent() {
        if (ThemeRegistry.themes.isEmpty()) {
            registerDefaultThemes()
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
                            saveWidgetDuration(applicationContext, appWidgetId, minutes)
                            Log.d(TAG, "onSave: Duration saved successfully.")

                            val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
                            TimerQuickStartWidgetProvider.updateAppWidget(applicationContext, appWidgetManager, appWidgetId)

                            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            setResult(Activity.RESULT_OK, resultValue)
                            finishAndRemoveTask()
                        }
                    },
                    onCancel = {
                        Log.d(TAG, "onCancel: User clicked Cancel for widget $appWidgetId.")
                        setResult(Activity.RESULT_CANCELED)
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
