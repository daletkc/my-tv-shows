package com.dalethomas.mytvshows.domain

import com.dalethomas.mytvshows.data.Episode
import com.dalethomas.mytvshows.data.WatchMark
import java.time.Instant

/** True when the episode is at or before the watched marker. */
fun Episode.isWatched(mark: WatchMark?): Boolean {
    val s = season ?: return false
    val n = number ?: return false
    return mark != null && (s < mark.season || (s == mark.season && n <= mark.number))
}

/** Seasons that have numbered episodes, in order. */
fun seasonsOf(episodes: List<Episode>): List<Int> =
    episodes.filter { it.number != null }.mapNotNull { it.season }.distinct().sorted()

/** One season's numbered episodes, in order. */
fun List<Episode>.inSeason(season: Int): List<Episode> =
    filter { it.season == season && it.number != null }.sortedBy { it.number }

/**
 * The season to show progress for: the one holding the next episode to watch, else the
 * season of the latest aired episode, else the last season listed.
 */
fun focusSeason(episodes: List<Episode>, mark: WatchMark?, now: Instant): Int? =
    (if (mark != null) nextToWatch(episodes, mark)?.season else null)
        ?: latestAired(episodes, now)?.season
        ?: seasonsOf(episodes).lastOrNull()
