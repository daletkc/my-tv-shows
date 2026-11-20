package com.dalethomas.mytvshows.data

import android.content.Context
import android.util.Log
import com.dalethomas.mytvshows.domain.TrackerStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/** The last episode watched. */
@Serializable
data class WatchMark(val season: Int, val number: Int)

/** A show on this TV's list. */
@Serializable
data class AddedShow(
    val tvmazeId: Long,
    val title: String,
    val platform: String,
    val posterUrl: String? = null,
    val addedAtMs: Long,
    /** A TrackerStatus id. */
    val status: String = TrackerStatus.WATCHING.id,
    /** Android package of the app it's watched on; null for lists saved before apps were detected. */
    val appPackage: String? = null,
) {
    val showId: String get() = "added-$tvmazeId"
}

@Serializable
data class UserData(
    val watched: Map<String, WatchMark> = emptyMap(),
    val added: List<AddedShow> = emptyList(),
)

@Serializable
private data class EpisodeCacheEntry(val fetchedAtMs: Long, val episodes: List<Episode>)

/**
 * Everything entered on the TV: the show list and watched markers, plus a cache of TVMaze
 * episode lists. Stored as JSON files in app storage (included in Android backup).
 */
class UserRepository private constructor(context: Context) {

    companion object {
        private const val TAG = "UserRepository"
        private const val EPISODES_MAX_AGE_MS = 6 * 60 * 60 * 1000L

        @Volatile private var instance: UserRepository? = null
        fun get(context: Context): UserRepository =
            instance ?: synchronized(this) {
                instance ?: UserRepository(context.applicationContext).also { instance = it }
            }
    }

    private val userFile = File(context.filesDir, "user.json")
    private val episodesFile = File(context.filesDir, "episodes.json")
    private val mutex = Mutex()
    private val episodeSerializer = MapSerializer(Long.serializer(), EpisodeCacheEntry.serializer())

    private val _data = MutableStateFlow(UserData())
    val data: StateFlow<UserData> = _data

    private val episodeCache = MutableStateFlow<Map<Long, EpisodeCacheEntry>>(emptyMap())
    private val _episodes = MutableStateFlow<Map<Long, List<Episode>>>(emptyMap())
    /** TVMaze id -> full episode list, for shows we have fetched. */
    val episodes: StateFlow<Map<Long, List<Episode>>> = _episodes

    suspend fun load() = withContext(Dispatchers.IO) {
        runCatching { if (userFile.exists()) _data.value = FeedParser.json.decodeFromString(UserData.serializer(), userFile.readText()) }
            .onFailure { Log.w(TAG, "user.json unreadable", it) }
        runCatching {
            if (episodesFile.exists()) setEpisodeCache(FeedParser.json.decodeFromString(episodeSerializer, episodesFile.readText()))
        }.onFailure { Log.w(TAG, "episodes.json unreadable", it) }
    }

    private suspend fun edit(transform: (UserData) -> UserData) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val next = transform(_data.value)
            _data.value = next
            writeAtomic(userFile, FeedParser.json.encodeToString(UserData.serializer(), next))
        }
    }

    suspend fun setWatched(showId: String, mark: WatchMark?) = edit { d ->
        d.copy(watched = if (mark == null) d.watched - showId else d.watched + (showId to mark))
    }

    suspend fun addShow(show: AddedShow) =
        edit { d -> d.copy(added = d.added.filterNot { it.tvmazeId == show.tvmazeId } + show) }

    suspend fun setApp(tvmazeId: Long, name: String, appPackage: String) = edit { d ->
        d.copy(added = d.added.map { if (it.tvmazeId == tvmazeId) it.copy(platform = name, appPackage = appPackage) else it })
    }

    suspend fun setStatus(tvmazeId: Long, status: TrackerStatus) = edit { d ->
        d.copy(added = d.added.map { if (it.tvmazeId == tvmazeId) it.copy(status = status.id) else it })
    }

    suspend fun removeAdded(tvmazeId: Long) = edit { d ->
        d.copy(added = d.added.filterNot { it.tvmazeId == tvmazeId }, watched = d.watched - "added-$tvmazeId")
    }

    /** Fetches episode lists for these TVMaze ids when missing or older than 6 hours. */
    suspend fun refreshEpisodes(ids: Collection<Long>, force: Boolean = false) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val stale = ids.filter { id -> force || episodeCache.value[id]?.let { now - it.fetchedAtMs > EPISODES_MAX_AGE_MS } ?: true }
        if (stale.isEmpty()) return@withContext
        val fetched = stale.mapNotNull { id ->
            runCatching { id to EpisodeCacheEntry(now, TvMazeApi.episodes(id)) }
                .onFailure { Log.w(TAG, "Episodes for $id failed", it) }
                .getOrNull()
        }
        if (fetched.isEmpty()) return@withContext
        mutex.withLock {
            setEpisodeCache(episodeCache.value + fetched)
            writeAtomic(episodesFile, FeedParser.json.encodeToString(episodeSerializer, episodeCache.value))
        }
    }

    private fun setEpisodeCache(map: Map<Long, EpisodeCacheEntry>) {
        episodeCache.value = map
        _episodes.update { map.mapValues { it.value.episodes } }
    }

    private fun writeAtomic(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) file.writeText(text)
    }
}
