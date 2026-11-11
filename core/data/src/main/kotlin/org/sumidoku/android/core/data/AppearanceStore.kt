package org.sumidoku.android.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Appearance preference (design §14 M9 theming): "system" | "dark" |
 *  "light". The app theme is dark-first (matches the webui), so light
 *  mode is the only real switch. */
@Singleton
class AppearanceStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val themeKey = stringPreferencesKey("appearance_theme")

    val theme: Flow<String> = dataStore.data.map { it[themeKey] ?: "system" }

    suspend fun setTheme(theme: String) {
        dataStore.edit { it[themeKey] = theme }
    }
}
