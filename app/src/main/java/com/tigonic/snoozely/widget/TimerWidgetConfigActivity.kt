package com.tigonic.snoozely.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class TimerWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ThemeRegistry.themes.isEmpty()) {
            registerDefaultThemes()
        }

        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            startActivity(Intent(this, com.tigonic.snoozely.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
            finish()
            return
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val (prefersDarkOrNull, _) = readThemeSettings()

        setContent {
            ConfigTheme(prefersDark = prefersDarkOrNull) {
                var loading by remember { mutableStateOf(true) }
                var isPremium by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    isPremium = try {
                        SettingsPreferenceHelper.getPremiumActive(applicationContext).first()
                    } catch (_: Throwable) {
                        false
                    }
                    loading = false
                }

                // Helper-Funktion, um zum Home-Screen zu navigieren und die Activity samt Task zu beenden
                val navigateHomeAndFinishTask: () -> Unit = {
                    val intent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                    // NEU: finishAndRemoveTask() statt finish()
                    // Dies entfernt die Aufgabe aus der "Zuletzt geöffnet"-Liste und signalisiert
                    // Android, dass der Prozess aufgeräumt werden kann.
                    finishAndRemoveTask()
                }

                when {
                    loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.loading),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                    isPremium -> {
                        WidgetWheelConfigScreen(
                            appWidgetId = appWidgetId,
                            onSave = { minutes ->
                                saveWidgetDuration(applicationContext, appWidgetId, minutes)
                                val mgr = AppWidgetManager.getInstance(applicationContext)
                                TimerQuickStartWidgetProvider.updateAppWidget(applicationContext, mgr, appWidgetId)
                                val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                setResult(Activity.RESULT_OK, result)
                                navigateHomeAndFinishTask()
                            },
                            onCancel = {
                                setResult(Activity.RESULT_CANCELED)
                                navigateHomeAndFinishTask()
                            }
                        )
                    }
                    else -> {
                        PremiumRequiredScreen(
                            onOpenApp = {
                                startActivity(
                                    Intent(this@TimerWidgetConfigActivity, com.tigonic.snoozely.MainActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                        putExtra("showPaywall", true)
                                        putExtra("source", "widget_config")
                                    }
                                )
                            },
                            onClose = {
                                setResult(Activity.RESULT_CANCELED)
                                navigateHomeAndFinishTask()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
    }

    private fun readThemeSettings(): Pair<Boolean?, Boolean> {
        val rawMode: Any? = runBlocking {
            try { SettingsPreferenceHelper.getThemeMode(applicationContext).first() } catch (_: Throwable) { null }
        }
        val rawDynamic: Any? = runBlocking {
            try { SettingsPreferenceHelper.getThemeDynamic(applicationContext).first() } catch (_: Throwable) { null }
        }
        val prefersDark: Boolean? = when (rawMode) {
            is Int -> when (rawMode) { 2 -> true; 1 -> false; else -> null }
            is String -> when (rawMode.lowercase()) {
                "2", "dark", "dunkel" -> true
                "1", "light", "hell" -> false
                "0", "system", "auto" -> null
                else -> null
            }
            else -> null
        }
        val dynamic = when (rawDynamic) {
            is Boolean -> rawDynamic
            is Int -> rawDynamic != 0
            is String -> rawDynamic.equals("true", ignoreCase = true) || rawDynamic == "1"
            else -> false
        }
        return prefersDark to dynamic
    }
}

// Die @Composable Funktionen (ConfigTheme, WidgetWheelConfigScreen, PremiumRequiredScreen)
// bleiben exakt wie in der vorherigen Antwort.
@Composable
private fun ConfigTheme(
    prefersDark: Boolean?,
    content: @Composable () -> Unit
) {
    val useDark = prefersDark ?: isSystemInDarkTheme()
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
    val scope = rememberCoroutineScope()

    var minutesState by remember { mutableStateOf<Int>(getWidgetDuration(ctx, appWidgetId, 15)) }
    var sliderMinutes by remember { mutableStateOf<Int>(minutesState.coerceAtLeast(1)) }
    var persistJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(minutesState) {
        sliderMinutes = minutesState.coerceAtLeast(1)
    }

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
        // 1. Header Section
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

        // 2. Wheel Slider Section
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(320.dp)
                .fillMaxWidth()
        ) {
            WheelSlider(
                value = sliderMinutes,
                onValueChange = { value ->
                    val coerced = value.coerceAtLeast(1)
                    sliderMinutes = coerced
                    persistJob?.cancel()
                    persistJob = scope.launch {
                        delay(150)
                        minutesState = coerced
                    }
                },
                minValue = 1,
                showCenterText = true,
                wheelAlpha = 1f,
                wheelScale = 1f,
                enabled = true
            )
            TimerCenterText(
                minutes = sliderMinutes,
                seconds = 0,
                showLabel = true
            )
        }

        // 3. Action Buttons Section
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
                onClick = { onSave(sliderMinutes.coerceAtLeast(1)) },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Text(text = stringResource(R.string.save), fontWeight = FontWeight.Bold, color = cs.onPrimary)
            }
        }
    }
}

@Composable
private fun PremiumRequiredScreen(
    onOpenApp: () -> Unit,
    onClose: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val extra = LocalExtraColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.premium_required),
                style = MaterialTheme.typography.headlineSmall,
                color = extra.menu,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.premium_benefit_widget),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onBackground.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FilledTonalButton(
                    onClick = onClose,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Text(text = stringResource(R.string.cancel), color = cs.onSurface)
                }
                Button(
                    onClick = onOpenApp,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Text(text = stringResource(R.string.premium_title), fontWeight = FontWeight.Bold, color = cs.onPrimary)
                }
            }
        }
    }
}
