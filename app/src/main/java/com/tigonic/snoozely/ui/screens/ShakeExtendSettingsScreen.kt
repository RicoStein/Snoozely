package com.tigonic.snoozely.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.media.RingtoneManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tigonic.snoozely.R
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShakeExtendSettingsScreen(
    onBack: () -> Unit,
    onNavigateShakeStrength: () -> Unit,
    onPickSound: () -> Unit // optional
) {
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    val enabled by SettingsPreferenceHelper.getShakeEnabled(ctx).collectAsState(initial = false)
    val extendMin by SettingsPreferenceHelper.getShakeExtendMinutes(ctx).collectAsState(initial = 10)
    val mode by SettingsPreferenceHelper.getShakeSoundMode(ctx).collectAsState(initial = "tone")
    val volume by SettingsPreferenceHelper.getShakeVolume(ctx).collectAsState(initial = 1f)
    val ringtone by SettingsPreferenceHelper.getShakeRingtone(ctx).collectAsState(initial = "")

    val activity = LocalContext.current
    val ringtoneLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val data = res.data
            val uri: Uri? =
                data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                scope.launch { SettingsPreferenceHelper.setShakeRingtone(ctx, uri.toString()) }
                scope.launch { SettingsPreferenceHelper.setShakeSoundMode(ctx, "tone") }
            }
        }

    fun openRingtonePicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_TYPE,
                RingtoneManager.TYPE_NOTIFICATION
            )
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_TITLE,
                activity.getString(R.string.notification_sound)
            )
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                Settings.System.DEFAULT_NOTIFICATION_URI
            )
        }
        ringtoneLauncher.launch(intent)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shake_to_extend)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF101010),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
        containerColor = Color(0xFF101010),
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(inner)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.enabled),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = { v ->
                        scope.launch { SettingsPreferenceHelper.setShakeEnabled(ctx, v) }
                    }
                )
            }

            Divider(color = Color(0x22FFFFFF))

            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.shake_strength), color = Color.White)
                },
                supportingContent = {
                    Text(stringResource(R.string.shake_strength_sub), color = Color.Gray)
                },
                leadingContent = {
                    Icon(Icons.Default.Vibration, null, tint = Color(0xFF7F7FFF))
                },
                trailingContent = {
                    Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
                },
                modifier = Modifier.clickable { onNavigateShakeStrength() }
            )

            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.extend_by_minutes),
                color = Color.LightGray,
                style = MaterialTheme.typography.titleMedium
            )
            Text(stringResource(R.string.timer_plus_x, extendMin), color = Color.Gray)
            Slider(
                value = extendMin.toFloat(),
                onValueChange = { v ->
                    scope.launch {
                        SettingsPreferenceHelper.setShakeExtendMinutes(ctx, v.toInt())
                    }
                },
                valueRange = 1f..30f,
                steps = 29,
                colors = SliderDefaults.colors(
                    activeTrackColor = Color(0xFF7F7FFF),
                    inactiveTrackColor = Color(0x33444444),
                    thumbColor = Color(0xFF7F7FFF),
                ),
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Divider(color = Color(0x22FFFFFF), modifier = Modifier.padding(vertical = 8.dp))

            Text(
                stringResource(R.string.notification_sound),
                color = Color.LightGray,
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = (mode == "tone"),
                    onClick = { openRingtonePicker() },
                    label = { Text(stringResource(R.string.pick_tone)) },
                    leadingIcon = { Icon(Icons.Default.MusicNote, null) }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = (mode == "vibrate"),
                    onClick = {
                        scope.launch {
                            SettingsPreferenceHelper.setShakeSoundMode(ctx, "vibrate")
                            SettingsPreferenceHelper.setShakeRingtone(ctx, "")
                        }
                    },
                    label = { Text(stringResource(R.string.vibrate)) },
                    leadingIcon = { Icon(Icons.Default.Vibration, null) }
                )
            }
            if (mode == "tone" && ringtone.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.current_tone_uri, ringtone),
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.notification_volume),
                color = Color.LightGray,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.volume_relative_hint),
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
            Slider(
                value = volume,
                onValueChange = { v ->
                    scope.launch { SettingsPreferenceHelper.setShakeVolume(ctx, v) }
                },
                valueRange = 0f..1f,
                steps = 0,
                colors = SliderDefaults.colors(
                    activeTrackColor = Color(0xFFFFD000),
                    inactiveTrackColor = Color(0x33444444),
                    thumbColor = Color(0xFFFFD000),
                ),
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
