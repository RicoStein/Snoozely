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
import com.tigonic.snoozely.ui.components.PremiumPaywallDialog
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
                val scope = rememberCoroutineScope()
                var isPremium by remember { mutableStateOf(false) }
                var loaded by remember { mutableStateOf(false) }
                var showPaywall by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    val v = SettingsPreferenceHelper.getPremiumActive(applicationContext).first()
                    isPremium = v
                    loaded = true
                    if (!v) showPaywall = true
                }

                if (!loaded) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = stringResource(R.string.appLoading))
                    }
                } else if (isPremium) {
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

                if (showPaywall) {
                    PremiumPaywallDialog(
                        onClose = {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        },
                        onPurchase = {
                            scope.launch {
                                SettingsPreferenceHelper.setPremiumActive(applicationContext, true)
                                isPremium = true
                                showPaywall = false
                            }
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
        modifier = Modifier.fillMaxSize().padding(20.dp),
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
