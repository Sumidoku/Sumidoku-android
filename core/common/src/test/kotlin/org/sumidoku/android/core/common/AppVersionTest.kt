package org.sumidoku.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors KMReader's `AppVersionTest`: the release tag vs. the installed version. */
class AppVersionTest {

    @Test
    fun ignoresLeadingV() {
        assertEquals(AppVersion.parse("1.2.3"), AppVersion.parse("v1.2.3"))
    }

    @Test
    fun comparesNumericSegmentsAsNumbers() {
        assertTrue(AppVersion.isNewer("1.10.0", "1.9.3"))
        assertFalse(AppVersion.isNewer("1.9.3", "1.10.0"))
    }

    @Test
    fun missingSegmentsCountAsZero() {
        assertFalse(AppVersion.isNewer("1.2", "1.2.0"))
        assertFalse(AppVersion.isNewer("1.2.0", "1.2"))
    }

    @Test
    fun finalReleaseOutranksPreRelease() {
        assertTrue(AppVersion.isNewer("1.3.0", "1.3.0-beta.1"))
        assertFalse(AppVersion.isNewer("1.3.0-beta.1", "1.3.0"))
    }

    @Test
    fun preReleaseLadderFollowsSemVer() {
        assertTrue(AppVersion.isNewer("1.3.0-beta.2", "1.3.0-beta.1"))
        assertFalse(AppVersion.isNewer("1.3.0-beta.1", "1.3.0-beta.2"))
        // Numeric identifiers rank below alphanumeric ones.
        assertTrue(AppVersion.isNewer("1.3.0-alpha", "1.3.0-1"))
    }

    @Test
    fun ignoresBuildMetadata() {
        assertEquals(AppVersion.parse("1.4.0+7"), AppVersion.parse("1.4.0+99"))
        assertFalse(AppVersion.isNewer("1.4.0+7", "1.4.0"))
    }

    @Test
    fun unreadableVersionsAreNeverNewer() {
        assertFalse(AppVersion.isNewer("nightly", "0.1.0"))
        assertFalse(AppVersion.isNewer("1.0.0", ""))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("v"))
    }

    @Test
    fun parsesSegmentsAndPreRelease() {
        val parsed = AppVersion.parse("v1.4.0-rc.2+7")
        assertEquals(listOf(1, 4, 0), parsed?.segments)
        assertEquals("rc.2", parsed?.prerelease)
    }
}
