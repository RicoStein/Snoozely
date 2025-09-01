package com.tigonic.snoozely.util

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlin.math.max

// Dedizierter DataStore für Timer-Zustand
private val Context.dataStore by preferencesDataStore(name = "timer_preferences")

/**
 * Kapselt den kompletten Timer-Zustand in DataStore:
 * - Sichtbarer Minutenwert
 * - Startzeit (ms seit Epoch) bei laufendem Timer
 * - Running-Flag
 * - User-Startwert (für „zurück auf Startwert“-Fälle, Reminder etc.)
 *
 * Alle Set-Operationen sind „sicher“ (min. 1 Minute).
 */
object TimerPreferenceHelper {

    // Keys
    private val TIMER_KEY = intPreferencesKey("timer_minutes")
    private val TIMER_START_TIME = longPreferencesKey("timer_start_time") // ms
    private val TIMER_RUNNING = booleanPreferencesKey("timer_running")
    private val TIMER_USER_BASE = intPreferencesKey("timer_user_base_minutes")

    // --- Getter ---

    /**
     * Fluss des aktuellen sichtbaren Minutenwerts.
     * Fällt auf den Standardwert der App zurück (Einstellung) und ist mind. 1.
     */
    fun getTimer(context: Context): Flow<Int> =
        combine(
            context.dataStore.data.map { it[TIMER_KEY] },
            SettingsPreferenceHelper.getDefaultTimerMinutes(context)
        ) { saved, def ->
            (saved ?: def).coerceAtLeast(1)
        }

    /** Startzeit in Millisekunden (0, wenn nicht gesetzt). */
    fun getTimerStartTime(context: Context): Flow<Long> =
        context.dataStore.data.map { it[TIMER_START_TIME] ?: 0L }

    /** Ob der Timer läuft. */
    fun getTimerRunning(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[TIMER_RUNNING] ?: false }

    /** Gemerkter User-Startwert (Minuten), 0 wenn unbekannt. */
    fun getTimerUserBase(context: Context): Flow<Int> =
        context.dataStore.data.map { it[TIMER_USER_BASE] ?: 0 }

    // --- Setter ---

    /** Sichtbare Minuten setzen (>=1). */
    suspend fun setTimer(context: Context, minutes: Int) {
        val safe = max(1, minutes)
        context.dataStore.edit { it[TIMER_KEY] = safe }
    }

    /** Startzeit setzen (ms seit Epoch). */
    suspend fun setTimerStartTime(context: Context, startTime: Long) {
        context.dataStore.edit { it[TIMER_START_TIME] = startTime }
    }

    /** Running-Flag setzen. */
    suspend fun setTimerRunning(context: Context, running: Boolean) {
        context.dataStore.edit { it[TIMER_RUNNING] = running }
    }

    /** User-Startwert (>=1) setzen. */
    suspend fun setTimerUserBase(context: Context, minutes: Int) {
        val safe = max(1, minutes)
        context.dataStore.edit { it[TIMER_USER_BASE] = safe }
    }

    // --- Atomare Operationen ---

    /**
     * Startet den Timer atomar:
     * - sichtbare Minuten setzen
     * - User-Startwert mitschreiben
     * - Startzeit = jetzt
     * - Running=true
     */
    suspend fun startTimer(context: Context, minutes: Int) {
        val safe = max(1, minutes)
        val now = System.currentTimeMillis()
        context.dataStore.edit { prefs ->
            prefs[TIMER_KEY] = safe
            prefs[TIMER_USER_BASE] = safe
            prefs[TIMER_START_TIME] = now
            prefs[TIMER_RUNNING] = true
        }
    }

    /**
     * Stoppt den Timer atomar:
     * - sichtbaren Minutenwert merken (>=1)
     * - Startzeit löschen
     * - Running=false
     */
    suspend fun stopTimer(context: Context, minutes: Int) {
        val safe = max(1, minutes)
        context.dataStore.edit { prefs ->
            prefs[TIMER_KEY] = safe
            prefs[TIMER_START_TIME] = 0L
            prefs[TIMER_RUNNING] = false
        }
    }
}
