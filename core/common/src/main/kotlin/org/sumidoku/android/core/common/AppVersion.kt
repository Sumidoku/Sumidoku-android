package org.sumidoku.android.core.common

/**
 * Release-version comparison for the in-app update check.
 *
 * The Android release workflow tags every GitHub release `v<versionName>` from
 * `app/build.gradle.kts` and attaches an APK asset, so comparing the release tag
 * with the installed `versionName` decides whether a newer build exists. This is
 * the KMReader rule (`AppVersion.kt`) ported unchanged:
 *
 * - a leading `v`/`V` is noise, build metadata (`+7`) is ignored;
 * - numeric segments compare as numbers (`1.10.0` is newer than `1.9.3`) and a
 *   missing segment counts as zero (`1.2` == `1.2.0`);
 * - a final release outranks its pre-releases (`1.3.0` > `1.3.0-rc.2`);
 * - pre-releases compare SemVer-style: numeric identifiers rank below
 *   alphanumeric ones (`1.3.0-1` < `1.3.0-alpha`) and a shorter list of equal
 *   identifiers ranks lower (`beta` < `beta.2`).
 *
 * Anything unparseable is never newer: a broken tag must not nag the user, and
 * an unreadable installed version must not suppress a real release.
 */
object AppVersion {

    data class Parsed(
        val segments: List<Int>,
        val prerelease: String? = null,
    ) : Comparable<Parsed> {

        /** SemVer precedence: numeric fields, then the pre-release ladder. */
        override fun compareTo(other: Parsed): Int {
            val size = maxOf(segments.size, other.segments.size)
            for (i in 0 until size) {
                val left = segments.getOrElse(i) { 0 }
                val right = other.segments.getOrElse(i) { 0 }
                if (left != right) return left.compareTo(right)
            }
            return when {
                prerelease == null && other.prerelease == null -> 0
                // A final release always beats its own pre-releases.
                prerelease == null -> 1
                other.prerelease == null -> -1
                else -> comparePrerelease(prerelease, other.prerelease)
            }
        }
    }

    /** `v1.4.0-rc.2+7` → segments `[1, 4, 0]`, pre-release `rc.2`. */
    fun parse(raw: String): Parsed? {
        val trimmed = raw.trim().removePrefix("v").removePrefix("V")
        if (trimmed.isEmpty()) return null
        // Build metadata never participates in precedence.
        val withoutBuild = trimmed.substringBefore('+')
        val core = withoutBuild.substringBefore('-')
        val prerelease = withoutBuild.substringAfter('-', "").takeIf { it.isNotBlank() }
        val segments = core.split('.')
            .mapNotNull { part ->
                val digits = part.takeWhile { it.isDigit() }
                digits.toIntOrNull()
            }
        if (segments.isEmpty()) return null
        return Parsed(segments = segments, prerelease = prerelease)
    }

    /** True when [candidate] is a strictly higher release than [installed]. */
    fun isNewer(candidate: String, installed: String): Boolean {
        val next = parse(candidate) ?: return false
        val current = parse(installed) ?: return false
        return next > current
    }

    private fun comparePrerelease(left: String, right: String): Int {
        val leftParts = left.split('.')
        val rightParts = right.split('.')
        val size = maxOf(leftParts.size, rightParts.size)
        for (i in 0 until size) {
            // Equal prefixes: the list that ends first has the lower precedence.
            val a = leftParts.getOrNull(i) ?: return -1
            val b = rightParts.getOrNull(i) ?: return 1
            val aNumber = a.toIntOrNull()
            val bNumber = b.toIntOrNull()
            val result = when {
                aNumber != null && bNumber != null -> aNumber.compareTo(bNumber)
                aNumber != null -> -1 // numeric identifiers rank below alphanumeric
                bNumber != null -> 1
                else -> a.compareTo(b)
            }
            if (result != 0) return result
        }
        return 0
    }
}
