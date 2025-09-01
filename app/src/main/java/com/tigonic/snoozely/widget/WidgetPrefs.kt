package com.tigonic.snoozely.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Kleine, widget-spezifische SharedPreferences-Helfer.
 * Jeder Eintrag ist pro AppWidgetId gespeichert, damit mehrere Instanzen
 * unabhängig voneinander konfigurierbar sind.
 */
private const val PREFS = "widget_prefs"
private fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

fun saveWidgetDuration(ctx: Context, appWidgetId: Int, minutes: Int) {
    prefs(ctx).edit { putInt("widget_${appWidgetId}_duration", minutes) }
}
fun getWidgetDuration(ctx: Context, appWidgetId: Int, fallback: Int = 15): Int =
    prefs(ctx).getInt("widget_${appWidgetId}_duration", fallback)

fun saveWidgetStyle(ctx: Context, appWidgetId: Int, bgColor: Int, bgAlpha: Float, textColor: Int) {
    prefs(ctx).edit {
        putInt("widget_${appWidgetId}_bgColor", bgColor)
        putFloat("widget_${appWidgetId}_bgAlpha", bgAlpha.coerceIn(0f, 1f))
        putInt("widget_${appWidgetId}_textColor", textColor)
    }
}
fun getWidgetBgColor(ctx: Context, appWidgetId: Int, fallback: Int): Int =
    prefs(ctx).getInt("widget_${appWidgetId}_bgColor", fallback)
fun getWidgetBgAlpha(ctx: Context, appWidgetId: Int, fallback: Float): Float =
    prefs(ctx).getFloat("widget_${appWidgetId}_bgAlpha", fallback)
fun getWidgetTextColor(ctx: Context, appWidgetId: Int, fallback: Int): Int =
    prefs(ctx).getInt("widget_${appWidgetId}_textColor", fallback)

fun deleteWidget(ctx: Context, appWidgetId: Int) {
    prefs(ctx).edit {
        remove("widget_${appWidgetId}_duration")
        remove("widget_${appWidgetId}_bgColor")
        remove("widget_${appWidgetId}_bgAlpha")
        remove("widget_${appWidgetId}_textColor")
    }
}
fun deleteWidgetStyle(ctx: Context, appWidgetId: Int) {
    prefs(ctx).edit {
        remove("widget_${appWidgetId}_bgColor")
        remove("widget_${appWidgetId}_bgAlpha")
        remove("widget_${appWidgetId}_textColor")
    }
}
