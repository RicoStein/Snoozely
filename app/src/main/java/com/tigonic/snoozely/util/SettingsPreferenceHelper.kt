package com.tigonic.snoozely.util

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings_preferences")
private val PROGRESS_EXTEND_MINUTES = intPreferencesKey("progress_extend_minutes")

object SettingsPreferenceHelper {

    // Wheel
    private val DEFAULT_TIMER_MINUTES = intPreferencesKey("default_timer_minutes")

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

    // Shake-Keys
    private val KEY_SHAKE_ENABLED = booleanPreferencesKey("shake_extend_enabled")
    private val KEY_SHAKE_STRENGTH = intPreferencesKey("shake_strength") // 0..100
    private val KEY_SHAKE_EXTEND_MIN = intPreferencesKey("shake_extend_minutes") // 1..30
    private val KEY_SHAKE_SOUND_MODE = stringPreferencesKey("shake_sound_mode") // "tone" | "vibrate"
    private val KEY_SHAKE_RINGTONE = stringPreferencesKey("shake_ringtone_uri") // optional URI
    private val KEY_SHAKE_VOLUME = floatPreferencesKey("shake_volume") // 0f..1f

    // Shake Aktivierungsfenster
    private val KEY_SHAKE_ACTIVATION_MODE = stringPreferencesKey("shake_activation_mode") // "immediate" | "after_start"
    private val KEY_SHAKE_ACTIVATION_DELAY_MIN = intPreferencesKey("shake_activation_delay_min") // 1..30

    // Notification-Buttons
    private val PROGRESS_EXTEND_ENABLED = booleanPreferencesKey("progress_extend_enabled")
    private val REMINDER_EXTEND_ENABLED = booleanPreferencesKey("reminder_extend_enabled")
    private val REMINDER_EXTEND_MINUTES = intPreferencesKey("reminder_extend_minutes")

    // THEME
    private val THEME_MODE = stringPreferencesKey("theme_mode")
    private val THEME_DYNAMIC = booleanPreferencesKey("theme_dynamic")

    // Bluetooth/WLAN
    private val KEY_BLUETOOTH_DISABLE_REQUESTED = booleanPreferencesKey("bluetooth_disable_requested")
    private val KEY_WIFI_DISABLE_REQUESTED = booleanPreferencesKey("wifi_disable_requested")

    // PREMIUM
    private val PREMIUM_ACTIVE = booleanPreferencesKey("premium_active")

    // -------- Getter --------
    fun getProgressExtendMinutes(context: Context): Flow<Int> =
        context.dataStore.data.map { it[PROGRESS_EXTEND_MINUTES] ?: 5 }

    fun getStopAudio(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[STOP_AUDIO] ?: true }

    fun getScreenOff(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[SCREEN_OFF] ?: false }

    fun getNotificationEnabled(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[NOTIFICATION_ENABLED] ?: false }

    fun getTimerVibrate(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[TIMER_VIBRATE] ?: false }

    fun getFadeOut(context: Context): Flow<Float> =
        context.dataStore.data.map { it[FADE_OUT] ?: 30f }

    fun getLanguage(context: Context): Flow<String> =
        context.dataStore.data.map { it[LANGUAGE] ?: "de" }

    fun getShowProgressNotification(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[SHOW_PROGRESS_NOTIFICATION] ?: false }

    fun getShowReminderPopup(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[SHOW_REMINDER_POPUP] ?: false }

    fun getReminderMinutes(context: Context): Flow<Int> =
        context.dataStore.data.map { it[REMINDER_MINUTES] ?: 5 }

    fun getIsFirstRun(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[IS_FIRST_RUN] ?: true }

    fun getShakeEnabled(ctx: Context) = ctx.dataStore.data.map { it[KEY_SHAKE_ENABLED] ?: false }
    fun getShakeStrength(ctx: Context) = ctx.dataStore.data.map { it[KEY_SHAKE_STRENGTH] ?: 50 }
    fun getShakeExtendMinutes(ctx: Context) = ctx.dataStore.data.map { it[KEY_SHAKE_EXTEND_MIN] ?: 10 }
    fun getShakeSoundMode(ctx: Context) = ctx.dataStore.data.map { it[KEY_SHAKE_SOUND_MODE] ?: "tone" }
    fun getShakeRingtone(ctx: Context) = ctx.dataStore.data.map { it[KEY_SHAKE_RINGTONE] ?: "" }
    fun getShakeVolume(ctx: Context) = ctx.dataStore.data.map { it[KEY_SHAKE_VOLUME] ?: 1f }

    fun getPremiumActive(ctx: Context) = ctx.dataStore.data.map { it[PREMIUM_ACTIVE] ?: false }

    fun getShakeActivationMode(ctx: Context) = ctx.dataStore.data.map { prefs ->
        prefs[KEY_SHAKE_ACTIVATION_MODE] ?: "immediate"
    }
    fun getShakeActivationDelayMinutes(ctx: Context) = ctx.dataStore.data.map { prefs ->
        prefs[KEY_SHAKE_ACTIVATION_DELAY_MIN] ?: 3
    }

    fun getProgressExtendEnabled(ctx: Context) =
        ctx.dataStore.data.map { it[PROGRESS_EXTEND_ENABLED] ?: false }

    fun getReminderExtendEnabled(ctx: Context) =
        ctx.dataStore.data.map { it[REMINDER_EXTEND_ENABLED] ?: false }

    fun getReminderExtendMinutes(ctx: Context) =
        ctx.dataStore.data.map { prefs -> prefs[REMINDER_EXTEND_MINUTES] ?: (prefs[PROGRESS_EXTEND_MINUTES] ?: 5) }

    fun getThemeMode(ctx: Context) = ctx.dataStore.data.map { prefs ->
        prefs[THEME_MODE] ?: "dark"
    }

    fun getThemeDynamic(ctx: Context) = ctx.dataStore.data.map { prefs ->
        prefs[THEME_DYNAMIC] ?: true
    }

    fun getBluetoothDisableRequested(ctx: Context): Flow<Boolean> =
        ctx.dataStore.data.map { prefs -> prefs[KEY_BLUETOOTH_DISABLE_REQUESTED] ?: false }

    fun getWifiDisableRequested(ctx: Context) =
        ctx.dataStore.data.map { it[KEY_WIFI_DISABLE_REQUESTED] ?: false }

    fun getDefaultTimerMinutes(ctx: Context) =
        ctx.dataStore.data.map { it[DEFAULT_TIMER_MINUTES] ?: 15 }

    // -------- Setter --------
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

    suspend fun setShakeEnabled(ctx: Context, v: Boolean) =
        ctx.dataStore.edit { it[KEY_SHAKE_ENABLED] = v }

    suspend fun setShakeStrength(ctx: Context, v: Int) =
        ctx.dataStore.edit { it[KEY_SHAKE_STRENGTH] = v.coerceIn(0, 100) }

    suspend fun setShakeExtendMinutes(ctx: Context, v: Int) =
        ctx.dataStore.edit { it[KEY_SHAKE_EXTEND_MIN] = v.coerceIn(1, 30) }

    suspend fun setShakeSoundMode(ctx: Context, v: String) =
        ctx.dataStore.edit { it[KEY_SHAKE_SOUND_MODE] = if (v == "vibrate") "vibrate" else "tone" }

    suspend fun setShakeRingtone(ctx: Context, uri: String) =
        ctx.dataStore.edit { it[KEY_SHAKE_RINGTONE] = uri }

    suspend fun setShakeVolume(ctx: Context, rel: Float) =
        ctx.dataStore.edit { it[KEY_SHAKE_VOLUME] = rel.coerceIn(0f, 1f) }

    suspend fun setProgressExtendEnabled(ctx: Context, v: Boolean) =
        ctx.dataStore.edit { it[PROGRESS_EXTEND_ENABLED] = v }

    suspend fun setReminderExtendEnabled(ctx: Context, v: Boolean) =
        ctx.dataStore.edit { it[REMINDER_EXTEND_ENABLED] = v }

    suspend fun setReminderExtendMinutes(ctx: Context, m: Int) =
        ctx.dataStore.edit { it[REMINDER_EXTEND_MINUTES] = m.coerceIn(1, 30) }

    suspend fun setShakeActivationMode(ctx: Context, mode: String) =
        ctx.dataStore.edit { it[KEY_SHAKE_ACTIVATION_MODE] = if (mode == "after_start") "after_start" else "immediate" }

    suspend fun setShakeActivationDelayMinutes(ctx: Context, minutes: Int) =
        ctx.dataStore.edit { it[KEY_SHAKE_ACTIVATION_DELAY_MIN] = minutes.coerceIn(1, 30) }

    suspend fun setThemeMode(ctx: Context, id: String) {
        ctx.dataStore.edit { it[THEME_MODE] = id }
    }

    suspend fun setThemeDynamic(ctx: Context, enabled: Boolean) {
        ctx.dataStore.edit { it[THEME_DYNAMIC] = enabled }
    }

    suspend fun setBluetoothDisableRequested(ctx: Context, value: Boolean) {
        ctx.dataStore.edit { prefs ->
            prefs[KEY_BLUETOOTH_DISABLE_REQUESTED] = value
        }
    }

    suspend fun setWifiDisableRequested(ctx: Context, v: Boolean) =
        ctx.dataStore.edit { it[KEY_WIFI_DISABLE_REQUESTED] = v }

    suspend fun setDefaultTimerMinutes(ctx: Context, minutes: Int) =
        ctx.dataStore.edit { it[DEFAULT_TIMER_MINUTES] = minutes.coerceIn(1, 600) }

    suspend fun setPremiumActive(ctx: Context, v: Boolean) =
        ctx.dataStore.edit { it[PREMIUM_ACTIVE] = v }
}
