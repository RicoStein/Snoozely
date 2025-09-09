package com.tigonic.snoozely.widget

import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
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
import androidx.compose.foundation.gestures.detectDragGestures

class TimerControlWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish(); return
        }

        lifecycleScope.launch {
            val isPremium = SettingsPreferenceHelper.getPremiumActive(applicationContext).first()
            if (isPremium) { // Paywall nur für Nicht-Premium
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

    @SuppressLint("AutoboxingStateCreation", "DefaultLocale")
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
                val defaultBgAlpha = 0.30f

                // Initialwerte
                val initialMinutes = remember { getWidgetDuration(ctx, appWidgetId, 15) }
                var minutes by remember { mutableIntStateOf(initialMinutes) }

                var bgAlpha by remember {
                    mutableFloatStateOf(getWidgetBgAlpha(ctx, appWidgetId, defaultBgAlpha).coerceIn(0f, 1f))
                }

                // HSV States Hintergrund
                var bgHue by remember { mutableStateOf(210f) }
                var bgSat by remember { mutableStateOf(0.2f) }
                var bgVal by remember { mutableStateOf(1.0f) }

                // HSV States Text
                var txtHue by remember { mutableStateOf(0f) }
                var txtSat by remember { mutableStateOf(0f) }
                var txtVal by remember { mutableStateOf(if (night) 1f else 0f) }
                var txtAlpha by remember { mutableFloatStateOf(1f) }

                // Aktuelle gespeicherte Farben laden und auf HSV/Alpha mappen
                LaunchedEffect(Unit) {
                    val currentBg = Color(getWidgetBgColor(ctx, appWidgetId, defaultBg.toArgb()))
                    val currentTxt = Color(getWidgetTextColor(ctx, appWidgetId, defaultText.toArgb()))
                    val (h1, s1, v1) = rgbToHsv(currentBg)
                    val (h2, s2, v2) = rgbToHsv(currentTxt)
                    bgHue = h1; bgSat = s1; bgVal = v1
                    txtHue = h2; txtSat = s2; txtVal = v2
                    txtAlpha = currentTxt.alpha // vorhandene Text-Transparenz übernehmen
                }

                // Compose-Farben aus HSV
                val bgColor = remember(bgHue, bgSat, bgVal) { hsvToColor(bgHue, bgSat, bgVal) }
                val baseTextColor = remember(txtHue, txtSat, txtVal) { hsvToColor(txtHue, txtSat, txtVal) }
                val textColor = remember(baseTextColor, txtAlpha) { baseTextColor.copy(alpha = txtAlpha.coerceIn(0f, 1f)) }

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
                                FilledTonalButton(
                                    onClick = { setResult(Activity.RESULT_CANCELED); finishAndRemoveTask() },
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape = MaterialTheme.shapes.extraLarge
                                ) { Text(stringResource(R.string.cancel)) }

                                Button(
                                    onClick = {
                                        scope.launch {
                                            saveWidgetDuration(ctx, appWidgetId, minutes)
                                            // Textfarbe inkl. Alpha speichern
                                            val textArgb = textColor.toArgb()
                                            saveWidgetStyle(ctx, appWidgetId, bgColor.toArgb(), bgAlpha, textArgb)
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
                        Spacer(Modifier.height(30.dp))

                        // Hintergrund: Hue+Alpha kombiniert + S/V
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(text = stringResource(R.string.background), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 4.dp))
                            HueAlphaSlider(
                                hue = bgHue,
                                alpha = bgAlpha,
                                onChange = { newHue, newAlpha ->
                                    bgHue = newHue
                                    bgAlpha = newAlpha.coerceIn(0f, 1f)
                                }
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.saturation), style = MaterialTheme.typography.labelLarge)
                                    Slider(value = bgSat, onValueChange = { v -> bgSat = v.coerceIn(0f, 1f) })
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.brightness), style = MaterialTheme.typography.labelLarge)
                                    Slider(value = bgVal, onValueChange = { v -> bgVal = v.coerceIn(0f, 1f) })
                                }
                            }
                        }

                        // Text: identisch aufgebaut (Hue+Alpha kombiniert) + S/V
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = stringResource(R.string.text),
                                style = MaterialTheme.typography.titleMedium,   // <-- hier war der Tippfehler
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            HueAlphaSlider(
                                hue = txtHue,
                                alpha = txtAlpha,
                                onChange = { newHue, newAlpha ->
                                    txtHue = newHue
                                    txtAlpha = newAlpha.coerceIn(0f, 1f)
                                }
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.saturation), style = MaterialTheme.typography.labelLarge)
                                    Slider(value = txtSat, onValueChange = { v -> txtSat = v.coerceIn(0f, 1f) })
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.brightness), style = MaterialTheme.typography.labelLarge)
                                    Slider(value = txtVal, onValueChange = { v -> txtVal = v.coerceIn(0f, 1f) })
                                }
                            }
                        }


                        // Live-Vorschau
                        val previewBg = bgColor.copy(alpha = bgAlpha)
                        val previewText = textColor
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(previewBg)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(painter = painterResource(R.drawable.ic_app_logo), contentDescription = null, tint = previewText)
                                    val previewTextStr = String.format("%02d:%02d", minutes, 0)
                                    Text(text = previewTextStr, color = previewText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Icon(painter = painterResource(R.drawable.ic_minus_24), contentDescription = null, tint = previewText)
                                    Icon(painter = painterResource(R.drawable.ic_play_24), contentDescription = null, tint = previewText)
                                    Icon(painter = painterResource(R.drawable.ic_plus_24), contentDescription = null, tint = previewText)
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
 * Kombinierter Slider:
 * - Horizontal: Hue (0..360°)
 * - Vertikal: Alpha (0..1)
 */
@Composable
private fun HueAlphaSlider(
    hue: Float,
    alpha: Float,
    onChange: (h: Float, a: Float) -> Unit,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(44.dp)
        .clip(RoundedCornerShape(10.dp))
) {
    var size by remember { mutableStateOf(IntSize.Zero) }

    val hueGradient = Brush.horizontalGradient(
        0f to Color(0xFFFF0000),
        0.16f to Color(0xFFFFFF00),
        0.33f to Color(0xFF00FF00),
        0.50f to Color(0xFF00FFFF),
        0.66f to Color(0xFF0000FF),
        0.83f to Color(0xFFFF00FF),
        1f to Color(0xFFFF0000)
    )

    fun updateFromOffset(offset: Offset) {
        if (size.width <= 0 || size.height <= 0) return
        val x = offset.x.coerceIn(0f, size.width.toFloat())
        val y = offset.y.coerceIn(0f, size.height.toFloat())
        val newHue = (x / size.width.toFloat()) * 360f
        val newAlpha = 1f - (y / size.height.toFloat())
        onChange(newHue.coerceIn(0f, 360f), newAlpha.coerceIn(0f, 1f))
    }

    Box(
        modifier = modifier
            .background(hueGradient)
            .onSizeChanged { size = it }
            .pointerInput(size) {
                detectDragGestures(
                    onDragStart = { pos -> updateFromOffset(pos) },
                    onDrag = { change, _ -> updateFromOffset(change.position) }
                )
            }
            .padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        // Marker
        Canvas(modifier = Modifier.fillMaxSize()) {
            val x = (hue.coerceIn(0f, 360f) / 360f) * size.width.toFloat()
            val y = (1f - alpha.coerceIn(0f, 1f)) * size.height.toFloat()
            drawCircle(
                color = Color.Black.copy(alpha = 0.85f),
                radius = 15f,
                center = Offset(x, y),
                style = Stroke(width = 3f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 1f),
                radius = 15f,
                center = Offset(x, y),
                style = Stroke(width = 1.5f)
            )
        }
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
 * HSV<->RGB Hilfsfunktionen.
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
