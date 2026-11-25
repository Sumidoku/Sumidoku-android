package org.sumidoku.android.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** App-local incognito switch: pauses reading-history writes entirely on the
 *  client side. Deliberately device-local and unrelated to the server's
 *  settings object — no network round-trip, instant toggle, and it applies
 *  regardless of which server profile is active. Manga detail pages can
 *  still override per manga (that flag is server-side per-title data). */
@Singleton
class IncognitoStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val enabledKey = booleanPreferencesKey("incognito_enabled")

    val enabled: Flow<Boolean> = dataStore.data.map { it[enabledKey] ?: false }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[enabledKey] = enabled }
    }
}
