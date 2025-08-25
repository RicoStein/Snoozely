package com.tigonic.snoozely.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.tigonic.snoozely.R

class TimerQuickStartWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, TimerQuickStartWidgetProvider::class.java))
        ids.forEach { updateAppWidget(context, mgr, it) }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            updateAppWidget(context, appWidgetManager, id)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_start)

            val intent = Intent(context, TimerStartReceiver::class.java).apply {
                action = "com.tigonic.snoozely.ACTION_START_FROM_WIDGET"
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val pending = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btnStart, pending)

            val minutes = getWidgetDuration(context, appWidgetId, 15)
            val subtitle = context.getString(R.string.widget_quick_start_subtitle) + " " + minutes + " min"
            views.setTextViewText(R.id.txtSubtitle, subtitle)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
