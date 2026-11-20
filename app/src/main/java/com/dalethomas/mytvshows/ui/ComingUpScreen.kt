package com.dalethomas.mytvshows.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.dalethomas.mytvshows.data.Show
import com.dalethomas.mytvshows.domain.DayColumn
import com.dalethomas.mytvshows.domain.dayLabel
import java.time.Instant
import java.time.ZoneId

/** New episodes arriving in the next 7 days, in order, as artwork cards. Days only, no clock times. */
@Composable
fun ComingUpScreen(
    days: List<DayColumn>,
    backdrops: Map<Long, String>,
    now: Instant,
    zone: ZoneId,
    footer: String,
    onVisible: (List<Show>) -> Unit,
    onShow: (Show) -> Unit,
) {
    val airings = days.flatMap { it.airings }
    LaunchedEffect(airings.map { it.show.id }) { onVisible(airings.map { it.show }.distinctBy { it.id }) }

    if (airings.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nothing new in the next 7 days.", color = Palette.Muted, fontSize = 16.sp)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 12.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Text("New episodes arriving in the next 7 days, in order.", color = Palette.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        items(airings, key = { it.show.id + it.at }) { airing ->
            val art = airing.show.tvmaze?.id?.let { backdrops[it] }?.takeIf { it.isNotBlank() } ?: airing.show.posterUrl
            FocusSurface(onClick = { onShow(airing.show) }, shape = RoundedCornerShape(12.dp), container = Palette.Panel, focusedContainer = Palette.Raised) {
                Column(Modifier.fillMaxWidth()) {
                    AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Palette.Raised))
                    Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(dayLabel(airing.at, now, zone).uppercase(), color = Palette.Accent, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                        Text(airing.show.title, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOf(airing.episode.code, airing.show.platform).filter { it.isNotBlank() }.joinToString(" · "),
                            color = Palette.Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Text(footer, color = Palette.Muted, fontSize = 9.sp)
        }
    }
}
