package com.tigonic.snoozely.ui.screens

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tigonic.snoozely.R
import com.tigonic.snoozely.ui.theme.LocalExtraColors
import com.tigonic.snoozely.ui.theme.ThemeRegistry
import com.tigonic.snoozely.util.LocaleHelper
import com.tigonic.snoozely.util.ScreenOffAdminReceiver
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import kotlinx.coroutines.launch
import com.tigonic.snoozely.ui.components.VerticalScrollbar

// ---- Helper: Context -> Activity
private tailrec fun Context.asActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.asActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateShakeSettings: () -> Unit,
    onNavigateNotificationSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext
    val activity = ctx.asActivity()
    val scope = rememberCoroutineScope()

    val cs = MaterialTheme.colorScheme
    val extra = LocalExtraColors.current

    // --- Admin / Screen-Off
    val dpm = app.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val admin = ComponentName(app, ScreenOffAdminReceiver::class.java)
    var isAdmin by remember { mutableStateOf(dpm.isAdminActive(admin)) }

    // --- Settings States
    val stopAudio by SettingsPreferenceHelper.getStopAudio(app).collectAsState(initial = true)
    val screenOff by SettingsPreferenceHelper.getScreenOff(app).collectAsState(initial = false)
    val fadeOut by SettingsPreferenceHelper.getFadeOut(app).collectAsState(initial = 30f)
    val language by SettingsPreferenceHelper.getLanguage(app).collectAsState(initial = "de")
    val notificationEnabled by SettingsPreferenceHelper.getNotificationEnabled(app).collectAsState(initial = false)
    val shakedEnabled by SettingsPreferenceHelper.getShakeEnabled(app).collectAsState(initial = false)
    val wifiDisableRequested by SettingsPreferenceHelper.getWifiDisableRequested(app).collectAsState(initial = false)
    val bluetoothDisableRequested by SettingsPreferenceHelper.getBluetoothDisableRequested(app).collectAsState(initial = false)

    val supportsBtToggle = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    val supportsWifiToggle = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P

    // --- Admin-Flow
    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val now = dpm.isAdminActive(admin)
        isAdmin = now
        scope.launch { SettingsPreferenceHelper.setScreenOff(app, now) }
        Toast.makeText(app, if (now) app.getString(R.string.device_admin_enabled) else app.getString(R.string.device_admin_failed), Toast.LENGTH_SHORT).show()
    }
    var showRemoveAdminDialog by remember { mutableStateOf(false) }

    // Admin-Status mit Switch synchron halten
    LaunchedEffect(screenOff) {
        val now = dpm.isAdminActive(admin)
        isAdmin = now
        if (!now && screenOff) scope.launch { SettingsPreferenceHelper.setScreenOff(app, false) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings), color = cs.onPrimaryContainer) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = cs.onPrimaryContainer)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.background,
                    titleContentColor = cs.onPrimaryContainer,
                    navigationIconContentColor = cs.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            // dezente Fläche, greift Theme-Farben
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(cs.surface)
            )
        },
        containerColor = cs.background
    ) { inner ->
        val scrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // ===== App / Playback =====
                SectionHeader(text = stringResource(R.string.app_name))

                SettingsRow(
                    icon = Icons.Default.PlayCircleFilled,
                    title = stringResource(R.string.playback),
                    subtitle = stringResource(R.string.stop_audio_video),
                    checked = stopAudio,
                    onCheckedChange = { v -> scope.launch { SettingsPreferenceHelper.setStopAudio(app, v) } }
                )

                // Fade-Out
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.fade_out_duration), color = cs.onBackground, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.seconds, fadeOut.toInt()), color = extra.infoText, style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = fadeOut,
                        onValueChange = { v -> scope.launch { SettingsPreferenceHelper.setFadeOut(app, v) } },
                        valueRange = 0f..120f,
                        steps = 11,
                        modifier = Modifier.padding(horizontal = 4.dp),
                        colors = SliderDefaults.colors(
                            activeTrackColor = extra.slider,
                            inactiveTrackColor = extra.slider.copy(alpha = 0.30f),
                            thumbColor = extra.slider,
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        )
                    )
                }

                // ===== Screen / Admin =====
                SettingsRow(
                    icon = Icons.Default.Brightness2,
                    title = stringResource(R.string.screen),
                    subtitle = if (isAdmin) stringResource(R.string.turn_off_screen) else stringResource(R.string.admin_permission_required),
                    checked = screenOff && isAdmin,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            if (!isAdmin && activity != null) {
                                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, app.getString(R.string.device_admin_explanation))
                                }
                                adminLauncher.launch(intent)
                            } else {
                                scope.launch { SettingsPreferenceHelper.setScreenOff(app, true) }
                                Toast.makeText(app, app.getString(R.string.device_admin_enabled), Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            if (isAdmin && activity != null) {
                                showRemoveAdminDialog = true
                            } else {
                                scope.launch { SettingsPreferenceHelper.setScreenOff(app, false) }
                                Toast.makeText(app, app.getString(R.string.device_admin_disabled), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )

                if (showRemoveAdminDialog) {
                    AlertDialog(
                        onDismissRequest = { showRemoveAdminDialog = false },
                        title = { Text(stringResource(R.string.remove_admin_title)) },
                        text = { Text(stringResource(R.string.remove_admin_message)) },
                        confirmButton = {
                            TextButton(onClick = {
                                showRemoveAdminDialog = false
                                runCatching { dpm.removeActiveAdmin(admin) }
                                isAdmin = false
                                scope.launch { SettingsPreferenceHelper.setScreenOff(app, false) }
                                Toast.makeText(app, app.getString(R.string.device_admin_disabled), Toast.LENGTH_SHORT).show()
                            }) { Text(stringResource(R.string.remove_admin_confirm_button)) }
                        },
                        dismissButton = { TextButton(onClick = { showRemoveAdminDialog = false }) { Text(stringResource(R.string.cancel)) } }
                    )
                }

                // ===== Radios =====
                SettingsRow(
                    icon = Icons.Default.BluetoothDisabled,
                    title = stringResource(R.string.bluetooth),
                    subtitle = if (supportsBtToggle) stringResource(R.string.bluetooth_turn_off) else stringResource(R.string.bluetooth_android_13_removed),
                    checked = if (supportsBtToggle) bluetoothDisableRequested else false,
                    onCheckedChange = { v -> scope.launch { SettingsPreferenceHelper.setBluetoothDisableRequested(app, v) } },
                    enabled = supportsBtToggle
                )

                SettingsRow(
                    icon = Icons.Default.WifiOff,
                    title = stringResource(R.string.wifi),
                    subtitle = if (supportsWifiToggle) stringResource(R.string.wifi_turn_off) else stringResource(R.string.wifi_android_10_removed),
                    checked = if (supportsWifiToggle) wifiDisableRequested else false,
                    onCheckedChange = { v -> scope.launch { SettingsPreferenceHelper.setWifiDisableRequested(app, v) } },
                    enabled = supportsWifiToggle
                )

                // ===== Notifications =====
                SectionHeader(text = stringResource(R.string.notification))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateNotificationSettings() }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = iconTint(active = notificationEnabled)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.notification), color = cs.onBackground, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (notificationEnabled) stringResource(R.string.enabled) else stringResource(R.string.disabled),
                            color = extra.infoText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = cs.onSurfaceVariant)
                }

                // ===== Shake to extend =====
                SectionHeader(text = stringResource(R.string.shake_to_extend))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateShakeSettings() }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Vibration,
                        contentDescription = null,
                        tint = iconTint(active = shakedEnabled)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.shake_to_extend), color = cs.onBackground, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (shakedEnabled) stringResource(R.string.enabled) else stringResource(R.string.disabled),
                            color = extra.infoText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = cs.onSurfaceVariant)
                }

                // ===== Sprache / Theme =====
                SectionHeader(text = stringResource(R.string.display_and_language))
                LanguageDropdown(
                    selectedLangCode = language,
                    onSelect = { code ->
                        activity?.let {
                            LocaleHelper.setAppLocaleAndRestart(it, code)
                            scope.launch { SettingsPreferenceHelper.setLanguage(app, code) }
                        }
                    }
                )

                ThemeSection()
                Spacer(Modifier.height(12.dp))
            }

            // <- Scrollbar MUSS innerhalb der Box liegen, damit align() funktioniert
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

// ---- Building blocks (farblich 100% aus ThemeSystem) ----

@Composable
private fun SectionHeader(text: String) {
    val extra = LocalExtraColors.current
    Text(
        text = text,
        color = extra.heading, // zentral über ThemeSystem steuerbar
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 12.dp, bottom = 0.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val cs = MaterialTheme.colorScheme
    val extra = LocalExtraColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled && checked) extra.icon else cs.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, color = if (enabled) cs.onBackground else cs.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
            Text(text = subtitle, color = extra.infoText, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = extra.toggle,
                checkedTrackColor = extra.toggle.copy(alpha = 0.35f),
                uncheckedThumbColor = cs.onSurface,
                uncheckedTrackColor = cs.onSurface.copy(alpha = 0.20f)
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(
    selectedLangCode: String,
    onSelect: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }

    val options = listOf(
        "de" to stringResource(R.string.german),
        "en" to stringResource(R.string.english),
        "fr" to stringResource(R.string.french)
    )
    val selectedLabel = options.firstOrNull { it.first == selectedLangCode }?.second ?: options.first().second

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.language)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                focusedBorderColor = cs.primary,
                unfocusedBorderColor = cs.outline
            )
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (code, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    expanded = false
                    onSelect(code)
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    val themeId by SettingsPreferenceHelper.getThemeMode(ctx).collectAsState(initial = "light")
    var expanded by remember { mutableStateOf(false) }
    val themes = remember { ThemeRegistry.themes }
    val selectedLabel = themes.firstOrNull { it.id == themeId }?.label ?: "Light"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.theme)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                focusedBorderColor = cs.primary,
                unfocusedBorderColor = cs.outline
            )
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            themes.forEach { spec ->
                DropdownMenuItem(
                    text = { Text(spec.label) },
                    onClick = {
                        expanded = false
                        scope.launch { SettingsPreferenceHelper.setThemeMode(ctx, spec.id) }
                    }
                )
            }
        }
    }
}

@Composable
private fun iconTint(active: Boolean, enabled: Boolean = true): Color {
    val cs = MaterialTheme.colorScheme
    val extra = LocalExtraColors.current
    val target = when {
        !enabled -> cs.onSurfaceVariant.copy(alpha = 0.5f)
        active   -> extra.icon
        else     -> cs.onSurfaceVariant
    }
    val animated by animateColorAsState(targetValue = target, label = "iconTint")
    return animated
}
