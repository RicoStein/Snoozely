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
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
import com.tigonic.snoozely.ui.theme.LocalExtraColors // ← für shakeGradient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShakeStrengthScreen(onBack: () -> Unit) {
    val appCtx = LocalContext.current.applicationContext
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val cs = MaterialTheme.colorScheme
    val extra = LocalExtraColors.current

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
        if (ringUriState.isBlank()) return // Silent by default

        runCatching {
            preview?.stop()
            val uri = Uri.parse(ringUriState)
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
    fun pulseVibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(VibratorManager::class.java)
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(600L, VibrationEffect.DEFAULT_AMPLITUDE))
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
            onShake = { },
            cooldownMs = 800L,
            hitsToTrigger = 1
        )
    }

    val magnitudeNorm by detector.magnitudeNorm.collectAsState(0f)
    val uiMagnitude by animateFloatAsState(
        targetValue = magnitudeNorm,
        animationSpec = spring(stiffness = 400f),
        label = "uiMagnitude"
    )

    LaunchedEffect(strength) { detector.updateStrength(strength) }

    DisposableEffect(Unit) {
        detector.start()
        onDispose {
            runCatching { preview?.stop() }
            detector.stop()
        }
    }

    // Schwellen-Logik (unverändert)
    var armed by remember { mutableStateOf(true) }
    var lastFiredAt by remember { mutableStateOf(0L) }
    val minIntervalMs = 500L
    LaunchedEffect(uiMagnitude, strength, modeState, ringUriState) {
        val thr = strength / 100f
        val rearmBelow = (thr - 0.08f).coerceAtLeast(0f)
        val now = System.currentTimeMillis()

        if (!armed && uiMagnitude < rearmBelow) {
            armed = true
        }

        if (armed && uiMagnitude >= thr && (now - lastFiredAt) >= minIntervalMs) {
            when (modeState) {
                "vibrate" -> {
                    // einmal kurz vibrieren
                    pulseVibrate()
                }
                "tone" -> {
                    // Nur spielen, wenn ein Ton gesetzt ist (Silent by default)
                    if (ringUriState.isNotBlank()) {
                        ensureAudioPermission { playTestTone() }
                    }
                }
                else -> {
                    // "silent" -> nichts
                }
            }
            lastFiredAt = now
            armed = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shake_strength), color = cs.onPrimaryContainer) },
                navigationIcon = {
                    IconButton(onClick = { runCatching { preview?.stop() }; onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = cs.onPrimaryContainer)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.background,
                    titleContentColor = cs.onPrimaryContainer,
                    navigationIconContentColor = cs.onPrimaryContainer
                )
            )
        },
        containerColor = cs.background
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
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
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

                    // Rahmen aus Theme
                    drawRoundRect(
                        color = cs.outlineVariant,
                        size = size,
                        style = Stroke(width = 2.dp.toPx()),
                        cornerRadius = CornerRadius(barCorner.toPx())
                    )

                    // Innenfläche aus Theme
                    val innerSize = Size(W - innerPad.toPx() * 2, H - innerPad.toPx() * 2)
                    val innerTopLeft = Offset(innerPad.toPx(), innerPad.toPx())

                    innerTop = innerTopLeft.y
                    innerHeight = innerSize.height

                    drawRoundRect(
                        color = cs.surfaceVariant,
                        size = innerSize,
                        topLeft = innerTopLeft,
                        cornerRadius = CornerRadius((barCorner - 2.dp).toPx())
                    )

                    // Füllung aus extra.shakeGradient
                    if (uiMagnitude > 0.002f) {
                        val fillHeight = innerSize.height * uiMagnitude.coerceIn(0f, 1f)
                        val top = innerTopLeft.y + innerSize.height - fillHeight
                        val brush = Brush.verticalGradient(
                            colors = extra.shakeGradient,
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

                    // Linie bei Schwelle
                    val y = innerTopLeft.y + innerSize.height * (1f - (strength / 100f))
                    drawLine(
                        color = cs.onSurface.copy(alpha = 0.5f),
                        start = Offset(innerTopLeft.x - 6.dp.toPx(), y),
                        end = Offset(innerTopLeft.x + innerSize.width + 6.dp.toPx(), y),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(stringResource(R.string.shake_strength), color = cs.onBackground, style = MaterialTheme.typography.titleMedium)
            Text("${strength}%", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.shake_strength_hint), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }
    }
}

