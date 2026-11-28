package org.sumidoku.android.core.image.upscale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnhancementCacheKeyTest {

    private val url = "https://sumi.example/api/v1/chapter/42/page/7?v=rev-a"

    @Test
    fun `variants of one page share the page key`() {
        val page = EnhancementCache.pageKey(url)
        assertEquals(page, EnhancementCache.pageKey(url))
        assertEquals(page, EnhancementCache.variantKey(page, DenoiseLevel.LOW, UpscalePrecision.AUTO).substringBefore('#'))
    }

    @Test
    fun `denoise and precision change the variant key`() {
        val low = EnhancementCache.cacheKey(url, DenoiseLevel.LOW, UpscalePrecision.AUTO)
        val high = EnhancementCache.cacheKey(url, DenoiseLevel.HIGH, UpscalePrecision.AUTO)
        val fp32 = EnhancementCache.cacheKey(url, DenoiseLevel.LOW, UpscalePrecision.FP32)
        assertNotEquals(low, high)
        assertNotEquals(low, fp32)
        assertTrue(low.contains("#low@auto"))
        assertTrue(fp32.contains("#low@fp32"))
    }

    @Test
    fun `another revision or another page is another key`() {
        assertNotEquals(
            EnhancementCache.pageKey(url),
            EnhancementCache.pageKey("https://sumi.example/api/v1/chapter/42/page/7?v=rev-b"),
        )
        assertNotEquals(
            EnhancementCache.pageKey(url),
            EnhancementCache.pageKey("https://sumi.example/api/v1/chapter/42/page/8?v=rev-a"),
        )
    }

    @Test
    fun `local file pages are keyed like remote ones`() {
        val file = "file:///data/user/0/org.sumidoku.android/files/downloads/42/007.png"
        val key = EnhancementCache.cacheKey(file, DenoiseLevel.LOW, UpscalePrecision.AUTO)
        assertEquals(key, EnhancementCache.cacheKey(file, DenoiseLevel.LOW, UpscalePrecision.AUTO))
        assertEquals(64, EnhancementCache.pageKey(file).length)
    }

    @Test
    fun `instance key normalises trailing slashes and case`() {
        assertEquals(
            EnhancementCache.instanceKey("https://SUMI.example/"),
            EnhancementCache.instanceKey("https://sumi.example"),
        )
        assertEquals(16, EnhancementCache.instanceKey("https://sumi.example").length)
        assertNotEquals(
            EnhancementCache.instanceKey("https://sumi.example"),
            EnhancementCache.instanceKey("https://other.example"),
        )
    }
}
