package com.tigonic.snoozely.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "timer_preferences")

object TimerPreferenceHelper {
    private val TIMER_KEY = intPreferencesKey("timer_minutes")
    private val TIMER_START_TIME = longPreferencesKey("timer_start_time") // in Millis
    private val TIMER_RUNNING = booleanPreferencesKey("timer_running")

    fun getTimer(context: Context): Flow<Int> =
        context.dataStore.data.map { it[TIMER_KEY] ?: 0 }

    suspend fun setTimer(context: Context, minutes: Int) {
        if (minutes < 1) return // Niemals 0 oder negativ speichern!
        context.dataStore.edit { it[TIMER_KEY] = minutes }
    }

    fun getTimerStartTime(context: Context): Flow<Long> =
        context.dataStore.data.map { it[TIMER_START_TIME] ?: 0L }

    suspend fun setTimerStartTime(context: Context, startTime: Long) {
        context.dataStore.edit { it[TIMER_START_TIME] = startTime }
    }

    fun getTimerRunning(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[TIMER_RUNNING] ?: false }

    suspend fun setTimerRunning(context: Context, running: Boolean) {
        context.dataStore.edit { it[TIMER_RUNNING] = running }
    }

    suspend fun startTimer(context: Context, minutes: Int) {
        setTimer(context, minutes)
        setTimerStartTime(context, System.currentTimeMillis())
        setTimerRunning(context, true)
    }

    suspend fun stopTimer(context: Context, minutes: Int) {
        setTimer(context, minutes)
        setTimerStartTime(context, 0L)
        setTimerRunning(context, false)
    }
}
