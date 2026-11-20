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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.dalethomas.mytvshows.data.Show
import com.dalethomas.mytvshows.domain.Airing
import com.dalethomas.mytvshows.domain.DayColumn
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

/** Seven columns, today first, with what airs each day. */
@Composable
fun WeekScreen(days: List<DayColumn>, zone: ZoneId, footer: String, onShow: (Show) -> Unit) {
    val today = days.firstOrNull()?.date
    Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("What airs this week. New episodes usually arrive on the streaming app the same night or the next day.",
            color = Palette.Muted, fontSize = 14.sp)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            days.forEachIndexed { i, day ->
                Column(
                    Modifier.weight(1f).fillMaxHeight().background(Palette.Panel, RoundedCornerShape(16.dp)).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(dayTitle(day.date, today), color = if (i == 0) Palette.Accent else Palette.Text,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(4.dp))
                    if (day.airings.isEmpty()) {
                        Text("Nothing new", color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.padding(4.dp))
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(day.airings, key = { it.show.id }) { airing ->
                            AiringRow(airing, zone, Modifier.endOfRow(i == days.lastIndex)) { onShow(airing.show) }
                        }
                    }
                }
            }
        }
        Text(footer, color = Palette.Muted, fontSize = 13.sp)
    }
}

private fun dayTitle(date: LocalDate, today: LocalDate?): String = when (date) {
    today -> "Today"
    today?.plusDays(1) -> "Tomorrow"
    else -> date.format(DAY)
}

@Composable
private fun AiringRow(airing: Airing, zone: ZoneId, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(10.dp), focusedContainer = Palette.Raised) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Poster(airing.show, Modifier.size(30.dp, 43.dp))
            Column {
                Text(airing.show.title, color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(airing.at.atZone(zone).format(TIME), color = Palette.Accent, fontSize = 12.sp, maxLines = 1)
                if (airing.episode.code.isNotBlank()) {
                    Text(airing.episode.code, color = Palette.Muted, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}
