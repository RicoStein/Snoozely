package com.tigonic.snoozely.util

import android.app.Activity
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {
    fun setAppLocaleAndRestart(activity: Activity, language: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: offizielle API
            val locales = LocaleList.forLanguageTags(language)
            activity.getSystemService(LocaleManager::class.java)
                ?.applicationLocales = locales
            // Hinweis: App/Activity muss neu gezeichnet werden
            activity.recreate()
        } else {
            // < Android 13: über Configuration
            val locale = Locale(language)
            Locale.setDefault(locale)
            val res = activity.resources
            val config = res.configuration
            config.setLocale(locale)
            @Suppress("DEPRECATION")
            res.updateConfiguration(config, res.displayMetrics)
            activity.recreate()
        }
    }
}
