package com.dalethomas.mytvshows.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.dalethomas.mytvshows.data.Episode
import com.dalethomas.mytvshows.data.Show
import com.dalethomas.mytvshows.data.WatchMark
import com.dalethomas.mytvshows.domain.Service
import com.dalethomas.mytvshows.domain.Services
import com.dalethomas.mytvshows.domain.TrackerStatus
import com.dalethomas.mytvshows.domain.airLabel
import com.dalethomas.mytvshows.domain.focusSeason
import com.dalethomas.mytvshows.domain.hasAired
import com.dalethomas.mytvshows.domain.inSeason
import com.dalethomas.mytvshows.domain.isWatched
import com.dalethomas.mytvshows.domain.latestAired
import com.dalethomas.mytvshows.domain.seasonsOf
import com.dalethomas.mytvshows.domain.toMark
import com.dalethomas.mytvshows.domain.unwatchedEpisodes
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Show details over its dimmed artwork. `extra` is an optional section under the progress
 * controls (the show status picker, where the list is kept on the TV).
 */
@Composable
fun DetailScreen(
    show: Show,
    installed: List<Service>,
    episodes: List<Episode>?,
    mark: WatchMark?,
    backdrop: String?,
    now: Instant,
    zone: ZoneId,
    onSetWatched: (WatchMark?) -> Unit,
    onRemove: (() -> Unit)?,
    onBack: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val openWith = Services.forShow(show, installed)
    val focus = remember { FocusRequester() }
    LaunchedEffect(show.id) { runCatching { focus.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Palette.Ground)) {
        if (!backdrop.isNullOrBlank()) {
            // Slight zoom crops the thin frames some TVMaze artwork has baked in.
            AsyncImage(model = backdrop, contentDescription = null, contentScale = ContentScale.Crop, alpha = 0.22f,
                modifier = Modifier.fillMaxSize().clipToBounds().graphicsLayer(scaleX = 1.1f, scaleY = 1.1f))
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 40.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Poster(show, Modifier.size(170.dp, 255.dp).clip(RoundedCornerShape(10.dp)))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(show.title, color = Palette.Text, fontSize = 38.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                Text(listOfNotNull(show.platform.takeIf { it.isNotBlank() }, show.status.takeIf { it.isNotBlank() },
                    show.tvmaze?.genres?.take(2)?.joinToString(", ")?.takeIf { it.isNotBlank() }).joinToString("  ·  "),
                    color = Color(0xFFD8D4CB), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                show.nextEpisode?.let { EpisodeLine("Next", it, now, zone) }
                show.tvmaze?.lastEpisode?.let { EpisodeLine("Last", it, now, zone) }
                if (show.notes.isNotBlank()) Text(show.notes, color = Palette.Text, fontSize = 12.sp)
                // Tracker text sometimes starts with a stray ", " from the source note.
                show.upcoming?.trim(',', ' ')?.takeIf { it.isNotEmpty() }
                    ?.let { Text("Tracker: $it", color = Palette.Muted, fontSize = 11.sp) }

                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    openWith.forEachIndexed { i, service ->
                        Pill("Open ${service.name}", onClick = { launchApp(context, service.packageName) }, selected = i == 0, large = true,
                            modifier = if (i == 0) Modifier.focusRequester(focus) else Modifier)
                    }
                    Pill("Find on Google TV", onClick = { searchGoogleTv(context, show.title) }, selected = openWith.isEmpty(), large = true,
                        modifier = if (openWith.isEmpty()) Modifier.focusRequester(focus) else Modifier)
                    Pill("Back", onClick = onBack, large = true)
                }

                if (show.tvmaze?.id != null) Progress(show.id, episodes, mark, now, onSetWatched)
                extra()
                if (onRemove != null) {
                    Row(Modifier.padding(top = 4.dp)) { Pill("Remove from my list", onClick = onRemove) }
                }

                Text("Data from TVMaze (CC BY-SA)", color = Palette.Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}

/** "Where are we?": pick the last episode watched from the season's episode buttons. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Progress(showId: String, episodes: List<Episode>?, mark: WatchMark?, now: Instant, onSetWatched: (WatchMark?) -> Unit) {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SectionTitle("OUR PROGRESS")
        if (episodes == null) {
            Text("Loading episodes...", color = Palette.Muted, fontSize = 11.sp)
            return@Column
        }
        val seasons = seasonsOf(episodes)
        if (seasons.isEmpty()) {
            Text("TVMaze has no episode list for this show yet.", color = Palette.Muted, fontSize = 11.sp)
            return@Column
        }
        val unwatched = mark?.let { unwatchedEpisodes(episodes, it, now) }
        Text(
            when {
                mark == null -> "Not tracked yet. Pick the last episode you watched to see what's new."
                unwatched.isNullOrEmpty() -> "Watched up to S${mark.season} E${mark.number}. You're caught up."
                else -> "Watched up to S${mark.season} E${mark.number}. ${newLabel(unwatched.size)} to watch."
            },
            color = if (!unwatched.isNullOrEmpty()) Palette.Accent else Palette.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        )

        var season by remember(showId, seasons) { mutableStateOf(focusSeason(episodes, mark, now) ?: seasons.last()) }
        if (seasons.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                seasons.forEach { s -> Pill("Season $s", selected = s == season, onClick = { season = s }) }
            }
        }
        val list = episodes.inSeason(season)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            list.forEachIndexed { i, ep ->
                EpisodeChip(ep, mark, now) {
                    val m = ep.toMark() ?: return@EpisodeChip
                    // Picking the current marker steps back one episode, so a mistake is easy to undo.
                    onSetWatched(if (m == mark) list.getOrNull(i - 1)?.toMark() else m)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(Palette.Text, null, "Watched")
            Legend(Palette.Accent, null, "New")
            Legend(Color.Transparent, Palette.Line, "Not out yet")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            latestAired(episodes, now)?.let { latest ->
                latest.toMark()?.takeIf { it != mark }?.let { m -> Pill("We're caught up (${latest.code})", onClick = { onSetWatched(m) }) }
            }
            if (mark != null) Pill("Clear", onClick = { onSetWatched(null) })
        }
    }
}

@Composable
private fun EpisodeChip(ep: Episode, mark: WatchMark?, now: Instant, onClick: () -> Unit) {
    val watched = ep.isWatched(mark)
    val aired = ep.hasAired(now)
    val shape = RoundedCornerShape(8.dp)
    // Amber "new" only means something once the household has marked its place.
    val isNew = aired && !watched && mark != null
    val bg = when { watched -> Palette.Text; isNew -> Palette.Accent; aired -> Palette.Raised; else -> Color.Transparent }
    val fg = when { watched || isNew -> Palette.Ground; aired -> Palette.Text; else -> Palette.Muted }
    Surface(
        onClick = onClick,
        enabled = aired,
        modifier = Modifier.size(40.dp, 30.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = bg, contentColor = fg, focusedContainerColor = bg, focusedContentColor = fg,
            disabledContainerColor = bg, disabledContentColor = fg,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, if (watched || aired) Color.Transparent else Palette.Line), shape = shape),
            focusedBorder = Border(BorderStroke(2.5.dp, Color.White), inset = (-3).dp, shape = shape),
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("E${ep.number}", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun Legend(fill: Color, outline: Color?, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(9.dp).background(fill, RoundedCornerShape(2.dp))
            .then(if (outline != null) Modifier.border(1.dp, outline, RoundedCornerShape(2.dp)) else Modifier))
        Text(label, color = Palette.Muted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EpisodeLine(label: String, ep: Episode, now: Instant, zone: ZoneId) {
    val whenText = ep.airstampUtc?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
        ?.let { airLabel(it, now, zone) } ?: ep.airdate.orEmpty()
    val parts = listOfNotNull(ep.code.takeIf { it.isNotBlank() }, ep.name?.takeIf { it != "TBA" }, whenText.takeIf { it.isNotBlank() })
    Text("$label: " + parts.joinToString(" · "), color = Palette.Text, fontSize = 12.sp)
}

/** "How's it going?": the show's status, as on the tracker. */
@Composable
fun StatusPicker(current: TrackerStatus?, options: List<TrackerStatus>, onPick: (TrackerStatus) -> Unit) {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SectionTitle("STATUS")
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            options.forEach { s -> Pill(s.label, selected = s == current, onClick = { onPick(s) }) }
        }
    }
}
