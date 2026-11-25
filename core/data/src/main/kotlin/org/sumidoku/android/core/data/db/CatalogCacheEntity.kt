package org.sumidoku.android.core.data.db

import androidx.room.Entity

/**
 * One cached item of a server-backed list (library page, history page, updates).
 * Rows are namespaced by profile so switching servers never mixes content, and
 * ordered by [position] so a seed restores the page in display order. The payload
 * is the @Serializable API model encoded as JSON — one generic table instead of a
 * per-screen schema.
 */
@Entity(tableName = "catalog_cache", primaryKeys = ["profileId", "listKey", "position"])
data class CatalogCacheEntity(
    val profileId: Long,
    val listKey: String,
    val position: Int,
    val payloadJson: String,
)
