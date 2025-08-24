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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
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
    val activationMode by SettingsPreferenceHelper.getShakeActivationMode(appCtx).collectAsState(initial = "immediate")
    val activationDelay by SettingsPreferenceHelper.getShakeActivationDelayMinutes(appCtx).collectAsState(initial = 3)

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
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent
    )

    val sectionAlpha = if (enabled) 1f else 0.5f

    // Dialog-UI State
    var showActivationDialog by remember { mutableStateOf(false) }
    var draftMode by remember(activationMode) { mutableStateOf(activationMode) } // "immediate" | "after_start"
    var draftDelay by remember(activationDelay) { mutableStateOf(activationDelay.coerceIn(1,30)) }

    // Label für aktuelle Auswahl
    val activationLabel = remember(activationMode, activationDelay) {
        if (activationMode == "after_start") {
            ctx.getString(R.string.shake_activation_after_start, activationDelay)
        } else {
            ctx.getString(R.string.shake_activation_immediate)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shake_to_extend), color = extra.menu) },
                navigationIcon = {
                    IconButton(onClick = { runCatching { preview?.stop() }; onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = extra.menu)
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

                // NEU) Aktivierungsfenster
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (enabled) 1f else 0.5f)
                        .clickable(enabled = enabled) { showActivationDialog = true }
                        .padding(vertical = 10.dp), // gleiche vertikale Padding wie bei den anderen Items
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.shake_activation_title),
                            color = cs.onBackground,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = activationLabel, // z. B. „Sofort“ oder „3 min nach Start“
                            color = cs.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    // Rechts das Icon, gleiche Farbe und vertikal mittig wie die Pfeile
                    IconButton(
                        enabled = enabled,
                        onClick = { showActivationDialog = true },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = cs.onSurfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = stringResource(R.string.configure),
                            tint = cs.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider(color = extra.divider)


                // 2) Schüttelkraft
                Spacer(Modifier.height(6.dp))
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
                            color = cs.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
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
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.alpha(sectionAlpha)
                )
                Slider(
                    enabled = enabled,
                    value = extendMin.toFloat(),
                    onValueChange = { v ->
                        if (enabled) {
                            val rounded = v.coerceIn(1f, 30f).toInt()
                            scope.launch { SettingsPreferenceHelper.setShakeExtendMinutes(appCtx, rounded) }
                        }
                    },
                    valueRange = 1f..30f,
                    steps = 0,
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
                        Text(toneTitle, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
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
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
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
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
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

            // MODAL: Aktivierungsfenster
            if (showActivationDialog) {
                // Hintergrund abdunkeln
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { /* consume */ }
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = cs.surface,
                        contentColor = cs.onSurface,
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth()
                    ) {
                        Column(
                            Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.shake_activation_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = cs.onSurface
                            )
                            Spacer(Modifier.height(8.dp))

                            // Radio: Sofort
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { draftMode = "immediate" }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = draftMode == "immediate",
                                    onClick = { draftMode = "immediate" }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.shake_activation_immediate), style = MaterialTheme.typography.bodyMedium)
                            }

                            // Radio: Nach X Minuten
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { draftMode = "after_start" }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = draftMode == "after_start",
                                    onClick = { draftMode = "after_start" }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.shake_activation_after_start_label), style = MaterialTheme.typography.bodyMedium)
                            }

                            if (draftMode == "after_start") {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.shake_activation_after_start_value, draftDelay),
                                    color = cs.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Slider(
                                    value = draftDelay.toFloat(),
                                    onValueChange = { v ->
                                        draftDelay = v.coerceIn(1f, 30f).toInt()
                                    },
                                    valueRange = 1f..30f,
                                    steps = 0,
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = extra.slider,
                                        inactiveTrackColor = extra.slider.copy(alpha = 0.30f),
                                        thumbColor = extra.slider,
                                        activeTickColor = Color.Transparent,
                                        inactiveTickColor = Color.Transparent
                                    ),
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }

                            Spacer(Modifier.height(12.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Button(onClick = {
                                    scope.launch {
                                        SettingsPreferenceHelper.setShakeActivationMode(appCtx, draftMode)
                                        if (draftMode == "after_start") {
                                            SettingsPreferenceHelper.setShakeActivationDelayMinutes(appCtx, draftDelay)
                                        }
                                    }
                                    showActivationDialog = false
                                }) {
                                    Text(stringResource(R.string.close))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
