package com.tigonic.snoozely

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.util.Log

class SimpleWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context?) {
        super.onEnabled(context)
        Log.d("???","onEnabled")
    }

    override fun onUpdate(
        context: Context?,
        appWidgetManager: AppWidgetManager?,
        appWidgetIds: IntArray?
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        Log.d("???","onUpdate")
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        super.onDeleted(context, appWidgetIds)

        Log.d("???","onDeleted $appWidgetIds")
    }

    override fun onDisabled(context: Context?) {
        super.onDisabled(context)

        Log.d("???","onDisabled")
    }
}