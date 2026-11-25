package org.sumidoku.android.core.network.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.sumidoku.android.core.common.AppVersion

/**
 * GitHub's `releases/latest` payload, trimmed to what an update prompt needs.
 *
 * Decoding is deliberately forgiving: GitHub adds fields over time and the app
 * must not break because of one it does not know.
 */
internal val UpdateJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
}

@Serializable
internal data class UpdateReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("published_at") val publishedAt: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<UpdateAssetDto> = emptyList(),
) {
    val version: String?
        get() = tagName.trim().removePrefix("v").removePrefix("V").takeIf { it.isNotBlank() }
}

@Serializable
internal data class UpdateAssetDto(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
)

/** One release, ready for the dialog. */
data class UpdateInfo(
    val version: String,
    val title: String,
    val notes: String,
    val releaseUrl: String,
    /** The release APK when the workflow attached one; otherwise null. */
    val downloadUrl: String?,
    val publishedAt: String?,
)

/** The release workflow names its uploads `sumidoku-android-<version>.apk`. */
private const val ApkPrefix = "sumidoku-android"
private const val ApkSuffix = ".apk"
/** Signed builds are preferred; the debug fallback is only used when the
 *  signing secrets are absent, so `-debug` marks the older path. */
private const val DebugMarker = "-debug"

internal fun UpdateReleaseDto.toUpdateInfo(): UpdateInfo? {
    // A tag the client cannot read means it cannot tell whether it is newer.
    AppVersion.parse(tagName) ?: return null
    val version = version ?: return null
    val apk = assets.firstOrNull { it.isReleaseApk() }
        ?: assets.firstOrNull { it.isApk() }
    return UpdateInfo(
        version = version,
        title = name.ifBlank { tagName.trim() },
        notes = body,
        releaseUrl = htmlUrl,
        downloadUrl = apk?.downloadUrl,
        publishedAt = publishedAt,
    )
}

private fun UpdateAssetDto.isApk(): Boolean =
    downloadUrl.isNotBlank() &&
        name.startsWith(ApkPrefix, ignoreCase = true) &&
        name.endsWith(ApkSuffix, ignoreCase = true)

private fun UpdateAssetDto.isReleaseApk(): Boolean =
    isApk() && !name.contains(DebugMarker, ignoreCase = true)
