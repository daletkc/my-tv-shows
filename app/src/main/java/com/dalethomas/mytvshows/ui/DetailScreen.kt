package com.dalethomas.mytvshows.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.dalethomas.mytvshows.data.Episode
import com.dalethomas.mytvshows.data.Show
import com.dalethomas.mytvshows.data.ShowStatus
import com.dalethomas.mytvshows.data.WatchMark
import com.dalethomas.mytvshows.domain.Service
import com.dalethomas.mytvshows.domain.Services
import com.dalethomas.mytvshows.domain.airLabel
import com.dalethomas.mytvshows.domain.hasAired
import com.dalethomas.mytvshows.domain.latestAired
import com.dalethomas.mytvshows.domain.nextToWatch
import com.dalethomas.mytvshows.domain.toMark
import com.dalethomas.mytvshows.domain.unwatchedEpisodes
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

@Composable
fun DetailScreen(
    show: Show,
    installed: List<Service>,
    episodes: List<Episode>?,
    mark: WatchMark?,
    now: Instant,
    zone: ZoneId,
    onSetWatched: (WatchMark?) -> Unit,
    status: String?,
    onSetStatus: (ShowStatus) -> Unit,
    onRemove: (() -> Unit)?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val openWith = Services.forPlatform(show.platform).filter { it in installed }
    val focus = remember { FocusRequester() }
    LaunchedEffect(show.id) { runCatching { focus.requestFocus() } }

    Row(Modifier.fillMaxSize().background(Palette.Ground).padding(48.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Poster(show, Modifier.size(220.dp, 314.dp).background(Palette.Raised, RoundedCornerShape(14.dp)))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(show.title, color = Palette.Text, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text(listOf(show.platform, show.status).filter { it.isNotBlank() }.joinToString(" · "),
                color = Palette.Muted, fontSize = 18.sp)
            show.nextEpisode?.let { EpisodeLine("Next", it, now, zone) }
            show.tvmaze?.lastEpisode?.let { EpisodeLine("Last", it, now, zone) }
            if (show.notes.isNotBlank()) Text(show.notes, color = Palette.Text, fontSize = 17.sp)
            show.tvmaze?.officialSite?.let { Text("Official site: $it", color = Palette.Muted, fontSize = 15.sp) }

            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                openWith.forEachIndexed { i, service ->
                    Pill("Open ${service.name}", onClick = { launchApp(context, service.packageName) }, selected = i == 0,
                        modifier = if (i == 0) Modifier.focusRequester(focus) else Modifier)
                }
                Pill("Find on Google TV", onClick = { searchGoogleTv(context, show.title) }, selected = openWith.isEmpty(),
                    modifier = if (openWith.isEmpty()) Modifier.focusRequester(focus) else Modifier)
                Pill("Back", onClick = onBack)
            }

            if (show.tvmaze?.id != null) Progress(episodes, mark, now, onSetWatched)
            if (status != null) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Status")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ShowStatus.entries.forEach { s ->
                            Pill(s.label, onClick = { onSetStatus(s) }, selected = s.id == status)
                        }
                    }
                }
            }
            if (onRemove != null) {
                Row(Modifier.padding(top = 4.dp)) { Pill("Remove from my list", onClick = onRemove) }
            }

            Text("Data from TVMaze (CC BY-SA)", color = Palette.Muted, fontSize = 13.sp,
                modifier = Modifier.padding(top = 12.dp))
        }
    }
}

/** "Where are we?" controls: mark the next episode watched, catch up, or clear. */
@Composable
private fun Progress(episodes: List<Episode>?, mark: WatchMark?, now: Instant, onSetWatched: (WatchMark?) -> Unit) {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Our progress")
        if (episodes == null) {
            Text("Loading episodes...", color = Palette.Muted, fontSize = 15.sp)
            return@Column
        }
        val unwatched = mark?.let { unwatchedEpisodes(episodes, it, now) }
        val latest = latestAired(episodes, now)
        val next = nextToWatch(episodes, mark)?.takeIf { it.hasAired(now) }
        Text(
            when {
                mark == null -> "Not tracked yet. Mark where you are to see new episodes."
                unwatched.isNullOrEmpty() -> "Watched up to S${mark.season} E${mark.number}. You're caught up."
                else -> "Watched up to S${mark.season} E${mark.number}. ${unwatched.size} new " +
                    (if (unwatched.size == 1) "episode" else "episodes") + " to watch."
            },
            color = if (!unwatched.isNullOrEmpty()) Palette.Accent else Palette.Text, fontSize = 17.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            next?.toMark()?.let { m -> Pill("Watched ${next.code}", onClick = { onSetWatched(m) }) }
            latest?.toMark()?.takeIf { it != mark }?.let { m -> Pill("We're caught up (${latest.code})", onClick = { onSetWatched(m) }) }
            if (mark != null) Pill("Clear", onClick = { onSetWatched(null) })
        }
    }
}

@Composable
private fun EpisodeLine(label: String, ep: Episode, now: Instant, zone: ZoneId) {
    val whenText = ep.airstampUtc?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
        ?.let { airLabel(it, now, zone) } ?: ep.airdate.orEmpty()
    val parts = listOfNotNull(ep.code.takeIf { it.isNotBlank() }, ep.name?.takeIf { it != "TBA" }, whenText.takeIf { it.isNotBlank() })
    Text("$label: " + parts.joinToString(" · "), color = Palette.Text, fontSize = 18.sp)
}
