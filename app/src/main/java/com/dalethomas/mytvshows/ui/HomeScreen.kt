@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.dalethomas.mytvshows.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.dalethomas.mytvshows.data.Episode
import com.dalethomas.mytvshows.data.Show
import com.dalethomas.mytvshows.data.WatchMark
import com.dalethomas.mytvshows.domain.RowKind
import com.dalethomas.mytvshows.domain.Service
import com.dalethomas.mytvshows.domain.ShowRow
import com.dalethomas.mytvshows.domain.Services
import com.dalethomas.mytvshows.domain.dayLabel
import com.dalethomas.mytvshows.domain.focusSeason
import com.dalethomas.mytvshows.domain.hasAired
import com.dalethomas.mytvshows.domain.inSeason
import com.dalethomas.mytvshows.domain.isWatched
import com.dalethomas.mytvshows.domain.nextAirInstant
import com.dalethomas.mytvshows.domain.nextToWatch
import com.dalethomas.mytvshows.domain.toMark
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Full-screen artwork for the show in the hero, faded into the background on the left and
 * bottom so text stays readable. Drawn by MainScreen behind the top bar.
 */
@Composable
fun CinematicBackdrop(url: String?) {
    Box(Modifier.fillMaxSize().background(Palette.Ground)) {
        Crossfade(targetState = url, animationSpec = tween(600), label = "backdrop") { u ->
            Box(Modifier.fillMaxSize()) {
                if (!u.isNullOrBlank()) {
                    AsyncImage(
                        model = u,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = BiasAlignment(0f, -0.4f),
                        modifier = Modifier.align(Alignment.TopEnd).size(750.dp, 430.dp),
                    )
                }
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Palette.Ground, 0.22f to Palette.Ground, 0.38f to Palette.Ground.copy(alpha = 0.85f),
                    0.62f to Palette.Ground.copy(alpha = 0.2f), 0.8f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.fillMaxWidth().height(90.dp).background(
                Brush.verticalGradient(0f to Palette.Ground.copy(alpha = 0.8f), 1f to Color.Transparent),
            ),
        )
        Box(
            Modifier.fillMaxSize().padding(top = 250.dp).background(
                Brush.verticalGradient(
                    0f to Color.Transparent, 0.4f to Palette.Ground.copy(alpha = 0.92f), 0.62f to Palette.Ground,
                ),
            ),
        )
    }
}

/** Mockup A: a big hero for the focused show over its artwork, then poster rows. */
@Composable
fun HomeScreen(
    rows: List<ShowRow>,
    hero: Show?,
    installed: List<Service>,
    now: Instant,
    zone: ZoneId,
    unwatched: Map<String, Int>,
    episodes: Map<Long, List<Episode>>,
    watched: Map<String, WatchMark>,
    footer: String,
    firstFocus: FocusRequester,
    onHero: (Show) -> Unit,
    onSetWatched: (Show, WatchMark) -> Unit,
    onAdd: () -> Unit,
    onShow: (Show) -> Unit,
) {
    val context = LocalContext.current
    // Rows can appear above what is on screen once episode lists load ("Ready to watch");
    // the list would keep its position and hide them, so go back to the top when that happens.
    val listState = rememberLazyListState()
    LaunchedEffect(rows.firstOrNull()?.kind) { listState.scrollToItem(0) }
    // TV-style rows: the focused row moves to the top of the list with its title showing.
    // Rows inside keep the normal "scroll just enough" behavior.
    val rowSpec = LocalBringIntoViewSpec.current
    val titleSpace = with(LocalDensity.current) { 44.dp.toPx() }
    val pivotTop = remember(titleSpace) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - titleSpace
        }
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(262.dp).padding(start = 48.dp, top = 28.dp)) {
            if (hero != null) {
                Hero(
                    show = hero,
                    episodes = hero.tvmaze?.id?.let { episodes[it] },
                    mark = watched[hero.id],
                    newCount = unwatched[hero.id] ?: 0,
                    installed = installed,
                    now = now,
                    zone = zone,
                    firstFocus = firstFocus,
                    onDetails = { onShow(hero) },
                    onSetWatched = { onSetWatched(hero, it) },
                )
            } else {
                EmptyHero(onAdd, firstFocus)
            }
        }

        CompositionLocalProvider(LocalBringIntoViewSpec provides pivotTop) {
        LazyColumn(
            state = listState,
            // Fade rows out under the hero instead of cutting them off mid-title.
            modifier = Modifier
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(0f to Color.Transparent, (10.dp.toPx() / size.height) to Color.Black),
                        blendMode = BlendMode.DstIn,
                    )
                },
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            rows.forEachIndexed { index, row ->
                item(key = row.kind.name) {
                    val sub = if (row.kind == RowKind.READY_TO_WATCH) {
                        val total = row.shows.sumOf { unwatched[it.id] ?: 0 }
                        "$total new " + (if (total == 1) "episode" else "episodes") + " across ${row.shows.size} " +
                            (if (row.shows.size == 1) "show" else "shows")
                    } else null
                    Rail(row.kind.title, sub, rowSpec) {
                        itemsIndexed(row.shows, key = { _, s -> s.id }) { i, show ->
                            val count = unwatched[show.id] ?: 0
                            val air = show.nextAirInstant()
                            val soon = air != null && !air.isBefore(now) && air.isBefore(now.plus(Duration.ofDays(7)))
                            val caption = when {
                                row.kind == RowKind.READY_TO_WATCH -> newLabel(count)
                                soon -> "New " + dayLabel(air!!, now, zone)
                                air != null && !air.isBefore(now) -> dayLabel(air, now, zone)
                                row.kind == RowKind.RECOMMENDED -> show.platform.takeIf { it.isNotBlank() }
                                else -> show.status.takeIf { it.isNotBlank() }
                            }
                            PosterTile(
                                show = show,
                                caption = caption,
                                captionHighlighted = row.kind == RowKind.READY_TO_WATCH || soon,
                                badge = if (row.kind == RowKind.READY_TO_WATCH) count else 0,
                                modifier = Modifier.endOfRow(i == row.shows.lastIndex),
                                onFocus = { onHero(show) },
                            ) { onShow(show) }
                        }
                    }
                }
                if (index == 0 && installed.isNotEmpty()) item(key = "apps") { AppsRail(installed, context, rowSpec) }
            }
            if (rows.isEmpty() && installed.isNotEmpty()) item(key = "apps") { AppsRail(installed, context, rowSpec) }
            item(key = "footer") { Text(footer, color = Palette.Muted, fontSize = 9.sp) }
        }
        }
    }
}

@Composable
private fun Rail(title: String, sub: String?, rowSpec: BringIntoViewSpec, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            if (sub != null) Text(sub, color = Palette.Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 1.dp))
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(13.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp), content = content)
        }
    }
}

@Composable
private fun AppsRail(installed: List<Service>, context: android.content.Context, rowSpec: BringIntoViewSpec) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Your apps", color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
            itemsIndexed(installed, key = { _, s -> s.packageName }) { i, service ->
                AppTile(service, onClick = { launchApp(context, service.packageName) }, width = 120.dp, height = 64.dp,
                    modifier = Modifier.endOfRow(i == installed.lastIndex))
            }
        } }
    }
}

fun newLabel(count: Int) = if (count == 1) "1 new episode" else "$count new episodes"

@Composable
fun SectionTitle(text: String) {
    Text(text, color = Palette.Muted, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
}

@Composable
private fun Hero(
    show: Show,
    episodes: List<Episode>?,
    mark: WatchMark?,
    newCount: Int,
    installed: List<Service>,
    now: Instant,
    zone: ZoneId,
    firstFocus: FocusRequester,
    onDetails: () -> Unit,
    onSetWatched: (WatchMark) -> Unit,
) {
    val context = LocalContext.current
    val openWith = Services.forPlatform(show.platform).firstOrNull { it in installed }
    val air = show.nextAirInstant()
    val upcoming = air != null && !air.isBefore(now)
    // Only offer a specific episode once the household has marked its place.
    val next = if (mark != null && episodes != null) nextToWatch(episodes, mark)?.takeIf { it.hasAired(now) } else null
    val season = episodes?.let { focusSeason(it, mark, now) }
    val seasonEps = season?.let { episodes.inSeason(it) }.orEmpty()

    val (eyebrow, eyebrowColor) = when {
        newCount > 0 -> "$newCount NEW ${if (newCount == 1) "EPISODE" else "EPISODES"} TO WATCH" to Palette.Accent
        upcoming && air!!.isBefore(now.plus(Duration.ofDays(7))) ->
            "NEW EPISODE ${dayLabel(air, now, zone).uppercase()}" to Palette.Accent
        upcoming -> "NEXT EPISODE ${dayLabel(air!!, now, zone).uppercase()}" to Palette.Accent
        show.statusGroup == "returning" || show.statusGroup == "between_seasons" -> "WAITING FOR A NEW SEASON" to Palette.Muted
        show.statusGroup == "watched" || show.statusGroup == "ended" -> "FINISHED" to Palette.Muted
        show.kind == "recommended" -> "RECOMMENDED FOR US" to Palette.Accent
        mark != null -> "ALL CAUGHT UP" to Palette.Muted
        else -> (show.status.uppercase().ifBlank { "ON YOUR LIST" }) to Palette.Muted
    }

    Column(Modifier.width(470.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.size(5.dp).background(eyebrowColor, RoundedCornerShape(50)))
            Text(eyebrow, color = eyebrowColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp, maxLines = 1)
        }
        Text(
            show.title,
            color = Palette.Text,
            fontSize = when { show.title.length <= 10 -> 56.sp; show.title.length <= 16 -> 44.sp; else -> 34.sp },
            lineHeight = when { show.title.length <= 10 -> 54.sp; show.title.length <= 16 -> 44.sp; else -> 36.sp },
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-1).sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = listOfNotNull(
            season?.let { "Season $it" },
            show.platform.takeIf { it.isNotBlank() },
            show.tvmaze?.genres?.firstOrNull(),
        )
        if (meta.isNotEmpty()) Text(meta.joinToString("  ·  "), color = Color(0xFFD8D4CB), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)

        if (seasonEps.isNotEmpty()) {
            val aired = seasonEps.count { it.hasAired(now) }
            Column(Modifier.width(300.dp).padding(top = 3.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        when {
                            mark == null -> "Not tracked yet"
                            mark.season == season -> "Watched up to S${mark.season} E${mark.number}"
                            mark.season > (season ?: 0) -> "Watched season $season"
                            else -> "Watched through season ${mark.season}"
                        },
                        color = Color(0xFFC9C5BC), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (newCount > 0) "${newLabel(newCount)} to catch up" else "$aired of ${seasonEps.size} out",
                        color = if (newCount > 0) Palette.Accent else Color(0xFFC9C5BC), fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
                    seasonEps.forEach { ep ->
                        val color = when {
                            ep.isWatched(mark) -> Palette.Text
                            ep.hasAired(now) && mark != null -> Palette.Accent
                            ep.hasAired(now) -> Palette.Muted.copy(alpha = 0.6f)
                            else -> Color.White.copy(alpha = 0.16f)
                        }
                        Box(Modifier.weight(1f).height(4.dp).background(color, RoundedCornerShape(2.dp)))
                    }
                }
            }
        }

        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            val primaryLabel = when {
                openWith != null && next != null -> "Watch ${next.code} on ${openWith.name}"
                openWith != null -> "Open ${openWith.name}"
                else -> "Find on Google TV"
            }
            Pill(primaryLabel, selected = true, large = true, modifier = Modifier.focusRequester(firstFocus), onClick = {
                if (openWith != null) launchApp(context, openWith.packageName) else searchGoogleTv(context, show.title)
            })
            Pill("Details", large = true, onClick = onDetails)
            next?.toMark()?.let { m -> Pill("Mark E${m.number} watched", large = true, onClick = { onSetWatched(m) }) }
        }
    }
}

@Composable
private fun EmptyHero(onAdd: () -> Unit, firstFocus: FocusRequester) {
    Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("No shows yet", color = Palette.Text, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
        Text("Add the shows you watch. New episodes, what's coming up, and your progress show up here.",
            color = Palette.Muted, fontSize = 13.sp)
        Row(Modifier.padding(top = 8.dp)) {
            Pill("+ Add your first show", selected = true, large = true, onClick = onAdd, modifier = Modifier.focusRequester(firstFocus))
        }
    }
}
