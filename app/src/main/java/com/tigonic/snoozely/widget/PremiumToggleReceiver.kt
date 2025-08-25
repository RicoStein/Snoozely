package com.tigonic.snoozely.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.runBlocking
import com.tigonic.snoozely.util.SettingsPreferenceHelper

class PremiumToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val enable = intent.getBooleanExtra("enable", false)
        runBlocking { SettingsPreferenceHelper.setPremiumActive(context, enable) }
    }
}
