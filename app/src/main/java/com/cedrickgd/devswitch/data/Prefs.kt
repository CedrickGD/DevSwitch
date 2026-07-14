package com.cedrickgd.devswitch.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "devswitch_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class Prefs(private val context: Context) {

    companion object {
        private val KEY_ONBOARDED = booleanPreferencesKey("onboarded")
        private val KEY_THEME = stringPreferencesKey("theme_mode")
        private val KEY_ACCENT = stringPreferencesKey("accent")
        private val KEY_WATCHED = stringSetPreferencesKey("watched")
        private val KEY_PERSISTENT_STATE = booleanPreferencesKey("persistent_state_notifications")
        private val KEY_SKIP_PLAY_PROTECT = booleanPreferencesKey("skip_play_protect")

        const val DEFAULT_ACCENT = "indigo"
    }

    val onboarded: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_ONBOARDED] ?: false }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[KEY_THEME] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    val accent: Flow<String> =
        context.dataStore.data.map { it[KEY_ACCENT] ?: DEFAULT_ACCENT }

    val watched: Flow<Set<String>> =
        context.dataStore.data.map { it[KEY_WATCHED] ?: emptySet() }

    /** Whether "X is ON" notifications for app-made toggles are ongoing (non-dismissible). */
    val persistentStateNotifications: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PERSISTENT_STATE] ?: true }

    /** Whether to keep Play Protect install scanning disabled (re-applied before each update). */
    val skipPlayProtect: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SKIP_PLAY_PROTECT] ?: false }

    suspend fun setOnboarded() {
        context.dataStore.edit { it[KEY_ONBOARDED] = true }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setAccent(id: String) {
        context.dataStore.edit { it[KEY_ACCENT] = id }
    }

    suspend fun setPersistentStateNotifications(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PERSISTENT_STATE] = enabled }
    }

    suspend fun setSkipPlayProtect(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SKIP_PLAY_PROTECT] = enabled }
    }

    /** Toggles a watched setting and returns the resulting set. */
    suspend fun toggleWatched(key: String): Set<String> {
        var result: Set<String> = emptySet()
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_WATCHED] ?: emptySet()
            result = if (key in current) current - key else current + key
            prefs[KEY_WATCHED] = result
        }
        return result
    }
}
