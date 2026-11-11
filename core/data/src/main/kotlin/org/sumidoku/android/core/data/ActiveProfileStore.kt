package org.sumidoku.android.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Remembers which server profile is active. A null value means "no server
 *  selected yet" and the app routes to the server manager. */
@Singleton
class ActiveProfileStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val activeIdKey = longPreferencesKey("active_server_id")

    val activeId: Flow<Long?> = dataStore.data.map { it[activeIdKey] }

    suspend fun setActive(id: Long) {
        dataStore.edit { it[activeIdKey] = id }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(activeIdKey) }
    }
}
