package com.tigonic.snoozely.ui.screens

import android.Manifest
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tigonic.snoozely.R
import com.tigonic.snoozely.util.LocaleHelper
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import com.tigonic.snoozely.util.ScreenOffAdminReceiver
import kotlinx.coroutines.launch

// Helper: Context → Activity (vermeidet direkten Cast von LocalContext)
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
    val appContext = LocalContext.current.applicationContext
    val activity = LocalContext.current.asActivity()
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    val devicePolicyManager =
        appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = ComponentName(appContext, ScreenOffAdminReceiver::class.java)

    var isAdmin by remember { mutableStateOf(devicePolicyManager.isAdminActive(adminComponent)) }
    val stopAudio by SettingsPreferenceHelper.getStopAudio(appContext).collectAsState(initial = true)
    val screenOff by SettingsPreferenceHelper.getScreenOff(appContext).collectAsState(initial = false)
    val notificationEnabled by SettingsPreferenceHelper.getNotificationEnabled(appContext).collectAsState(initial = false)
    val timerVibrate by SettingsPreferenceHelper.getTimerVibrate(appContext).collectAsState(initial = false)
    val fadeOut by SettingsPreferenceHelper.getFadeOut(appContext).collectAsState(initial = 30f)
    val language by SettingsPreferenceHelper.getLanguage(appContext).collectAsState(initial = "de")
    val showReminderPopup by SettingsPreferenceHelper.getShowReminderPopup(appContext).collectAsState(initial = false)
    val reminderMinutes by SettingsPreferenceHelper.getReminderMinutes(appContext).collectAsState(initial = 5)
    val extendStep by SettingsPreferenceHelper.getProgressExtendMinutes(appContext).collectAsState(initial = 5)

    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val nowIsAdmin = devicePolicyManager.isAdminActive(adminComponent)
        isAdmin = nowIsAdmin
        scope.launch { SettingsPreferenceHelper.setScreenOff(appContext, nowIsAdmin) }
        Toast.makeText(
            appContext,
            if (nowIsAdmin) appContext.getString(R.string.device_admin_enabled)
            else appContext.getString(R.string.device_admin_failed),
            Toast.LENGTH_SHORT
        ).show()
    }

    var showRemoveAdminDialog by remember { mutableStateOf(false) }
    var showNotificationPermissionDialog by remember { mutableStateOf(false) }
    var showDisableNotificationDialog by remember { mutableStateOf(false) }

    // Notification Permission (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        scope.launch {
            SettingsPreferenceHelper.setNotificationEnabled(appContext, isGranted)
            if (!isGranted) {
                Toast.makeText(
                    appContext,
                    appContext.getString(R.string.notification_permission_denied),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Admin-Status mit Switch synchron halten
    LaunchedEffect(screenOff) {
        val nowIsAdmin = devicePolicyManager.isAdminActive(adminComponent)
        isAdmin = nowIsAdmin
        if (!nowIsAdmin && screenOff) {
            scope.launch { SettingsPreferenceHelper.setScreenOff(appContext, false) }
        }
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
                    containerColor = cs.primaryContainer,
                    titleContentColor = cs.onPrimaryContainer,
                    navigationIconContentColor = cs.onPrimaryContainer,
                ),
            )
        },
        bottomBar = {
            // dezente Fläche, die sich ans Theme anpasst
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(cs.surface)
            )
        },
        containerColor = cs.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 8.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Sleep Timer
            SectionHeader(text = stringResource(R.string.sleep_timer))

            SettingsRow(
                icon = Icons.Default.PlayCircleFilled,
                title = stringResource(R.string.playback),
                subtitle = stringResource(R.string.stop_audio_video),
                checked = stopAudio,
                onCheckedChange = { value -> scope.launch { SettingsPreferenceHelper.setStopAudio(appContext, value) } },
                enabled = true
            )

            // Fade-Out
            Text(
                stringResource(R.string.fade_out_duration),
                color = cs.onBackground,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                stringResource(R.string.seconds, fadeOut.toInt()),
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = fadeOut,
                onValueChange = { v -> scope.launch { SettingsPreferenceHelper.setFadeOut(appContext, v) } },
                valueRange = 0f..120f,
                steps = 11,
                modifier = Modifier.padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    activeTrackColor = cs.primary,
                    inactiveTrackColor = cs.primary.copy(alpha = 0.30f),
                    thumbColor = cs.primary,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                )
            )

            // Bildschirm ausschalten (Device Admin)
            SettingsRow(
                icon = Icons.Default.Brightness2,
                title = stringResource(R.string.screen),
                subtitle = if (isAdmin)
                    stringResource(R.string.turn_off_screen)
                else
                    stringResource(R.string.admin_permission_required),
                checked = screenOff && isAdmin,
                onCheckedChange = { value ->
                    if (value) {
                        if (!isAdmin && activity != null) {
                            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                                putExtra(
                                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                    appContext.getString(R.string.device_admin_explanation)
                                )
                            }
                            adminLauncher.launch(intent)
                        } else {
                            scope.launch { SettingsPreferenceHelper.setScreenOff(appContext, true) }
                            Toast.makeText(appContext, appContext.getString(R.string.device_admin_enabled), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        if (isAdmin && activity != null) {
                            showRemoveAdminDialog = true
                        } else {
                            scope.launch { SettingsPreferenceHelper.setScreenOff(appContext, false) }
                            Toast.makeText(appContext, appContext.getString(R.string.device_admin_disabled), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = true
            )

            if (showRemoveAdminDialog) {
                AlertDialog(
                    onDismissRequest = { showRemoveAdminDialog = false },
                    title = { Text(stringResource(R.string.remove_admin_title)) },
                    text = { Text(stringResource(R.string.remove_admin_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showRemoveAdminDialog = false
                            try {
                                devicePolicyManager.removeActiveAdmin(adminComponent)
                                isAdmin = false
                                scope.launch { SettingsPreferenceHelper.setScreenOff(appContext, false) }
                                Toast.makeText(
                                    appContext,
                                    appContext.getString(R.string.device_admin_disabled),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    appContext,
                                    appContext.getString(R.string.device_admin_remove_manual_hint),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }) { Text(stringResource(R.string.remove_admin_confirm_button)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRemoveAdminDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            // Dummy Einstellungen ...
            SettingsRow(
                icon = Icons.Default.BluetoothDisabled,
                title = stringResource(R.string.bluetooth),
                subtitle = stringResource(R.string.bluetooth_android_13_removed),
                checked = false,
                onCheckedChange = {},
                enabled = false
            )
            SettingsRow(
                icon = Icons.Default.WifiOff,
                title = stringResource(R.string.wifi),
                subtitle = stringResource(R.string.wifi_android_10_removed),
                checked = false,
                onCheckedChange = {},
                enabled = false
            )

            Spacer(Modifier.height(12.dp))

            // --------- Benachrichtigungen ---------
            SectionHeader(text = stringResource(R.string.notification))

            // Ein Eintrag, der zur separaten Seite navigiert
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
                    tint = cs.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.notification),
                        color = cs.onBackground,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.show_remaining_time),
                        color = cs.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant
                )
            }

            // --- Shake to Extend ---------------------------------------------------------
            val shakeEnabled by SettingsPreferenceHelper
                .getShakeEnabled(appContext)
                .collectAsState(initial = false)
            val shakeExtend by SettingsPreferenceHelper
                .getShakeExtendMinutes(appContext)
                .collectAsState(initial = 10)

            Spacer(Modifier.height(12.dp))
            SectionHeader(text = stringResource(R.string.shake_to_extend))

            // Ganze Zeile als Button → immer zur Detailseite navigieren
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
                    tint = if (shakeEnabled) cs.primary else cs.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.shake_to_extend),
                        color = cs.onBackground,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (shakeEnabled)
                            stringResource(R.string.shake_to_extend_enabled_sub, shakeExtend)
                        else
                            stringResource(R.string.disabled),
                        color = cs.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            // Sprache
            SectionHeader(text = stringResource(R.string.language))
            LanguageDropdown(
                selectedLangCode = language,
                onSelect = { code ->
                    activity?.let {
                        LocaleHelper.setAppLocaleAndRestart(it, code)
                        scope.launch { SettingsPreferenceHelper.setLanguage(appContext, code) }
                    }
                }
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    val cs = MaterialTheme.colorScheme
    Text(
        text = text,
        color = cs.primary,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
    )
}

@Composable
private fun SectionSubheader(text: String) {
    val cs = MaterialTheme.colorScheme
    Text(
        text = text,
        color = cs.onBackground,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled && checked) cs.primary else cs.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) cs.onBackground else cs.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = subtitle,
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageDropdown(
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

    // WICHTIG: Keine eigene Überschrift & kein Label → sonst doppelt
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},            // read-only
            readOnly = true,
            // KEIN label = {...} -> verhindert “zweites Language”
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (code, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onSelect(code)
                    }
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    ThemeSection()
}

// ===== Theme (Dropdown + Dynamic Colors) =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val themeId by SettingsPreferenceHelper.getThemeMode(ctx).collectAsState(initial = "system")
    val dynamic by SettingsPreferenceHelper.getThemeDynamic(ctx).collectAsState(initial = true)

    var expanded by remember { mutableStateOf(false) }
    val themes = remember { com.tigonic.snoozely.ui.theme.ThemeRegistry.themes }
    val selectedLabel = themes.firstOrNull { it.id == themeId }?.label ?: "System"

    // Überschrift im selben Stil wie andere Kapitel (primary, bold)
    SectionHeader(text = stringResource(R.string.theme))

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            // Label bleibt neutral oder kann entfallen – die Überschrift oben ist die “Section”-Betitelung
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
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
