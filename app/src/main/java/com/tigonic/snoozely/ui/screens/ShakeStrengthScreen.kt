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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.tigonic.snoozely.ui.theme.LocalExtraColors
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShakeStrengthScreen(
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()

    val cs = MaterialTheme.colorScheme
    val extra = LocalExtraColors.current

    // Audio-Feedback Einstellungen
    val mode by SettingsPreferenceHelper.getShakeSoundMode(appCtx).collectAsState(initial = "tone")
    val ringtoneUriStr by SettingsPreferenceHelper.getShakeRingtone(appCtx).collectAsState(initial = "")

    // 1) Einmalig initial aus DataStore laden – kein collectAsState(initial=50) für die Stärke!
    var strength by remember { mutableStateOf(50) }
    var hydrated by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val v = SettingsPreferenceHelper.getShakeStrength(appCtx).first() // echter Wert
        strength = v.coerceIn(0, 100)
        hydrated = true
    }

    // 2) Audio-Permission für Testton
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

    // 3) Test-Feedback
    var preview: Ringtone? by remember { mutableStateOf(null) }
    fun playTestTone() {
        if (mode != "tone") return
        if (ringtoneUriStr.isBlank()) return
        runCatching {
            preview?.stop()
            val uri = Uri.parse(ringtoneUriStr)
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
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(VibratorManager::class.java)
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(500L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Vibrator::class.java)?.vibrate(500L)
            }
        }
    }

    // 4) Live-Detector: gleiche Logik wie im Service
    val detector = remember {
        ShakeDetector(
            context = appCtx,
            strengthPercent = strength,
            onShake = {
                when (mode) {
                    "vibrate" -> pulseVibrate()
                    "tone" -> if (audioPermGranted) playTestTone() else ensureAudioPermission { playTestTone() }
                    else -> Unit // silent
                }
            },
            cooldownMs = 600L,
            hitsToTrigger = 1,
            overFactor = 1.0f
        )
    }
    // Detector folgt der UI-Stärke (ohne zurück in den DataStore zu schreiben)
    LaunchedEffect(strength) { if (hydrated) detector.updateStrength(strength) }

    val magnitudeNorm by detector.magnitudeNorm.collectAsState(0f)

    // Lifecycle
    DisposableEffect(Unit) {
        detector.start()
        onDispose {
            runCatching { preview?.stop() }
            detector.stop()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.shake_strength), color = extra.menu) },
                navigationIcon = {
                    IconButton(onClick = {
                        runCatching { preview?.stop() }
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = extra.menu
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.background,
                    scrolledContainerColor = cs.background,
                    titleContentColor = cs.onBackground,
                    navigationIconContentColor = cs.onBackground
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

                fun yToPercent(yPx: Float): Int {
                    if (innerHeight <= 0f) return strength
                    val clampedY = yPx.coerceIn(innerTop, innerTop + innerHeight)
                    val norm = 1f - ((clampedY - innerTop) / innerHeight) // 0..1 (oben=100%)
                    return (norm * 100f).roundToInt().coerceIn(0, 100)
                }

                Canvas(
                    modifier = Modifier
                        .width(barWidth)
                        .fillMaxHeight(0.82f)
                        .pointerInput(hydrated) {
                            // Interaktion erst wenn geladen
                            if (!hydrated) return@pointerInput
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                // Initial setzen + speichern
                                val p0 = yToPercent(down.position.y)
                                if (p0 != strength) {
                                    strength = p0
                                    scope.launch { SettingsPreferenceHelper.setShakeStrength(appCtx, p0) }
                                }
                                // Drag verfolgen
                                drag(down.id) { change ->
                                    val p = yToPercent(change.position.y)
                                    if (p != strength) {
                                        strength = p
                                        scope.launch { SettingsPreferenceHelper.setShakeStrength(appCtx, p) }
                                    }
                                    change.consume()
                                }
                            }
                        }
                ) {
                    val W = size.width
                    val H = size.height

                    // Rahmen
                    drawRoundRect(
                        color = cs.outlineVariant,
                        size = size,
                        style = Stroke(width = 2.dp.toPx()),
                        cornerRadius = CornerRadius(barCorner.toPx())
                    )

                    // Innenfläche
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

                    // Live-Füllung (Magnitude 0..1)
                    if (magnitudeNorm > 0.002f) {
                        val fillHeight = innerSize.height * magnitudeNorm.coerceIn(0f, 1f)
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

                    // Schwellen-Linie (aus Prozentwert)
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
            Text("$strength%", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.shake_strength_hint), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }
    }
}
