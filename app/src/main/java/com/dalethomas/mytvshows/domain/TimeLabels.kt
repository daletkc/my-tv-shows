package com.dalethomas.mytvshows.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.US)
private val DATE = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
private val DATE_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

/**
 * Relative air-time label computed at render time in the device zone:
 * "Tonight 8:01 PM", "Today 11:00 AM", "Tomorrow 7:00 PM", "Mon 7:00 PM" (within a week),
 * "Fri, Nov 27", or "Jan 5, 2027" in another year. Past times give "Aired Nov 12".
 */
fun airLabel(air: Instant, now: Instant, zone: ZoneId): String {
    val at = air.atZone(zone)
    val today = now.atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(today, at.toLocalDate())
    return when {
        days < 0 -> "Aired " + at.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
        days == 0L -> (if (at.hour >= 17) "Tonight " else "Today ") + at.format(TIME)
        days == 1L -> "Tomorrow " + at.format(TIME)
        days < 7 -> at.format(WEEKDAY) + " " + at.format(TIME)
        at.year == today.year || days < 120 -> at.format(DATE)
        else -> at.format(DATE_YEAR)
    }
}
