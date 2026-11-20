package com.dalethomas.mytvshows

import com.dalethomas.mytvshows.data.AddedShow
import com.dalethomas.mytvshows.data.Episode
import com.dalethomas.mytvshows.data.Show
import com.dalethomas.mytvshows.domain.TrackerStatus
import com.dalethomas.mytvshows.data.TvMaze
import com.dalethomas.mytvshows.data.WatchMark
import com.dalethomas.mytvshows.domain.RowKind
import com.dalethomas.mytvshows.domain.groupByDay
import com.dalethomas.mytvshows.domain.groupRows
import com.dalethomas.mytvshows.domain.latestAired
import com.dalethomas.mytvshows.domain.nextToWatch
import com.dalethomas.mytvshows.domain.toShows
import com.dalethomas.mytvshows.domain.unwatchedCounts
import com.dalethomas.mytvshows.domain.unwatchedEpisodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ProgressTest {
    private val now = Instant.parse("2026-11-19T18:00:00Z")
    private val zone = ZoneId.of("America/Chicago")

    private fun ep(s: Int, n: Int?, stamp: String?, date: String? = null) =
        Episode(season = s, number = n, airstampUtc = stamp, airdate = date)

    private val episodes = listOf(
        ep(1, 1, "2026-10-01T01:00:00+00:00"),
        ep(1, 2, "2026-10-08T01:00:00+00:00"),
        ep(1, null, "2026-10-10T01:00:00+00:00"), // special, ignored
        ep(2, 1, "2026-11-12T01:00:00+00:00"),
        ep(2, 2, null, date = "2026-11-19"),       // date only, today: counts as aired
        ep(2, 3, "2026-11-26T02:00:00+00:00"),     // future
    )

    @Test fun countsAiredEpisodesAfterMark() {
        val left = unwatchedEpisodes(episodes, WatchMark(1, 2), now)
        assertEquals(listOf("S2 E1", "S2 E2"), left.map { it.code })
    }

    @Test fun caughtUpMeansZero() {
        assertTrue(unwatchedEpisodes(episodes, WatchMark(2, 2), now).isEmpty())
    }

    @Test fun latestAiredSkipsFuture() {
        assertEquals("S2 E2", latestAired(episodes, now)?.code)
    }

    @Test fun nextToWatchFollowsMark() {
        assertEquals("S1 E1", nextToWatch(episodes, null)?.code)
        assertEquals("S2 E1", nextToWatch(episodes, WatchMark(1, 2))?.code)
        assertNull(nextToWatch(episodes, WatchMark(2, 3)))
    }

    @Test fun onlyMarkedShowsGetCounts() {
        val a = Show(id = "a", title = "A", tvmaze = TvMaze(id = 1))
        val b = Show(id = "b", title = "B", tvmaze = TvMaze(id = 2))
        val counts = unwatchedCounts(listOf(a, b), mapOf("a" to WatchMark(1, 2)), mapOf(1L to episodes, 2L to episodes), now)
        assertEquals(mapOf("a" to 2), counts)
    }

    @Test fun readyToWatchRowComesFirst() {
        val a = Show(id = "a", title = "A", statusGroup = "returning")
        val b = Show(id = "b", title = "B", statusGroup = "watching")
        val rows = groupRows(listOf(a, b), now, mapOf("a" to 3, "b" to 0))
        assertEquals(RowKind.READY_TO_WATCH, rows.first().kind)
        assertEquals(listOf("a"), rows.first().shows.map { it.id })
        assertEquals(RowKind.CAUGHT_UP, rows[1].kind)
    }

    @Test fun listShowsCarryStatusAndEpisodes() {
        val added = listOf(
            AddedShow(tvmazeId = 10, title = "Old One", platform = "Hulu", addedAtMs = 0, status = TrackerStatus.LOVED.id),
            AddedShow(tvmazeId = 20, title = "New One", platform = "Netflix", addedAtMs = 0),
        )
        val shows = toShows(added, mapOf(20L to episodes), now)
        assertEquals(listOf("added-10", "added-20"), shows.map { it.id })
        assertEquals("watched", shows[0].statusGroup)
        assertEquals("Loved", shows[0].status)
        assertEquals("Watching", shows[1].status)
        assertEquals("S2 E3", shows[1].nextEpisode?.code)
        assertEquals("S2 E2", shows[1].tvmaze?.lastEpisode?.code)
        val rows = groupRows(shows.take(1), now)
        assertEquals(RowKind.FINISHED, rows.single().kind)
    }

    @Test fun weekGroupsByLocalDay() {
        val show = Show(id = "s", title = "S", tvmaze = TvMaze(id = 5))
        // 02:00 UTC on Nov 26 is 8:00 PM Nov 25 in Chicago.
        val days = groupByDay(listOf(show), mapOf(5L to episodes), now, zone)
        assertEquals(7, days.size)
        assertEquals(LocalDate.of(2026, 11, 19), days.first().date)
        val withAiring = days.filter { it.airings.isNotEmpty() }
        assertEquals(listOf(LocalDate.of(2026, 11, 25)), withAiring.map { it.date })
    }

    @Test fun weekFallsBackToFeedNextEpisode() {
        val show = Show(id = "s", title = "S", tvmaze = TvMaze(id = 5, nextEpisode = ep(3, 1, "2026-11-20T02:00:00+00:00")))
        val days = groupByDay(listOf(show), emptyMap(), now, zone)
        assertEquals(1, days[0].airings.size) // 8 PM Nov 19 Chicago = today
    }
}
