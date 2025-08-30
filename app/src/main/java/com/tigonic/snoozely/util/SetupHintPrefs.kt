package com.tigonic.snoozely.util

import android.content.Context

/**
 * Einfache SharedPreferences für die „Nicht mehr anzeigen“-Option des Setup-Hinweises.
 * Absichtlich unabhängig von einer evtl. vorhandenen DataStore-Implementierung gehalten,
 * damit du diesen Code 1:1 einfügen kannst.
 */
object SetupHintPrefs {
    private const val NAME = "setup_hint_prefs"
    private const val KEY_SUPPRESS = "suppress_setup_hint"

    fun getSuppressSetupHint(context: Context): Boolean {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SUPPRESS, false)
    }

    fun setSuppressSetupHint(context: Context, suppress: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SUPPRESS, suppress)
            .apply()
    }
}
