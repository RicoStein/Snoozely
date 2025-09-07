package com.tigonic.snoozely.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.*
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import com.tigonic.snoozely.MainActivity
import com.tigonic.snoozely.R
import com.tigonic.snoozely.service.TimerContracts
import com.tigonic.snoozely.service.TimerEngineService
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import com.tigonic.snoozely.util.TimerPreferenceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "TimerCtrlWidget"

/**
 * 3x1-Steuerungs-Widget:
 * - Links: App-Icon
 * - Mitte: mm:ss bzw. konfigurierte mm:00
 * - Rechts: −, Play/Pause, +
 * - Hintergrundfarbe, -transparenz und Textfarbe per Konfig-Activity einstellbar
 *
 * Wichtige Punkte:
 * - Größenberechnung passt Widgets dynamisch an AppWidget-Optionen an
 * - Service-Start „compat“: nur ACTION_START als Foreground-Service
 */
class TimerControlWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_MINUS, ACTION_PLUS, ACTION_TOGGLE -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    CoroutineScope(Dispatchers.IO).launch { handleControlAction(context, appWidgetId, intent.action!!) }
                } else {
                    requestSelfUpdate(context)
                }
            }
            TimerContracts.ACTION_TICK -> requestSelfUpdate(context) // vom Service gesendet
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            for (id in appWidgetIds) updateAppWidget(context, appWidgetManager, id)
            pending.finish()
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle?) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        CoroutineScope(Dispatchers.IO).launch { updateAppWidget(context, appWidgetManager, appWidgetId) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        // Prefs-Cleanup
        try {
            for (id in appWidgetIds) {
                deleteWidget(context, id)
                deleteWidgetStyle(context, id)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Cleanup failed", t)
        }
    }

    /**
     * Verarbeitet Buttonaktionen. Bei nicht-Premium: öffnet Paywall.
     */
    private suspend fun handleControlAction(context: Context, appWidgetId: Int, action: String) {
        val isPremium = SettingsPreferenceHelper.getPremiumActive(context).first()
        if (!isPremium) {
            context.startActivity(Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("showPaywall", true)
            })
            return
        }

        val running = TimerPreferenceHelper.getTimerRunning(context).first()
        when (action) {
            ACTION_MINUS -> {
                if (running) {
                    startSvcCompat(context, Intent(context, TimerEngineService::class.java).setAction(TimerContracts.ACTION_REDUCE))
                } else {
                    val cur = getWidgetDuration(context, appWidgetId, TimerPreferenceHelper.getTimer(context).first())
                    saveWidgetDuration(context, appWidgetId, max(1, cur - 1))
                }
                requestSelfUpdate(context)
            }
            ACTION_PLUS -> {
                if (running) {
                    startSvcCompat(context, Intent(context, TimerEngineService::class.java).setAction(TimerContracts.ACTION_EXTEND))
                } else {
                    val cur = getWidgetDuration(context, appWidgetId, TimerPreferenceHelper.getTimer(context).first())
                    saveWidgetDuration(context, appWidgetId, max(1, cur + 1))
                }
                requestSelfUpdate(context)
            }
            ACTION_TOGGLE -> {
                if (running) {
                    startSvcCompat(context, Intent(context, TimerEngineService::class.java).setAction(TimerContracts.ACTION_STOP))
                } else {
                    val minutes = getWidgetDuration(context, appWidgetId, TimerPreferenceHelper.getTimer(context).first())
                    startSvcCompat(
                        context,
                        Intent(context, TimerEngineService::class.java)
                            .setAction(TimerContracts.ACTION_START)
                            .putExtra(TimerContracts.EXTRA_MINUTES, minutes)
                    )
                }
            }
        }
    }

    companion object {
        const val ACTION_MINUS = "com.tigonic.snoozely.widget.ACTION_MINUS"
        const val ACTION_PLUS = "com.tigonic.snoozely.widget.ACTION_PLUS"
        const val ACTION_TOGGLE = "com.tigonic.snoozely.widget.ACTION_TOGGLE"

        /**
         * Startet Service auf O+ nur als FGS bei ACTION_START. Andere Aktionen versuchen normalen Start.
         * Falls Service nicht läuft, werden Nicht-Start-Aktionen stillschweigend ignoriert (kein Crash).
         */
        private fun startSvcCompat(context: Context, intent: Intent) {
            val action = intent.action ?: ""
            val needsForeground = action == TimerContracts.ACTION_START

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (needsForeground) {
                    context.startForegroundService(intent)
                } else {
                    try {
                        context.startService(intent)
                    } catch (t: Throwable) {
                        Log.w(TAG, "startService failed for ${intent.action}, ignoring in background", t)
                    }
                }
            } else {
                context.startService(intent)
            }
        }

        /**
         * Fordert Update aller Instanzen dieses Widgets an.
         */
        fun requestSelfUpdate(context: Context) {
            try {
                val awm = AppWidgetManager.getInstance(context)
                val ids = awm.getAppWidgetIds(ComponentName(context, TimerControlWidgetProvider::class.java))
                if (ids.isNotEmpty()) {
                    CoroutineScope(Dispatchers.IO).launch { for (id in ids) updateAppWidget(context, awm, id) }
                }
            } catch (_: Throwable) {}
        }

        /**
         * Zeichnet Hintergrund, setzt Text/Icon-Größen adaptiv und verknüpft Klick-Intents.
         */
        @SuppressLint("RemoteViewLayout", "DefaultLocale")
        suspend fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_timer_controls)
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)

                // Farben aus Konfiguration
                val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                val defaultBg = if (night) Color.BLACK else Color.WHITE
                val defaultText = if (night) Color.WHITE else Color.BLACK
                val defaultAlpha = 0.30f

                val bgColor = getWidgetBgColor(context, appWidgetId, defaultBg)
                val bgAlpha = getWidgetBgAlpha(context, appWidgetId, defaultAlpha).coerceIn(0f, 1f)
                val textColor = getWidgetTextColor(context, appWidgetId, defaultText)

                // Timerstatus
                val running = TimerPreferenceHelper.getTimerRunning(context).first()
                val startTime = TimerPreferenceHelper.getTimerStartTime(context).first()
                val totalMinutes = TimerPreferenceHelper.getTimer(context).first()

                // Abmessungen
                val isPortrait = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                val widthDp = options.getInt(if (isPortrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH).coerceAtLeast(1)
                val heightDp = options.getInt(if (isPortrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).coerceAtLeast(1)

                val dm = context.resources.displayMetrics
                val widthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, widthDp.toFloat(), dm).toInt().coerceAtLeast(1)
                val heightPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, heightDp.toFloat(), dm).toInt().coerceAtLeast(1)
                val baseH = heightPx.toFloat().coerceAtLeast(1f)

                val isThreeCols = widthDp < 245 // 3x1 ~210dp, 4x1 ~280dp

                // Hintergrund zeichnen (rundes Rechteck)
                runCatching {
                    val bgBmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bgBmp)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    val radius = heightPx * 0.4f
                    val bgColorWithAlpha = (bgColor and 0x00FFFFFF) or (((bgAlpha * 255f).toInt().coerceIn(0, 255)) shl 24)
                    paint.style = Paint.Style.FILL
                    paint.color = bgColorWithAlpha
                    canvas.drawRoundRect(RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()), radius, radius, paint)
                    views.setImageViewBitmap(R.id.bg_image, bgBmp)
                }

                // Text (mm:ss oder mm:00)
                val timeText = if (running && startTime > 0L) {
                    val totalMs = totalMinutes * 60_000L
                    val remainingMs = (totalMs - (System.currentTimeMillis() - startTime)).coerceAtLeast(0)
                    val minutes = (remainingMs / 1000L / 60L).toInt()
                    val seconds = ((remainingMs / 1000L) % 60L).toInt()
                    String.format("%02d:%02d", minutes, seconds)
                } else {
                    val cfgMinutes = getWidgetDuration(context, appWidgetId, totalMinutes)
                    String.format("%02d:%02d", cfgMinutes, 0)
                }
                views.setTextViewText(R.id.txtTime, timeText)
                views.setTextColor(R.id.txtTime, textColor)

                // Einheit für Logo/Icons/Text
                val minUnit = dpToPx(context, if (isThreeCols) 13 else 14)
                val maxUnit = dpToPx(context, 48)
                var unit = (baseH * 0.34f).roundToInt().coerceIn(minUnit, maxUnit)

                // Statischer Horizontal-Overhead
                val txtTimeLeftPadDp = 8
                val txtTimeRightPadDp = if (isThreeCols) 4 else 2
                val reservedFixed = dpToPx(context, 24 + txtTimeLeftPadDp + txtTimeRightPadDp + 24)
                val safety = dpToPx(context, if (isThreeCols) 8 else 4)

                // Textgröße in Abhängigkeit der Zielhöhe bestimmen und ggf. skalieren
                var timeSize = resolveTextSizeForHeight(unit.toFloat(), bold = true)

                runCatching {
                    views.setViewPadding(R.id.txtTime, dpToPx(context, txtTimeLeftPadDp), 0, dpToPx(context, txtTimeRightPadDp), 0)
                }

                val worstTime = "88:88"
                repeat(8) {
                    val timeWidth = measureTextWidthPx(timeSize, worstTime, bold = true) + safety
                    val needed = reservedFixed + (unit * 4) + timeWidth // Logo(1) + 3 Buttons + Zeit
                    if (needed <= widthPx) return@repeat

                    val availForScalable = (widthPx - reservedFixed).coerceAtLeast(1)
                    val current = (unit * 4) + timeWidth
                    var f = (availForScalable.toFloat() / current.toFloat()).coerceAtMost(1f)
                    if (isThreeCols) f *= 0.98f

                    val newUnit = (unit * f).roundToInt().coerceAtLeast(minUnit)
                    val scaleApplied = newUnit.toFloat() / unit.toFloat()
                    unit = newUnit
                    timeSize = (timeSize * scaleApplied).coerceAtLeast(minUnit.toFloat())
                    val tsAdjusted = resolveTextSizeForHeight(unit.toFloat(), bold = true, start = timeSize)
                    timeSize = (0.6f * timeSize + 0.4f * tsAdjusted)
                }

                // Safety-Finalcheck
                runCatching {
                    val tw = measureTextWidthPx(timeSize, worstTime, bold = true) + safety
                    val needed = reservedFixed + (unit * 4) + tw
                    if (needed > widthPx) {
                        val avail = (widthPx - reservedFixed).coerceAtLeast(1)
                        val current = (unit * 4) + tw
                        val f = ((avail.toFloat() / current.toFloat()) * 0.98f).coerceAtMost(1f)
                        val newUnit = (unit * f).roundToInt().coerceAtLeast(minUnit)
                        val scaleApplied = newUnit.toFloat() / unit.toFloat()
                        unit = newUnit
                        timeSize = (timeSize * scaleApplied).coerceAtLeast(minUnit.toFloat())
                        val tsAdjusted = resolveTextSizeForHeight(unit.toFloat(), bold = true, start = timeSize)
                        timeSize = (0.5f * timeSize + 0.5f * tsAdjusted)
                    }
                }

                // Anwenden
                views.setTextViewTextSize(R.id.txtTime, TypedValue.COMPLEX_UNIT_PX, timeSize)

                // Logo ohne Tönung (hier in Textfarbe getönt, damit konsistent)
                runCatching {
                    val logoBmp = vectorToBitmap(context, R.drawable.ic_app_logo, unit, tint = textColor)
                    views.setImageViewBitmap(R.id.imgIcon, logoBmp)
                }

                // Bedienelemente
                val playIconRes = if (running) R.drawable.ic_pause_24 else R.drawable.ic_play_24
                runCatching {
                    val minusBmp = vectorToBitmap(context, R.drawable.ic_minus_24, unit, tint = textColor)
                    val playBmp = vectorToBitmap(context, playIconRes, unit, tint = textColor)
                    val plusBmp = vectorToBitmap(context, R.drawable.ic_plus_24, unit, tint = textColor)
                    views.setImageViewBitmap(R.id.btnMinusIv, minusBmp)
                    views.setImageViewBitmap(R.id.btnPlayIv, playBmp)
                    views.setImageViewBitmap(R.id.btnPlusIv, plusBmp)
                }

                // Klick-Intents
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
                fun controlPi(act: String, req: Int): PendingIntent =
                    PendingIntent.getBroadcast(
                        context, appWidgetId + req,
                        Intent(context, TimerControlWidgetProvider::class.java)
                            .setAction(act)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                        flags
                    )
                views.setOnClickPendingIntent(R.id.btnMinusIv, controlPi(ACTION_MINUS, 100))
                views.setOnClickPendingIntent(R.id.btnPlayIv, controlPi(ACTION_TOGGLE, 200))
                views.setOnClickPendingIntent(R.id.btnPlusIv, controlPi(ACTION_PLUS, 300))

                // Tap auf Fläche: Premium -> Toggle, sonst Paywall
                val isPremium = SettingsPreferenceHelper.getPremiumActive(context).first()
                val defaultTapPi: PendingIntent =
                    if (isPremium) controlPi(ACTION_TOGGLE, 400)
                    else PendingIntent.getActivity(
                        context, appWidgetId + 500,
                        Intent(context, MainActivity::class.java).apply {
                            val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            putExtra("showPaywall", true)
                        },
                        flags
                    )
                views.setOnClickPendingIntent(R.id.widget_root, defaultTapPi)

                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (t: Throwable) {
                Log.e("TimerCtrlWidget", "updateAppWidget failed id=$appWidgetId", t)
            }
        }

        private fun dpToPx(context: Context, dp: Int): Int =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics).toInt()

        private fun measureTextWidthPx(textSizePx: Float, sample: String, bold: Boolean): Int {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.textSize = textSizePx
            if (bold) p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val w = p.measureText(sample)
            return kotlin.math.ceil(w.toDouble()).toInt().coerceAtLeast(1)
        }

        // Schätzt eine Textgröße, deren tatsächliche Glyphenhöhe ~ targetPx ist
        private fun resolveTextSizeForHeight(targetPx: Float, bold: Boolean, start: Float? = null): Float {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            if (bold) p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            var ts = start ?: targetPx
            repeat(5) {
                p.textSize = ts
                val fm = p.fontMetrics
                val h = (fm.descent - fm.ascent)
                if (h <= 0f) return@repeat
                val f = targetPx / h
                if (abs(1f - f) < 0.02f) return@repeat
                ts *= f
            }
            return ts
        }

        private fun vectorToBitmap(context: Context, resId: Int, sizePx: Int, tint: Int?): Bitmap {
            val d = AppCompatResources.getDrawable(context, resId)
                ?: throw IllegalArgumentException("Drawable not found: $resId")
            val dr = d.mutate()
            if (tint != null) {
                DrawableCompat.setTint(dr, tint)
                DrawableCompat.setTintMode(dr, PorterDuff.Mode.SRC_IN)
            }
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            dr.setBounds(0, 0, sizePx, sizePx)
            dr.draw(c)
            return bmp
        }
    }
}
