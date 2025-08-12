package com.tigonic.snoozely.util

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.max

private val Context.dataStore by preferencesDataStore(name = "timer_preferences")

object TimerPreferenceHelper {
    private val TIMER_KEY = intPreferencesKey("timer_minutes")
    private val TIMER_START_TIME = longPreferencesKey("timer_start_time") // ms
    private val TIMER_RUNNING = booleanPreferencesKey("timer_running")

    fun getTimer(context: Context): Flow<Int> =
        context.dataStore.data.map { it[TIMER_KEY] ?: 0 }

    suspend fun setTimer(context: Context, minutes: Int) {
        val safe = max(1, minutes)
        context.dataStore.edit { it[TIMER_KEY] = safe }
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

    /** Startet den Timer atomar: Minuten setzen, Startzeit setzen, Running=true */
    suspend fun startTimer(context: Context, minutes: Int) {
        val safe = max(1, minutes)
        val now = System.currentTimeMillis()
        context.dataStore.edit { prefs ->
            prefs[TIMER_KEY] = safe               // laufender Zielwert (kann später steigen)
            prefs[TIMER_USER_BASE] = safe         // <- NEU: User-Startwert
            prefs[TIMER_START_TIME] = now
            prefs[TIMER_RUNNING] = true
        }
    }

    /** Stoppt den Timer atomar: Minuten merken (z. B. letzte User-Auswahl), Startzeit löschen, Running=false */
    suspend fun stopTimer(context: Context, minutes: Int) {
        val safe = max(1, minutes)
        context.dataStore.edit { prefs ->
            prefs[TIMER_KEY] = safe
            prefs[TIMER_START_TIME] = 0L
            prefs[TIMER_RUNNING] = false
        }
    }

    private val TIMER_USER_BASE = intPreferencesKey("timer_user_base_minutes")

    fun getTimerUserBase(context: Context): Flow<Int> =
        context.dataStore.data.map { it[TIMER_USER_BASE] ?: 0 }

    suspend fun setTimerUserBase(context: Context, minutes: Int) {
        val safe = max(1, minutes)
        context.dataStore.edit { it[TIMER_USER_BASE] = safe }
    }
}
