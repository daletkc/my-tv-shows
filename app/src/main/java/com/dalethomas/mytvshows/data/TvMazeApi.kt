package com.dalethomas.mytvshows.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Public TVMaze API (no key). Used for show search, full episode lists and backdrop art. Data is CC BY-SA. */
object TvMazeApi {
    private const val BASE = "https://api.tvmaze.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Serializable
    data class SearchHit(val score: Double = 0.0, val show: MazeShow)

    @Serializable
    data class MazeShow(
        val id: Long,
        val name: String,
        val premiered: String? = null,
        val status: String? = null,
        val network: Named? = null,
        val webChannel: Named? = null,
        val image: Image? = null,
    ) {
        val year: String? get() = premiered?.take(4)
        val channel: String? get() = webChannel?.name ?: network?.name
        val posterUrl: String? get() = image?.medium ?: image?.original
    }

    @Serializable
    data class Named(val name: String? = null)

    @Serializable
    data class Image(val medium: String? = null, val original: String? = null)

    @Serializable
    data class MazeEpisode(
        val season: Int? = null,
        val number: Int? = null,
        val name: String? = null,
        val airdate: String? = null,
        val airtime: String? = null,
        val airstamp: String? = null,
    ) {
        fun toEpisode() = Episode(season, number, name, airdate, airtime, airstamp)
    }

    suspend fun search(query: String): List<MazeShow> {
        val url = "$BASE/search/shows".toHttpUrl().newBuilder().addQueryParameter("q", query).build()
        return get(url.toString()) { FeedParser.json.decodeFromString(ListSerializer(SearchHit.serializer()), it) }
            .map { it.show }
    }

    suspend fun episodes(tvmazeId: Long): List<Episode> =
        get("$BASE/shows/$tvmazeId/episodes") {
            FeedParser.json.decodeFromString(ListSerializer(MazeEpisode.serializer()), it)
        }.map { it.toEpisode() }

    @Serializable
    data class ImageSize(val url: String? = null, val width: Int? = null)

    @Serializable
    data class Resolutions(val original: ImageSize? = null, val large: ImageSize? = null)

    @Serializable
    data class ShowImage(val type: String? = null, val resolutions: Resolutions = Resolutions())

    /** The widest "background" image for a show (wide artwork for the backdrop), or null if it has none. */
    suspend fun backdrop(tvmazeId: Long): String? =
        get("$BASE/shows/$tvmazeId/images") {
            FeedParser.json.decodeFromString(ListSerializer(ShowImage.serializer()), it)
        }.filter { it.type == "background" }
            .maxByOrNull { it.resolutions.original?.width ?: 0 }
            ?.resolutions?.let { it.large?.url ?: it.original?.url }

    private suspend fun <T> get(url: String, parse: (String) -> T): T = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", "MyTvShows-AndroidTV").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("TVMaze HTTP ${response.code}")
            parse(response.body?.string().orEmpty())
        }
    }
}
