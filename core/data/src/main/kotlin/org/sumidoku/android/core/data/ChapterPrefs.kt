package org.sumidoku.android.core.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.sumidoku.android.core.api.model.ChapterWithDownload

/** Tri-state chapter filter value (webui chapterPrefs.ts: 0→1→-1 cycle). */
enum class Tri {
    OFF, INCLUDE, EXCLUDE;

    fun next(): Tri = when (this) {
        OFF -> INCLUDE
        INCLUDE -> EXCLUDE
        EXCLUDE -> OFF
    }
}

/** Chapter list sort keys (webui chapterPrefs.ts sort). */
enum class ChapterListSort {
    SOURCE, CHAPTER, DATE;
}

/** Per-manga chapter list presentation prefs, persisted per manga id
 *  (webui `sumidoku.chapterPrefs.{mangaId}` parity). [scanlators] null means
 *  "all"; an empty-string entry stands for chapters with no scanlator. */
data class ChapterPrefs(
    val sort: ChapterListSort = ChapterListSort.SOURCE,
    val asc: Boolean = false,
    val unread: Tri = Tri.OFF,
    val downloaded: Tri = Tri.OFF,
    val bookmarked: Tri = Tri.OFF,
    val locked: Tri = Tri.OFF,
    val language: String? = null,
    val scanlators: Set<String>? = null,
) {
    val filtersActive: Boolean
        get() = unread != Tri.OFF || downloaded != Tri.OFF ||
            bookmarked != Tri.OFF || locked != Tri.OFF ||
            language != null || scanlators != null

    private fun Tri.matches(value: Boolean): Boolean = when (this) {
        Tri.OFF -> true
        Tri.INCLUDE -> value
        Tri.EXCLUDE -> !value
    }

    private fun accepts(chapter: ChapterWithDownload): Boolean {
        if (!unread.matches(!chapter.isRead)) return false
        if (!downloaded.matches(chapter.isDownloaded)) return false
        if (!bookmarked.matches(chapter.isBookmarked)) return false
        if (!locked.matches(chapter.locked)) return false
        language?.let { if (chapter.language != it) return false }
        scanlators?.let { selected ->
            val groups = chapter.scanlators.orEmpty()
            if (groups.none { it in selected } && !(groups.isEmpty() && "" in selected)) return false
        }
        return true
    }

    /** Filter + sort. SOURCE keeps the API order (webui "source" sort);
     *  chapters without a number sort as -1, chapters without a date sink
     *  to the end in both directions. */
    fun applyTo(chapters: List<ChapterWithDownload>): List<ChapterWithDownload> {
        val filtered = chapters.filter { accepts(it) }
        return when (sort) {
            ChapterListSort.SOURCE -> if (asc) filtered.asReversed() else filtered
            ChapterListSort.CHAPTER -> {
                val sorted = filtered.sortedBy { it.chapterNumber ?: -1f }
                if (asc) sorted else sorted.asReversed()
            }
            ChapterListSort.DATE -> {
                val dated = filtered.filter { it.dateUploaded != null }
                val undated = filtered.filter { it.dateUploaded == null }
                val sorted = if (asc) {
                    dated.sortedBy { it.dateUploaded }
                } else {
                    dated.sortedByDescending { it.dateUploaded }
                }
                sorted + undated
            }
        }
    }

    fun encode(): String = buildJsonObject {
        put("sort", sort.name.lowercase())
        put("asc", asc)
        put("unread", unread.toWire())
        put("downloaded", downloaded.toWire())
        put("bookmarked", bookmarked.toWire())
        put("locked", locked.toWire())
        language?.let { put("language", it) }
        scanlators?.let { selected -> put("scanlators", JsonArray(selected.map { JsonPrimitive(it) })) }
    }.toString()

    companion object {
        private fun Tri.toWire(): Int = when (this) {
            Tri.OFF -> 0
            Tri.INCLUDE -> 1
            Tri.EXCLUDE -> -1
        }

        private fun triFromWire(value: Int?): Tri = when (value) {
            1 -> Tri.INCLUDE
            -1 -> Tri.EXCLUDE
            else -> Tri.OFF
        }

        fun decode(raw: String): ChapterPrefs? = runCatching {
            val obj = Json.parseToJsonElement(raw) as? JsonObject ?: return null
            ChapterPrefs(
                sort = when (obj["sort"]?.jsonPrimitive?.contentOrNull) {
                    "chapter" -> ChapterListSort.CHAPTER
                    "date" -> ChapterListSort.DATE
                    else -> ChapterListSort.SOURCE
                },
                asc = obj["asc"]?.jsonPrimitive?.booleanOrNull ?: false,
                unread = triFromWire(obj["unread"]?.jsonPrimitive?.intOrNull),
                downloaded = triFromWire(obj["downloaded"]?.jsonPrimitive?.intOrNull),
                bookmarked = triFromWire(obj["bookmarked"]?.jsonPrimitive?.intOrNull),
                locked = triFromWire(obj["locked"]?.jsonPrimitive?.intOrNull),
                language = obj["language"]?.jsonPrimitive?.contentOrNull,
                scanlators = (obj["scanlators"] as? JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?.toSet(),
            )
        }.getOrNull()
    }
}

/** Per-manga persistence on the shared app DataStore (same store the global
 *  search filter store uses). */
@Singleton
class ChapterPrefsStore @Inject constructor(
    private val dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
) {
    private fun key(mangaId: Long) = stringPreferencesKey("chapter_prefs_$mangaId")

    suspend fun load(mangaId: Long): ChapterPrefs? =
        dataStore.data.first()[key(mangaId)]?.let(ChapterPrefs::decode)

    suspend fun save(mangaId: Long, prefs: ChapterPrefs) {
        dataStore.edit { it[key(mangaId)] = prefs.encode() }
    }
}
