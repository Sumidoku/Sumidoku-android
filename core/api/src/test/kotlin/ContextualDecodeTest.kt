package org.sumidoku.android.core.api

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.sumidoku.android.core.api.model.MigrationSource
import org.sumidoku.android.core.api.model.SourceRepo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Probe test for the repos / migration screens: the generated models mark a
 * few fields @Contextual. This test decodes the exact payload shapes the
 * server produces so a missing contextual serializer shows up as a failing
 * test instead of an error screen on device.
 */
class ContextualDecodeTest {

    // Same Json the network stack uses (sumidokuJson), so a missing
    // contextual serializer fails here exactly as it would on device.
    private val json = sumidokuJson()

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

    // The settings wire encodes Button and server-unknown kinds (e.g. the
    // local source "list" rows) as bare strings; the generated SettingValue
    // has no string branch and used to abort the whole settings decode.
    private val sourceSettings = """
        [
          {"type":"list","key":"paths","title":"路径","value":"list"},
          {"type":"button","key":"scan","title":"扫描","value":"Button"},
          {"type":"toggle","key":"analyze_pages","title":"分析页面","value":{"Toggle":{"default":false}}}
        ]
    """.trimIndent()

    @Test
    fun sourceSettingsDecodeBareStringValues() {
        val settings = json.decodeFromString(ListSerializer(org.sumidoku.android.core.api.model.SourceSetting.serializer()), sourceSettings)
        assertEquals(3, settings.size)
        assertEquals("list", settings[0].value.rawString)
        assertEquals("Button", settings[1].value.rawString)
        assertEquals(null, settings[2].value.rawString)
        assertTrue(settings[2].value.toggle != null)
    }

    // Verbatim local-source settings payload captured from server v1.18.0
    // (GET /sources/{id}/settings): bare-string "list" values, capitalized
    // externally-tagged objects, explicit nulls everywhere.
    private val localSourceSettings = """
        {"settings":[{"key":"paths","notification":null,"refreshes":null,"requires":null,"requires_false":null,"title":"Directories","type":"list","value":"list"},{"key":"oneshotsDir","notification":null,"refreshes":null,"requires":null,"requires_false":null,"title":"Oneshots directory","type":"text","value":{"Text":{"autocapitalization_type":null,"autocorrection_disabled":null,"default":null,"keyboard_type":null,"placeholder":null,"return_key_type":null,"secure":null}}},{"key":"exclusions","notification":null,"refreshes":null,"requires":null,"requires_false":null,"title":"Directory exclusions","type":"list","value":"list"},{"key":"forceModifiedTime","notification":null,"refreshes":null,"requires":null,"requires_false":null,"title":"Force directory modified time","type":"checkbox","value":{"Toggle":{"auth_to_disable":null,"default":false,"subtitle":null}}},{"key":"analyzePages","notification":null,"refreshes":null,"requires":null,"requires_false":null,"title":"Analyze chapter pages","type":"checkbox","value":{"Toggle":{"auth_to_disable":null,"default":true,"subtitle":null}}},{"key":"analyzeMetadata","notification":null,"refreshes":null,"requires":null,"requires_false":null,"title":"Analyze embedded metadata","type":"checkbox","value":{"Toggle":{"auth_to_disable":null,"default":true,"subtitle":null}}}],"values":{"analyzeMetadata":true,"analyzePages":true,"exclusions":[],"forceModifiedTime":false,"oneshotsDir":null,"paths":["G:\\temp\\manga-lib"]}}
    """.trimIndent()

    @Test
    fun localSourceSettingsDecode() {
        val settings = json.decodeFromString(org.sumidoku.android.core.api.model.SourceSettings.serializer(), localSourceSettings)
        assertEquals(6, settings.settings.size)
        assertEquals("list", settings.settings[0].value.rawString)
        assertTrue(settings.settings[1].value.text != null)
        assertEquals(false, settings.settings[3].value.toggle?.`default`)
        assertEquals(true, settings.settings[4].value.toggle?.`default`)
        assertEquals(true, settings.propertyValues["analyzePages"]
            ?.jsonPrimitive?.booleanOrNull)
    }

    // Tracker remote state (GET /trackers/{provider}/state). The generator
    // emitted @Contextual BigDecimal for the numeric fields; without the
    // Double patch this payload aborts with "Serializer for class
    // 'BigDecimal' is not found" and tracker pills never show remote data.
    private val remoteState = """
        {"score":8,"status":"reading","lastReadChapter":12.5,"lastReadVolume":null,"totalChapters":120,"totalVolumes":null,"startReadDate":null,"finishReadDate":null}
    """.trimIndent()

    @Test
    fun remoteStateDecode() {
        val state = json.decodeFromString(org.sumidoku.android.core.api.model.RemoteState.serializer(), remoteState)
        assertTrue(state.lastReadChapter == 12.5)
        assertEquals(120, state.totalChapters)
        assertEquals(org.sumidoku.android.core.api.model.TrackStatus.READING, state.status)
    }

    // Sync response (POST /trackers/links/{id}/{provider}/sync): progress is
    // a required number, so this decode used to fail 100% of the time.
    @Test
    fun trackerSyncResultDecode() {
        val result = json.decodeFromString(
            org.sumidoku.android.core.api.model.TrackerSyncResult.serializer(),
            """{"progress":12.0,"marked":3,"chapterIds":[1,2,3]}""",
        )
        assertTrue(result.progress == 12.0)
        assertEquals(3, result.marked)
        assertEquals(listOf(1L, 2L, 3L), result.chapterIds)
    }
}

