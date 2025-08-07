package com.tigonic.snoozely.ui.screens

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context as? Activity

    val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = ComponentName(context, ScreenOffAdminReceiver::class.java)

    // Status immer frisch lesen
    var isAdmin by remember { mutableStateOf(devicePolicyManager.isAdminActive(adminComponent)) }

    // Settings aus DataStore
    val stopAudio by SettingsPreferenceHelper.getStopAudio(context).collectAsState(initial = true)
    val screenOff by SettingsPreferenceHelper.getScreenOff(context).collectAsState(initial = false)
    val notificationEnabled by SettingsPreferenceHelper.getNotificationEnabled(context).collectAsState(initial = false)
    val timerVibrate by SettingsPreferenceHelper.getTimerVibrate(context).collectAsState(initial = false)
    val fadeOut by SettingsPreferenceHelper.getFadeOut(context).collectAsState(initial = 30f)
    val language by SettingsPreferenceHelper.getLanguage(context).collectAsState(initial = "de")

    var showRemoveAdminDialog by remember { mutableStateOf(false) }

    // Admin Launcher: wird benutzt zum Aktivieren
    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Nach Rückkehr: Status prüfen & setzen
        val nowIsAdmin = devicePolicyManager.isAdminActive(adminComponent)
        isAdmin = nowIsAdmin
        scope.launch { SettingsPreferenceHelper.setScreenOff(context, nowIsAdmin) }
        Toast.makeText(
            context,
            if (nowIsAdmin) context.getString(R.string.device_admin_enabled)
            else context.getString(R.string.device_admin_failed),
            Toast.LENGTH_SHORT
        ).show()
    }

    // Nach jedem Composable-Start Status prüfen
    LaunchedEffect(screenOff) {
        val nowIsAdmin = devicePolicyManager.isAdminActive(adminComponent)
        isAdmin = nowIsAdmin
        if (!nowIsAdmin && screenOff) {
            // Admin wurde außerhalb entzogen → Setting auf false
            scope.launch { SettingsPreferenceHelper.setScreenOff(context, false) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
            .padding(horizontal = 16.dp)
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp)
    ) {
        // TopBar mit Back-Button
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.settings),
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

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
            onCheckedChange = { value -> scope.launch { SettingsPreferenceHelper.setStopAudio(context, value) } },
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
            onValueChange = { value -> scope.launch { SettingsPreferenceHelper.setFadeOut(context, value) } },
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

        // --- Bildschirm ausschalten mit Adminrechte ---
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
                    // Einschalten: Adminrechte holen!
                    if (!isAdmin && activity != null) {
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.device_admin_explanation))
                        }
                        adminLauncher.launch(intent)
                    } else {
                        // Schon Admin: einfach Setting setzen
                        scope.launch { SettingsPreferenceHelper.setScreenOff(context, true) }
                        Toast.makeText(context, context.getString(R.string.device_admin_enabled), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Ausschalten: Dialog für DeviceAdmin entfernen zeigen!
                    if (isAdmin && activity != null) {
                        showRemoveAdminDialog = true
                    } else {
                        scope.launch { SettingsPreferenceHelper.setScreenOff(context, false) }
                        Toast.makeText(context, context.getString(R.string.device_admin_disabled), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            enabled = true
        )

        // --- Remove Device Admin Dialog ---
        if (showRemoveAdminDialog) {
            AlertDialog(
                onDismissRequest = { showRemoveAdminDialog = false },
                title = { Text(stringResource(R.string.remove_admin_title)) },
                text = { Text(stringResource(R.string.remove_admin_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showRemoveAdminDialog = false
                        try {
                            val intent = Intent("android.settings.ACTION_DEVICE_ADMIN_SETTINGS")
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.device_admin_remove_manual_hint),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }) { Text(stringResource(R.string.open_settings)) }
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

        Text(
            stringResource(R.string.notification),
            color = Color(0xFF7F7FFF),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        )

        SettingsRow(
            icon = Icons.Default.Notifications,
            title = stringResource(R.string.enable_notification),
            subtitle = stringResource(R.string.show_remaining_time),
            checked = notificationEnabled,
            onCheckedChange = { value -> scope.launch { SettingsPreferenceHelper.setNotificationEnabled(context, value) } },
            enabled = true
        )

        Spacer(Modifier.height(12.dp))

        // Haptisches Feedback
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
            onCheckedChange = { value -> scope.launch { SettingsPreferenceHelper.setTimerVibrate(context, value) } },
            enabled = true
        )

        // Sprache wählen (Dropdown)
        Text(
            stringResource(R.string.language),
            color = Color(0xFF7F7FFF),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        LanguageDropdown(
            selectedLangCode = language,
            onSelect = { code ->
                if (activity != null) {
                    LocaleHelper.setAppLocaleAndRestart(activity, code)
                    scope.launch { SettingsPreferenceHelper.setLanguage(context, code) }
                }
            }
        )
    }
}

// --- UNVERÄNDERT: SettingsRow und LanguageDropdown bleiben wie gehabt ---

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
            tint = if (enabled && checked) Color(0xFF7F7FFF) else if (enabled) Color.LightGray else Color(0xFF222222),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier.weight(1f)
        ) {
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
