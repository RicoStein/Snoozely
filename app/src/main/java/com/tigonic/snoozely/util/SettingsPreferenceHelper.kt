package com.tigonic.snoozely.util

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings_preferences")
private val PROGRESS_EXTEND_MINUTES = intPreferencesKey("progress_extend_minutes")

object SettingsPreferenceHelper {
    private val STOP_AUDIO = booleanPreferencesKey("stop_audio")
    private val SCREEN_OFF = booleanPreferencesKey("screen_off")
    private val NOTIFICATION_ENABLED = booleanPreferencesKey("notification_enabled")
    private val TIMER_VIBRATE = booleanPreferencesKey("timer_vibrate")
    private val FADE_OUT = floatPreferencesKey("fade_out")
    private val LANGUAGE = stringPreferencesKey("language")
    private val SHOW_PROGRESS_NOTIFICATION = booleanPreferencesKey("show_progress_notification")
    private val SHOW_REMINDER_POPUP = booleanPreferencesKey("show_reminder_popup")
    private val REMINDER_MINUTES = intPreferencesKey("reminder_minutes")
    private val IS_FIRST_RUN = booleanPreferencesKey("is_first_run")

    // -------- Getter --------
    fun getProgressExtendMinutes(context: Context): Flow<Int> =
        context.dataStore.data.map { it[PROGRESS_EXTEND_MINUTES] ?: 5 }

    fun getStopAudio(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[STOP_AUDIO] ?: true }

    fun getScreenOff(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[SCREEN_OFF] ?: false }

    fun getNotificationEnabled(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[NOTIFICATION_ENABLED] ?: true }

    fun getTimerVibrate(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[TIMER_VIBRATE] ?: false }

    fun getFadeOut(context: Context): Flow<Float> =
        context.dataStore.data.map { it[FADE_OUT] ?: 30f }

    fun getLanguage(context: Context): Flow<String> =
        context.dataStore.data.map { it[LANGUAGE] ?: "de" }

    fun getShowProgressNotification(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[SHOW_PROGRESS_NOTIFICATION] ?: true }

    fun getShowReminderPopup(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[SHOW_REMINDER_POPUP] ?: true }

    fun getReminderMinutes(context: Context): Flow<Int> =
        context.dataStore.data.map { it[REMINDER_MINUTES] ?: 2 }

    fun getIsFirstRun(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[IS_FIRST_RUN] ?: true }

    // -------- Setter (mit Clamping wo sinnvoll) --------
    suspend fun setProgressExtendMinutes(context: Context, value: Int) {
        val v = value.coerceIn(1, 30)
        context.dataStore.edit { it[PROGRESS_EXTEND_MINUTES] = v }
    }

    suspend fun setStopAudio(context: Context, value: Boolean) {
        context.dataStore.edit { it[STOP_AUDIO] = value }
    }

    suspend fun setScreenOff(context: Context, value: Boolean) {
        context.dataStore.edit { it[SCREEN_OFF] = value }
    }

    suspend fun setNotificationEnabled(context: Context, value: Boolean) {
        context.dataStore.edit { it[NOTIFICATION_ENABLED] = value }
    }

    suspend fun setTimerVibrate(context: Context, value: Boolean) {
        context.dataStore.edit { it[TIMER_VIBRATE] = value }
    }

    suspend fun setFadeOut(context: Context, value: Float) {
        val v = value.coerceIn(0f, 120f)
        context.dataStore.edit { it[FADE_OUT] = v }
    }

    suspend fun setLanguage(context: Context, value: String) {
        context.dataStore.edit { it[LANGUAGE] = value }
    }

    suspend fun setShowProgressNotification(context: Context, value: Boolean) {
        context.dataStore.edit { it[SHOW_PROGRESS_NOTIFICATION] = value }
    }

    suspend fun setShowReminderPopup(context: Context, value: Boolean) {
        context.dataStore.edit { it[SHOW_REMINDER_POPUP] = value }
    }

    suspend fun setReminderMinutes(context: Context, value: Int) {
        val v = value.coerceIn(1, 10)
        context.dataStore.edit { it[REMINDER_MINUTES] = v }
    }

    suspend fun setIsFirstRun(context: Context, value: Boolean) {
        context.dataStore.edit { it[IS_FIRST_RUN] = value }
    }
}
