package com.tigonic.snoozely.service

import android.content.Context
import android.content.Intent
import android.os.Build

fun updateNotification(context: Context, remainingMs: Long, totalMs: Long) {
    val intent = Intent(context, TimerNotificationService::class.java).apply {
        action = TimerNotificationService.ACTION_UPDATE
        putExtra(TimerNotificationService.EXTRA_REMAINING_MS, remainingMs)
        putExtra(TimerNotificationService.EXTRA_TOTAL_MS, totalMs)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

fun stopNotification(context: Context) {
    val intent = Intent(context, TimerNotificationService::class.java).apply {
        action = TimerNotificationService.ACTION_STOP
    }
    context.startService(intent)
}
