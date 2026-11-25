package org.sumidoku.android.core.network.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Release-payload mapping: tag parsing and APK asset choice. */
class UpdateReleaseDtoTest {

    private fun decode(json: String): UpdateReleaseDto =
        UpdateJson.decodeFromString(UpdateReleaseDto.serializer(), json)

    @Test
    fun readsVersionAndPicksTheReleaseApk() {
        val dto = decode(
            """
            {
              "tag_name": "v0.2.0",
              "name": "Sumidoku 0.2.0",
              "body": "- volume key paging",
              "html_url": "https://github.com/Sumidoku/Sumidoku-android/releases/tag/v0.2.0",
              "published_at": "2026-11-25T00:00:00Z",
              "draft": false,
              "prerelease": false,
              "assets": [
                {"name": "sumidoku-android-0.2.0-debug.apk", "browser_download_url": "https://example.com/debug.apk"},
                {"name": "sumidoku-android-0.2.0.apk", "browser_download_url": "https://example.com/release.apk"},
                {"name": "sumidoku-android-0.2.0.apk.sha256", "browser_download_url": "https://example.com/release.apk.sha256"}
              ]
            }
            """.trimIndent(),
        )

        val info = dto.toUpdateInfo()
        assertEquals("0.2.0", info?.version)
        assertEquals("Sumidoku 0.2.0", info?.title)
        assertEquals("https://example.com/release.apk", info?.downloadUrl)
    }

    @Test
    fun fallsBackToTheDebugApkAndIgnoresChecksums() {
        val dto = decode(
            """
            {
              "tag_name": "0.2.0",
              "html_url": "https://example.com/release",
              "assets": [
                {"name": "sumidoku-android-0.2.0.apk.sha256", "browser_download_url": "https://example.com/sum"},
                {"name": "sumidoku-android-0.2.0-debug.apk", "browser_download_url": "https://example.com/debug"}
              ]
            }
            """.trimIndent(),
        )

        assertEquals("https://example.com/debug", dto.toUpdateInfo()?.downloadUrl)
    }

    @Test
    fun releaseWithoutApkStillHasAReleasePage() {
        val dto = decode(
            """
            {"tag_name": "v0.3.0", "html_url": "https://example.com/tag", "assets": []}
            """.trimIndent(),
        )

        val info = dto.toUpdateInfo()
        assertEquals("0.3.0", info?.version)
        assertNull(info?.downloadUrl)
        assertEquals("https://example.com/tag", info?.releaseUrl)
    }

    @Test
    fun unreadableTagIsDropped() {
        assertNull(decode("""{"tag_name": "nightly"}""").toUpdateInfo())
    }
}
