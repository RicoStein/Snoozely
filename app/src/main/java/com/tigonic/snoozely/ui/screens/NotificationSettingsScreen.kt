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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tigonic.snoozely.R
import com.tigonic.snoozely.ui.components.VerticalScrollbar
import com.tigonic.snoozely.ui.theme.LocalExtraColors
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val act = ctx as? Activity
    val scope = rememberCoroutineScope()

    val cs = MaterialTheme.colorScheme
    val extra = LocalExtraColors.current

    // DataStore States
    val notificationEnabled by SettingsPreferenceHelper.getNotificationEnabled(ctx).collectAsState(initial = false)
    val showProgress by SettingsPreferenceHelper.getShowProgressNotification(ctx).collectAsState(initial = false)
    val extendMinutes by SettingsPreferenceHelper.getProgressExtendMinutes(ctx).collectAsState(initial = 5)
    val showReminder by SettingsPreferenceHelper.getShowReminderPopup(ctx).collectAsState(initial = false)
    val reminderMinutes by SettingsPreferenceHelper.getReminderMinutes(ctx).collectAsState(initial = 5)

    // Optische Dimmung für deaktivierte Abschnitte (wie im Shake-Screen)
    val sectionAlpha = if (notificationEnabled) 1f else 0.5f

    // Permission Dialog
    var showGoToSettings by remember { mutableStateOf(false) }
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

    if (showGoToSettings) {
        AlertDialog(
            onDismissRequest = { showGoToSettings = false },
            title = { Text(stringResource(R.string.notif_perm_needed_title), color = cs.onSurface) },
            text = { Text(stringResource(R.string.notif_perm_needed_body), color = cs.onSurface) },
            confirmButton = {
                TextButton(onClick = {
                    showGoToSettings = false
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            act?.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                            )
                        } else {
                            act?.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(android.net.Uri.parse("package:${ctx.packageName}"))
                            )
                        }
                    }
                }) { Text(stringResource(R.string.open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showGoToSettings = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Slider-Farben wie im Shake-Screen
    val sliderColors = SliderDefaults.colors(
        activeTrackColor = extra.slider,
        inactiveTrackColor = extra.slider.copy(alpha = 0.30f),
        thumbColor = extra.slider,
        activeTickColor = cs.surface.copy(alpha = 0f),
        inactiveTickColor = cs.surface.copy(alpha = 0f)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_notifications), color = cs.onPrimaryContainer) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = cs.onPrimaryContainer)
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
                // 1) Master-Schalter
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.notifications_master),
                        color = cs.onBackground,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = notificationEnabled,
                        onCheckedChange = { v ->
                            if (v) requestOrExplain()
                            else scope.launch { SettingsPreferenceHelper.setNotificationEnabled(ctx, false) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = extra.toggle,
                            checkedTrackColor = extra.toggle.copy(alpha = 0.35f),
                            uncheckedThumbColor = cs.onSurface,
                            uncheckedTrackColor = cs.onSurface.copy(alpha = 0.20f)
                        )
                    )
                }
                HorizontalDivider(color = extra.divider)

                // 2) Verlängerungs-Timer (gedimmt + deaktiviert wenn Master off)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.notifications_extend_title),
                    color = cs.onBackground,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.alpha(sectionAlpha)
                )
                Text(
                    text = stringResource(R.string.notifications_extend_minutes, extendMinutes),
                    color = extra.infoText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.alpha(sectionAlpha)
                )
                Slider(
                    enabled = notificationEnabled,
                    value = extendMinutes.toFloat(),
                    onValueChange = { v -> if (notificationEnabled) scope.launch { SettingsPreferenceHelper.setProgressExtendMinutes(ctx, v.toInt()) } },
                    valueRange = 1f..30f,
                    steps = 29,
                    colors = sliderColors,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .alpha(sectionAlpha)
                )
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = extra.divider)

                // 3) Fortschritt in Statusleiste (gedimmt + deaktivierter Switch bei Master off)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .alpha(sectionAlpha),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.notifications_progress_show),
                        color = cs.onBackground,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        enabled = notificationEnabled,
                        checked = showProgress,
                        onCheckedChange = { v -> if (notificationEnabled) scope.launch { SettingsPreferenceHelper.setShowProgressNotification(ctx, v) } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = extra.toggle,
                            checkedTrackColor = extra.toggle.copy(alpha = 0.35f),
                            uncheckedThumbColor = cs.onSurface,
                            uncheckedTrackColor = cs.onSurface.copy(alpha = 0.20f)
                        )
                    )
                }
                Text(
                    text = stringResource(R.string.notifications_progress_hint),
                    color = extra.infoText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(top = 2.dp, bottom = 8.dp)
                        .alpha(sectionAlpha)
                )
                HorizontalDivider(color = extra.divider)

                // 4) Reminder (OHNE Header „Erinnerung“; gedimmt + deaktiviert bei Master off)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .alpha(sectionAlpha),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.notifications_reminder_show),
                        color = cs.onBackground,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        enabled = notificationEnabled,
                        checked = showReminder,
                        onCheckedChange = { v -> if (notificationEnabled) scope.launch { SettingsPreferenceHelper.setShowReminderPopup(ctx, v) } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = extra.toggle,
                            checkedTrackColor = extra.toggle.copy(alpha = 0.35f),
                            uncheckedThumbColor = cs.onSurface,
                            uncheckedTrackColor = cs.onSurface.copy(alpha = 0.20f)
                        )
                    )
                }

                Text(
                    text = stringResource(R.string.notifications_reminder_minutes, reminderMinutes),
                    color = extra.infoText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.alpha(sectionAlpha)
                )
                Slider(
                    enabled = notificationEnabled && showReminder,
                    value = reminderMinutes.toFloat(),
                    onValueChange = { v -> if (notificationEnabled && showReminder) scope.launch { SettingsPreferenceHelper.setReminderMinutes(ctx, v.toInt()) } },
                    valueRange = 1f..10f,
                    steps = 9,
                    colors = sliderColors,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .alpha(sectionAlpha)
                )

                Spacer(Modifier.height(24.dp))
            }

            // Rechte, dezente Scrollbar
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
