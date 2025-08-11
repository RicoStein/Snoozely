package com.tigonic.snoozely.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tigonic.snoozely.R
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShakeExtendSettingsScreen(
    onBack: () -> Unit,
    onNavigateShakeStrength: () -> Unit,
    onPickSound: () -> Unit
) {
    val appCtx = LocalContext.current.applicationContext
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val enabled by SettingsPreferenceHelper.getShakeEnabled(appCtx).collectAsState(initial = false)
    val extendMin by SettingsPreferenceHelper.getShakeExtendMinutes(appCtx).collectAsState(initial = 10)
    val mode by SettingsPreferenceHelper.getShakeSoundMode(appCtx).collectAsState(initial = "tone")
    val ringtoneUriStr by SettingsPreferenceHelper.getShakeRingtone(appCtx).collectAsState(initial = "")

    // --- Audio-Lese-Rechte (für eigene Töne) ---
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

    // --- System-Volume (Notification) ---
    val audioManager = remember { appCtx.getSystemService(AudioManager::class.java) }
    val maxVol = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION).coerceAtLeast(1) }
    val curVolStart = remember { audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION) }
    var sysVol by remember { mutableStateOf(curVolStart.toFloat() / maxVol) }

    // --- Ringtone-Name/Helfer ---
    fun currentToneTitle(uriStr: String): String {
        if (uriStr.isEmpty()) return ctx.getString(R.string.silent)
        return runCatching {
            val uri = Uri.parse(uriStr)
            RingtoneManager.getRingtone(ctx, uri)?.getTitle(ctx) ?: ctx.getString(R.string.unknown_tone)
        }.getOrElse { ctx.getString(R.string.unknown_tone) }
    }
    var toneTitle by remember(ringtoneUriStr) { mutableStateOf(currentToneTitle(ringtoneUriStr)) }

    // --- Picker ---
    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val data = res.data
        val uri: Uri? = data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        scope.launch {
            if (uri == null) {
                SettingsPreferenceHelper.setShakeRingtone(appCtx, "")
                toneTitle = ctx.getString(R.string.silent)
                SettingsPreferenceHelper.setShakeSoundMode(appCtx, "tone")
            } else {
                SettingsPreferenceHelper.setShakeRingtone(appCtx, uri.toString())
                SettingsPreferenceHelper.setShakeSoundMode(appCtx, "tone")
                toneTitle = currentToneTitle(uri.toString())
            }
        }
    }
    fun openRingtonePicker() {
        ensureAudioPermission {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, ctx.getString(R.string.notification_sound))
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI)
                val existing = ringtoneUriStr.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
            }
            ringtoneLauncher.launch(intent)
        }
    }

    // --- Vorschau-Player ---
    var preview: Ringtone? by remember { mutableStateOf(null) }

    fun chosenOrDefaultTone(): Uri? =
        if (ringtoneUriStr.isNotEmpty()) Uri.parse(ringtoneUriStr) else Settings.System.DEFAULT_NOTIFICATION_URI

    fun playPreviewOnce() {
        if (mode != "tone") return
        if (!audioPermGranted) return
        val uri = chosenOrDefaultTone() ?: return

        runCatching {
            // Stoppe vorherigen Player NICHT aggressiv – nur ersetzen, falls neu gestartet wird
            preview?.stop()
            val r = RingtoneManager.getRingtone(ctx, uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                r.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            preview = r
            r.play() // spielt einmal komplett; wir stoppen nicht bei Finger-loslassen
        }
    }

    fun pulseVibrate() {
        try {
            val hasVibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(VibratorManager::class.java)
                vm?.defaultVibrator?.hasVibrator() == true
            } else {
                @Suppress("DEPRECATION")
                (ctx.getSystemService(Vibrator::class.java)?.hasVibrator() == true)
            }
            if (!hasVibrator) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(600L, VibrationEffect.DEFAULT_AMPLITUDE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = ctx.getSystemService(VibratorManager::class.java)
                    vm?.defaultVibrator?.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    ctx.getSystemService(Vibrator::class.java)?.vibrate(effect)
                }
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Vibrator::class.java)?.vibrate(600L)
            }
        } catch (_: Throwable) { }
    }

    DisposableEffect(Unit) { onDispose { runCatching { preview?.stop() } } }
    LaunchedEffect(Unit) {
        sysVol = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION).toFloat() / maxVol
    }

    val sliderColors = SliderDefaults.colors(
        activeTrackColor = Color(0xFF7F7FFF),
        inactiveTrackColor = Color(0x33444444),
        thumbColor = Color(0xFF7F7FFF),
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shake_to_extend)) },
                navigationIcon = {
                    IconButton(onClick = { runCatching { preview?.stop() }; onBack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF101010),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
        containerColor = Color(0xFF101010),
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(inner)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {

            // 1) Master
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.shake_to_extend),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = { v -> scope.launch { SettingsPreferenceHelper.setShakeEnabled(appCtx, v) } }
                )
            }
            Divider(color = Color(0x22FFFFFF))

            // 2) Schüttelkraft
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateShakeStrength() }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.shake_strength),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.shake_strength_sub),
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = Color.Gray
                )
            }
            Divider(color = Color(0x22FFFFFF))

            // 3) Verlängerungstimer
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.extend_by_minutes),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            Text(stringResource(R.string.timer_plus_x, extendMin), color = Color.Gray)
            Slider(
                value = extendMin.toFloat(),
                onValueChange = { v -> scope.launch { SettingsPreferenceHelper.setShakeExtendMinutes(appCtx, v.toInt()) } },
                valueRange = 1f..30f,
                steps = 29,
                colors = sliderColors,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Divider(color = Color(0x22FFFFFF), modifier = Modifier.padding(top = 8.dp))

            // 4) Benachrichtigungston
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openRingtonePicker() }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.notification_sound),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(toneTitle, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = Color.Gray
                )
            }
            Divider(color = Color(0x22FFFFFF))

            // 5) Vibration
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.vibrate),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                val vibrateEnabled = (mode == "vibrate")
                Switch(
                    checked = vibrateEnabled,
                    onCheckedChange = { v ->
                        scope.launch {
                            if (v) {
                                SettingsPreferenceHelper.setShakeSoundMode(appCtx, "vibrate")
                                SettingsPreferenceHelper.setShakeRingtone(appCtx, "")
                                toneTitle = ctx.getString(R.string.silent)
                                runCatching { preview?.stop() }
                                pulseVibrate()
                            } else {
                                SettingsPreferenceHelper.setShakeSoundMode(appCtx, "tone")
                            }
                        }
                    }
                )
            }
            Text(
                text = stringResource(R.string.shake_vibration_hint),
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )
            Divider(color = Color(0x22FFFFFF))

            // 6) Benachrichtigungslautstärke
            Text(
                stringResource(R.string.notification_volume),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                stringResource(R.string.volume_relative_hint),
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )

            // → Vorschau-Logik: Tippen ODER Wischen soll voll abspielen.
            val volumeInteraction = remember { MutableInteractionSource() }
            val pressed by volumeInteraction.collectIsPressedAsState()

            // Bei Beginn einer Interaktion (auch Tap auf Track/Thumb) einmal starten.
            LaunchedEffect(pressed) {
                if (pressed) {
                    ensureAudioPermission { playPreviewOnce() }
                }
            }

            Slider(
                value = sysVol,
                onValueChange = { v ->
                    sysVol = v
                    val newVol = (v * maxVol).coerceIn(0f, maxVol.toFloat()).toInt()
                    audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, newVol, 0)
                    // Beim Ziehen erneut anstoßen (ersetzt den laufenden Player ohne zu schneiden)
                    ensureAudioPermission { playPreviewOnce() }
                },
                // WICHTIG: NICHT stoppen – der Ton darf ausklingen
                onValueChangeFinished = { /* no stop */ },
                valueRange = 0f..1f,
                steps = 0,
                colors = sliderColors,
                interactionSource = volumeInteraction,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
