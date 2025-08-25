package com.tigonic.snoozely.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.tigonic.snoozely.R
import com.tigonic.snoozely.service.TimerContracts
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import com.tigonic.snoozely.util.TimerPreferenceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import com.tigonic.snoozely.widget.getWidgetDuration
import com.tigonic.snoozely.widget.deleteWidget

private const val TAG = "WidgetProvider"

class TimerQuickStartWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called for widget IDs: ${appWidgetIds.joinToString()}")
        // goAsync() ist die empfohlene Methode für Hintergrundarbeit in einem BroadcastReceiver
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {
            for (appWidgetId in appWidgetIds) {
                try {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update widget $appWidgetId in onUpdate", e)
                }
            }
            pendingResult.finish() // System mitteilen, dass die Arbeit erledigt ist
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        Log.d(TAG, "onDeleted called for widget IDs: ${appWidgetIds.joinToString()}")
        for (appWidgetId in appWidgetIds) {
            // Korrekt aus den SharedPreferences löschen
            deleteWidget(context, appWidgetId)
        }
    }

    companion object {
        @SuppressLint("RemoteViewLayout")
        suspend fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            try {
                val running = TimerPreferenceHelper.getTimerRunning(context).first()
                val startTime = TimerPreferenceHelper.getTimerStartTime(context).first()
                val totalMinutes = getWidgetDuration(context, appWidgetId)

                val views = RemoteViews(context.packageName, R.layout.widget_quick_start)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

                if (running && startTime > 0) {
                    val totalMs = totalMinutes * 60_000L
                    val elapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(0)
                    val remainingMs = (totalMs - elapsedMs).coerceAtLeast(0)
                    val remainingMinutes = (remainingMs / 60_000L).toInt()
                    val progress = if (totalMs > 0) (remainingMs * 100 / totalMs).toInt() else 0

                    views.setTextViewText(R.id.txtTime, remainingMinutes.toString())
                    views.setProgressBar(R.id.progress_bar, 100, progress, false)
                    views.setViewVisibility(R.id.progress_bar, View.VISIBLE)

                    val stopIntent = Intent(context, TimerStartReceiver::class.java).apply { action = TimerContracts.ACTION_STOP }
                    val stopPendingIntent = PendingIntent.getBroadcast(context, appWidgetId, stopIntent, flags)
                    views.setOnClickPendingIntent(R.id.widget_root, stopPendingIntent)

                } else {
                    views.setTextViewText(R.id.txtTime, totalMinutes.toString())
                    views.setViewVisibility(R.id.progress_bar, View.GONE)

                    val startIntent = Intent(context, TimerStartReceiver::class.java).apply {
                        action = TimerContracts.ACTION_START
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    }
                    val startPendingIntent = PendingIntent.getBroadcast(context, appWidgetId, startIntent, flags)
                    views.setOnClickPendingIntent(R.id.widget_root, startPendingIntent)
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                Log.e(TAG, "Error in suspend updateAppWidget for $appWidgetId", e)
            }
        }

        fun requestUpdateAll(context: Context) {
            val intent = Intent(context, TimerQuickStartWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val ids = appWidgetManager.getAppWidgetIds(android.content.ComponentName(context, TimerQuickStartWidgetProvider::class.java))
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}
