package com.dalethomas.mytvshows.domain

import com.dalethomas.mytvshows.data.AddedShow
import com.dalethomas.mytvshows.data.Episode
import com.dalethomas.mytvshows.data.Poster
import com.dalethomas.mytvshows.data.Show
import com.dalethomas.mytvshows.data.TvMaze
import com.dalethomas.mytvshows.data.WatchMark
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Episodes with a season and number, in airing order. Specials (no number) are skipped. */
private fun List<Episode>.numbered(): List<Episode> =
    filter { it.season != null && it.number != null }.sortedWith(compareBy({ it.season }, { it.number }))

private fun Episode.isAfter(mark: WatchMark): Boolean =
    season!! > mark.season || (season == mark.season && number!! > mark.number)

/** True once the episode's air time (or air date, if no time) has passed. */
fun Episode.hasAired(now: Instant): Boolean {
    airstampUtc?.let { s -> runCatching { OffsetDateTime.parse(s).toInstant() }.getOrNull()?.let { return !it.isAfter(now) } }
    val date = airdate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
    return !date.isAfter(now.atOffset(ZoneOffset.UTC).toLocalDate())
}

/** Aired episodes after the marker, oldest first. */
fun unwatchedEpisodes(episodes: List<Episode>, mark: WatchMark, now: Instant): List<Episode> =
    episodes.numbered().filter { it.isAfter(mark) && it.hasAired(now) }

/** The most recent aired episode, used for "We're caught up". */
fun latestAired(episodes: List<Episode>, now: Instant): Episode? =
    episodes.numbered().lastOrNull { it.hasAired(now) }

/** The first episode after the marker (or the first episode if no marker), aired or not. */
fun nextToWatch(episodes: List<Episode>, mark: WatchMark?): Episode? =
    episodes.numbered().firstOrNull { mark == null || it.isAfter(mark) }

fun Episode.toMark(): WatchMark? = if (season != null && number != null) WatchMark(season, number) else null

/**
 * Number of aired, unwatched episodes per show id. Only shows with a watched marker and a
 * fetched episode list get an entry; everything else is unknown, not zero.
 */
fun unwatchedCounts(
    shows: List<Show>,
    watched: Map<String, WatchMark>,
    episodes: Map<Long, List<Episode>>,
    now: Instant,
): Map<String, Int> = shows.mapNotNull { show ->
    val mark = watched[show.id] ?: return@mapNotNull null
    val eps = show.tvmaze?.id?.let { episodes[it] } ?: return@mapNotNull null
    show.id to unwatchedEpisodes(eps, mark, now).size
}.toMap()

/** The shows on this TV's list, as Show objects for the rows. Next/last episode come from the fetched episode list. */
fun toShows(
    added: List<AddedShow>,
    episodes: Map<Long, List<Episode>>,
    now: Instant,
): List<Show> = added.map { a ->
    val eps = episodes[a.tvmazeId].orEmpty().numbered()
    val status = TrackerStatus.of(a.status)
    Show(
        id = a.showId,
        title = a.title,
        kind = "tracked",
        platform = a.platform,
        appPackage = a.appPackage,
        status = status.label,
        statusGroup = status.group,
        tvmaze = TvMaze(
            id = a.tvmazeId,
            name = a.title,
            poster = a.posterUrl?.let { Poster(medium = it) },
            nextEpisode = eps.firstOrNull { !it.hasAired(now) },
            lastEpisode = eps.lastOrNull { it.hasAired(now) },
        ),
    )
}
