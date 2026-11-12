import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.sumidoku.android.core.api.model.HomeLayout

/**
 * Decodes the source-home wire family against payloads shaped like what the
 * server actually sends (vendored aidoku types serialize Option fields as
 * explicit nulls) plus variants with optional keys omitted entirely. The
 * tolerant-defaults codegen patch must keep every variant decodable.
 *
 * Note: the generator renames `entries` to `propertyEntries` (with
 * @SerialName("entries") keeping the wire key), so the test reads through
 * the generated property names.
 */
class DecodeSmokeTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(body: String): HomeLayout =
        json.decodeFromString(HomeLayout.serializer(), """{"components":[$body]}""")

    private val manga =
        """{"key":"k","title":"t","cover":null,"artists":null,"authors":null,"description":null,"url":null,"tags":null,"status":"Ongoing","content_rating":"Safe","viewer":"LeftToRight","update_strategy":"Always","nextUpdateTime":null,"chapters":null}"""

    private val chapter =
        """{"key":"c","title":null,"chapterNumber":1.0,"volumeNumber":null,"dateUploaded":0,"scanlators":null,"url":null,"language":null,"thumbnail":null,"locked":false}"""

    @Test
    fun `scroller with manga links and explicit nulls`() {
        val layout = decode(
            """{"title":"Latest added","subtitle":null,"value":{"Scroller":{"entries":[
                {"title":"TestManga","subtitle":null,"imageUrl":null,"value":{"Manga":$manga}},
                {"title":"AnotherManga","subtitle":"c2","imageUrl":"http://x/c.jpg","value":{"Manga":$manga}}
            ],"listing":null}}}""",
        )
        assertEquals(1, layout.components.size)
        val scroller = layout.components[0].value?.scroller
        assertNotNull(scroller)
        assertEquals(2, scroller!!.propertyEntries.size)
        assertEquals("t", scroller.propertyEntries[1].value?.manga?.title)
    }

    @Test
    fun `all component variants decode`() {
        val layout = decode(
            """
            {"title":null,"subtitle":null,"value":{"ImageScroller":{"links":[{"title":"a","subtitle":null,"imageUrl":null,"value":{"Url":"https://x"}}],"autoScrollInterval":null,"width":null,"height":null}}},
            {"value":{"BigScroller":{"entries":[$manga],"autoScrollInterval":null}}},
            {"value":{"Scroller":{"entries":[{"title":"t","subtitle":null,"imageUrl":null,"value":{"Manga":$manga}}],"listing":null}}},
            {"value":{"MangaList":{"ranking":false,"pageSize":null,"entries":[{"title":"t","subtitle":null,"imageUrl":null,"value":{"Manga":$manga}}],"listing":null}}},
            {"value":{"MangaChapterList":{"pageSize":null,"entries":[{"manga":$manga,"chapter":$chapter}],"listing":null}}},
            {"value":{"Filters":[{"title":"F","filters":null}]}},
            {"value":{"Links":[{"title":"l","subtitle":null,"imageUrl":null,"value":{"Listing":{"id":"all","name":"All","kind":"Default"}}}]}}
            """.trimIndent(),
        )
        assertEquals(7, layout.components.size)
        assertNotNull(layout.components[0].value?.imageScroller)
        assertNotNull(layout.components[1].value?.bigScroller)
        assertNotNull(layout.components[2].value?.scroller)
        assertNotNull(layout.components[3].value?.mangaList)
        assertNotNull(layout.components[4].value?.mangaChapterList)
        assertNotNull(layout.components[5].value?.filters)
        assertNotNull(layout.components[6].value?.links)
    }

    @Test
    fun `omitted optional keys do not fail decode`() {
        // Same scroller payload with every optional key removed outright.
        val layout = decode(
            """{"value":{"Scroller":{"entries":[{"title":"TestManga","value":{"Manga":{"key":"a","title":"b","status":"Ongoing","content_rating":"Safe","viewer":"LeftToRight","update_strategy":"Always"}}}]}}}""",
        )
        val scroller = layout.components[0].value?.scroller
        assertNotNull(scroller)
        assertEquals("a", scroller!!.propertyEntries[0].value?.manga?.key)
    }
}



