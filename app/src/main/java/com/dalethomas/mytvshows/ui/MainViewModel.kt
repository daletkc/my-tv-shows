package com.dalethomas.mytvshows.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dalethomas.mytvshows.data.AddedShow
import com.dalethomas.mytvshows.data.BackdropRepository
import com.dalethomas.mytvshows.data.Episode
import com.dalethomas.mytvshows.data.Show
import com.dalethomas.mytvshows.domain.TrackerStatus
import com.dalethomas.mytvshows.data.TvMazeApi
import com.dalethomas.mytvshows.data.UserData
import com.dalethomas.mytvshows.data.UserRepository
import com.dalethomas.mytvshows.data.WatchMark
import com.dalethomas.mytvshows.domain.Service
import com.dalethomas.mytvshows.domain.toShows
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val user = UserRepository.get(app)
    private val art = BackdropRepository.get(app)

    val userData: StateFlow<UserData> = user.data
    val episodes: StateFlow<Map<Long, List<Episode>>> = user.episodes
    /** TVMaze id -> backdrop image URL ("" = none). */
    val backdrops: StateFlow<Map<Long, String>> = art.urls

    /** The shows on this TV's list. */
    val shows: StateFlow<List<Show>> = combine(user.data, user.episodes) { data, eps ->
        toShows(data.added, eps, Instant.now())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _loaded = MutableStateFlow(false)
    /** False until the saved list has been read from storage. */
    val loaded: StateFlow<Boolean> = _loaded

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    /** Debug demo mode: show every service tile even if not installed. */
    val showAllApps = MutableStateFlow(false)

    private var lastRefreshMs = 0L

    init {
        viewModelScope.launch {
            user.load()
            art.load()
            _loaded.value = true
            refresh(force = false)
        }
    }

    /** Re-fetches episode lists from TVMaze. The Refresh button forces it; otherwise only stale lists update. */
    fun refresh(force: Boolean = true) {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            user.refreshEpisodes(user.data.value.added.map { it.tvmazeId }, force)
            lastRefreshMs = System.currentTimeMillis()
            _refreshing.value = false
        }
    }

    /** Called when the app returns to the foreground: refresh if the last check is over an hour old. */
    fun refreshIfStale() {
        if (_loaded.value && System.currentTimeMillis() - lastRefreshMs > 60 * 60 * 1000L) refresh(force = false)
    }

    /** Loads the episode list for one show (detail screen). */
    fun loadEpisodes(show: Show) {
        val id = show.tvmaze?.id ?: return
        viewModelScope.launch { user.refreshEpisodes(listOf(id)) }
    }

    /** Looks up backdrop art for these shows (once each; cached on the TV). */
    fun loadBackdrops(shows: List<Show>) {
        val ids = shows.mapNotNull { it.tvmaze?.id }
        viewModelScope.launch { ids.forEach { art.ensure(it) } }
    }

    fun setWatched(show: Show, mark: WatchMark?) {
        viewModelScope.launch {
            user.setWatched(show.id, mark)
            // Watching an episode of a "To watch" show means we started it.
            val entry = user.data.value.added.firstOrNull { it.showId == show.id }
            if (mark != null && entry != null && entry.status == TrackerStatus.TO_WATCH.id) user.setStatus(entry.tvmazeId, TrackerStatus.WATCHING)
        }
    }

    fun addShow(result: TvMazeApi.MazeShow, app: Service) {
        viewModelScope.launch {
            user.addShow(AddedShow(result.id, result.name, app.name, result.posterUrl, System.currentTimeMillis(), appPackage = app.packageName))
            user.refreshEpisodes(listOf(result.id))
        }
    }

    fun setApp(tvmazeId: Long, app: Service) {
        viewModelScope.launch { user.setApp(tvmazeId, app.name, app.packageName) }
    }

    fun setStatus(tvmazeId: Long, status: TrackerStatus) {
        viewModelScope.launch { user.setStatus(tvmazeId, status) }
    }

    fun removeShow(tvmazeId: Long) {
        viewModelScope.launch { user.removeAdded(tvmazeId) }
    }
}
