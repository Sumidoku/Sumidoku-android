package org.sumidoku.android.core.common

import java.net.URLEncoder

/** Cover URL for a registered manga, identical to webui `coverUrl(mangaId, v)`:
 *  the server resolves the image itself (cache, source fetch, or local-disk
 *  resolution for internal sources), so this works even when the manga's
 *  `cover` field is null — the typical case for local sources.
 *  `cover` is only appended as the cache-busting `?v=` when present. */
fun coverUrl(baseUrl: String, mangaId: Long, cover: String?): String = buildString {
    append(baseUrl.trimEnd('/'))
    append("/api/v1/manga/").append(mangaId).append("/cover")
    cover?.let { append("?v=").append(URLEncoder.encode(it, "UTF-8")) }
}

/** Proxied URL for an image belonging to a source but not to a registered
 *  manga (search results, listings), matching webui `sourceImageUrl`:
 *  everything goes through the source image proxy. */
fun sourceImageUrl(baseUrl: String, sourceId: Long, url: String): String =
    "${baseUrl.trimEnd('/')}/api/v1/sources/$sourceId/image?url=" +
        URLEncoder.encode(url, "UTF-8")

/** Cover URL for a browse/search entry, mirroring webui SourceBrowsePage
 *  `entryCover`: a server-relative `/api/v1/manga/…` cover is used as-is
 *  (local sources set this), other covers go through the source image proxy,
 *  and local-source entries without a cover fall back to the manga cover
 *  endpoint when an internal id is known. */
fun entryCoverUrl(
    baseUrl: String,
    sourceId: Long,
    cover: String?,
    mangaId: Long?,
    isLocal: Boolean,
): String? {
    val base = baseUrl.trimEnd('/')
    if (cover != null) {
        if (cover.startsWith("/api/v1/manga/")) return base + cover
        return sourceImageUrl(baseUrl, sourceId, cover)
    }
    if (isLocal && mangaId != null) return coverUrl(base, mangaId, null)
    return null
}
