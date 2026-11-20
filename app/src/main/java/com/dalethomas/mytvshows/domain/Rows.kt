package com.dalethomas.mytvshows.domain

import com.dalethomas.mytvshows.data.Show
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

enum class RowKind(val title: String) {
    READY_TO_WATCH("Ready to watch"),
    START_NEW("Start something new"),
    NEW_THIS_WEEK("New episodes this week"),
    COMING_SOON("Coming soon"),
    CAUGHT_UP("All caught up"),
    FINISHED("Finished"),
}

data class ShowRow(val kind: RowKind, val shows: List<Show>)

fun Show.nextAirInstant(): Instant? = nextEpisode?.airstampUtc?.let {
    runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull()
}

/**
 * Puts each show in exactly one row, first match wins:
 * 1. Ready to watch: aired episodes after the watched marker (`unwatched`, most first).
 * 2. Start something new: "To watch" and recommendations, before any air dates, since
 *    new episodes of a show not started yet don't matter.
 * 3. New episodes this week / 4. Coming soon: a future next episode. The tracker's
 *    `upcoming` text often describes dates that already aired, so it isn't used.
 * 5. All caught up: watching, returning or between seasons with nothing new.
 * 6. Finished: watched (Loved / Liked / Meh) or ended.
 * Anything else ("Not for us") is not shown.
 */
fun groupRows(shows: List<Show>, now: Instant, unwatched: Map<String, Int> = emptyMap()): List<ShowRow> {
    val weekEnd = now.plus(Duration.ofDays(7))
    val buckets = RowKind.entries.associateWith { mutableListOf<Show>() }

    for (show in shows) {
        val air = show.nextAirInstant()
        val kind = when {
            (unwatched[show.id] ?: 0) > 0 -> RowKind.READY_TO_WATCH
            show.statusGroup == "to_watch" || show.statusGroup == "recommended" ||
                (show.kind == "recommended" && show.statusGroup == "other") -> RowKind.START_NEW
            show.statusGroup == "not_for_us" -> null
            air != null && !air.isBefore(now) && !air.isAfter(weekEnd) -> RowKind.NEW_THIS_WEEK
            air != null && air.isAfter(weekEnd) -> RowKind.COMING_SOON
            show.statusGroup == "watching" || show.statusGroup == "returning" || show.statusGroup == "between_seasons" -> RowKind.CAUGHT_UP
            show.statusGroup == "watched" || show.statusGroup == "ended" -> RowKind.FINISHED
            else -> null
        }
        kind?.let { buckets.getValue(it).add(show) }
    }

    // To watch before recommendations.
    buckets.getValue(RowKind.START_NEW).sortBy { if (it.statusGroup == "to_watch") 0 else 1 }
    buckets.getValue(RowKind.READY_TO_WATCH).sortByDescending { unwatched[it.id] }
    buckets.getValue(RowKind.NEW_THIS_WEEK).sortBy { it.nextAirInstant() }
    buckets.getValue(RowKind.COMING_SOON).sortBy { it.nextAirInstant() }

    return RowKind.entries.map { ShowRow(it, buckets.getValue(it)) }.filter { it.shows.isNotEmpty() }
}
