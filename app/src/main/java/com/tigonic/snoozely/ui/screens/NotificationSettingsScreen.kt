package com.tigonic.snoozely.ui

import android.Manifest
import android.os.Build
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
import com.tigonic.snoozely.R
import com.tigonic.snoozely.ui.theme.LocalExtraColors
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val extra = LocalExtraColors.current
    val cs = MaterialTheme.colorScheme

    // Master
    val notificationEnabled by SettingsPreferenceHelper
        .getNotificationEnabled(ctx).collectAsState(initial = true)

    // Statusbar
    val showProgress by SettingsPreferenceHelper
        .getShowProgressNotification(ctx).collectAsState(initial = true)

    // Gemeinsamer Verlängerungs-Schritt (1..30)
    val extendMinutes by SettingsPreferenceHelper
        .getProgressExtendMinutes(ctx).collectAsState(initial = 5)

    // Reminder
    val showReminder by SettingsPreferenceHelper
        .getShowReminderPopup(ctx).collectAsState(initial = false)
    val reminderMinutes by SettingsPreferenceHelper
        .getReminderMinutes(ctx).collectAsState(initial = 5)

    // Android 13+ Permission
    val notifPermissionLauncher =
        if (Build.VERSION.SDK_INT >= 33)
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                scope.launch { SettingsPreferenceHelper.setNotificationEnabled(ctx, granted) }
            }
        else null

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
            // Benachrichtigung aktivieren (toggle)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.notifications_master),
                    color = cs.onBackground,
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
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = extra.toggle,
                        checkedTrackColor = extra.toggle.copy(alpha = 0.35f),
                        uncheckedThumbColor = cs.onSurface,
                        uncheckedTrackColor = cs.onSurface.copy(alpha = 0.20f)
                    )
                )
            }

            // Verlängerungstimer (Slider 1–30)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, start = 4.dp, end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.notifications_extend_minutes, extendMinutes),
                    color = extra.infoText,
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    enabled = notificationEnabled,
                    value = extendMinutes.toFloat(),
                    valueRange = 1f..30f,
                    onValueChange = { v ->
                        scope.launch { SettingsPreferenceHelper.setProgressExtendMinutes(ctx, v.toInt()) }
                    },
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

            // Fortschritt in der Statusleiste anzeigen? (toggle)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.notifications_progress_show),
                    color = cs.onBackground,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    enabled = notificationEnabled,
                    checked = showProgress,
                    onCheckedChange = { v ->
                        scope.launch { SettingsPreferenceHelper.setShowProgressNotification(ctx, v) }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = extra.toggle,
                        checkedTrackColor = extra.toggle.copy(alpha = 0.35f),
                        uncheckedThumbColor = cs.onSurface,
                        uncheckedTrackColor = cs.onSurface.copy(alpha = 0.20f)
                    )
                )
            }

            Divider(color = cs.outlineVariant.copy(alpha = 0.4f))

            // Erinnerung (Überschrift)
            Text(
                text = stringResource(R.string.notifications_section_reminder),
                color = extra.heading,
                style = MaterialTheme.typography.titleMedium
            )

            // Reminder vor Ablauf? (toggle)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.notifications_reminder_show),
                    color = cs.onBackground,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    enabled = notificationEnabled,
                    checked = showReminder,
                    onCheckedChange = { v ->
                        scope.launch { SettingsPreferenceHelper.setShowReminderPopup(ctx, v) }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = extra.toggle,
                        checkedTrackColor = extra.toggle.copy(alpha = 0.35f),
                        uncheckedThumbColor = cs.onSurface,
                        uncheckedTrackColor = cs.onSurface.copy(alpha = 0.20f)
                    )
                )
            }

            // Minuten vorher (Slider 1–10)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.notifications_reminder_minutes, reminderMinutes),
                    color = extra.infoText,
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    enabled = notificationEnabled && showReminder,
                    value = reminderMinutes.toFloat(),
                    valueRange = 1f..10f,
                    onValueChange = { v ->
                        scope.launch { SettingsPreferenceHelper.setReminderMinutes(ctx, v.toInt()) }
                    },
                    colors = SliderDefaults.colors(
                        activeTrackColor = extra.slider,
                        inactiveTrackColor = extra.slider.copy(alpha = 0.30f),
                        thumbColor = extra.slider,
                        activeTickColor = cs.surface.copy(alpha = 0f),
                        inactiveTickColor = cs.surface.copy(alpha = 0f)
                    )
                )
            }

            // Optional: kleiner Bottom-Spacer
            Spacer(Modifier.height(24.dp))
        }
    }
}
