package org.sumidoku.android.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.sumidoku.android.core.api.model.Page
import org.sumidoku.android.core.api.model.PageContent

/** Pins the `pageText.ts` parity rule: description first, then text. */
class PageTextTest {

    private fun page(
        content: PageContent,
        description: String? = null,
        hasDescription: Boolean = false,
    ) = Page(
        content = content,
        hasDescription = hasDescription,
        description = description,
        index = 0,
        cacheRevision = 0,
    )

    @Test
    fun textContentIsTheBody() {
        assertEquals("body", page(PageContent(text = "body")).pageText())
    }

    @Test
    fun descriptionWinsOverText() {
        assertEquals(
            "chapter one",
            page(PageContent(text = "body"), description = "chapter one", hasDescription = true).pageText(),
        )
    }

    @Test
    fun missingDescriptionFallsBackToText() {
        assertEquals("body", page(PageContent(text = "body"), hasDescription = true).pageText())
    }

    @Test
    fun unrequestedDescriptionIsIgnored() {
        assertEquals(
            "body",
            page(PageContent(text = "body"), description = "caption", hasDescription = false).pageText(),
        )
    }

    @Test
    fun imagePageHasNoText() {
        assertNull(page(PageContent(image = "0.png")).pageText())
    }
}
