package com.tigonic.snoozely.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tigonic.snoozely.service.TimerContracts
import com.tigonic.snoozely.service.TimerEngineService
import kotlinx.coroutines.runBlocking

class TimerStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TimerContracts.ACTION_START -> handleStart(context, intent)
            TimerContracts.ACTION_STOP -> handleStop(context)
        }
    }

    private fun handleStart(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val minutes = getWidgetDuration(context, appWidgetId, 15)

        val startIntent = Intent(context, TimerEngineService::class.java).apply {
            action = TimerContracts.ACTION_START
            putExtra(TimerContracts.EXTRA_MINUTES, minutes)
        }
        context.startService(startIntent)
    }

    private fun handleStop(context: Context) {
        val stopIntent = Intent(context, TimerEngineService::class.java).apply {
            action = TimerContracts.ACTION_STOP
        }
        context.startService(stopIntent)
    }
}
