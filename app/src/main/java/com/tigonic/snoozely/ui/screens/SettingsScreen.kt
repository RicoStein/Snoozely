package com.tigonic.snoozely.ui.screens

import android.app.Activity
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
import android.os.Build

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var stopAudio by remember { mutableStateOf(true) }
    var screenOff by remember { mutableStateOf(false) }
    var notificationEnabled by remember { mutableStateOf(false) }
    var timerVibrate by remember { mutableStateOf(false) }
    var fadeOut by remember { mutableStateOf(30f) }

    val context = LocalContext.current

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

        // Bereich: Sleep Timer (blauer Titel-Link)
        Text(
            stringResource(R.string.sleep_timer),
            color = Color(0xFF7F7FFF),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )

        // Wiedergabe stoppen (mit Switch)
        SettingsRow(
            icon = Icons.Default.PlayCircleFilled,
            title = stringResource(R.string.playback),
            subtitle = stringResource(R.string.stop_audio_video),
            checked = stopAudio,
            onCheckedChange = { stopAudio = it },
            enabled = true
        )

        // Fade-Out Dauer (Slider)
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
            onValueChange = { fadeOut = it },
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

        // Bildschirm ausschalten
        SettingsRow(
            icon = Icons.Default.Brightness2,
            title = stringResource(R.string.screen),
            subtitle = stringResource(R.string.turn_off_screen),
            checked = screenOff,
            onCheckedChange = { screenOff = it },
            enabled = true
        )

        // Bluetooth deaktiviert
        SettingsRow(
            icon = Icons.Default.BluetoothDisabled,
            title = stringResource(R.string.bluetooth),
            subtitle = stringResource(R.string.bluetooth_android_13_removed),
            checked = false,
            onCheckedChange = {},
            enabled = false
        )

        // WLAN deaktiviert
        SettingsRow(
            icon = Icons.Default.WifiOff,
            title = stringResource(R.string.wifi),
            subtitle = stringResource(R.string.wifi_android_10_removed),
            checked = false,
            onCheckedChange = {},
            enabled = false
        )

        Spacer(Modifier.height(12.dp))

        // Benachrichtigung (blauer Titel-Link)
        Text(
            stringResource(R.string.notification),
            color = Color(0xFF7F7FFF),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        )

        // Benachrichtigung aktivieren
        SettingsRow(
            icon = Icons.Default.Notifications,
            title = stringResource(R.string.enable_notification),
            subtitle = stringResource(R.string.show_remaining_time),
            checked = notificationEnabled,
            onCheckedChange = { notificationEnabled = it },
            enabled = true
        )

        Spacer(Modifier.height(12.dp))

        // Haptisches Feedback (als blauer Link)
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
            onCheckedChange = { timerVibrate = it },
            enabled = true
        )

        // Sprache wählen (Dropdown)
        Text(
            stringResource(R.string.language),
            color = Color(0xFF7F7FFF),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        LanguageDropdown()
    }
}

// Hilfsfunktion für eine Einstellungszeile
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
fun LanguageDropdown() {
    val context = LocalContext.current
    val activity = context as? Activity
    var expanded by remember { mutableStateOf(false) }

    // Sprachzuordnung: Anzeigename → ISO-Code
    val languageMap = mapOf(
        stringResource(R.string.german) to "de",
        stringResource(R.string.english) to "en",
        stringResource(R.string.french) to "fr"
    )
    val languages = languageMap.keys.toList()

    // Hole aktuelle Sprache jedes Mal neu (immer im Compose-Kontext!)
    val currentLangCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.resources.configuration.locales[0].language
    } else {
        @Suppress("DEPRECATION")
        context.resources.configuration.locale.language
    }
    // Ermittle das aktuelle Label (aus Map, passend zum Sprach-Code)
    val initialLabel = languageMap.entries.firstOrNull { it.value == currentLangCode }?.key ?: languages.first()

    // selectedLanguage NICHT in remember block initialisieren!
    var selectedLanguage by remember { mutableStateOf(initialLabel) }

    // Update das Label, wenn sich die aktuelle Sprache geändert hat
    LaunchedEffect(currentLangCode) {
        val newLabel = languageMap.entries.firstOrNull { it.value == currentLangCode }?.key ?: languages.first()
        selectedLanguage = newLabel
    }

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
                text = selectedLanguage,
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
            languages.forEach { lang ->
                DropdownMenuItem(
                    text = { Text(lang, color = Color.White) },
                    onClick = {
                        selectedLanguage = lang
                        expanded = false
                        languageMap[lang]?.let { code ->
                            if (activity != null) {
                                // Sprache wechseln und App neustarten
                                LocaleHelper.setAppLocaleAndRestart(activity, code)
                            }
                        }
                    }
                )
            }
        }
    }
}
