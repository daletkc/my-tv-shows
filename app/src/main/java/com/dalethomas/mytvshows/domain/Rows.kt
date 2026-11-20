package com.dalethomas.mytvshows.domain

import com.dalethomas.mytvshows.data.Show
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

enum class RowKind(val title: String) {
    READY_TO_WATCH("Ready to watch"),
    NEW_THIS_WEEK("New episodes this week"),
    COMING_SOON("Coming soon"),
    WATCHING("Watching / To watch"),
    RETURNING("Waiting for new season"),
    RECOMMENDED("Recommended for us"),
    FINISHED("Finished"),
}

data class ShowRow(val kind: RowKind, val shows: List<Show>)

fun Show.nextAirInstant(): Instant? = nextEpisode?.airstampUtc?.let {
    runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull()
}

/**
 * Puts each show in exactly one row, first match wins, in RowKind order.
 * `unwatched` (show id -> aired episodes not yet watched) puts shows with new episodes first.
 * "Coming soon" uses only a future next_episode: the tracker's `upcoming` text often
 * describes dates that already aired, so it is not reliable for scheduling.
 */
fun groupRows(shows: List<Show>, now: Instant, unwatched: Map<String, Int> = emptyMap()): List<ShowRow> {
    val weekEnd = now.plus(Duration.ofDays(7))
    val buckets = RowKind.entries.associateWith { mutableListOf<Show>() }

    for (show in shows) {
        val air = show.nextAirInstant()
        val kind = when {
            (unwatched[show.id] ?: 0) > 0 -> RowKind.READY_TO_WATCH
            air != null && !air.isBefore(now) && !air.isAfter(weekEnd) -> RowKind.NEW_THIS_WEEK
            air != null && air.isAfter(weekEnd) -> RowKind.COMING_SOON
            show.statusGroup == "watching" || show.statusGroup == "to_watch" -> RowKind.WATCHING
            show.statusGroup == "returning" || show.statusGroup == "between_seasons" -> RowKind.RETURNING
            show.kind == "recommended" -> RowKind.RECOMMENDED
            show.statusGroup == "watched" || show.statusGroup == "ended" -> RowKind.FINISHED
            else -> null
        }
        kind?.let { buckets.getValue(it).add(show) }
    }

    buckets.getValue(RowKind.READY_TO_WATCH).sortByDescending { unwatched[it.id] }
    buckets.getValue(RowKind.NEW_THIS_WEEK).sortBy { it.nextAirInstant() }
    buckets.getValue(RowKind.COMING_SOON).sortBy { it.nextAirInstant() }

    return RowKind.entries.map { ShowRow(it, buckets.getValue(it)) }.filter { it.shows.isNotEmpty() }
}
