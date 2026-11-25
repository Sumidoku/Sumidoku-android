package org.sumidoku.android.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Library grid shape (design 34 / Komikku `MangaCompactGridItem`): the
 *  compact form puts the title on the cover's bottom gradient, comfortable
 *  keeps a two-line title under the cover, and list is the row form. */
const val FORM_COMPACT = "compact"
const val FORM_COMFORTABLE = "comfortable"
const val FORM_LIST = "list"

val LIBRARY_FORMS = listOf(FORM_COMPACT, FORM_COMFORTABLE, FORM_LIST)

/** Density slider range (Komikku `LibrarySettingsDialog` parity): the slider
 *  runs 0..10 where 0 means auto (adaptive) and 1..10 fix the column count. */
const val LIBRARY_COLUMNS_MAX = 10

/** Everything the display sheet owns. Columns are remembered per
 *  orientation (Komikku `LibraryColumns`), so rotating does not overwrite
 *  the portrait choice — null means "auto/adaptive" at that orientation. */
@Serializable
data class LibraryDisplay(
    val form: String = FORM_COMPACT,
    val portraitColumns: Int? = null,
    val landscapeColumns: Int? = null,
    val badgeUnread: Boolean = true,
    val badgeDownload: Boolean = true,
    /** Source mark stays off by default (design 34). */
    val badgeSource: Boolean = false,
) {
    /** Unknown forms/columns (legacy or hand-edited storage) fall back to
     *  the compact auto layout instead of showing a broken grid. */
    fun sanitized(): LibraryDisplay = copy(
        form = if (form in LIBRARY_FORMS) form else FORM_COMPACT,
        portraitColumns = portraitColumns.sanitizedColumns(),
        landscapeColumns = landscapeColumns.sanitizedColumns(),
    )

    fun columns(landscape: Boolean): Int? =
        if (landscape) landscapeColumns else portraitColumns

    private fun Int?.sanitizedColumns(): Int? = this?.takeIf { it in 1..LIBRARY_COLUMNS_MAX }
}

@Singleton
class LibraryDisplayStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val key = stringPreferencesKey("library_display")
    private val json = Json { ignoreUnknownKeys = true }

    /** Broken JSON counts as "nothing saved" and never blocks the screen. */
    suspend fun load(): LibraryDisplay {
        val fallback = LibraryDisplay()
        val raw = dataStore.data.first()[key] ?: return fallback
        return runCatching {
            json.decodeFromString(LibraryDisplay.serializer(), raw)
        }.getOrDefault(fallback).sanitized()
    }

    suspend fun save(display: LibraryDisplay) {
        val encoded = json.encodeToString(LibraryDisplay.serializer(), display.sanitized())
        dataStore.edit { prefs -> prefs[key] = encoded }
    }
}
