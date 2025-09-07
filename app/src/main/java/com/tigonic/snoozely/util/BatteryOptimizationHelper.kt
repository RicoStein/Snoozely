package com.tigonic.snoozely.util

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimizationHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return runCatching { pm.isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(false)
    }

    fun isBackgroundRestricted(context: Context): Boolean {
        val am = context.getSystemService(ActivityManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { am.isBackgroundRestricted }.getOrDefault(false)
        } else {
            // Auf älteren APIs gibt es kein direktes Äquivalent – konservativ: false
            false
        }
    }

    /**
     * „Uneingeschränkt“ im Sinne der App: Von Batterieoptimierung ausgenommen UND nicht background-restricted.
     */
    fun isUnrestricted(context: Context): Boolean {
        val ignoring = isIgnoringBatteryOptimizations(context)
        val restricted = isBackgroundRestricted(context)
        return ignoring && !restricted
    }

    /**
     * Öffnet die passende Seite, um „Uneingeschränkt / Nicht optimieren“ zu setzen.
     * Nutzt String-Literals, damit es mit niedrigem compileSdk kompiliert.
     */
    fun openBatterySettings(context: Context) {
        val pkg = context.packageName
        val newTask = Intent.FLAG_ACTIVITY_NEW_TASK

        // 1) App-spezifische Battery-Settings (API 28+), Action als Literal:
        // "android.settings.APP_BATTERY_SETTINGS"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val appBattery = Intent("android.settings.APP_BATTERY_SETTINGS").apply {
                data = Uri.parse("package:$pkg")
                addFlags(newTask)
            }
            try {
                context.startActivity(appBattery)
                return
            } catch (_: ActivityNotFoundException) {
                // fallback unten
            }
        }

        // 2) Allgemeine „Nicht optimieren“-Liste (Doze-Whitelist), Action als Literal:
        // "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"
        val ignoreList = Intent("android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS").apply {
            addFlags(newTask)
        }
        try {
            context.startActivity(ignoreList)
            return
        } catch (_: ActivityNotFoundException) {
            // fallback unten
        }

        // 3) App-Detailseite als letzte Instanz (Konstante existiert auf allen compileSdks)
        val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$pkg")
            addFlags(newTask)
        }
        try {
            context.startActivity(appDetails)
        } catch (_: Throwable) {
            // nichts mehr übrig
        }
    }

    /**
     * Optional: Systemdialog „Nicht optimieren“ (Doze-Whitelist) direkt anfragen.
     * Benötigt REQUEST_IGNORE_BATTERY_OPTIMIZATIONS im Manifest.
     * Action als Literal: "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val pkg = context.packageName
        val intent = Intent("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")
            .setData(Uri.parse("package:$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            openBatterySettings(context)
        }
    }
}
