package com.tigonic.snoozely.ui.screens

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
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
    onNavigateNotificationSettings: () -> Unit, // <- NEU
) {
    val appContext = LocalContext.current.applicationContext // nur ApplicationContext
    val activity = LocalContext.current.asActivity()
    val scope = rememberCoroutineScope()

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
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF101010),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color(0xFF101010))
            )
        },
        containerColor = Color(0xFF101010),
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
            Text(
                stringResource(R.string.sleep_timer),
                color = Color(0xFF7F7FFF),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )

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
                color = Color.LightGray,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                stringResource(R.string.seconds, fadeOut.toInt()),
                color = Color.Gray,
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = fadeOut,
                onValueChange = { value -> scope.launch { SettingsPreferenceHelper.setFadeOut(appContext, value) } },
                valueRange = 0f..120f,
                steps = 11,
                colors = SliderDefaults.colors(
                    activeTrackColor = Color(0xFF7F7FFF),
                    inactiveTrackColor = Color(0x33444444),
                    thumbColor = Color(0xFF7F7FFF),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                ),
                modifier = Modifier.padding(horizontal = 8.dp)
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
            Text(
                stringResource(R.string.notification),
                color = Color(0xFF7F7FFF),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )

            // NEU: Ein Eintrag, der zur separaten Seite navigiert
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
                    tint = Color(0xFF7F7FFF),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.notification), // falls vorhanden; sonst „Benachrichtigungen“
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.show_remaining_time), // vorhandener Subtext
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.Gray
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
            Text(
                text = stringResource(R.string.shake_to_extend),
                color = Color(0xFF7F7FFF),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )

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
                    tint = if (shakeEnabled) Color(0xFF7F7FFF) else Color.LightGray,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.shake_to_extend),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (shakeEnabled)
                            stringResource(R.string.shake_to_extend_enabled_sub, shakeExtend)
                        else
                            stringResource(R.string.disabled),
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                // Statt Switch: Chevron als visuelles „weiter“-Signal
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.Gray
                )
            }


            // Haptik
            Text(
                stringResource(R.string.haptic_feedback),
                color = Color(0xFF7F7FFF),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            SettingsRow(
                icon = Icons.Default.PlayCircleFilled,
                title = stringResource(R.string.timer),
                subtitle = stringResource(R.string.device_vibrate_on_timer),
                checked = timerVibrate,
                onCheckedChange = { value ->
                    scope.launch { SettingsPreferenceHelper.setTimerVibrate(appContext, value) }
                },
                enabled = true
            )

            // Sprache
            Text(
                stringResource(R.string.language),
                color = Color(0xFF7F7FFF),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
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
fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled && checked) Color(0xFF7F7FFF)
            else if (enabled) Color.LightGray
            else Color(0xFF222222),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) Color.White else Color.Gray,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = subtitle,
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF7F7FFF),
                checkedTrackColor = Color(0x447F7FFF),
                uncheckedThumbColor = Color.LightGray,
                uncheckedTrackColor = Color(0x33444444),
                disabledCheckedThumbColor = Color.DarkGray,
                disabledCheckedTrackColor = Color.Gray,
                disabledUncheckedThumbColor = Color.DarkGray,
                disabledUncheckedTrackColor = Color.Gray
            ),
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
fun LanguageDropdown(
    selectedLangCode: String,
    onSelect: (String) -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val languageMap = mapOf(
        stringResource(R.string.german) to "de",
        stringResource(R.string.english) to "en",
        stringResource(R.string.french) to "fr"
    )
    val languages = languageMap.keys.toList()
    val label = languageMap.entries.firstOrNull { it.value == selectedLangCode }?.key ?: languages.first()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(Color(0xFF181818), shape = MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = null,
                tint = Color(0xFF7F7FFF),
                modifier = Modifier
                    .size(20.dp)
                    .rotate(90f)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF242449))
        ) {
            languageMap.forEach { (name, code) ->
                DropdownMenuItem(
                    text = { Text(name, color = Color.White) },
                    onClick = {
                        expanded = false
                        onSelect(code)
                    }
                )
            }
        }
    }
}
