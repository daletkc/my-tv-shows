package com.dalethomas.mytvshows.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.dalethomas.mytvshows.data.Show
import com.dalethomas.mytvshows.domain.RowKind
import com.dalethomas.mytvshows.domain.Service
import com.dalethomas.mytvshows.domain.ShowRow
import com.dalethomas.mytvshows.domain.Services
import com.dalethomas.mytvshows.domain.airLabel
import com.dalethomas.mytvshows.domain.nextAirInstant
import java.time.Instant
import java.time.ZoneId

/** Next-up banner, your apps, then the show rows. */
@Composable
fun HomeScreen(
    rows: List<ShowRow>,
    installed: List<Service>,
    now: Instant,
    zone: ZoneId,
    unwatched: Map<String, Int>,
    footer: String,
    firstFocus: FocusRequester,
    onAdd: () -> Unit,
    onShow: (Show) -> Unit,
) {
    val context = LocalContext.current
    val upcoming = rows.filter { it.kind == RowKind.NEW_THIS_WEEK || it.kind == RowKind.COMING_SOON }.flatMap { it.shows }
    val hero = upcoming.firstOrNull()

    // Rows can appear above what is on screen (the banner, "Ready to watch"); the list keeps
    // its position when that happens, so jump back to the top when the banner first shows.
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (hero != null) {
            item(key = "hero") {
                Hero(hero, upcoming.drop(1).take(3), installed, now, zone, firstFocus, onShown = { listState.scrollToItem(0) }, onShow = onShow)
            }
        }
        if (rows.isEmpty()) {
            item(key = "empty") {
                Column(
                    Modifier.fillMaxWidth().background(Palette.Panel, RoundedCornerShape(18.dp)).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("No shows yet", color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("Add the shows you watch. New episodes, what airs this week, and your progress show up here.",
                        color = Palette.Muted, fontSize = 17.sp)
                    Row { Pill("+ Add your first show", onClick = onAdd, selected = true, modifier = Modifier.focusRequester(firstFocus)) }
                }
            }
        }
        if (installed.isNotEmpty()) {
            item(key = "apps") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Your apps")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                        itemsIndexed(installed, key = { _, s -> s.packageName }) { i, service ->
                            AppTile(service, onClick = { launchApp(context, service.packageName) },
                                modifier = Modifier.endOfRow(i == installed.lastIndex))
                        }
                    }
                }
            }
        }
        items(rows, key = { it.kind.name }) { row ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(row.kind.title)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                    itemsIndexed(row.shows, key = { _, s -> s.id }) { i, show ->
                        val air = show.nextAirInstant()
                        val caption = when {
                            row.kind == RowKind.READY_TO_WATCH -> newLabel(unwatched[show.id] ?: 0)
                            air != null -> airLabel(air, now, zone)
                            row.kind == RowKind.RECOMMENDED -> show.platform.takeIf { it.isNotBlank() }
                            else -> show.status.takeIf { it.isNotBlank() }
                        }
                        // Without a banner, the first poster takes the initial focus.
                        val first = hero == null && i == 0 && row == rows.first()
                        PosterCard(show, caption, captionHighlighted = row.kind == RowKind.NEW_THIS_WEEK || row.kind == RowKind.READY_TO_WATCH,
                            modifier = Modifier.endOfRow(i == row.shows.lastIndex)
                                .then(if (first) Modifier.focusRequester(firstFocus) else Modifier)) { onShow(show) }
                    }
                }
            }
        }
        item(key = "footer") {
            Text(footer, color = Palette.Muted, fontSize = 13.sp)
        }
    }
}

fun newLabel(count: Int) = if (count == 1) "1 new episode" else "$count new episodes"

@Composable
fun SectionTitle(text: String) {
    Text(text, color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Hero(
    show: Show,
    alsoSoon: List<Show>,
    installed: List<Service>,
    now: Instant,
    zone: ZoneId,
    firstFocus: FocusRequester,
    onShown: suspend () -> Unit,
    onShow: (Show) -> Unit,
) {
    val context = LocalContext.current
    val air = show.nextAirInstant()
    val ep = show.nextEpisode
    val openWith = Services.forPlatform(show.platform).firstOrNull { it in installed }
    // Focus the primary button once it exists, so the screen opens at the top on the banner.
    LaunchedEffect(show.id) { runCatching { firstFocus.requestFocus() }; onShown() }

    Row(
        Modifier.fillMaxWidth().background(Palette.Panel, RoundedCornerShape(18.dp)).padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(show, Modifier.size(84.dp, 120.dp).background(Palette.Raised, RoundedCornerShape(10.dp)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "NEXT UP" + (air?.let { " · " + airLabel(it, now, zone).uppercase() } ?: ""),
                color = Palette.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
            )
            Text(show.title, color = Palette.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(ep?.code?.takeIf { it.isNotBlank() }, ep?.name?.takeIf { it != "TBA" }, show.platform)
                    .joinToString(" · "),
                color = Palette.Muted, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (openWith != null) {
                    Pill("Open ${openWith.name}", onClick = { launchApp(context, openWith.packageName) },
                        selected = true, modifier = Modifier.focusRequester(firstFocus))
                    Pill("Show details", onClick = { onShow(show) })
                } else {
                    Pill("Show details", onClick = { onShow(show) }, selected = true,
                        modifier = Modifier.focusRequester(firstFocus))
                }
            }
        }
        if (alsoSoon.isNotEmpty()) {
            Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ALSO COMING UP", color = Palette.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                alsoSoon.forEach { s ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s.title, color = Palette.Text, fontSize = 15.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(s.nextAirInstant()?.let { airLabel(it, now, zone) } ?: "", color = Palette.Muted, fontSize = 15.sp,
                            modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
        }
    }
}
