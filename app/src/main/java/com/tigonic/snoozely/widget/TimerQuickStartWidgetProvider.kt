package com.tigonic.snoozely.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.*
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
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)

                val isPortrait = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                val widthDp = options.getInt(if (isPortrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
                val heightDp = options.getInt(if (isPortrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

                val sideDp = min(widthDp, heightDp)
                if (sideDp <= 0) return

                val displayMetrics = context.resources.displayMetrics
                val sidePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, sideDp.toFloat(), displayMetrics).toInt()
                if (sidePx <= 0) return

                val bitmap = Bitmap.createBitmap(sidePx, sidePx, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)

                paint.color = Color.BLACK
                paint.style = Paint.Style.FILL
                canvas.drawCircle(sidePx / 2f, sidePx / 2f, sidePx / 2f, paint)

                val running = TimerPreferenceHelper.getTimerRunning(context).first()
                val startTime = TimerPreferenceHelper.getTimerStartTime(context).first()
                val totalMinutes = TimerPreferenceHelper.getTimer(context).first()

                if (running && startTime > 0) {
                    paint.color = Color.parseColor("#4285F4")
                    paint.style = Paint.Style.STROKE
                    val strokeWidth = sidePx / 14f
                    paint.strokeWidth = strokeWidth
                    paint.strokeCap = Paint.Cap.BUTT

                    val oval = RectF(strokeWidth / 2f, strokeWidth / 2f, sidePx - strokeWidth / 2f, sidePx - strokeWidth / 2f)

                    val totalMs = totalMinutes * 60_000L
                    val elapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(0)
                    val remainingMs = (totalMs - elapsedMs).coerceAtLeast(0)
                    val progress = if (totalMs > 0) remainingMs.toFloat() / totalMs.toFloat() else 0f
                    val sweepAngle = progress * 360f

                    canvas.drawArc(oval, -90f, sweepAngle, false, paint)

                    val remainingMinutes = (remainingMs / 60_000L).toInt()
                    val numberString = remainingMinutes.toString()

                    // --- START: DYNAMISCHE SCHRIFTGRÖSSE ---
                    // Basisgröße ist proportional zur Widget-Größe (z.B. 1/3 des Durchmessers)
                    var textSizePx = sidePx / 2f
                    // Schrift verkleinern, wenn die Zahl mehr Ziffern hat
                    if (numberString.length >= 4) {
                        textSizePx *= 0.7f // z.B. für "1000"
                    } else if (numberString.length >= 3) {
                        textSizePx *= 0.85f // z.B. für "120"
                    }
                    views.setTextViewTextSize(R.id.txtTime, TypedValue.COMPLEX_UNIT_PX, textSizePx)
                    // --- ENDE: DYNAMISCHE SCHRIFTGRÖSSE ---

                    views.setTextViewText(R.id.txtTime, numberString)
                    views.setTextColor(R.id.txtTime, getAnimatedBlueColor())

                } else {
                    val configMinutes = getWidgetDuration(context, appWidgetId, totalMinutes)
                    val numberString = configMinutes.toString()

                    // --- START: DYNAMISCHE SCHRIFTGRÖSSE ---
                    var textSizePx = sidePx / 2f
                    if (numberString.length >= 4) {
                        textSizePx *= 0.7f
                    } else if (numberString.length >= 3) {
                        textSizePx *= 0.85f
                    }
                    views.setTextViewTextSize(R.id.txtTime, TypedValue.COMPLEX_UNIT_PX, textSizePx)
                    // --- ENDE: DYNAMISCHE SCHRIFTGRÖSSE ---

                    views.setTextViewText(R.id.txtTime, numberString)
                    views.setTextColor(R.id.txtTime, Color.WHITE)
                }

                views.setImageViewBitmap(R.id.widget_image, bitmap)

                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
                val intent = if (running) {
                    Intent(context, TimerEngineService::class.java).apply { action = TimerContracts.ACTION_STOP }
                } else {
                    val configMinutes = getWidgetDuration(context, appWidgetId, totalMinutes)
                    Intent(context, TimerEngineService::class.java).apply {
                        action = TimerContracts.ACTION_START
                        putExtra(TimerContracts.EXTRA_MINUTES, configMinutes)
                    }
                }
                val pendingIntent = PendingIntent.getForegroundService(context, appWidgetId, intent, flags)
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

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
