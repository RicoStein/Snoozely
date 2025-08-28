package com.tigonic.snoozely.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tigonic.snoozely.R
import com.tigonic.snoozely.service.TimerContracts.ACTION_TICK
import com.tigonic.snoozely.service.TimerContracts.EXTRA_REMAINING_MS
import com.tigonic.snoozely.service.TimerContracts.EXTRA_TOTAL_MS
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

class AudioFadeService : Service() {

    companion object {
        private const val TAG = "AudioFadeService"
        private const val FADE_CHANNEL_ID = "audio_fade"
        private const val FADE_NOTIFICATION_ID = 1007
        const val ACTION_FADE_FINALIZE = "com.tigonic.snoozely.action.FADE_FINALIZE"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var audioManager: AudioManager
    private val stream = AudioManager.STREAM_MUSIC
    private var originalVolume: Int? = null
    private var fadingActive: Boolean = false

    @Volatile private var stopAudioEnabled: Boolean = true
    @Volatile private var fadeOutSec: Int = 30
    @Volatile private var finalizing: Boolean = false

    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_TICK) return
            val totalMs = intent.getLongExtra(EXTRA_TOTAL_MS, 0L)
            val remainingMs = intent.getLongExtra(EXTRA_REMAINING_MS, 0L)
            onTick(totalMs, remainingMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        registerReceivers()
        createFadeChannelIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_FADE_FINALIZE) {
            finalizing = true
            scope.launch {
                try {
                    kotlinx.coroutines.delay(1200)
                } catch (_: Throwable) { /* ignore */ }
                restoreVolumeIfNeeded()
                stopSelf()
            }
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (!finalizing) {
            restoreVolumeIfNeeded()
        }
        unregisterReceivers()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onTick(totalMs: Long, remainingMs: Long) {
        scope.launch {
            stopAudioEnabled = SettingsPreferenceHelper.getStopAudio(applicationContext).first()
            val fadeOut = SettingsPreferenceHelper.getFadeOut(applicationContext).first()
            fadeOutSec = max(0, fadeOut.roundToInt())

            if (!stopAudioEnabled || fadeOutSec <= 0 || totalMs <= 0L) {
                if (fadingActive) {
                    restoreVolumeIfNeeded()
                    stopForegroundIfRunning()
                }
                return@launch
            }

            val remainingSec = max(0L, remainingMs / 1000L).toInt()

            if (remainingSec <= fadeOutSec) {
                if (!fadingActive) {
                    originalVolume = audioManager.getStreamVolume(stream).coerceAtLeast(0)
                    fadingActive = true
                }

                val base = (originalVolume ?: audioManager.getStreamVolume(stream)).coerceAtLeast(0)
                val ratio: Double = remainingSec.toDouble() / max(1, fadeOutSec).toDouble()
                val target = (base * ratio).let { ceil(it).toInt() }.coerceIn(0, base)

                setStreamVolumeSafe(target)
            } else {
                if (fadingActive) {
                    restoreVolumeIfNeeded()
                    stopForegroundIfRunning()
                }
            }
        }
    }

    private fun setStreamVolumeSafe(vol: Int) {
        try {
            val clamped = vol.coerceIn(0, audioManager.getStreamMaxVolume(stream))
            if (audioManager.getStreamVolume(stream) != clamped) {
                audioManager.setStreamVolume(stream, clamped, 0)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "setStreamVolume failed: $t")
        }
    }

    private fun restoreVolumeIfNeeded() {
        val base = originalVolume
        if (base != null) {
            setStreamVolumeSafe(base)
        }
        originalVolume = null
        fadingActive = false
    }

    private fun registerReceivers() {
        val f = IntentFilter().apply { addAction(ACTION_TICK) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(tickReceiver, f, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(tickReceiver, f)
        }
    }

    private fun unregisterReceivers() {
        try {
            unregisterReceiver(tickReceiver)
        } catch (_: Throwable) { /* ignore */ }
    }

    private fun createFadeChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(FADE_CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        FADE_CHANNEL_ID,
                        "Audio Fade",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Aktiver Audio-Fade während des Sleep-Timers"
                        setShowBadge(false)
                        lockscreenVisibility = Notification.VISIBILITY_SECRET
                    }
                )
            }
        }
    }

    private fun startForegroundWithTinyNotification() {
        val notif = NotificationCompat.Builder(this, FADE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.fade_out_running))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        startForeground(FADE_NOTIFICATION_ID, notif)
    }

    private fun stopForegroundIfRunning() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Throwable) { /* ignore */ }
    }
}
