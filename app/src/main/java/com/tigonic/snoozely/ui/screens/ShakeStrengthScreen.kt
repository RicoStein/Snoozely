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

    // Physik-Konsistenz: identisch zum Detector
    val MIN_THR = 8f
    val MAX_THR = 20f
    val MAX_MAG = 25f
    fun mapPercentToThreshold(p: Int): Float = MIN_THR + (p / 100f) * (MAX_THR - MIN_THR)

    // Settings
    val mode by SettingsPreferenceHelper.getShakeSoundMode(appCtx).collectAsState(initial = "tone")
    val ringtoneUriStr by SettingsPreferenceHelper.getShakeRingtone(appCtx).collectAsState(initial = "")
    val strengthSaved by SettingsPreferenceHelper.getShakeStrength(appCtx).collectAsState(initial = 50)
    var strength by remember(strengthSaved) { mutableStateOf(strengthSaved.coerceIn(0, 100)) }

    // Audio-Permission (nur für Custom-Ringtone)
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
        } else requestAudioPermLauncher.launch(audioPermission)
    }

    // Ton/Vibe
    val modeState by rememberUpdatedState(mode)
    val ringUriState by rememberUpdatedState(ringtoneUriStr)
    var preview: Ringtone? by remember { mutableStateOf(null) }

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
        if (isCustomTone) ensureAudioPermission { play() } else play()
    }
    fun pulseVibrate() {
        if (modeState != "vibrate") return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ctx.getSystemService(VibratorManager::class.java)
                    ?.defaultVibrator
                    ?.vibrate(VibrationEffect.createOneShot(600L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Vibrator::class.java)?.vibrate(600L)
            }
        } catch (_: Throwable) {}
    }

    val detector = remember {
        ShakeDetector(
            context = appCtx,
            strengthPercent = strength,
            onShake = { /* im Screen nichts triggern! */ },
            cooldownMs = 800L,
            hitsToTrigger = 1
        )
    }

    // Live-Magnitude (absolut, 0..1), ideal fürs UI
    val magnitudeNorm by detector.magnitudeNorm.collectAsState(0f)
    val uiMagnitude by animateFloatAsState(
        targetValue = magnitudeNorm,
        animationSpec = spring(stiffness = 400f),
        label = "uiMagnitude"
    )

    // Strength live in den Detector
    LaunchedEffect(strength) { detector.updateStrength(strength) }

    DisposableEffect(Unit) {
        detector.start()
        onDispose {
            runCatching { preview?.stop() }
            detector.stop()
        }
    }


    // Hysterese: erst wieder „scharf“, wenn wir deutlich unter die Linie zurückgehen
    var armed by remember { mutableStateOf(true) }

// Optional: Minimalabstand zwischen zwei Auslösungen (ms), schützt vor Rattern
    var lastFiredAt by remember { mutableStateOf(0L) }
    val minIntervalMs = 500L

    LaunchedEffect(uiMagnitude, strength) {
        val thr = strength / 100f
        val rearmBelow = (thr - 0.08f).coerceAtLeast(0f) // 8%-Punkte unter der Linie re-armen

        val now = System.currentTimeMillis()

        // Re-Arm: erst wenn wir deutlich unter die Linie zurückgehen
        if (!armed && uiMagnitude < rearmBelow) {
            armed = true
        }

        // Fire: exakt beim Erreichen/Überschreiten der Linie (und nicht zu schnell hintereinander)
        if (armed && uiMagnitude >= thr && (now - lastFiredAt) >= minIntervalMs) {
            if (mode == "vibrate") pulseVibrate() else playTestTone()
            lastFiredAt = now
            armed = false
        }
    }



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
            val barWidth = 54.dp
            val barCorner = 10.dp
            val innerPad = 2.dp

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                var innerTop by remember { mutableStateOf(0f) }
                var innerHeight by remember { mutableStateOf(0f) }

                Canvas(
                    modifier = Modifier
                        .width(barWidth)
                        .fillMaxHeight(0.82f)
                        .pointerInput(Unit) {
                            detectTapGestures { pos ->
                                if (innerHeight > 0f) {
                                    val clampedY = pos.y.coerceIn(innerTop, innerTop + innerHeight)
                                    val norm = 1f - ((clampedY - innerTop) / innerHeight) // 0..1
                                    // Norm (0..1) -> absolute m/s² -> Prozent 0..100 (8..20 m/s²)
                                    val pct = (norm * 100f).roundToInt().coerceIn(0, 100)
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

                    // Innenfläche
                    val innerSize = Size(W - innerPad.toPx() * 2, H - innerPad.toPx() * 2)
                    val innerTopLeft = Offset(innerPad.toPx(), innerPad.toPx())

                    innerTop = innerTopLeft.y
                    innerHeight = innerSize.height

                    // Hintergrund
                    drawRoundRect(
                        color = Color(0xFF2A2A2A),
                        size = innerSize,
                        topLeft = innerTopLeft,
                        cornerRadius = CornerRadius((barCorner - 2.dp).toPx())
                    )

                    // Füllung = absolute Magnitude (0..1)
                    if (uiMagnitude > 0.002f) {
                        val fillHeight = innerSize.height * uiMagnitude.coerceIn(0f, 1f)
                        val top = innerTopLeft.y + innerSize.height - fillHeight
                        val brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFFE000), Color(0xFF7CD458), Color(0xFF0AB1A4)
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

                    // Linie bei echter Schwelle (threshold/MAX_MAG)
                    val y = innerTopLeft.y + innerSize.height * (1f - (strength / 100f))
                    drawLine(
                        color = Color(0x88FFFFFF),
                        start = Offset(innerTopLeft.x - 6.dp.toPx(), y),
                        end = Offset(innerTopLeft.x + innerSize.width + 6.dp.toPx(), y),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(text = stringResource(R.string.shake_strength), color = Color.White,
                style = MaterialTheme.typography.titleMedium)
            Text(text = "${strength}%", color = Color(0xFFBDBDBD),
                style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(8.dp))
            Text(text = stringResource(R.string.shake_strength_hint), color = Color(0xFF9E9E9E),
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }
    }
}
