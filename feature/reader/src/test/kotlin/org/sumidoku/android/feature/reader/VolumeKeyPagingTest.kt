package org.sumidoku.android.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the KMReader `VolumeKeyPaging.kt` mapping: volume-up steps forward and
 * volume-down back in a left-to-right chapter, and the pair mirrors with the
 * reading direction so volume-up keeps pointing at the physically right page.
 */
class VolumeKeyPagingTest {

    @Test
    fun volumeUpTurnsForwardLeftToRight() {
        assertEquals(VolumeKeyPage.Next, volumeKeyPage(volumeUp = true, rtl = false))
        assertEquals(VolumeKeyPage.Previous, volumeKeyPage(volumeUp = false, rtl = false))
    }

    @Test
    fun rightToLeftMirrorsThePair() {
        assertEquals(VolumeKeyPage.Previous, volumeKeyPage(volumeUp = true, rtl = true))
        assertEquals(VolumeKeyPage.Next, volumeKeyPage(volumeUp = false, rtl = true))
    }
}
