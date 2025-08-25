package com.tigonic.snoozely.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.tigonic.snoozely.R
import com.tigonic.snoozely.util.TimerPreferenceHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class TimerQuickStartWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, TimerQuickStartWidgetProvider::class.java))
        ids.forEach { updateAppWidget(context, mgr, it) }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateAppWidget(context, appWidgetManager, id) }
    }

    companion object {
        const val ACTION_TOGGLE = "com.tigonic.snoozely.ACTION_TOGGLE_TIMER_FROM_WIDGET"

        private const val COLOR_IDLE = 0xFF1F2A2E.toInt()
        private const val COLOR_RUNNING = 0xFF284345.toInt()

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_start)

            val minutesConfigured = getWidgetDuration(context, appWidgetId, 15)

            val (isRunning, remainingSec) = runBlocking {
                val running = TimerPreferenceHelper.getTimerRunning(context).first()
                val remaining = if (running) {
                    val startMs = TimerPreferenceHelper.getTimerStartTime(context).first()
                    val totalMin = TimerPreferenceHelper.getTimer(context).first()
                    val endMs = startMs + totalMin * 60_000L
                    val now = System.currentTimeMillis()
                    ((endMs - now) / 1000L).coerceAtLeast(0L)
                } else 0L
                running to remaining
            }

            if (isRunning && remainingSec > 0) {
                val mm = (remainingSec / 60).toInt()
                val ss = (remainingSec % 60).toInt()
                views.setTextViewText(R.id.txtTime, String.format("%02d:%02d", mm, ss))
                views.setImageViewResource(R.id.btnToggle, android.R.drawable.ic_media_pause)
                views.setInt(R.id.root, "setBackgroundColor", COLOR_RUNNING)
            } else {
                views.setTextViewText(R.id.txtTime, minutesConfigured.toString())
                views.setImageViewResource(R.id.btnToggle, android.R.drawable.ic_media_play)
                views.setInt(R.id.root, "setBackgroundColor", COLOR_IDLE)
            }

            // Toggle
            val toggleIntent = Intent(context, WidgetCommandReceiver::class.java).apply {
                action = ACTION_TOGGLE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val togglePending = PendingIntent.getBroadcast(
                context, appWidgetId, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btnToggle, togglePending)

            // Direkter Config-Button (zusätzlich zum Long-Press im Launcher)
            val configIntent = Intent(context, TimerWidgetConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val configPending = PendingIntent.getActivity(
                context, 10_000 + appWidgetId, configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btnConfig, configPending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun requestUpdateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, TimerQuickStartWidgetProvider::class.java))
            ids.forEach { updateAppWidget(context, mgr, it) }
        }


    }
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        // Nach einer Re-Konfiguration direkt neu rendern
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

}
