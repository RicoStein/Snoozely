package com.tigonic.snoozely.util

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.LocaleList
import android.app.LocaleManager

object LocaleHelper {
    fun setAppLocaleAndRestart(activity: Activity, language: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeList = LocaleList.forLanguageTags(language)
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales = localeList
        }
        activity.recreate()
    }
}
