package org.sumidoku.android.core.network.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one unauthenticated request the app makes: which GitHub release is latest.
 *
 * Deliberately not built on [org.sumidoku.android.core.network.SumidokuClientFactory]:
 * that stack is per server profile, carries the profile's API key on every call
 * and fails over between the user's mirrors. An update check must work before a
 * server is configured and must never hand a server credential to github.com,
 * so it owns a small plain [OkHttpClient] instead — the same reasoning as
 * KMReader's `UpdateRepository`.
 */
@Singleton
class UpdateRepository @Inject constructor() {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /** Latest published release, or a failure carrying a user-readable reason. */
    suspend fun latestRelease(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$ApiBase/repos/$RepositorySlug/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", UserAgent)
                .build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "GitHub returned HTTP ${response.code}" }
                val body = response.body?.string().orEmpty()
                val dto = UpdateJson.decodeFromString(UpdateReleaseDto.serializer(), body)
                check(!dto.draft) { "GitHub returned a draft release" }
                dto.toUpdateInfo() ?: error("Unreadable release tag ${dto.tagName}")
            }
        }
    }

    companion object {
        /** The repository the release workflow publishes to. */
        const val RepositorySlug = "Sumidoku/Sumidoku-android"

        private const val ApiBase = "https://api.github.com"
        private const val UserAgent = "Sumidoku-Android"
        private const val TIMEOUT_SECONDS = 15L
        private const val CALL_TIMEOUT_SECONDS = 20L
    }
}
