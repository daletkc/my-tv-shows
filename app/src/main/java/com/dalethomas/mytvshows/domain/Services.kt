package com.dalethomas.mytvshows.domain

import com.dalethomas.mytvshows.data.Show
import java.time.Instant

/** A streaming app on the TV. `pattern` matches the free-text platform field. */
data class Service(
    val name: String,
    val packageName: String,
    val pattern: Regex,
    val background: Long,
    val foreground: Long,
)

object Services {
    /** Streaming apps the app knows how to open. Only the ones installed on the TV are shown. */
    val all = listOf(
        Service("Hulu", "com.hulu.livingroomplus", Regex("hulu", RegexOption.IGNORE_CASE), 0xFF1CE783, 0xFF0B0C0F),
        Service("YouTube TV", "com.google.android.youtube.tvunplugged", Regex("youtube tv", RegexOption.IGNORE_CASE), 0xFFFFFFFF, 0xFFCC0000),
        Service("YouTube", "com.google.android.youtube.tv", Regex("""youtube(?! tv)""", RegexOption.IGNORE_CASE), 0xFFCC0000, 0xFFFFFFFF),
        Service("Netflix", "com.netflix.ninja", Regex("netflix", RegexOption.IGNORE_CASE), 0xFF000000, 0xFFE50914),
        Service("Acorn TV", "com.acorntv.androidtv", Regex("acorn", RegexOption.IGNORE_CASE), 0xFF24563A, 0xFFFFFFFF),
        Service("HBO Max", "com.wbd.stream", Regex("""\bhbo\b|\bmax\b""", RegexOption.IGNORE_CASE), 0xFF1B2FD6, 0xFFFFFFFF),
        Service("Disney+", "com.disney.disneyplus", Regex("disney", RegexOption.IGNORE_CASE), 0xFF0E1A4F, 0xFFFFFFFF),
        Service("Prime Video", "com.amazon.amazonvideo.livingroom", Regex("""prime|amazon""", RegexOption.IGNORE_CASE), 0xFF0F6BA8, 0xFFFFFFFF),
        Service("Apple TV", "com.apple.atve.androidtv.appletv", Regex("apple tv", RegexOption.IGNORE_CASE), 0xFF1C1C1E, 0xFFFFFFFF),
        Service("BritBox", "com.britbox.tv", Regex("britbox", RegexOption.IGNORE_CASE), 0xFF00205B, 0xFFFFFFFF),
        Service("Peacock", "com.peacocktv.peacockandroid", Regex("peacock", RegexOption.IGNORE_CASE), 0xFF000000, 0xFFFFFFFF),
        Service("Paramount+", "com.cbs.ott", Regex("""paramount""", RegexOption.IGNORE_CASE), 0xFF0057D9, 0xFFFFFFFF),
    )

    /** Every service whose name appears in the platform text, e.g. "CBS (next-day Hulu/Paramount+)" -> Hulu, Paramount+. */
    fun forPlatform(platform: String): List<Service> = all.filter { it.pattern.containsMatchIn(platform) }
}

data class ServiceColumn(val service: Service, val shows: List<Show>)

/**
 * One column per service that has at least one show. A show available on two services
 * appears in both. Within a column shows keep the row priority (new, soon, watching...).
 */
fun groupByService(shows: List<Show>, now: Instant, unwatched: Map<String, Int> = emptyMap()): List<ServiceColumn> {
    val ordered = groupRows(shows, now, unwatched).flatMap { it.shows }
    return Services.all.mapNotNull { service ->
        val matching = ordered.filter { service in Services.forPlatform(it.platform) }
        if (matching.isEmpty()) null else ServiceColumn(service, matching)
    }
}
