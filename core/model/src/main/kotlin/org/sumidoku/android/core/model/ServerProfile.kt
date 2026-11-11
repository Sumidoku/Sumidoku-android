package org.sumidoku.android.core.model

/** A configured Sumidoku server. Multi-instance profiles follow the Aidoku
 *  Komga built-in pattern: every credential is namespaced per profile and the
 *  client never mixes data across profiles. See docs/android-client.md §4. */
data class ServerProfile(
    val id: Long,
    val name: String,
    val baseUrl: String,
    /** Ordered failover mirrors, tried after [baseUrl] on connection errors. */
    val mirrors: List<String> = emptyList(),
    /** Bearer API key created under Settings → System → API keys on the server. */
    val apiKey: String? = null,
) {
    /** Ordered candidate base URLs: primary first, then mirrors. */
    val candidates: List<String> get() = listOf(baseUrl) + mirrors
}
