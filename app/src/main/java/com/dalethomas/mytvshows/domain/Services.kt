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
    /** Streaming apps the app recognizes by name (brand colors, matching TVMaze network names). */
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

    fun byPackage(packageName: String): Service? = all.firstOrNull { it.packageName == packageName }

    /** Any other app on the TV, shown as a plain tile with its name. */
    fun otherApp(label: String, packageName: String) =
        Service(label, packageName, Regex("(?!)"), 0xFF24262C, 0xFFF4F1EA)

    /**
     * The apps (from `installed`) that open this show: the app picked when it was added,
     * then any recognized service named in its platform text.
     */
    fun forShow(show: Show, installed: List<Service>): List<Service> =
        (installed.filter { it.packageName == show.appPackage } + forPlatform(show.platform).filter { it in installed }).distinct()
}

data class ServiceColumn(val service: Service, val shows: List<Show>)

/**
 * One column per service that has at least one show. A show available on two services
 * appears in both. Within a column shows keep the row priority (new, soon, watching...).
 * `others` adds apps outside the recognized list (matched by the app picked for a show).
 */
fun groupByService(
    shows: List<Show>,
    now: Instant,
    unwatched: Map<String, Int> = emptyMap(),
    others: List<Service> = emptyList(),
): List<ServiceColumn> {
    val ordered = groupRows(shows, now, unwatched).flatMap { it.shows }
    return (Services.all + others).distinctBy { it.packageName }.mapNotNull { service ->
        val matching = ordered.filter { it.appPackage == service.packageName || service in Services.forPlatform(it.platform) }
        if (matching.isEmpty()) null else ServiceColumn(service, matching)
    }
}

/**
 * The app to use without asking when a show is added: the one installed app that TVMaze's
 * network name points to. Null means ask (provider not installed, unknown, or several match).
 */
fun autoPickApp(channel: String?, installed: List<Service>): Service? =
    Services.forPlatform(channel.orEmpty()).filter { it in installed }.singleOrNull()

/** The question shown when the app has to ask where a show is watched. */
fun whichAppQuestion(channel: String?, installed: List<Service>): String {
    val named = Services.forPlatform(channel.orEmpty())
    val onTv = named.filter { it in installed }
    return when {
        named.isEmpty() && channel.isNullOrBlank() -> "Which app do you watch it on?"
        named.isEmpty() -> "TVMaze lists it on $channel. Which app do you watch it on?"
        onTv.isEmpty() -> "TVMaze lists it on ${named.joinToString(" or ") { it.name }}, which isn't on this TV. Which app do you use?"
        else -> "It's on more than one of your apps. Which one do you use?"
    }
}
