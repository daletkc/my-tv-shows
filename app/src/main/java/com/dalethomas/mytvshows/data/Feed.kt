package com.dalethomas.mytvshows.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Show and episode models (originally a JSON feed schema; also used for the list saved on the TV). */
@Serializable
data class Feed(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    @SerialName("generated_at") val generatedAt: String? = null,
    @SerialName("content_hash") val contentHash: String? = null,
    val shows: List<Show> = emptyList(),
)

@Serializable
data class Show(
    val id: String,
    val title: String,
    val kind: String = "tracked",
    @SerialName("note_order") val noteOrder: Int = 0,
    val who: String? = null,
    val platform: String = "",
    val status: String = "",
    @SerialName("status_group") val statusGroup: String = "other",
    val notes: String = "",
    val upcoming: String? = null,
    @SerialName("recommended_on") val recommendedOn: String? = null,
    val tvmaze: TvMaze? = null,
    /** Android package of the app picked to watch it (shows added on the TV). */
    @SerialName("app_package") val appPackage: String? = null,
) {
    val posterUrl: String? get() = tvmaze?.poster?.medium ?: tvmaze?.poster?.original
    val nextEpisode: Episode? get() = tvmaze?.nextEpisode
}

@Serializable
data class TvMaze(
    val id: Long? = null,
    val name: String? = null,
    val url: String? = null,
    val status: String? = null,
    val premiered: String? = null,
    val ended: String? = null,
    val genres: List<String> = emptyList(),
    val network: String? = null,
    @SerialName("web_channel") val webChannel: String? = null,
    @SerialName("official_site") val officialSite: String? = null,
    val poster: Poster? = null,
    @SerialName("next_episode") val nextEpisode: Episode? = null,
    @SerialName("last_episode") val lastEpisode: Episode? = null,
)

@Serializable
data class Poster(val medium: String? = null, val original: String? = null)

@Serializable
data class Episode(
    val season: Int? = null,
    val number: Int? = null,
    val name: String? = null,
    val airdate: String? = null,
    val airtime: String? = null,
    @SerialName("airstamp_utc") val airstampUtc: String? = null,
) {
    /** "S4 E6" or "S4" when the episode number is unknown. */
    val code: String
        get() = listOfNotNull(season?.let { "S$it" }, number?.let { "E$it" }).joinToString(" ")
}

object FeedParser {
    const val SUPPORTED_SCHEMA = 1

    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun parse(text: String): Feed = json.decodeFromString(Feed.serializer(), text)
}
