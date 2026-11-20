package com.dalethomas.mytvshows

import com.dalethomas.mytvshows.domain.airLabel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TimeLabelsTest {
    private val chicago = ZoneId.of("America/Chicago")
    private val now = Instant.parse("2026-11-19T00:00:00Z") // Wed Nov 18, 6:00 PM Chicago

    private fun label(utc: String, zone: ZoneId = chicago) = airLabel(Instant.parse(utc), now, zone)

    @Test fun tonight() = assertEquals("Tonight 8:01 PM", label("2026-11-19T02:01:00Z"))
    @Test fun tomorrow() = assertEquals("Tomorrow 8:01 PM", label("2026-11-20T02:01:00Z"))
    @Test fun withinAWeekUsesWeekday() = assertEquals("Mon 7:00 PM", label("2026-11-24T01:00:00Z"))
    @Test fun laterThisYearUsesDate() = assertEquals("Fri, Nov 27", label("2026-11-27T12:00:00Z"))
    @Test fun earlyNextYearStillShort() = assertEquals("Tue, Jan 5", label("2027-01-06T02:00:00Z"))
    @Test fun farFutureIncludesYear() = assertEquals("Jul 9, 2027", label("2027-07-09T12:00:00Z"))
    @Test fun past() = assertEquals("Aired Nov 12", label("2026-11-13T02:01:00Z"))

    @Test
    fun usesDeviceZone() =
        assertEquals("Tomorrow 9:01 PM", label("2026-11-20T02:01:00Z", ZoneId.of("America/New_York")))
}
