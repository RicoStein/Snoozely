package com.tigonic.snoozely.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

private const val PREFS = "widget_prefs"
private fun prefs(ctx: Context): SharedPreferences =
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

fun saveWidgetDuration(ctx: Context, appWidgetId: Int, minutes: Int) {
    prefs(ctx).edit { putInt("widget_${appWidgetId}_duration", minutes) }
}

fun getWidgetDuration(ctx: Context, appWidgetId: Int, fallback: Int = 15): Int =
    prefs(ctx).getInt("widget_${appWidgetId}_duration", fallback)

fun deleteWidget(ctx: Context, appWidgetId: Int) {
    prefs(ctx).edit { remove("widget_${appWidgetId}_duration") }
}
