package org.sumidoku.android.core.download

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One locally downloaded chapter: the CBZ fetched from
 *  GET /chapter/{id}/file, stored under downloads/<mangaId>/<chapterId>.cbz. */
@Entity(tableName = "local_downloads")
data class LocalDownloadEntity(
    @PrimaryKey val chapterId: Long,
    val mangaId: Long,
    val mangaTitle: String,
    val chapterLabel: String,
    val path: String,
    val bytes: Long,
    val status: String,
    val error: String? = null,
    val createdAt: Long,
)
