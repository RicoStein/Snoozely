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
                    val scope = CoroutineScope(Dispatchers.IO)
                    scope.launch { handleControlAction(context, appWidgetId, intent.action!!) }
                } else {
                    requestSelfUpdate(context)
                }
            }
            TimerContracts.ACTION_TICK -> requestSelfUpdate(context)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            for (id in appWidgetIds) updateAppWidget(context, appWidgetManager, id)
            pending.finish()
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle?) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch { updateAppWidget(context, appWidgetManager, appWidgetId) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        try {
            for (id in appWidgetIds) {
                deleteWidget(context, id)
                deleteWidgetStyle(context, id)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Cleanup failed", t)
        }
    }

    private suspend fun handleControlAction(context: Context, appWidgetId: Int, action: String) {
        val isPremium = SettingsPreferenceHelper.getPremiumActive(context).first()
        if (!isPremium) {
            val piIntent = Intent(context, MainActivity::class.java).apply {
                this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("showPaywall", true)
            }
            context.startActivity(piIntent)
            return
        }

        val running = TimerPreferenceHelper.getTimerRunning(context).first()
        when (action) {
            ACTION_MINUS -> {
                if (running) {
                    val i = Intent(context, TimerEngineService::class.java).setAction(TimerContracts.ACTION_REDUCE)
                    startSvcCompat(context, i)
                } else {
                    val cur = getWidgetDuration(context, appWidgetId, TimerPreferenceHelper.getTimer(context).first())
                    saveWidgetDuration(context, appWidgetId, max(1, cur - 1))
                }
                requestSelfUpdate(context)
            }
            ACTION_PLUS -> {
                if (running) {
                    val i = Intent(context, TimerEngineService::class.java).setAction(TimerContracts.ACTION_EXTEND)
                    startSvcCompat(context, i)
                } else {
                    val cur = getWidgetDuration(context, appWidgetId, TimerPreferenceHelper.getTimer(context).first())
                    saveWidgetDuration(context, appWidgetId, max(1, cur + 1))
                }
                requestSelfUpdate(context)
            }
            ACTION_TOGGLE -> {
                if (running) {
                    val stop = Intent(context, TimerEngineService::class.java).setAction(TimerContracts.ACTION_STOP)
                    startSvcCompat(context, stop)
                } else {
                    val minutes = getWidgetDuration(context, appWidgetId, TimerPreferenceHelper.getTimer(context).first())
                    val start = Intent(context, TimerEngineService::class.java)
                        .setAction(TimerContracts.ACTION_START)
                        .putExtra(TimerContracts.EXTRA_MINUTES, minutes)
                    startSvcCompat(context, start)
                }
            }
        }
    }

    companion object {
        const val ACTION_MINUS = "com.tigonic.snoozely.widget.ACTION_MINUS"
        const val ACTION_PLUS = "com.tigonic.snoozely.widget.ACTION_PLUS"
        const val ACTION_TOGGLE = "com.tigonic.snoozely.widget.ACTION_TOGGLE"

        private fun startSvcCompat(context: Context, intent: Intent) {
            val action = intent.action ?: ""
            val needsForeground = action == TimerContracts.ACTION_START

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (needsForeground) {
                    // Nur bei START in den Vordergrund
                    context.startForegroundService(intent)
                } else {
                    // Für STOP/EXTEND/REDUCE: normalen Start versuchen (liefert Intent an laufenden Service)
                    try {
                        context.startService(intent)
                    } catch (t: Throwable) {
                        // Fallback: wenn Service wider Erwarten nicht läuft, nicht crashe(n),
                        // sondern minimal absichern – aber KEIN FGS erzwingen
                        Log.w(TAG, "startService failed for ${intent.action}, ignoring in background", t)
                    }
                }
            } else {
                context.startService(intent)
            }
        }


        fun requestSelfUpdate(context: Context) {
            try {
                val awm = AppWidgetManager.getInstance(context)
                val ids = awm.getAppWidgetIds(ComponentName(context, TimerControlWidgetProvider::class.java))
                if (ids.isNotEmpty()) {
                    val scope = CoroutineScope(Dispatchers.IO)
                    scope.launch { for (id in ids) updateAppWidget(context, awm, id) }
                }
            } catch (_: Throwable) {}
        }

        @SuppressLint("RemoteViewLayout")
        suspend fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_timer_controls)
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)

                val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                val defaultBg = if (night) Color.BLACK else Color.WHITE
                val defaultText = if (night) Color.WHITE else Color.BLACK
                val defaultAlpha = 0.30f

                val bgColor = getWidgetBgColor(context, appWidgetId, defaultBg)
                val bgAlpha = getWidgetBgAlpha(context, appWidgetId, defaultAlpha).coerceIn(0f, 1f)
                val textColor = getWidgetTextColor(context, appWidgetId, defaultText)

                val running = TimerPreferenceHelper.getTimerRunning(context).first()
                val startTime = TimerPreferenceHelper.getTimerStartTime(context).first()
                val totalMinutes = TimerPreferenceHelper.getTimer(context).first()

                val isPortrait = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                val widthDp = options.getInt(
                    if (isPortrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                ).coerceAtLeast(1)
                val heightDp = options.getInt(
                    if (isPortrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                ).coerceAtLeast(1)

                val dm = context.resources.displayMetrics
                val widthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, widthDp.toFloat(), dm).toInt().coerceAtLeast(1)
                val heightPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, heightDp.toFloat(), dm).toInt().coerceAtLeast(1)
                val baseH = heightPx.toFloat().coerceAtLeast(1f)

                // Grobe Spalten-Schätzung (Standard: 70dp pro Spalte)
                val isThreeCols = widthDp < 245 // 3x1 ~210dp, 4x1 ~280dp

                // Hintergrund zeichnen
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

                // Zeittext berechnen
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

                // Einheitliche Zielgröße ("unit") für Logo, Zeit, Buttons
                val minUnit = dpToPx(context, if (isThreeCols) 13 else 14) // bei 3x1 etwas kleiner zulassen
                val maxUnit = dpToPx(context, 48)
                var unit = (baseH * 0.34f).roundToInt().coerceIn(minUnit, maxUnit)

                // Feste horizontale Abzüge gemäß Layout:
                // content_row paddingLeft/Right = 12dp + 12dp
                // txtTime paddingLeft = 8dp, paddingRight = 2/4dp (s.u.)
                // Icon-Paddings (links vor −, ▷/⏸, +) = 8dp + 8dp + 8dp
                val txtTimeLeftPadDp = 8
                val txtTimeRightPadDp = if (isThreeCols) 4 else 2
                val reservedFixed = dpToPx(context, 24 + txtTimeLeftPadDp + txtTimeRightPadDp + 24) // 56/58dp
                val safety = dpToPx(context, if (isThreeCols) 8 else 4) // mehr Puffer bei 3x1

                // Ermittele TextSize so, dass Texthöhe ≈ unit ist
                var timeSize = resolveTextSizeForHeight(unit.toFloat(), bold = true)

                // Padding für txtTime setzen (rechte Kante schützt letzte Ziffer)
                runCatching {
                    views.setViewPadding(R.id.txtTime, dpToPx(context, txtTimeLeftPadDp), 0, dpToPx(context, txtTimeRightPadDp), 0)
                }

                // Breitenbudget prüfen und alle Elemente gemeinsam skalieren, bis es passt
                val worstTime = "88:88" // Worst-Case für mm:ss
                repeat(8) {
                    val timeWidth = measureTextWidthPx(timeSize, worstTime, bold = true) + safety
                    val needed = reservedFixed + (unit * 4) + timeWidth // Logo(1) + 3 Buttons + Zeit
                    if (needed <= widthPx) return@repeat

                    val availForScalable = (widthPx - reservedFixed).coerceAtLeast(1)
                    val current = (unit * 4) + timeWidth
                    var f = (availForScalable.toFloat() / current.toFloat()).coerceAtMost(1f)
                    // bei 3x1 einen Tick konservativer schrumpfen
                    if (isThreeCols) f *= 0.98f

                    val newUnit = (unit * f).roundToInt().coerceAtLeast(minUnit)
                    val scaleApplied = newUnit.toFloat() / unit.toFloat()
                    unit = newUnit

                    timeSize = (timeSize * scaleApplied).coerceAtLeast(minUnit.toFloat())
                    val tsAdjusted = resolveTextSizeForHeight(unit.toFloat(), bold = true, start = timeSize)
                    timeSize = (0.6f * timeSize + 0.4f * tsAdjusted)
                }

                // Sicherheits-Finalcheck: falls immer noch eng, ein letztes kleines Downsizing
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

                // Finalgrößen anwenden
                views.setTextViewTextSize(R.id.txtTime, TypedValue.COMPLEX_UNIT_PX, timeSize)

                // Logo in gleicher Einheit (keine Tönung)
                runCatching {
                    val logoBmp = vectorToBitmap(context, R.drawable.ic_app_logo, unit, tint = textColor)
                    views.setImageViewBitmap(R.id.imgIcon, logoBmp)
                }

                // Bedienelemente in gleicher Einheit (auf Textfarbe getönt)
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

                // Optionaler Tap auf Fläche
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

        // Findet eine Textgröße, deren tatsächliche Glyphenhöhe (ascent..descent) ≈ targetPx ist
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
