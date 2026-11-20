package com.dalethomas.mytvshows

import com.dalethomas.mytvshows.data.Episode
import com.dalethomas.mytvshows.data.Show
import com.dalethomas.mytvshows.data.TvMaze
import com.dalethomas.mytvshows.domain.RowKind
import com.dalethomas.mytvshows.domain.Services
import com.dalethomas.mytvshows.domain.groupByService
import com.dalethomas.mytvshows.domain.groupRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RowsTest {
    private val now = Instant.parse("2026-11-19T00:00:00Z") // Wed Nov 18, 6:00 PM Chicago

    private fun show(
        id: String,
        group: String = "returning",
        kind: String = "tracked",
        air: String? = null,
        platform: String = "Hulu",
    ) = Show(
        id = id, title = id, kind = kind, platform = platform, statusGroup = group,
        tvmaze = TvMaze(nextEpisode = air?.let { Episode(season = 1, number = 1, airstampUtc = it) }),
    )

    private fun rowsById(shows: List<Show>) = groupRows(shows, now).associate { r -> r.kind to r.shows.map { it.id } }

    @Test
    fun placesEachShowInTheRightRow() {
        val rows = rowsById(listOf(
            show("tomorrow", air = "2026-11-20T02:01:00+00:00"),
            show("in-6-days", air = "2026-11-24T23:00:00+00:00"),
            show("in-9-days", air = "2026-11-27T12:00:00+00:00"),
            show("watching", group = "watching"),
            show("to-watch", group = "to_watch"),
            show("waiting", group = "returning"),
            show("between", group = "between_seasons"),
            show("rec", group = "recommended", kind = "recommended"),
            show("loved", group = "watched"),
            show("cancelled", group = "ended"),
            show("odd", group = "other"),
        ))
        assertEquals(listOf("tomorrow", "in-6-days"), rows[RowKind.NEW_THIS_WEEK])
        assertEquals(listOf("in-9-days"), rows[RowKind.COMING_SOON])
        assertEquals(listOf("watching", "to-watch"), rows[RowKind.WATCHING])
        assertEquals(listOf("waiting", "between"), rows[RowKind.RETURNING])
        assertEquals(listOf("rec"), rows[RowKind.RECOMMENDED])
        assertEquals(listOf("loved", "cancelled"), rows[RowKind.FINISHED])
        assertTrue(rows.values.none { "odd" in it })
    }

    @Test
    fun sortsDatedRowsByAirTimeAndIgnoresPastEpisodes() {
        val rows = rowsById(listOf(
            show("later", air = "2026-11-23T01:00:00+00:00"),
            show("sooner", air = "2026-11-20T01:00:00+00:00"),
            show("past", group = "watching", air = "2026-11-12T02:00:00+00:00"),
            show("bad-date", group = "watching", air = "not a date"),
        ))
        assertEquals(listOf("sooner", "later"), rows[RowKind.NEW_THIS_WEEK])
        assertEquals(listOf("past", "bad-date"), rows[RowKind.WATCHING])
    }

    @Test
    fun omitsEmptyRows() {
        val rows = groupRows(listOf(show("only", group = "watched")), now)
        assertEquals(listOf(RowKind.FINISHED), rows.map { it.kind })
    }

    @Test
    fun matchesPlatformTextToServices() {
        fun names(p: String) = Services.forPlatform(p).map { it.name }
        assertEquals(listOf("Hulu", "Paramount+"), names("CBS (next-day Hulu/Paramount+)"))
        assertEquals(listOf("HBO Max"), names("HBO / HBO Max"))
        assertEquals(listOf("HBO Max"), names("TLC (next-day on Max/Discovery+)"))
        assertEquals(listOf("Apple TV"), names("Apple TV+"))
        assertEquals(listOf("Prime Video"), names("Prime Video"))
        assertEquals(listOf("BritBox"), names("BBC / BritBox"))
        assertEquals(emptyList<String>(), names("ABC"))
    }

    @Test
    fun groupsByServiceWithSharedShowsInEachColumn() {
        val cols = groupByService(listOf(
            show("elsbeth", platform = "CBS (next-day Hulu/Paramount+)", air = "2026-11-20T02:01:00+00:00"),
            show("omitb", platform = "Hulu", group = "watched"),
            show("abc-only", platform = "ABC"),
        ), now).associate { it.service.name to it.shows.map { s -> s.id } }
        assertEquals(listOf("elsbeth", "omitb"), cols["Hulu"])
        assertEquals(listOf("elsbeth"), cols["Paramount+"])
        assertEquals(setOf("Hulu", "Paramount+"), cols.keys)
    }
}
