package com.tigonic.snoozely.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
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
import com.tigonic.snoozely.ui.theme.LocalExtraColors
import com.tigonic.snoozely.ui.theme.ThemeRegistry
import com.tigonic.snoozely.ui.theme.Typography
import com.tigonic.snoozely.ui.theme.registerDefaultThemes
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Konfigurations-Activity für das 3x1-Steuerungs-Widget.
 *
 * Features:
 * - Timerdauer per Wheel festlegen
 * - Farbstil des Widgets (Hintergrund in RGBA, Text) per HSV-Slider konfigurieren
 * - Speichert Einstellungen widget-spezifisch in SharedPreferences (siehe WidgetPrefs.kt)
 * - Paywall: nur für Premium-Nutzer zugänglich
 */
class TimerControlWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Default = abgebrochen (falls etwas schiefgeht oder Activity vorzeitig beendet wird)
        setResult(Activity.RESULT_CANCELED)

        // Extract AppWidgetId aus Intent
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish(); return
        }

        // Premium prüfen – Konfig nur mit Premium
        lifecycleScope.launch {
            val isPremium = SettingsPreferenceHelper.getPremiumActive(applicationContext).first()
            if (!isPremium) {
                val intent = Intent(applicationContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("showPaywall", true)
                }
                startActivity(intent)
                Toast.makeText(
                    applicationContext,
                    getString(R.string.premium_feature_widget),
                    Toast.LENGTH_LONG
                ).show()
                finish(); return@launch
            }
            setupContent()
        }
    }

    /**
     * Baut die Compose-Oberfläche und stellt Theme/Insets ein.
     */
    private fun setupContent() {
        if (ThemeRegistry.themes.isEmpty()) registerDefaultThemes()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val themeId by SettingsPreferenceHelper.getThemeMode(this).collectAsState(initial = "system")
            val useDark = when (themeId) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }

            ConfigTheme(useDark = useDark) {
                val ctx = applicationContext
                val scope = rememberCoroutineScope()

                val night = useDark
                val defaultBg = if (night) Color.Black else Color.White
                val defaultText = if (night) Color.White else Color.Black
                val defaultAlpha = 0.30f

                // Initialwerte aus Prefs lesen
                val initialMinutes = remember { getWidgetDuration(ctx, appWidgetId, 15) }
                var minutes by remember { mutableStateOf(initialMinutes) }

                var bgAlpha by remember {
                    mutableStateOf(getWidgetBgAlpha(ctx, appWidgetId, defaultAlpha).coerceIn(0f, 1f))
                }

                // Hintergrundfarbe (HSV)
                var bgHue by remember { mutableStateOf(210f) } // 0..360
                var bgSat by remember { mutableStateOf(0.2f) }  // 0..1
                var bgVal by remember { mutableStateOf(1.0f) }  // 0..1

                // Textfarbe (HSV)
                var txtHue by remember { mutableStateOf(0f) }
                var txtSat by remember { mutableStateOf(0f) }
                var txtVal by remember { mutableStateOf(if (night) 1f else 0f) }

                // Vorhandene Farben -> HSV mappen
                LaunchedEffect(Unit) {
                    val currentBg = Color(getWidgetBgColor(ctx, appWidgetId, defaultBg.toArgb()))
                    val currentTxt = Color(getWidgetTextColor(ctx, appWidgetId, defaultText.toArgb()))
                    val (h1, s1, v1) = rgbToHsv(currentBg)
                    val (h2, s2, v2) = rgbToHsv(currentTxt)
                    bgHue = h1; bgSat = s1; bgVal = v1
                    txtHue = h2; txtSat = s2; txtVal = v2
                }

                val bgColor = remember(bgHue, bgSat, bgVal) { hsvToColor(bgHue, bgSat, bgVal) }
                val textColor = remember(txtHue, txtSat, txtVal) { hsvToColor(txtHue, txtSat, txtVal) }

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Abbrechen
                                FilledTonalButton(
                                    onClick = { setResult(Activity.RESULT_CANCELED); finishAndRemoveTask() },
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape = MaterialTheme.shapes.extraLarge
                                ) { Text(stringResource(R.string.cancel)) }

                                // Speichern & Widget aktualisieren
                                Button(
                                    onClick = {
                                        scope.launch {
                                            saveWidgetDuration(ctx, appWidgetId, minutes)
                                            saveWidgetStyle(ctx, appWidgetId, bgColor.toArgb(), bgAlpha, textColor.toArgb())
                                            val awm = AppWidgetManager.getInstance(ctx)
                                            TimerControlWidgetProvider.updateAppWidget(ctx, awm, appWidgetId)
                                            val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                            setResult(Activity.RESULT_OK, result)
                                            finishAndRemoveTask()
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape = MaterialTheme.shapes.extraLarge
                                ) { Text(text = stringResource(R.string.save), fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                ) { inner ->
                    val scroll = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(inner)
                            .padding(horizontal = 20.dp)
                            .verticalScroll(scroll),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(22.dp)
                    ) {
                        // Kopf
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineLarge,
                            color = LocalExtraColors.current.menu,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.widget_config_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )

                        // Wheel: Minuten wählen
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.height(280.dp).fillMaxWidth()
                        ) {
                            WheelSlider(value = minutes, onValueChange = { v -> minutes = max(1, v) }, minValue = 1)
                            TimerCenterText(minutes = minutes, seconds = 0, showLabel = true)
                        }
                        Spacer(Modifier.height(24.dp))

                        // Hintergrundfarbe
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(text = stringResource(R.string.background), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 4.dp))
                            HueSlider(hue = bgHue, onHueChange = { bgHue = it })
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.saturation), style = MaterialTheme.typography.labelLarge)
                                    Slider(value = bgSat, onValueChange = { bgSat = it.coerceIn(0f, 1f) })
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.brightness), style = MaterialTheme.typography.labelLarge)
                                    Slider(value = bgVal, onValueChange = { bgVal = it.coerceIn(0f, 1f) })
                                }
                            }
                        }

                        // Textfarbe
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(text = stringResource(R.string.text), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 4.dp))
                            HueSlider(hue = txtHue, onHueChange = { txtHue = it })
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.saturation), style = MaterialTheme.typography.labelLarge)
                                    Slider(value = txtSat, onValueChange = { txtSat = it.coerceIn(0f, 1f) })
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.brightness), style = MaterialTheme.typography.labelLarge)
                                    Slider(value = txtVal, onValueChange = { txtVal = it.coerceIn(0f, 1f) })
                                }
                            }
                        }

                        // Live-Vorschauzeile des Widgets
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(bgColor.copy(alpha = bgAlpha))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(painter = painterResource(R.drawable.ic_app_logo), contentDescription = null, tint = textColor)
                                    val previewText = String.format("%02d:%02d", minutes, 0)
                                    Text(text = previewText, color = textColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Icon(painter = painterResource(R.drawable.ic_minus_24), contentDescription = null, tint = textColor)
                                    Icon(painter = painterResource(R.drawable.ic_play_24), contentDescription = null, tint = textColor)
                                    Icon(painter = painterResource(R.drawable.ic_plus_24), contentDescription = null, tint = textColor)
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

/**
 * Farbbalken für Hue (0..360°). Slider-Wert wird linear abgebildet.
 */
@Composable
private fun HueSlider(hue: Float, onHueChange: (Float) -> Unit) {
    val hueGradient = Brush.horizontalGradient(
        0f to Color(0xFFFF0000),
        0.16f to Color(0xFFFFFF00),
        0.33f to Color(0xFF00FF00),
        0.50f to Color(0xFF00FFFF),
        0.66f to Color(0xFF0000FF),
        0.83f to Color(0xFFFF00FF),
        1f to Color(0xFFFF0000)
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(hueGradient)
    ) {
        Slider(
            value = hue.coerceIn(0f, 360f) / 360f,
            onValueChange = { onHueChange((it * 360f).coerceIn(0f, 360f)) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Minimaler Themen-Wrapper, der ThemeRegistry respektiert und ExtraColors bereitstellt.
 */
@Composable
private fun ConfigTheme(useDark: Boolean, content: @Composable () -> Unit) {
    val spec = com.tigonic.snoozely.ui.theme.ThemeRegistry.byId(if (useDark) "dark" else "light")
    val cs = when {
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
    CompositionLocalProvider(LocalExtraColors provides extras) {
        MaterialTheme(colorScheme = cs, typography = Typography, content = content)
    }
}

/**
 * HSV->RGB und RGB->HSV Hilfsfunktionen für Compose-Color.
 */
private fun hsvToColor(h: Float, s: Float, v: Float): Color {
    val c = (v * s)
    val x = c * (1 - kotlin.math.abs((h / 60f) % 2 - 1))
    val m = v - c
    val (r1, g1, b1) = when {
        h < 60f -> floatArrayOf(c, x, 0f)
        h < 120f -> floatArrayOf(x, c, 0f)
        h < 180f -> floatArrayOf(0f, c, x)
        h < 240f -> floatArrayOf(0f, x, c)
        h < 300f -> floatArrayOf(x, 0f, c)
        else -> floatArrayOf(c, 0f, x)
    }
    val r = ((r1 + m) * 255).toInt().coerceIn(0, 255)
    val g = ((g1 + m) * 255).toInt().coerceIn(0, 255)
    val b = ((b1 + m) * 255).toInt().coerceIn(0, 255)
    return Color(r, g, b)
}

private fun rgbToHsv(color: Color): Triple<Float, Float, Float> {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val h = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val s = if (max == 0f) 0f else delta / max
    val v = max
    return Triple(h, s, v)
}
