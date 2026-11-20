package com.dalethomas.mytvshows.domain

import com.dalethomas.mytvshows.data.Episode
import com.dalethomas.mytvshows.data.Show
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

data class Airing(val show: Show, val episode: Episode, val at: Instant)

data class DayColumn(val date: LocalDate, val airings: List<Airing>)

/**
 * Seven days starting today (device zone). Each day lists the episodes airing that day,
 * by time. Uses the full episode list when fetched, otherwise the feed's next episode.
 * A show that drops several episodes at once appears once, with its first episode.
 */
fun groupByDay(shows: List<Show>, episodes: Map<Long, List<Episode>>, now: Instant, zone: ZoneId): List<DayColumn> {
    val today = now.atZone(zone).toLocalDate()
    val days = (0L until 7L).map { today.plusDays(it) }
    val airings = shows.flatMap { show ->
        val eps = show.tvmaze?.id?.let { episodes[it] } ?: listOfNotNull(show.nextEpisode)
        eps.mapNotNull { ep ->
            val at = ep.airstampUtc?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() } ?: return@mapNotNull null
            Airing(show, ep, at)
        }
    }
    return days.map { day ->
        val onDay = airings.filter { it.at.atZone(zone).toLocalDate() == day }
            .sortedBy { it.at }
            .distinctBy { it.show.id }
        DayColumn(day, onDay)
    }
}
