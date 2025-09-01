package com.tigonic.snoozely.util

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Dedizierter DataStore für allgemeine App-Einstellungen
private val Context.dataStore by preferencesDataStore(name = "settings_preferences")

/**
 * Zentrale Preferences-Helferklasse für App-/User-Settings.
 *
 * Themenblöcke:
 * 1) Keys/Defaults
 * 2) Getter (nach Themen)
 * 3) Setter (nach Themen)
 * 4) Ads/Consent & Zähler
 */
object SettingsPreferenceHelper {

    // ------------------------------------------------------------
    // 1) Keys + Defaults
    // ------------------------------------------------------------

    // Timer Defaults / Bedienung
    private val DEFAULT_TIMER_MINUTES = intPreferencesKey("default_timer_minutes")
    private val PROGRESS_EXTEND_MINUTES = intPreferencesKey("progress_extend_minutes")

    // General
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

    // Shake settings
    private val KEY_SHAKE_ENABLED = booleanPreferencesKey("shake_extend_enabled")
    private val KEY_SHAKE_STRENGTH = intPreferencesKey("shake_strength")
    private val KEY_SHAKE_EXTEND_MIN = intPreferencesKey("shake_extend_minutes")
    private val KEY_SHAKE_SOUND_MODE = stringPreferencesKey("shake_sound_mode")
    private val KEY_SHAKE_RINGTONE = stringPreferencesKey("shake_ringtone_uri")
    private val KEY_SHAKE_VOLUME = floatPreferencesKey("shake_volume")

    // Shake activation window
    private val KEY_SHAKE_ACTIVATION_MODE = stringPreferencesKey("shake_activation_mode") // "immediate" | "after_start"
    private val KEY_SHAKE_ACTIVATION_DELAY_MIN = intPreferencesKey("shake_activation_delay_min") // minutes

    // Notification action buttons
    private val PROGRESS_EXTEND_ENABLED = booleanPreferencesKey("progress_extend_enabled")
    private val REMINDER_EXTEND_ENABLED = booleanPreferencesKey("reminder_extend_enabled")
    private val REMINDER_EXTEND_MINUTES = intPreferencesKey("reminder_extend_minutes")

    // Theme
    private val THEME_MODE = stringPreferencesKey("theme_mode") // "system" | "light" | "dark"
    private val THEME_DYNAMIC = booleanPreferencesKey("theme_dynamic")

    // Bluetooth/WiFi requests
    private val KEY_BLUETOOTH_DISABLE_REQUESTED = booleanPreferencesKey("bluetooth_disable_requested")
    private val KEY_WIFI_DISABLE_REQUESTED = booleanPreferencesKey("wifi_disable_requested")

    // Premium
    private val PREMIUM_ACTIVE = booleanPreferencesKey("premium_active")

    // Ads/Consent
    private val ADS_CONSENT_RESOLVED = booleanPreferencesKey("ads_consent_resolved")
    private val ADS_CONSENT_TYPE = stringPreferencesKey("ads_consent_type")
    private val ADS_OPEN_COUNTER = intPreferencesKey("ads_open_counter")

    // Battery/Setup-Hinweis
    private val KEY_SUPPRESS_SETUP_HINT = booleanPreferencesKey("suppress_setup_hint")
    private val KEY_BATTERY_OPT_PROMPT_HANDLED = booleanPreferencesKey("battery_opt_prompt_handled")

    // ------------------------------------------------------------
    // 2) Getter
    // ------------------------------------------------------------

    // Timer / Bedienung
    fun getDefaultTimerMinutes(ctx: Context): Flow<Int> =
        ctx.dataStore.data.map { it[DEFAULT_TIMER_MINUTES] ?: 15 }

    fun getProgressExtendMinutes(ctx: Context): Flow<Int> =
        ctx.dataStore.data.map { it[PROGRESS_EXTEND_MINUTES] ?: 5 }

    fun getStopAudio(ctx: Context): Flow<Boolean> =
        ctx.dataStore.data.map { it[STOP_AUDIO] ?: true }

    fun getScreenOff(ctx: Context): Flow<Boolean> =
        ctx.dataStore.data.map { it[SCREEN_OFF] ?: false }

    fun getNotificationEnabled(ctx: Context): Flow<Boolean> =
        ctx.dataStore.data.map { it[NOTIFICATION_ENABLED] ?: false }

    fun getTimerVibrate(ctx: Context): Flow<Boolean> =
        ctx.dataStore.data.map { it[TIMER_VIBRATE] ?: false }

    fun getFadeOut(ctx: Context): Flow<Float> =
        ctx.dataStore.data.map { it[FADE_OUT] ?: 30f }

    fun getLanguage(ctx: Context): Flow<String> =
        ctx.dataStore.data.map { it[LANGUAGE] ?: "de" }

    fun getShowProgressNotification(ctx: Context): Flow<Boolean> =
        ctx.dataStore.data.map { it[SHOW_PROGRESS_NOTIFICATION] ?: false }

    fun getShowReminderPopup(ctx: Context): Flow<Boolean> =
        ctx.dataStore.data.map { it[SHOW_REMINDER_POPUP] ?: false }

    fun getReminderMinutes(ctx: Context): Flow<Int> =
        ctx.dataStore.data.map { it[REMINDER_MINUTES] ?: 5 }

    fun getIsFirstRun(ctx: Context): Flow<Boolean> =
        ctx.dataStore.data.map { it[IS_FIRST_RUN] ?: true }

    // Shake
    fun getShakeEnabled(ctx: Context): Flow<Boolean> =
        ctx.dataStore.data.map { it[KEY_SHAKE_ENABLED] ?: false }

    fun getShakeStrength(ctx: Context): Flow<Int> =
        ctx.dataStore.data.map { it[KEY_SHAKE_STRENGTH] ?: 50 }

    fun getShakeExtendMinutes(ctx: Context): Flow<Int> =
        ctx.dataStore.data.map { it[KEY_SHAKE_EXTEND_MIN] ?: 10 }

    fun getShakeSoundMode(ctx: Context): Flow<String> =
        ctx.dataStore.data.map { it[KEY_SHAKE_SOUND_MODE] ?: "tone" }

    fun getShakeRingtone(ctx: Context): Flow<String> =
        ctx.dataStore.data.map { it[KEY_SHAKE_RINGTONE] ?: "" }

    fun getShakeVolume(ctx: Context): Flow<Float> =
        ctx.dataStore.data.map { it[KEY_SHAKE_VOLUME] ?: 1f }

    // Shake Activation Window
    fun getShakeActivationMode(ctx: Context): Flow<String> =
        ctx.dataStore.data.map { it[KEY_SHAKE_ACTIVATION_MODE] ?: "immediate" }

    fun getShakeActivationDelayMinutes(ctx: Context): Flow<Int> =
        ctx.dataStore.data.map { it[KEY_SHAKE_ACTIVATION_DELAY_MIN] ?: 3 }

    // Notifications – Action-Konfiguration
    fun getProgressExtendEnabled(ctx: Context): Flow<Boolean> =
        ctx.dataStore.data.map { it[PROGRESS_EXTEND_ENABLED] ?: false }

    fun getReminderExtendEnabled(ctx: Context): Flow<Boolean> =
        ctx.dataStore.data.map { it[REMINDER_EXTEND_ENABLED] ?: false }

    fun getReminderExtendMinutes(ctx: Context): Flow<Int> =
        ctx.dataStore.data.map { p -> p[REMINDER_EXTEND_MINUTES] ?: (p[PROGRESS_EXTEND_MINUTES] ?: 5) }

    // Theme
    fun getThemeMode(ctx: Context): Flow<String> =
        ctx.dataStore.data.map { p -> p[THEME_MODE] ?: "dark" }

    fun getThemeDynamic(ctx: Context): Flow<Boolean> =
        ctx.dataStore.data.map { p -> p[THEME_DYNAMIC] ?: true }

    // Bluetooth/WiFi
    fun getBluetoothDisableRequested(ctx: Context): Flow<Boolean> =
        ctx.dataStore.data.map { p -> p[KEY_BLUETOOTH_DISABLE_REQUESTED] ?: false }

    fun getWifiDisableRequested(ctx: Context): Flow<Boolean> =
        ctx.dataStore.data.map { p -> p[KEY_WIFI_DISABLE_REQUESTED] ?: false }

    // Premium
    fun getPremiumActive(ctx: Context): Flow<Boolean> =
        ctx.dataStore.data.map { it[PREMIUM_ACTIVE] ?: false }

    // Ads/Consent
    fun getAdsConsentResolved(ctx: Context): Flow<Boolean> =
        ctx.dataStore.data.map { it[ADS_CONSENT_RESOLVED] ?: false }

    fun getAdsConsentType(ctx: Context): Flow<String> =
        ctx.dataStore.data.map { it[ADS_CONSENT_TYPE] ?: "Unknown" }

    fun getAdsOpenCounter(ctx: Context): Flow<Int> =
        ctx.dataStore.data.map { it[ADS_OPEN_COUNTER] ?: 0 }

    // Battery/Setup-Hinweis
    fun getSuppressSetupHint(ctx: Context): Flow<Boolean> =
        ctx.dataStore.data.map { it[KEY_SUPPRESS_SETUP_HINT] ?: false }

    fun getBatteryOptPromptHandled(ctx: Context): Flow<Boolean> =
        ctx.dataStore.data.map { it[KEY_BATTERY_OPT_PROMPT_HANDLED] ?: false }

    // ------------------------------------------------------------
    // 3) Setter
    // ------------------------------------------------------------

    // Timer / Bedienung
    suspend fun setDefaultTimerMinutes(ctx: Context, value: Int) {
        ctx.dataStore.edit { it[DEFAULT_TIMER_MINUTES] = value.coerceIn(1, 300) }
    }

    suspend fun setProgressExtendMinutes(ctx: Context, value: Int) {
        ctx.dataStore.edit { it[PROGRESS_EXTEND_MINUTES] = value.coerceIn(1, 30) }
    }

    suspend fun setStopAudio(ctx: Context, value: Boolean) {
        ctx.dataStore.edit { it[STOP_AUDIO] = value }
    }

    suspend fun setScreenOff(ctx: Context, value: Boolean) {
        ctx.dataStore.edit { it[SCREEN_OFF] = value }
    }

    suspend fun setNotificationEnabled(ctx: Context, value: Boolean) {
        ctx.dataStore.edit { it[NOTIFICATION_ENABLED] = value }
    }

    suspend fun setTimerVibrate(ctx: Context, value: Boolean) {
        ctx.dataStore.edit { it[TIMER_VIBRATE] = value }
    }

    suspend fun setFadeOut(ctx: Context, value: Float) {
        ctx.dataStore.edit { it[FADE_OUT] = value.coerceIn(0f, 120f) }
    }

    suspend fun setLanguage(ctx: Context, value: String) {
        ctx.dataStore.edit { it[LANGUAGE] = value }
    }

    suspend fun setShowProgressNotification(ctx: Context, value: Boolean) {
        ctx.dataStore.edit { it[SHOW_PROGRESS_NOTIFICATION] = value }
    }

    suspend fun setShowReminderPopup(ctx: Context, value: Boolean) {
        ctx.dataStore.edit { it[SHOW_REMINDER_POPUP] = value }
    }

    suspend fun setReminderMinutes(ctx: Context, value: Int) {
        ctx.dataStore.edit { it[REMINDER_MINUTES] = value.coerceIn(1, 10) }
    }

    suspend fun setIsFirstRun(ctx: Context, value: Boolean) {
        ctx.dataStore.edit { it[IS_FIRST_RUN] = value }
    }

    // Shake
    suspend fun setShakeEnabled(ctx: Context, v: Boolean) =
        ctx.dataStore.edit { it[KEY_SHAKE_ENABLED] = v }

    suspend fun setShakeStrength(ctx: Context, v: Int) =
        ctx.dataStore.edit { it[KEY_SHAKE_STRENGTH] = v.coerceIn(0, 100) }

    suspend fun setShakeExtendMinutes(ctx: Context, v: Int) =
        ctx.dataStore.edit { it[KEY_SHAKE_EXTEND_MIN] = v.coerceIn(1, 30) }

    suspend fun setShakeSoundMode(ctx: Context, v: String) =
        ctx.dataStore.edit { it[KEY_SHAKE_SOUND_MODE] = v }

    suspend fun setShakeRingtone(ctx: Context, v: String) =
        ctx.dataStore.edit { it[KEY_SHAKE_RINGTONE] = v }

    suspend fun setShakeVolume(ctx: Context, v: Float) =
        ctx.dataStore.edit { it[KEY_SHAKE_VOLUME] = v.coerceIn(0f, 1f) }

    // Shake Activation Window
    suspend fun setShakeActivationMode(ctx: Context, value: String) {
        val safe = when (value) { "immediate", "after_start" -> value; else -> "immediate" }
        ctx.dataStore.edit { it[KEY_SHAKE_ACTIVATION_MODE] = safe }
    }

    suspend fun setShakeActivationDelayMinutes(ctx: Context, value: Int) {
        ctx.dataStore.edit { it[KEY_SHAKE_ACTIVATION_DELAY_MIN] = value.coerceIn(1, 30) }
    }

    // Theme
    suspend fun setThemeMode(ctx: Context, value: String) {
        val safe = when (value) { "system", "light", "dark" -> value; else -> "system" }
        ctx.dataStore.edit { it[THEME_MODE] = safe }
    }

    suspend fun setThemeDynamic(ctx: Context, value: Boolean) {
        ctx.dataStore.edit { it[THEME_DYNAMIC] = value }
    }

    // Bluetooth/WiFi requests
    suspend fun setBluetoothDisableRequested(ctx: Context, value: Boolean) {
        ctx.dataStore.edit { it[KEY_BLUETOOTH_DISABLE_REQUESTED] = value }
    }

    suspend fun setWifiDisableRequested(ctx: Context, value: Boolean) {
        ctx.dataStore.edit { it[KEY_WIFI_DISABLE_REQUESTED] = value }
    }

    // Premium
    suspend fun setPremiumActive(ctx: Context, v: Boolean) =
        ctx.dataStore.edit { it[PREMIUM_ACTIVE] = v }

    // ------------------------------------------------------------
    // 4) Ads/Consent & Zähler
    // ------------------------------------------------------------

    suspend fun setAdsConsent(ctx: Context, resolved: Boolean, type: String) {
        ctx.dataStore.edit {
            it[ADS_CONSENT_RESOLVED] = resolved
            it[ADS_CONSENT_TYPE] = type
        }
    }

    suspend fun incrementAdsOpenCounter(ctx: Context) {
        ctx.dataStore.edit { prefs ->
            val current = prefs[ADS_OPEN_COUNTER] ?: 0
            prefs[ADS_OPEN_COUNTER] = (current + 1).coerceAtMost(99_999)
        }
    }

    suspend fun resetAdsOpenCounter(ctx: Context) {
        ctx.dataStore.edit { it[ADS_OPEN_COUNTER] = 0 }
    }

    // Battery-Setup Setter (Getter sind bereits oben einsortiert)
    suspend fun setSuppressSetupHint(ctx: Context, suppress: Boolean) {
        ctx.dataStore.edit { it[KEY_SUPPRESS_SETUP_HINT] = suppress }
    }

    suspend fun setBatteryOptPromptHandled(ctx: Context, handled: Boolean) {
        ctx.dataStore.edit { it[KEY_BATTERY_OPT_PROMPT_HANDLED] = handled }
    }
}
