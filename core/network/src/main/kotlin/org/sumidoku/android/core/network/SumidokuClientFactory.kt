package org.sumidoku.android.core.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.sumidoku.android.core.model.ServerProfile
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds per-profile network stacks. Every request carries the profile's API
 * key as a bearer token — this header also applies to image and SSE requests,
 * matching the server's `require_auth` middleware (docs/android-client.md §5).
 *
 * Failover follows the Aidoku KomgaHelper pattern: connection-level failures
 * against the primary base URL are retried against the profile's mirrors in
 * order; HTTP-level responses (including errors) are never retried on another
 * host, because the server answered.
 */
@Singleton
class SumidokuClientFactory @Inject constructor() {

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun okHttp(profile: ServerProfile): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(profile.apiKey))
            .addInterceptor(FailoverInterceptor(profile))
            .addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
            )
            .build()

    fun retrofit(profile: ServerProfile): Retrofit =
        Retrofit.Builder()
            .baseUrl(profile.baseUrl.ensureTrailingSlash())
            .client(okHttp(profile))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    /** Create one of the generated OpenAPI services (e.g. `SystemApi`). */
    inline fun <reified T : Any> create(profile: ServerProfile): T = retrofit(profile).create(T::class.java)

    /** NDJSON partial streaming — design §14. The server streams
     *  newline-delimited JSON when the request asks for it; this is a
     *  client-side opt-in enhancement for screens that benefit from
     *  progressive rendering (cross-source search, source home). The
     *  terminating {"type":"final","payload":…} event mirrors the
     *  non-streaming response body. */
    fun ndjsonCall(
        profile: ServerProfile,
        path: String,
        body: JsonObject,
    ): Flow<JsonObject> = flow {
        val url = profile.baseUrl.trimEnd('/') + "/api/v1" + path
        val request = Request.Builder()
            .url(url)
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody("application/json".toMediaType()))
            .header("Accept", "application/x-ndjson")
            .build()
        okHttp(profile).newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("NDJSON call failed: ${response.code}")
            val reader = response.body.byteStream().bufferedReader()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                val obj = json.parseToJsonElement(line) as? JsonObject ?: continue
                emit(obj)
                if (obj["type"]?.toString()?.trim('"') == "final" || obj["type"]?.toString()?.trim('"') == "error") break
            }
        }
    }

    /**
     * Connectivity probe used when adding a server: GET /api/v1/version is the
     * only endpoint that works without authentication, so it doubles as the
     * "is this really a Sumidoku server?" check. Returns the reported version,
     * or null when no candidate URL answered with a parseable version.
     */
    suspend fun probeVersion(profile: ServerProfile): String? =
        probeVersionDetailed(profile).version

    /** Detailed probe result: the version, or the last failure reason so the
     *  add-server dialog can tell "DNS unreachable" apart from "not a
     *  Sumidoku server". */
    data class ProbeResult(val version: String?, val error: String?)

    suspend fun probeVersionDetailed(profile: ServerProfile): ProbeResult {
        var lastError: String? = null
        for (base in profile.candidates) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .readTimeout(PROBE_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .addInterceptor(AuthInterceptor(profile.apiKey))
                    .build()
                val request = okhttp3.Request.Builder()
                    .url(base.ensureTrailingSlash() + "api/v1/version")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        lastError = "HTTP ${response.code}"
                        return@use
                    }
                    val body = response.body.string()
                    val element = json.parseToJsonElement(body).jsonObjectOrNull()
                    val version = element?.get("version")?.toString()?.trim('"')
                    if (!version.isNullOrBlank()) return ProbeResult(version, null)
                    lastError = "响应不是 Sumidoku 服务器"
                }
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
            }
        }
        return ProbeResult(null, lastError ?: "无可用地址")
    }

    private fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"

    private fun kotlinx.serialization.json.JsonElement.jsonObjectOrNull() =
        this as? kotlinx.serialization.json.JsonObject

    private class AuthInterceptor(private val apiKey: String?) : okhttp3.Interceptor {
        override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
            val request = if (apiKey.isNullOrBlank()) {
                chain.request()
            } else {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $apiKey")
                    .build()
            }
            return chain.proceed(request)
        }
    }

    private class FailoverInterceptor(private val profile: ServerProfile) : okhttp3.Interceptor {
        override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
            var lastError: IOException? = null
            for ((index, base) in profile.candidates.withIndex()) {
                val request = if (index == 0) {
                    chain.request()
                } else {
                    chain.request().rebasedOn(base) ?: break
                }
                try {
                    return chain.proceed(request)
                } catch (e: IOException) {
                    lastError = e
                }
            }
            throw lastError ?: IOException("No candidate base URL available")
        }

        private fun okhttp3.Request.rebasedOn(newBase: String): okhttp3.Request? {
            val current = url
            val base = newBase.ensureSlash().toHttpUrlOrNull() ?: return null
            val relative = current.encodedPath.removePrefix(base.encodedPath.removeSuffix("/"))
            val rebased = base.newBuilder()
                .encodedPath(base.encodedPath.removeSuffix("/") + relative)
                .encodedQuery(current.encodedQuery)
                .build()
            return newBuilder().url(rebased).build()
        }

        private fun String.ensureSlash(): String = if (endsWith("/")) this else "$this/"
    }

    companion object {
        const val CONNECT_TIMEOUT_SECONDS = 5L
        const val READ_TIMEOUT_SECONDS = 60L
        const val PROBE_READ_TIMEOUT_SECONDS = 15L
    }
}
