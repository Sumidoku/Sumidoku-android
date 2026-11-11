package org.sumidoku.android.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** One pending progress write captured while the server was
 *  unreachable (design §12 offline progress queue). */
data class PendingProgress(
    val chapterId: Long,
    val lastPageRead: Int,
    val isRead: Boolean,
)

/** Offline progress queue: reader PATCH failures are stored here and
 *  flushed best-effort on the next app start (design §12, M2). The
 *  wire format is a compact `id:page:read;…` string. */
@Singleton
class OfflineProgressStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val queueKey = stringPreferencesKey("offline_progress_queue")

    val pending: Flow<List<PendingProgress>> = dataStore.data.map { prefs ->
        parse(prefs[queueKey])
    }

    /** Latest write per chapter wins (the server applies patches
     *  idempotently, progress only moves forward). */
    suspend fun add(entry: PendingProgress) {
        dataStore.edit { prefs ->
            val merged = parse(prefs[queueKey])
                .filterNot { it.chapterId == entry.chapterId } + entry
            prefs[queueKey] = encode(merged)
        }
    }

    suspend fun remove(chapterIds: Set<Long>) {
        if (chapterIds.isEmpty()) return
        dataStore.edit { prefs ->
            val kept = parse(prefs[queueKey]).filterNot { it.chapterId in chapterIds }
            prefs[queueKey] = encode(kept)
        }
    }

    private fun parse(raw: String?): List<PendingProgress> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(";").mapNotNull { part ->
            val bits = part.split(":")
            val id = bits.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val page = bits.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            PendingProgress(id, page, bits.getOrNull(2) == "1")
        }
    }

    private fun encode(entries: List<PendingProgress>): String =
        entries.joinToString(";") { "${it.chapterId}:${it.lastPageRead}:${if (it.isRead) 1 else 0}" }
}
