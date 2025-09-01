package com.tigonic.snoozely.util

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Wrapper für die „Setup-Hinweis unterdrücken“-Einstellung.
 *
 * Hinweis zu Redundanz:
 * - Die App besitzt bereits DataStore-Felder in SettingsPreferenceHelper für diese Einstellung.
 * - Diese Klasse delegiert deshalb an SettingsPreferenceHelper, um Doppelhaltung zu vermeiden.
 *
 * API bleibt synchron (Boolean hin/zurück), damit vorhandener Code unverändert funktioniert.
 */
object SetupHintPrefs {

    /**
     * Liest synchron den DataStore-Wert.
     * Verwendet runBlocking bewusst kurzzeitig, da dies nur bei explizitem Aufruf passiert.
     */
    fun getSuppressSetupHint(context: Context): Unit =
        runBlocking { SettingsPreferenceHelper.getSuppressSetupHint(context).first() }

    /**
     * Schreibt synchron in den DataStore.
     */
    fun setSuppressSetupHint(context: Context, suppress: Boolean) {
        runBlocking { SettingsPreferenceHelper.setSuppressSetupHint(context, suppress) }
    }
}
