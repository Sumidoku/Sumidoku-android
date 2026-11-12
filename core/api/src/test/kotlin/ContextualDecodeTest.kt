package org.sumidoku.android.core.api

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Test
import org.sumidoku.android.core.api.model.MigrationSource
import org.sumidoku.android.core.api.model.SourceRepo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Probe test for the repos / migration screens: the generated models mark a
 * few fields @Contextual. This test decodes the exact payload shapes the
 * server produces so a missing contextual serializer shows up as a failing
 * test instead of a 报错 screen on device.
 */
class ContextualDecodeTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val migrateSources = """
        [{"baseUrl":null,"breakingChangeVersion":0,"config":{"allowsBaseUrlSelect":false,"hidesFiltersWhileSearching":true,"supportsArtistSearch":true,"supportsAuthorSearch":true,"supportsTagSearch":true},"contentRating":0,"enabled":true,"hasHome":true,"id":1,"installedAt":0,"key":"local.local","kind":"local","languages":[],"mangaCount":1,"missingFromRepos":false,"name":"Local","pinOrder":null,"pinned":false,"updateVersion":null,"updatedAt":0,"version":0},{"baseUrl":"https://weebcentral.com","breakingChangeVersion":0,"config":null,"contentRating":0,"enabled":true,"hasHome":null,"id":2,"installedAt":1794457792,"key":"en.weebcentral","kind":"aidoku","languages":[],"mangaCount":0,"missingFromRepos":false,"name":"Weeb Central","pinOrder":null,"pinned":false,"updateVersion":null,"updatedAt":1794457792,"version":5}]
    """.trimIndent()

    private val sourceRepos = """
        [{"id":1,"url":"https://example.test/index.json","name":"Example repo","sourceCount":12,"addedAt":1794457792,"fetchedAt":1794457792,"fetchError":null},{"id":2,"url":"https://example.test/stale.json","name":null,"sourceCount":0,"addedAt":1794457792,"fetchedAt":null,"fetchError":"failed to fetch repository index"}]
    """.trimIndent()

    @Test
    fun migrationSourcesDecode() {
        val sources = json.decodeFromString(ListSerializer(MigrationSource.serializer()), migrateSources)
        assertEquals(2, sources.size)
        assertEquals("local.local", sources[0].key)
        assertEquals(1L, sources[0].mangaCount)
        assertTrue(sources[0].config?.supportsAuthorSearch == true)
        assertEquals(null, sources[1].config)
    }

    @Test
    fun sourceReposDecode() {
        val repos = json.decodeFromString(ListSerializer(SourceRepo.serializer()), sourceRepos)
        assertEquals(2, repos.size)
        assertEquals("https://example.test/index.json", repos[0].url.toString())
        assertEquals(null, repos[1].name)
        assertEquals("failed to fetch repository index", repos[1].fetchError)
    }
}

