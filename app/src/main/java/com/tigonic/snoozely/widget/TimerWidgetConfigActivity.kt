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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    private var finishedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Wichtig: Sicherstellen, dass die Themes registriert sind.
        // In der MainActivity passiert das bereits. Hier als Fallback, falls die
        // Activity "kalt" gestartet wird, ohne dass die App je lief.
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

        val (prefersDarkOrNull, _) = readThemeSettings()

        setContent {
            // Das neue, robuste ConfigTheme verwenden, das auch ExtraColors korrekt setzt.
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

                when {
                    loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
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
                                finishedOnce = true
                                finish()
                            },
                            onCancel = {
                                setResult(Activity.RESULT_CANCELED)
                                finishedOnce = true
                                finish()
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
                                finishedOnce = true
                                finish()
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

/**
 * Ein robustes, lokales Theme, das sowohl MaterialTheme.colorScheme als auch
 * die custom LocalExtraColors korrekt für den Dark/Light-Mode bereitstellt.
 * Es liest die Theme-Spezifikationen aus der ThemeRegistry.
 */
@Composable
private fun ConfigTheme(
    prefersDark: Boolean?, // true = dark, false = light, null = system
    content: @Composable () -> Unit
) {
    // 1. Bestimme, ob Dark Mode aktiv ist (System-Einstellung als Fallback)
    val useDark = prefersDark ?: isSystemInDarkTheme()
    val themeId = if (useDark) "dark" else "light"

    // 2. Lade die passende Theme-Spezifikation aus der Registry
    val spec = ThemeRegistry.byId(themeId)

    // 3. Wähle das korrekte ColorScheme und die ExtraColors aus der Spezifikation
    // Fallback auf Standard M3-Themes, falls die Registry leer sein sollte.
    val colorScheme = when {
        spec != null && useDark -> spec.dark ?: darkColorScheme()
        spec != null && !useDark -> spec.light ?: lightColorScheme()
        useDark -> darkColorScheme()
        else -> lightColorScheme()
    }

    val extras = when {
        spec != null && useDark -> spec.extraDark
        spec != null && !useDark -> spec.extraLight
        // Absoluter Notfall-Fallback, falls Registry leer ist -> nimm die Default-Werte
        else -> LocalExtraColors.current
    }

    // 4. Stelle beide Farb-Sets für den Content bereit
    CompositionLocalProvider(LocalExtraColors provides extras) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography, // Dein Typography-Objekt aus ThemeSystem.kt
            content = content
        )
    }
}


// Die folgenden Composables bleiben unverändert, da sie ihre Farben nun
// korrekt aus dem bereitgestellten LocalExtraColors.current und MaterialTheme.colorScheme beziehen.

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .padding(horizontal = 20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.app_name),
                fontSize = MaterialTheme.typography.headlineLarge.fontSize,
                color = extra.menu,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.widget_config_title),
                style = MaterialTheme.typography.titleMedium,
                color = cs.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 6.dp),
                textAlign = TextAlign.Center
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(top = 100.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.height(340.dp).fillMaxWidth()
            ) {
                // WheelSlider und TimerCenterText nutzen jetzt die korrekten Farben aus dem Theme
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
}
