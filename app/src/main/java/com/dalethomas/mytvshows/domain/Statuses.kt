package com.dalethomas.mytvshows.domain

/**
 * The statuses a household can set on a show. `tracker` is the word the Shows Tracker uses
 * (and what the feed reports), `group` the status group the rows use, `id` what the TV saves.
 */
enum class TrackerStatus(val id: String, val label: String, val tracker: String, val group: String) {
    WATCHING("watching", "Watching", "Watching", "watching"),
    TO_WATCH("to_watch", "To watch", "To watch", "to_watch"),
    WAITING("returning", "Waiting for new season", "Returning", "returning"),
    LOVED("loved", "Loved", "Loved", "watched"),
    LIKED("liked", "Liked", "Liked", "watched"),
    MEH("meh", "Meh", "Meh (3/5)", "watched"),
    NOT_FOR_US("not_for_us", "Not for us", "Not for us", "not_for_us"),
    ;

    companion object {
        /** Saved id, with "watched" (the old "Done") read as Liked. */
        fun of(id: String): TrackerStatus = entries.firstOrNull { it.id == id } ?: if (id == "watched") LIKED else WATCHING

        /** Matches the tracker's text, e.g. "Meh (3/5)" or "Returning". */
        fun fromTracker(text: String): TrackerStatus? = entries.firstOrNull { it.tracker.equals(text.trim(), ignoreCase = true) }
    }
}

/** Shows that haven't been started; marking an episode watched moves them to Watching. */
fun isNotStarted(statusGroup: String, kind: String) =
    statusGroup == "to_watch" || statusGroup == "recommended" || kind == "recommended"
