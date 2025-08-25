package com.tigonic.snoozely.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tigonic.snoozely.R
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import kotlinx.coroutines.flow.first

class TimerWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish(); return
        }

        setContent {
            MaterialTheme {
                var isPremium by remember { mutableStateOf(false) }
                var loaded by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    val v = SettingsPreferenceHelper.getPremiumActive(applicationContext).first()
                    if (!v) {
                        val intent = Intent(
                            this@TimerWidgetConfigActivity,
                            com.tigonic.snoozely.MainActivity::class.java
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            putExtra("showPaywall", true)
                            putExtra("source", "widget_config")
                        }
                        startActivity(intent)
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    } else {
                        isPremium = true
                        loaded = true
                    }
                }

                if (loaded && isPremium) {
                    ConfigContent(
                        onConfirm = { minutes ->
                            saveWidgetDuration(applicationContext, appWidgetId, minutes)
                            val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
                            TimerQuickStartWidgetProvider.updateAppWidget(
                                applicationContext,
                                appWidgetManager,
                                appWidgetId
                            )
                            val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            setResult(Activity.RESULT_OK, result)
                            finish()
                        },
                        onCancel = {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigContent(onConfirm: (Int) -> Unit, onCancel: () -> Unit) {
    var minutes by remember { mutableStateOf(15f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.widget_config_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.widget_config_duration) + ": " + minutes.toInt() + " min")
        Spacer(Modifier.height(8.dp))
        Slider(
            value = minutes,
            onValueChange = { minutes = it.coerceIn(1f, 120f) },
            valueRange = 1f..120f,
            steps = 119
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            Button(onClick = { onConfirm(minutes.toInt()) }) { Text(stringResource(R.string.widget_add)) }
        }
    }
}
