package com.dalethomas.mytvshows.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/**
 * Backdrop image URL per TVMaze show, looked up the first time a show is shown and kept in
 * app storage. An empty string means TVMaze has no backdrop for that show.
 */
class BackdropRepository private constructor(context: Context) {

    companion object {
        private const val TAG = "BackdropRepository"

        @Volatile private var instance: BackdropRepository? = null
        fun get(context: Context): BackdropRepository =
            instance ?: synchronized(this) {
                instance ?: BackdropRepository(context.applicationContext).also { instance = it }
            }
    }

    private val file = File(context.filesDir, "backdrops.json")
    private val serializer = MapSerializer(Long.serializer(), String.serializer())
    private val mutex = Mutex()
    private val inFlight = mutableSetOf<Long>()

    private val _urls = MutableStateFlow<Map<Long, String>>(emptyMap())
    /** TVMaze id -> backdrop URL ("" = none). */
    val urls: StateFlow<Map<Long, String>> = _urls

    suspend fun load() = withContext(Dispatchers.IO) {
        runCatching { if (file.exists()) _urls.value = FeedParser.json.decodeFromString(serializer, file.readText()) }
            .onFailure { Log.w(TAG, "backdrops.json unreadable", it) }
    }

    /** Looks up the backdrop once per show. Failures are retried next time; "no backdrop" is remembered. */
    suspend fun ensure(tvmazeId: Long) {
        synchronized(inFlight) {
            if (tvmazeId in _urls.value || !inFlight.add(tvmazeId)) return
        }
        try {
            val url = runCatching { TvMazeApi.backdrop(tvmazeId) }
                .onFailure { Log.w(TAG, "Backdrop for $tvmazeId failed", it) }
                .getOrElse { return }
            mutex.withLock {
                withContext(Dispatchers.IO) {
                    _urls.update { it + (tvmazeId to url.orEmpty()) }
                    val tmp = File(file.parentFile, file.name + ".tmp")
                    tmp.writeText(FeedParser.json.encodeToString(serializer, _urls.value))
                    if (!tmp.renameTo(file)) file.writeText(tmp.readText())
                }
            }
        } finally {
            synchronized(inFlight) { inFlight.remove(tvmazeId) }
        }
    }
}
