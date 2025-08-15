package com.tigonic.snoozely.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tigonic.snoozely.R
import com.tigonic.snoozely.ui.theme.LocalExtraColors
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val act = ctx as? Activity
    val scope = rememberCoroutineScope()

    // Theme-Objekte
    val cs = MaterialTheme.colorScheme
    val extra = LocalExtraColors.current

    // State aus DataStore
    val notificationEnabled by SettingsPreferenceHelper.getNotificationEnabled(ctx).collectAsState(initial = false)
    val showProgress by SettingsPreferenceHelper.getShowProgressNotification(ctx).collectAsState(initial = false)
    val extendMinutes by SettingsPreferenceHelper.getProgressExtendMinutes(ctx).collectAsState(initial = 5)
    val showReminder by SettingsPreferenceHelper.getShowReminderPopup(ctx).collectAsState(initial = false)
    val reminderMinutes by SettingsPreferenceHelper.getReminderMinutes(ctx).collectAsState(initial = 5)

    var showGoToSettings by remember { mutableStateOf(false) }

    // Runtime-Permission Launcher
    val notifPermissionLauncher =
        if (Build.VERSION.SDK_INT >= 33)
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                scope.launch {
                    if (granted) {
                        SettingsPreferenceHelper.setNotificationEnabled(ctx, true)
                    } else {
                        SettingsPreferenceHelper.setNotificationEnabled(ctx, false)
                        val rational = act?.let {
                            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
                        } ?: false
                        if (!rational) showGoToSettings = true
                    }
                }
            }
        else null

    fun requestOrExplain() {
        if (Build.VERSION.SDK_INT < 33) {
            scope.launch { SettingsPreferenceHelper.setNotificationEnabled(ctx, true) }
            return
        }
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            scope.launch { SettingsPreferenceHelper.setNotificationEnabled(ctx, true) }
            return
        }
        if (act == null) { showGoToSettings = true; return }
        notifPermissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS) ?: run { showGoToSettings = true }
    }

    // Dialog: App-Einstellungen öffnen
    if (showGoToSettings) {
        AlertDialog(
            onDismissRequest = { showGoToSettings = false },
            title = { Text(stringResource(R.string.notif_perm_needed_title), color = cs.onSurface) },
            text = { Text(stringResource(R.string.notif_perm_needed_body), color = cs.onSurface) },
            confirmButton = {
                TextButton(onClick = {
                    showGoToSettings = false
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            act?.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                        } else {
                            act?.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(android.net.Uri.parse("package:${ctx.packageName}")))
                        }
                    } catch (_: Throwable) { }
                }) { Text(stringResource(R.string.open_settings), color = cs.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showGoToSettings = false }) {
                    Text(stringResource(R.string.cancel), color = cs.primary)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_notifications), color = cs.onPrimaryContainer) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = cs.onPrimaryContainer)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.primaryContainer,
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
                .verticalScroll(rememberScrollState())
                .padding(inner)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Master-Toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.notifications_master), color = cs.onBackground, modifier = Modifier.weight(1f))
                Switch(
                    checked = notificationEnabled,
                    onCheckedChange = { v -> if (v) requestOrExplain() else scope.launch { SettingsPreferenceHelper.setNotificationEnabled(ctx, false) } },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = extra.toggle,
                        checkedTrackColor = extra.toggle.copy(alpha = 0.35f),
                        uncheckedThumbColor = cs.onSurface,
                        uncheckedTrackColor = cs.onSurface.copy(alpha = 0.20f)
                    )
                )
            }

            // Verlängerungs-Slider
            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.notifications_extend_minutes, extendMinutes), color = extra.infoText, style = MaterialTheme.typography.bodyMedium)
                Slider(
                    enabled = notificationEnabled,
                    value = extendMinutes.toFloat(),
                    valueRange = 1f..30f,
                    onValueChange = { v -> scope.launch { SettingsPreferenceHelper.setProgressExtendMinutes(ctx, v.toInt()) } },
                    colors = SliderDefaults.colors(
                        activeTrackColor = extra.slider,
                        inactiveTrackColor = extra.slider.copy(alpha = 0.30f),
                        thumbColor = extra.slider,
                        activeTickColor = cs.surface.copy(alpha = 0f),
                        inactiveTickColor = cs.surface.copy(alpha = 0f)
                    )
                )
            }

            Divider(color = cs.outlineVariant.copy(alpha = 0.4f))

            // Fortschritt in Statusleiste
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.notifications_progress_show), color = cs.onBackground, modifier = Modifier.weight(1f))
                Switch(
                    enabled = notificationEnabled,
                    checked = showProgress,
                    onCheckedChange = { v -> scope.launch { SettingsPreferenceHelper.setShowProgressNotification(ctx, v) } },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = extra.toggle,
                        checkedTrackColor = extra.toggle.copy(alpha = 0.35f),
                        uncheckedThumbColor = cs.onSurface,
                        uncheckedTrackColor = cs.onSurface.copy(alpha = 0.20f)
                    )
                )
            }

            Divider(color = cs.outlineVariant.copy(alpha = 0.4f))

            // Reminder-Überschrift
            Text(stringResource(R.string.notifications_section_reminder), color = extra.heading, style = MaterialTheme.typography.titleMedium)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.notifications_reminder_show), color = cs.onBackground, modifier = Modifier.weight(1f))
                Switch(
                    enabled = notificationEnabled,
                    checked = showReminder,
                    onCheckedChange = { v -> scope.launch { SettingsPreferenceHelper.setShowReminderPopup(ctx, v) } },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = extra.toggle,
                        checkedTrackColor = extra.toggle.copy(alpha = 0.35f),
                        uncheckedThumbColor = cs.onSurface,
                        uncheckedTrackColor = cs.onSurface.copy(alpha = 0.20f)
                    )
                )
            }

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.notifications_reminder_minutes, reminderMinutes), color = extra.infoText, style = MaterialTheme.typography.bodyMedium)
                Slider(
                    enabled = notificationEnabled && showReminder,
                    value = reminderMinutes.toFloat(),
                    valueRange = 1f..10f,
                    onValueChange = { v -> scope.launch { SettingsPreferenceHelper.setReminderMinutes(ctx, v.toInt()) } },
                    colors = SliderDefaults.colors(
                        activeTrackColor = extra.slider,
                        inactiveTrackColor = extra.slider.copy(alpha = 0.30f),
                        thumbColor = extra.slider,
                        activeTickColor = cs.surface.copy(alpha = 0f),
                        inactiveTickColor = cs.surface.copy(alpha = 0f)
                    )
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
