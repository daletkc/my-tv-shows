package com.dalethomas.mytvshows.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.dalethomas.mytvshows.data.Show
import com.dalethomas.mytvshows.domain.ServiceColumn
import com.dalethomas.mytvshows.domain.airLabel
import com.dalethomas.mytvshows.domain.nextAirInstant
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Mockup C: one column per streaming app with the tracked shows found on it. */
@Composable
fun ServicesScreen(
    columns: List<ServiceColumn>,
    now: Instant,
    zone: ZoneId,
    unwatched: Map<String, Int>,
    footer: String,
    onShow: (Show) -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pick a service to open it. Unwatched and this week's new episodes are in amber.", color = Palette.Muted, fontSize = 14.sp)
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            itemsIndexed(columns, key = { _, c -> c.service.packageName }) { ci, col ->
                val lastCol = ci == columns.lastIndex
                Column(
                    Modifier.width(164.dp).fillMaxHeight().background(Palette.Panel, RoundedCornerShape(16.dp)).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppTile(col.service, onClick = { launchApp(context, col.service.packageName) }, width = 148.dp, height = 56.dp,
                        modifier = Modifier.endOfRow(lastCol))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(col.shows, key = { it.id }) { show ->
                            ServiceShowRow(show, now, zone, unwatched[show.id] ?: 0, Modifier.endOfRow(lastCol)) { onShow(show) }
                        }
                    }
                }
            }
        }
        Text(footer, color = Palette.Muted, fontSize = 13.sp)
    }
}

@Composable
private fun ServiceShowRow(show: Show, now: Instant, zone: ZoneId, newCount: Int, modifier: Modifier, onClick: () -> Unit) {
    val air = show.nextAirInstant()
    val isNew = newCount > 0 || (air != null && !air.isBefore(now) && air.isBefore(now.plus(Duration.ofDays(7))))
    val tag = when {
        newCount > 0 -> newLabel(newCount)
        air != null && !air.isBefore(now) -> airLabel(air, now, zone)
        show.kind == "recommended" -> "Recommended"
        else -> show.status
    }
    FocusSurface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(10.dp), focusedContainer = Palette.Raised) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Poster(show, Modifier.size(30.dp, 43.dp))
            Column(Modifier.width(100.dp)) {
                Text(show.title, color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tag, color = if (isNew) Palette.Accent else Palette.Muted, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
