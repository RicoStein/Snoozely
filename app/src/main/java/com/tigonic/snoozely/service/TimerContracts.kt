package com.tigonic.snoozely.service

object TimerContracts {
    // Actions
    const val ACTION_START = "com.tigonic.snoozely.ACTION_START"
    const val ACTION_STOP = "com.tigonic.snoozely.ACTION_STOP"
    const val ACTION_EXTEND = "com.tigonic.snoozely.ACTION_EXTEND"
    const val ACTION_REDUCE = "com.tigonic.snoozely.ACTION_REDUCE"
    const val ACTION_NOTIFY_UPDATE = "com.tigonic.snoozely.ACTION_NOTIFY_UPDATE"
    const val ACTION_NOTIFY_REMINDER = "com.tigonic.snoozely.ACTION_NOTIFY_REMINDER"
    const val ACTION_TICK = "com.tigonic.snoozely.ACTION_TICK"

    // Channels
    const val CHANNEL_FOREGROUND = "timer_engine"
    const val CHANNEL_RUNNING = "timer_running"
    const val CHANNEL_REMINDER = "timer_reminder"

    // Extras
    const val EXTRA_TOTAL_MS = "extra_total_ms"
    const val EXTRA_REMAINING_MS = "extra_remaining_ms"
    const val EXTRA_MINUTES = "extra_minutes"
}
