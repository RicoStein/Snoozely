package com.tigonic.snoozely.ui.dialog

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import com.tigonic.snoozely.util.TimerPreferenceHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.tigonic.snoozely.R

class ReminderDialogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Diese Flags sorgen dafür, dass die DialogActivity auch auf dem Lockscreen und über anderen Apps erscheint
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        // Für neuere Android-Versionen, zusätzlich:
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        setContent {
            ReminderDialogScreen(
                onExtend = { extendMinutes ->
                    // Dialog beenden und Timer verlängern (sicher im lifecycleScope)
                    lifecycleScope.launch {
                        extendTimer(this@ReminderDialogActivity, extendMinutes)
                        finish()
                    }
                },
                onDismiss = { finish() }
            )
        }
    }

    companion object {
        // Von überall aufrufbar: ReminderDialogActivity.show(context)
        fun show(context: Context) {
            val intent = Intent(context, ReminderDialogActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}

@Composable
fun ReminderDialogScreen(
    onExtend: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var extendMinutes by remember { mutableStateOf(5) } // Default

    // Wert aus DataStore laden (wird nur 1x pro Dialog geladen)
    LaunchedEffect(Unit) {
        extendMinutes = SettingsPreferenceHelper.getProgressExtendMinutes(context).first()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Timer läuft ab!") },
        text = { Text("Der Timer läuft in 5 Minuten ab. Möchtest du um $extendMinutes Minuten verlängern?") },
        confirmButton = {
            TextButton(onClick = { onExtend(extendMinutes) }) {
                Text("+$extendMinutes min")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Schließen")
            }
        },
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = true,
            usePlatformDefaultWidth = false
        )
    )
}

// Suspend-Funktion: Timer wird verlängert, wenn er läuft
suspend fun extendTimer(context: Context, minutesToAdd: Int) {
    val timer = TimerPreferenceHelper.getTimer(context).first()
    val running = TimerPreferenceHelper.getTimerRunning(context).first()
    if (running) {
        val newValue = timer + minutesToAdd
        TimerPreferenceHelper.setTimer(context, newValue)
        TimerPreferenceHelper.startTimer(context, newValue)
    }
}
