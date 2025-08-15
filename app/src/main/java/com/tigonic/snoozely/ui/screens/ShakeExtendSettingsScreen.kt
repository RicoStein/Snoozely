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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tigonic.snoozely.R
import com.tigonic.snoozely.ui.components.VerticalScrollbar
import com.tigonic.snoozely.ui.theme.LocalExtraColors
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

    val cs = MaterialTheme.colorScheme
    val extra = LocalExtraColors.current

    val enabled by SettingsPreferenceHelper.getShakeEnabled(appCtx).collectAsState(initial = false)
    val extendMin by SettingsPreferenceHelper.getShakeExtendMinutes(appCtx).collectAsState(initial = 10)
    val mode by SettingsPreferenceHelper.getShakeSoundMode(appCtx).collectAsState(initial = "tone")
    val ringtoneUriStr by SettingsPreferenceHelper.getShakeRingtone(appCtx).collectAsState(initial = "")

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

    val audioManager = remember { appCtx.getSystemService(AudioManager::class.java) }
    val maxVol = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION).coerceAtLeast(1) }
    val curVolStart = remember { audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION) }
    var sysVol by remember { mutableStateOf(curVolStart.toFloat() / maxVol) }

    fun currentToneTitle(uriStr: String): String {
        if (uriStr.isEmpty()) return ctx.getString(R.string.silent)
        return runCatching {
            val uri = Uri.parse(uriStr)
            RingtoneManager.getRingtone(ctx, uri)?.getTitle(ctx) ?: ctx.getString(R.string.unknown_tone)
        }.getOrElse { ctx.getString(R.string.unknown_tone) }
    }
    var toneTitle by remember(ringtoneUriStr) { mutableStateOf(currentToneTitle(ringtoneUriStr)) }

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
        if (!enabled) return
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

    var preview: Ringtone? by remember { mutableStateOf(null) }

    fun chosenOrDefaultTone(): Uri? =
        if (ringtoneUriStr.isNotEmpty()) Uri.parse(ringtoneUriStr) else Settings.System.DEFAULT_NOTIFICATION_URI

    fun playPreviewOnce() {
        if (!enabled) return
        if (mode != "tone") return
        if (!audioPermGranted) return
        val uri = chosenOrDefaultTone() ?: return
        runCatching {
            preview?.stop()
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
        if (!enabled) return
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
        activeTrackColor = extra.slider,
        inactiveTrackColor = extra.slider.copy(alpha = 0.30f),
        thumbColor = extra.slider,
        activeTickColor = cs.surface.copy(alpha = 0f),
        inactiveTickColor = cs.surface.copy(alpha = 0f)
    )

    val sectionAlpha = if (enabled) 1f else 0.5f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shake_to_extend), color = cs.onPrimaryContainer) },
                navigationIcon = {
                    IconButton(onClick = { runCatching { preview?.stop() }; onBack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = cs.onPrimaryContainer)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.primaryContainer,
                    titleContentColor = cs.onPrimaryContainer,
                    navigationIconContentColor = cs.onPrimaryContainer
                ),
            )
        },
        containerColor = cs.background
    ) { inner ->
        val scrollState = rememberScrollState()

        Box(
            Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
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
                        color = cs.onBackground,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = { v -> scope.launch { SettingsPreferenceHelper.setShakeEnabled(appCtx, v) } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = extra.toggle,
                            checkedTrackColor = extra.toggle.copy(alpha = 0.35f),
                            uncheckedThumbColor = cs.onSurface,
                            uncheckedTrackColor = cs.onSurface.copy(alpha = 0.20f)
                        )
                    )
                }
                HorizontalDivider(color = extra.divider)

                // 2) Schüttelkraft
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (enabled) Modifier.clickable { onNavigateShakeStrength() } else Modifier)
                        .padding(vertical = 10.dp)
                        .alpha(sectionAlpha),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.shake_strength),
                            color = cs.onBackground,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.shake_strength_sub),
                            color = extra.infoText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = cs.onSurfaceVariant)
                }
                HorizontalDivider(color = extra.divider)

                // 3) Verlängerungstimer
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.notifications_extend_title),
                    color = cs.onBackground,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.alpha(sectionAlpha)
                )
                Text(
                    stringResource(R.string.shake_extend_minutes_hint, extendMin),
                    color = extra.infoText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.alpha(sectionAlpha)
                )
                Slider(
                    enabled = enabled,
                    value = extendMin.toFloat(),
                    onValueChange = { v -> if (enabled) scope.launch { SettingsPreferenceHelper.setShakeExtendMinutes(appCtx, v.toInt()) } },
                    valueRange = 1f..30f,
                    steps = 29,
                    colors = sliderColors,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .alpha(sectionAlpha)
                )
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = extra.divider)

                // 4) Benachrichtigungston
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (enabled) Modifier.clickable { openRingtonePicker() } else Modifier)
                        .padding(vertical = 10.dp)
                        .alpha(sectionAlpha),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.notification_sound),
                            color = cs.onBackground,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(toneTitle, color = extra.infoText, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = cs.onSurfaceVariant)
                }
                HorizontalDivider(color = extra.divider)

                // 5) Vibration
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .alpha(sectionAlpha),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.vibrate),
                        color = cs.onBackground,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        enabled = enabled,
                        checked = (mode == "vibrate"),
                        onCheckedChange = { v ->
                            if (!enabled) return@Switch
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
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = extra.toggle,
                            checkedTrackColor = extra.toggle.copy(alpha = 0.35f),
                            uncheckedThumbColor = cs.onSurface,
                            uncheckedTrackColor = cs.onSurface.copy(alpha = 0.20f)
                        )
                    )
                }
                Text(
                    text = stringResource(R.string.shake_vibration_hint),
                    color = extra.infoText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(top = 2.dp, bottom = 8.dp)
                        .alpha(sectionAlpha)
                )
                HorizontalDivider(color = extra.divider)

                // 6) Lautstärke
                Text(
                    stringResource(R.string.notification_volume),
                    color = cs.onBackground,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .alpha(sectionAlpha)
                )
                Text(
                    stringResource(R.string.volume_relative_hint),
                    color = extra.infoText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.alpha(sectionAlpha)
                )

                val volumeInteraction = remember { MutableInteractionSource() }
                val pressed by volumeInteraction.collectIsPressedAsState()

                LaunchedEffect(pressed, enabled) {
                    if (enabled && pressed) {
                        ensureAudioPermission { playPreviewOnce() }
                    }
                }

                Slider(
                    enabled = enabled,
                    value = sysVol,
                    onValueChange = { v ->
                        if (!enabled) return@Slider
                        sysVol = v
                        val newVol = (v * maxVol).coerceIn(0f, maxVol.toFloat()).toInt()
                        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, newVol, 0)
                        ensureAudioPermission { playPreviewOnce() }
                    },
                    valueRange = 0f..1f,
                    steps = 0,
                    colors = sliderColors,
                    interactionSource = volumeInteraction,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .alpha(sectionAlpha)
                )

                Spacer(Modifier.height(24.dp))
            }

            // Scrollbar
            VerticalScrollbar(
                scrollState = scrollState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(end = 2.dp)
            )
        }
    }
}
