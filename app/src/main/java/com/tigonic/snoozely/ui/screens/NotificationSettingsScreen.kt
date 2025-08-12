package com.tigonic.snoozely.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tigonic.snoozely.R
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // Master + gemeinsame Basis
    val notificationEnabled by SettingsPreferenceHelper.getNotificationEnabled(ctx).collectAsState(initial = true)

    // Statusbar-Progress
    val showProgress by SettingsPreferenceHelper.getShowProgressNotification(ctx).collectAsState(initial = true)
    val progressExtendEnabled by SettingsPreferenceHelper.getProgressExtendEnabled(ctx).collectAsState(initial = true)
    val progressExtendMinutes by SettingsPreferenceHelper.getProgressExtendMinutes(ctx).collectAsState(initial = 5)

    // Reminder (Heads-up)
    val showReminder by SettingsPreferenceHelper.getShowReminderPopup(ctx).collectAsState(initial = false)
    val reminderMinutes by SettingsPreferenceHelper.getReminderMinutes(ctx).collectAsState(initial = 5)
    val reminderExtendEnabled by SettingsPreferenceHelper.getReminderExtendEnabled(ctx).collectAsState(initial = true)
    val reminderExtendMinutes by SettingsPreferenceHelper.getReminderExtendMinutes(ctx).collectAsState(initial = progressExtendMinutes)

    // Android 13+: POST_NOTIFICATIONS Permission
    val notifPermissionLauncher =
        if (Build.VERSION.SDK_INT >= 33)
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                scope.launch { SettingsPreferenceHelper.setNotificationEnabled(ctx, granted) }
            }
        else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_notifications)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ————— Master —————
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.notifications_master),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = notificationEnabled,
                    onCheckedChange = { v ->
                        if (Build.VERSION.SDK_INT >= 33 && v && notifPermissionLauncher != null) {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            scope.launch { SettingsPreferenceHelper.setNotificationEnabled(ctx, v) }
                        }
                    }
                )
            }

            Divider()

            // ————— Statusbar: Fortschritt —————
            Text(
                text = stringResource(R.string.notifications_section_progress),
                style = MaterialTheme.typography.titleMedium
            )

            // Fortschritt anzeigen
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.notifications_progress_show),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    enabled = notificationEnabled,
                    checked = showProgress,
                    onCheckedChange = { v -> scope.launch { SettingsPreferenceHelper.setShowProgressNotification(ctx, v) } }
                )
            }

            // „Verlängern“-Button
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.notifications_progress_extend_btn),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    enabled = notificationEnabled && showProgress,
                    checked = progressExtendEnabled,
                    onCheckedChange = { v -> scope.launch { SettingsPreferenceHelper.setProgressExtendEnabled(ctx, v) } }
                )
            }

            // +X Minuten (Statusbar)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Slider(
                    enabled = notificationEnabled && showProgress && progressExtendEnabled,
                    value = progressExtendMinutes.toFloat(),
                    valueRange = 1f..30f,
                    onValueChange = { v -> scope.launch { SettingsPreferenceHelper.setProgressExtendMinutes(ctx, v.toInt()) } }
                )
                Text(
                    text = stringResource(R.string.notifications_extend_minutes, progressExtendMinutes),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Divider()

            // ————— Reminder (Heads-up) —————
            Text(
                text = stringResource(R.string.notifications_section_reminder),
                style = MaterialTheme.typography.titleMedium
            )

            // Reminder anzeigen
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.notifications_reminder_show),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    enabled = notificationEnabled,
                    checked = showReminder,
                    onCheckedChange = { v -> scope.launch { SettingsPreferenceHelper.setShowReminderPopup(ctx, v) } }
                )
            }

            // Minuten vor Ablauf
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Slider(
                    enabled = notificationEnabled && showReminder,
                    value = reminderMinutes.toFloat(),
                    valueRange = 1f..10f,
                    onValueChange = { v -> scope.launch { SettingsPreferenceHelper.setReminderMinutes(ctx, v.toInt()) } }
                )
                Text(
                    text = stringResource(R.string.notifications_reminder_minutes, reminderMinutes),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // „Verlängern“-Button im Reminder
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.notifications_reminder_extend_btn),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    enabled = notificationEnabled && showReminder,
                    checked = reminderExtendEnabled,
                    onCheckedChange = { v -> scope.launch { SettingsPreferenceHelper.setReminderExtendEnabled(ctx, v) } }
                )
            }

            // +X Minuten (Reminder – eigener Wert; sonst liest der Helper automatisch den Progress-Wert)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Slider(
                    enabled = notificationEnabled && showReminder && reminderExtendEnabled,
                    value = reminderExtendMinutes.toFloat(),
                    valueRange = 1f..30f,
                    onValueChange = { v -> scope.launch { SettingsPreferenceHelper.setReminderExtendMinutes(ctx, v.toInt()) } }
                )
                Text(
                    text = stringResource(R.string.notifications_extend_minutes, reminderExtendMinutes),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
