package com.tigonic.snoozely.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.appwidget.AppWidgetManager
import com.tigonic.snoozely.service.TimerContracts
import com.tigonic.snoozely.service.TimerEngineService
import com.tigonic.snoozely.ui.screens.startForegroundServiceCompat
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import com.tigonic.snoozely.util.TimerPreferenceHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class TimerStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val isPremium = runBlocking { SettingsPreferenceHelper.getPremiumActive(context).first() }
        if (!isPremium) {
            Toast.makeText(context, "Premium erforderlich – bitte freischalten.", Toast.LENGTH_SHORT).show()
            return
        }

        val minutes = getWidgetDuration(context, appWidgetId, 15)
        val startIntent = Intent(context, TimerEngineService::class.java)
            .setAction(TimerContracts.ACTION_START)
            .putExtra(TimerContracts.EXTRA_MINUTES, minutes)

        context.startForegroundServiceCompat(startIntent)
        runBlocking { TimerPreferenceHelper.startTimer(context, minutes) }
    }
}
