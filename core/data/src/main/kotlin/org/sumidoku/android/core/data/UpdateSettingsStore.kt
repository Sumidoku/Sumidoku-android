package org.sumidoku.android.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device-local state of the in-app update check (no server round-trip, so it
 * belongs beside the other preference stores rather than in server settings):
 *
 * - [autoCheck] — default on; the launch check is throttled to once a day;
 * - [lastCheckedAt] — epoch millis of the last *successful* lookup, so a failed
 *   check retries on the next launch instead of going silent for a day;
 * - [dismissedVersion] — the release the user chose "Later" for, so the same
 *   version is not announced again on every launch (a manual check still
 *   re-opens it).
 */
@Singleton
class UpdateSettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val autoCheckKey = booleanPreferencesKey("update_auto_check")
    private val lastCheckedKey = longPreferencesKey("update_last_checked_at")
    private val dismissedKey = stringPreferencesKey("update_dismissed_version")

    val autoCheck: Flow<Boolean> = dataStore.data.map { it[autoCheckKey] ?: true }

    /** 0 means "never checked". */
    val lastCheckedAt: Flow<Long> = dataStore.data.map { it[lastCheckedKey] ?: 0L }

    val dismissedVersion: Flow<String> = dataStore.data.map { it[dismissedKey].orEmpty() }

    suspend fun setAutoCheck(enabled: Boolean) {
        dataStore.edit { it[autoCheckKey] = enabled }
    }

    suspend fun setLastCheckedAt(atMillis: Long) {
        dataStore.edit { it[lastCheckedKey] = atMillis }
    }

    suspend fun setDismissedVersion(version: String) {
        dataStore.edit { it[dismissedKey] = version }
    }
}
