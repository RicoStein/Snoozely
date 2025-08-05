package com.tigonic.snoozely.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "timer_preferences")

object TimerPreferenceHelper {
    private val TIMER_KEY = intPreferencesKey("timer_minutes")

    fun getTimer(context: Context): Flow<Int> {
        return context.dataStore.data.map { preferences ->
            preferences[TIMER_KEY] ?: 0
        }
    }

    suspend fun setTimer(context: Context, minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[TIMER_KEY] = minutes
        }
    }

    suspend fun getInitialTimer(context: Context): Int {
        return getTimer(context).first()
    }
}
