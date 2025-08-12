package com.tigonic.snoozely.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tigonic.snoozely.R
import com.tigonic.snoozely.shake.ShakeDetector
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShakeStrengthScreen(onBack: () -> Unit) {
    val appCtx = LocalContext.current.applicationContext
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // Dieselben Werte wie im Detector
    val MIN_THR = 8f
    val MAX_THR = 20f
    val MAX_MAG = 25f

    fun mapPercentToThreshold(p: Int): Float =
        MIN_THR + (p / 100f) * (MAX_THR - MIN_THR)




    // Aktuelle Einstellungen (für Feedback: Ton/Vibration + Ringtone)
    val mode by SettingsPreferenceHelper.getShakeSoundMode(appCtx).collectAsState(initial = "tone") // "tone"|"vibrate"
    val ringtoneUriStr by SettingsPreferenceHelper.getShakeRingtone(appCtx).collectAsState(initial = "")
    val strengthSaved by SettingsPreferenceHelper.getShakeStrength(appCtx).collectAsState(initial = 50)
    var strength by remember(strengthSaved) { mutableStateOf(strengthSaved.coerceIn(0, 100)) }

    // Nur fürs UI (Linie), die eigentliche Erkennung macht der ShakeDetector
    var thresholdNorm by remember(strength) { mutableStateOf(strength / 100f) }

    // Audio-Berechtigung für eigene Töne (Standardton geht auch ohne)
    val audioPermission =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    var audioPermGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, audioPermission) == PackageManager.PERMISSION_GRANTED
                    || (Build.VERSION.SDK_INT >= 33 && audioPermission == Manifest.permission.READ_EXTERNAL_STORAGE)
        )
    }
    val requestAudioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> audioPermGranted = granted }

    fun ensureAudioPermission(onGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 33 && audioPermission == Manifest.permission.READ_EXTERNAL_STORAGE) {
            onGranted(); return
        }
        if (ContextCompat.checkSelfPermission(ctx, audioPermission) == PackageManager.PERMISSION_GRANTED) {
            audioPermGranted = true; onGranted()
        } else {
            requestAudioPermLauncher.launch(audioPermission)
        }
    }

    // State für aktualisierende Werte
    val modeState by rememberUpdatedState(mode)            // "tone" | "vibrate"
    val ringUriState by rememberUpdatedState(ringtoneUriStr)

    var preview: Ringtone? by remember { mutableStateOf(null) }

    // Helfer für Test-Feedback
    fun chosenOrDefaultTone(): Uri =
        if (ringUriState.isNotEmpty()) Uri.parse(ringUriState)
        else Settings.System.DEFAULT_NOTIFICATION_URI

    fun playTestTone() {
        if (modeState != "tone") return

        val isCustomTone = ringUriState.isNotEmpty()
        val play = {
            runCatching {
                preview?.stop()
                val uri = if (isCustomTone) Uri.parse(ringUriState) else Settings.System.DEFAULT_NOTIFICATION_URI
                val r = RingtoneManager.getRingtone(ctx, uri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    r.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
                preview = r
                r.play()
            }
        }

        if (isCustomTone) {
            ensureAudioPermission { play() }   // Nur für eigene Töne
        } else {
            play()                              // System-Standard: keine Permission nötig
        }
    }


    fun pulseVibrate() {
        if (modeState != "vibrate") return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(VibratorManager::class.java)
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(80L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Vibrator::class.java)?.vibrate(80L)
            }
        } catch (_: Throwable) { /* no-op */ }
    }

    // Einheitliches Feedback für onShake
    val onShakeFeedback by rememberUpdatedState<() -> Unit> {
            scope.launch {
                if (modeState == "vibrate") pulseVibrate() else playTestTone()
            }
    }

    // ShakeDetector nutzt die gespeicherte Stärke und liefert auch das Live-Level fürs UI
    val detector = remember {
        ShakeDetector(
            context = appCtx,
            strengthPercent = strength,
            onShake = onShakeFeedback,
            cooldownMs = 800L,          // kürzer für Vorschau
            hitsToTrigger = 1           // <— nur 1 Peak in der Preview
        )
    }


    LaunchedEffect(strength) {
        detector.updateStrength(strength)
    }
    DisposableEffect(Unit) {
        detector.start()
        onDispose { detector.stop() }
    }

    // Live-Level (leicht geglättet fürs UI)
    val rawLevel by detector.level.collectAsState(0f)
    val uiLevel by animateFloatAsState(
        targetValue = rawLevel,
        animationSpec = spring(stiffness = 400f),
        label = "shakeLevel"
    )

    // Absoluter Schwellenwert (m/s²) & als [0..1] dargestellt
    val thrAbs = mapPercentToThreshold(strength)
    val thrNorm = (thrAbs / MAX_MAG).coerceIn(0f, 1f)

// Detector-Level -> absolute Norm zurückrechnen (für den Balken)
    val levelAbs = (thrNorm + rawLevel * (1f - thrNorm)).coerceIn(0f, 1f)

// Optional weichzeichnen, wenn du magst:
    val uiLevelAbs by animateFloatAsState(
        targetValue = levelAbs,
        animationSpec = spring(stiffness = 400f),
        label = "shakeLevelAbs"
    )

    DisposableEffect(Unit) { onDispose { runCatching { preview?.stop() } } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shake_strength)) },
                navigationIcon = {
                    IconButton(onClick = { runCatching { preview?.stop() }; onBack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF101010),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF101010)
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Vertikaler Balken mit ziehbarer Schwellenlinie (nur Visualisierung)
            val barWidth = 54.dp
            val barCorner = 10.dp
            val innerPad = 2.dp

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // Geometrie für Pointer-Mapping
                var innerTop by remember { mutableStateOf(0f) }
                var innerHeight by remember { mutableStateOf(0f) }
                var innerLeft by remember { mutableStateOf(0f) }
                var innerWidth by remember { mutableStateOf(0f) }

                Canvas(
                    modifier = Modifier
                        .width(barWidth)
                        .fillMaxHeight(0.82f)
                        .pointerInput(Unit) {
                            detectTapGestures { pos ->
                                if (innerHeight > 0f) {
                                    val clampedY = pos.y.coerceIn(innerTop, innerTop + innerHeight)
                                    val norm = 1f - ((clampedY - innerTop) / innerHeight)
                                    val pct = (norm * 100f).roundToInt().coerceIn(0, 100)
                                    thresholdNorm = pct / 100f
                                    strength = pct
                                    scope.launch { SettingsPreferenceHelper.setShakeStrength(appCtx, pct) }
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                if (innerHeight > 0f) {
                                    val clampedY = change.position.y.coerceIn(innerTop, innerTop + innerHeight)
                                    val norm = 1f - ((clampedY - innerTop) / innerHeight)
                                    val pct = (norm * 100f).roundToInt().coerceIn(0, 100)
                                    thresholdNorm = pct / 100f
                                    strength = pct
                                    scope.launch { SettingsPreferenceHelper.setShakeStrength(appCtx, pct) }
                                }
                            }
                        }
                ) {
                    val W = size.width
                    val H = size.height

                    // Rahmen
                    drawRoundRect(
                        color = Color(0xFF2B2B2B),
                        size = size,
                        style = Stroke(width = 2.dp.toPx()),
                        cornerRadius = CornerRadius(barCorner.toPx())
                    )

                    // Inneres Rechteck (für Füllung/Background)
                    val innerSize = Size(W - innerPad.toPx() * 2, H - innerPad.toPx() * 2)
                    val innerTopLeft = Offset(innerPad.toPx(), innerPad.toPx())

                    // Geometrie merken
                    innerTop = innerTopLeft.y
                    innerHeight = innerSize.height
                    innerLeft = innerTopLeft.x
                    innerWidth = innerSize.width

                    // Hintergrund (grau)
                    drawRoundRect(
                        color = Color(0xFF2A2A2A),
                        size = innerSize,
                        topLeft = innerTopLeft,
                        cornerRadius = CornerRadius((barCorner - 2.dp).toPx())
                    )

                    // Farbige Füllung nur bei Bewegung
                    if (uiLevel > 0.02f) {
                        val fillHeight = innerSize.height * uiLevel.coerceIn(0f, 1f)
                        val top = innerTopLeft.y + innerSize.height - fillHeight
                        val brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFFE000), // gelb
                                Color(0xFF7CD458), // grün
                                Color(0xFF0AB1A4)  // türkis
                            ),
                            startY = top,
                            endY = innerTopLeft.y + innerSize.height
                        )
                        drawRoundRect(
                            brush = brush,
                            topLeft = Offset(innerTopLeft.x, top),
                            size = Size(innerSize.width, fillHeight),
                            cornerRadius = CornerRadius((barCorner - 2.dp).toPx())
                        )
                    }

                    // Schwellenlinie (ziehbar) – rein visuell anhand thresholdNorm
                    val y = innerTopLeft.y + innerSize.height * (1f - thresholdNorm.coerceIn(0f, 1f))
                    drawLine(
                        color = Color(0x88FFFFFF),
                        start = Offset(innerTopLeft.x - 6.dp.toPx(), y),
                        end = Offset(innerTopLeft.x + innerSize.width + 6.dp.toPx(), y),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Titel + numerische Anzeige
            Text(
                text = stringResource(R.string.shake_strength),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "${(thresholdNorm * 100f).roundToInt()}%",
                color = Color(0xFFBDBDBD),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.shake_strength_hint),
                color = Color(0xFF9E9E9E),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
