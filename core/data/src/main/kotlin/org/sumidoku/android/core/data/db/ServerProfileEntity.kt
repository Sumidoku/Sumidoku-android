package org.sumidoku.android.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.sumidoku.android.core.model.ServerProfile

@Entity(tableName = "server_profile")
data class ServerProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val baseUrl: String,
    /** Mirrors, one per line. */
    val mirrorsText: String = "",
    // TODO(M9): move to Keystore-backed encrypted storage, see docs §5.
    val apiKey: String? = null,
)

fun ServerProfileEntity.toDomain(): ServerProfile = ServerProfile(
    id = id,
    name = name,
    baseUrl = baseUrl,
    mirrors = mirrorsText.lines().map { it.trim() }.filter { it.isNotEmpty() },
    apiKey = apiKey,
)

fun ServerProfile.toEntity(): ServerProfileEntity = ServerProfileEntity(
    id = id,
    name = name,
    baseUrl = baseUrl,
    mirrorsText = mirrors.joinToString("\n"),
    apiKey = apiKey,
)
