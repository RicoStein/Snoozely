package com.tigonic.snoozely.service

import android.content.Context
import android.content.Intent
import android.os.Build

fun notifyTimerUpdate(context: Context, remainingMs: Long, totalMs: Long) {
    val i = Intent(context, TimerNotificationService::class.java).apply {
        action = TimerContracts.ACTION_NOTIFY_UPDATE
        putExtra(TimerContracts.EXTRA_REMAINING_MS, remainingMs)
        putExtra(TimerContracts.EXTRA_TOTAL_MS, totalMs)
    }
    context.startService(i)
}

fun clearTimerNotifications(context: Context) {
    val i = Intent(context, TimerNotificationService::class.java).apply {
        action = TimerContracts.ACTION_STOP
    }
    context.startService(i)
}
