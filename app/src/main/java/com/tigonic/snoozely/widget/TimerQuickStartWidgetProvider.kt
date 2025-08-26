package com.tigonic.snoozely.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.widget.RemoteViews
import com.tigonic.snoozely.R
import com.tigonic.snoozely.service.TimerContracts
import com.tigonic.snoozely.service.TimerEngineService
import com.tigonic.snoozely.util.TimerPreferenceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.sin

private const val TAG = "WidgetProvider"

class TimerQuickStartWidgetProvider : AppWidgetProvider() {

    // onUpdate wird jetzt einfacher und ruft nur noch die neue Logik auf.
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
            pendingResult.finish()
        }
    }

    // onAppWidgetOptionsChanged wird aufgerufen, wenn der Nutzer die Widget-Größe ändert.
    // Wir lösen hier einfach ein manuelles Update aus.
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        Log.d(TAG, "onDeleted called for widget IDs: ${appWidgetIds.joinToString()}")
    }

    companion object {
        @SuppressLint("RemoteViewLayout")
        suspend fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_quick_start)

                // --- START DER NEUEN LOGIK ---
                // Lese die Widget-Größe in DP
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                val isPortrait = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                val width = options.getInt(if (isPortrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
                val height = options.getInt(if (isPortrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

                // Finde die kleinere der beiden Dimensionen, das wird die Seite unseres Quadrats
                val side = min(width, height)

                // Berechne den horizontalen und vertikalen Abstand, um das Quadrat zu zentrieren
                val horizontalPadding = (width - side) / 2
                val verticalPadding = (height - side) / 2

                // Konvertiere die DP-Werte in Pixel, da setViewPadding Pixel erwartet
                val displayMetrics = context.resources.displayMetrics
                val horizontalPaddingPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, horizontalPadding.toFloat(), displayMetrics).toInt()
                val verticalPaddingPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, verticalPadding.toFloat(), displayMetrics).toInt()

                // Wende das Padding auf das Root-Layout an. Dies erzeugt unsere quadratische Zeichenfläche.
                views.setViewPadding(R.id.widget_root, horizontalPaddingPx, verticalPaddingPx, horizontalPaddingPx, verticalPaddingPx)
                // --- ENDE DER NEUEN LOGIK ---


                // Der Rest des Codes bleibt fast gleich
                val running = TimerPreferenceHelper.getTimerRunning(context).first()
                val startTime = TimerPreferenceHelper.getTimerStartTime(context).first()
                val totalMinutes = TimerPreferenceHelper.getTimer(context).first()
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)

                if (running && startTime > 0) {
                    val totalMs = totalMinutes * 60_000L
                    val elapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(0)
                    val remainingMs = (totalMs - elapsedMs).coerceAtLeast(0)
                    val remainingMinutes = (remainingMs / 60_000L).toInt()
                    val progress = if (totalMs > 0) ((remainingMs * 1000) / totalMs).toInt() else 0

                    views.setProgressBar(R.id.widget_progressbar, 1000, progress, false)
                    views.setTextViewText(R.id.txtTime, remainingMinutes.toString())
                    views.setTextColor(R.id.txtTime, getAnimatedBlueColor())

                    val stopIntent = Intent(context, TimerEngineService::class.java).apply { action = TimerContracts.ACTION_STOP }
                    val stopPendingIntent = PendingIntent.getForegroundService(context, appWidgetId, stopIntent, flags)
                    views.setOnClickPendingIntent(R.id.widget_root, stopPendingIntent)
                } else {
                    views.setProgressBar(R.id.widget_progressbar, 1000, 0, false)
                    val configMinutes = getWidgetDuration(context, appWidgetId, totalMinutes)
                    views.setTextViewText(R.id.txtTime, configMinutes.toString())
                    views.setTextColor(R.id.txtTime, Color.WHITE)

                    val startIntent = Intent(context, TimerEngineService::class.java).apply {
                        action = TimerContracts.ACTION_START
                        putExtra(TimerContracts.EXTRA_MINUTES, configMinutes)
                    }
                    val startPendingIntent = PendingIntent.getForegroundService(context, appWidgetId, startIntent, flags)
                    views.setOnClickPendingIntent(R.id.widget_root, startPendingIntent)
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                Log.e(TAG, "Error in suspend updateAppWidget for $appWidgetId", e)
            }
        }

        private fun getAnimatedBlueColor(): Int {
            val time = System.currentTimeMillis() / 800.0
            val t = (sin(time) + 1) / 2.0
            val r1 = 66; val g1 = 133; val b1 = 244;
            val r2 = 3; val g2 = 218; val b2 = 197;
            val r = (r1 + t * (r2 - r1)).toInt()
            val g = (g1 + t * (g2 - g1)).toInt()
            val b = (b1 + t * (b2 - b1)).toInt()
            return Color.rgb(r, g, b)
        }

        private fun getWidgetDuration(ctx: Context, appWidgetId: Int, fallback: Int = 15): Int {
            val prefs = ctx.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            return prefs.getInt("widget_${appWidgetId}_duration", fallback)
        }
    }
}
