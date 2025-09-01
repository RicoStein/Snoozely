package com.tigonic.snoozely.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.runBlocking
import com.tigonic.snoozely.util.SettingsPreferenceHelper

/**
 * Debug-Receiver um Premium on/off zu schalten (siehe Manifest Intent-Filter).
 * Nicht im Produktionsbuild verwenden.
 */
class PremiumToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val enable = intent.getBooleanExtra("enable", false)
        runBlocking { SettingsPreferenceHelper.setPremiumActive(context, enable) }
    }
}
