package com.dalethomas.mytvshows

import com.dalethomas.mytvshows.data.Episode
import com.dalethomas.mytvshows.data.WatchMark
import com.dalethomas.mytvshows.domain.dayLabel
import com.dalethomas.mytvshows.domain.focusSeason
import com.dalethomas.mytvshows.domain.inSeason
import com.dalethomas.mytvshows.domain.isWatched
import com.dalethomas.mytvshows.domain.seasonsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SeasonsTest {
    private val now = Instant.parse("2026-11-19T18:00:00Z")
    private val chicago = ZoneId.of("America/Chicago")

    private fun ep(s: Int, n: Int?, stamp: String) = Episode(season = s, number = n, airstampUtc = stamp)

    private val episodes = listOf(
        ep(1, 2, "2026-10-08T01:00:00+00:00"),
        ep(1, 1, "2026-10-01T01:00:00+00:00"),
        ep(1, null, "2026-10-10T01:00:00+00:00"),
        ep(2, 1, "2026-11-12T01:00:00+00:00"),
        ep(2, 2, "2026-11-26T02:00:00+00:00"),
        ep(3, 1, "2027-03-01T02:00:00+00:00"),
    )

    @Test fun seasonsAndOrder() {
        assertEquals(listOf(1, 2, 3), seasonsOf(episodes))
        assertEquals(listOf(1, 2), episodes.inSeason(1).map { it.number })
    }

    @Test fun watchedComparesSeasonThenNumber() {
        val mark = WatchMark(2, 1)
        assertTrue(episodes[0].isWatched(mark))
        assertTrue(episodes[3].isWatched(mark))
        assertFalse(episodes[4].isWatched(mark))
        assertFalse(episodes[0].isWatched(null))
    }

    @Test fun focusSeasonFollowsTheNextEpisode() {
        assertEquals(2, focusSeason(episodes, WatchMark(1, 2), now))
        // No marker: the season of the latest aired episode.
        assertEquals(2, focusSeason(episodes, null, now))
    }

    @Test fun dayLabelsHaveNoClockTime() {
        fun label(utc: String) = dayLabel(Instant.parse(utc), Instant.parse("2026-11-19T00:00:00Z"), chicago)
        assertEquals("Today", label("2026-11-19T02:01:00Z"))
        assertEquals("Tomorrow", label("2026-11-20T02:01:00Z"))
        assertEquals("Monday", label("2026-11-24T01:00:00Z"))
        assertEquals("Fri, Nov 27", label("2026-11-27T12:00:00Z"))
        assertEquals("Aired Nov 12", label("2026-11-13T02:01:00Z"))
    }
}
