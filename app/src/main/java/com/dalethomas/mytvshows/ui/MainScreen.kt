package com.dalethomas.mytvshows.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.dalethomas.mytvshows.data.Show
import com.dalethomas.mytvshows.domain.Services
import com.dalethomas.mytvshows.domain.groupByService
import com.dalethomas.mytvshows.domain.groupByDay
import com.dalethomas.mytvshows.domain.groupRows
import com.dalethomas.mytvshows.domain.unwatchedCounts
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Tab(val label: String) { HOME("Home"), SERVICES("By service"), WEEK("This week") }

@Composable
fun MainScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val loaded by vm.loaded.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }
    val firstFocus = remember { FocusRequester() }

    // Labels like "Tonight 8:01 PM" are relative, so re-render every minute.
    val now by produceState(Instant.now()) {
        while (true) { delay(60_000); value = Instant.now() }
    }
    val showAllApps by vm.showAllApps.collectAsState()
    val installed = remember(showAllApps) { Services.all.filter { showAllApps || isInstalled(context, it.packageName) } }
    val shows by vm.shows.collectAsState()
    val userData by vm.userData.collectAsState()
    val episodes by vm.episodes.collectAsState()
    val unwatched = remember(shows, userData, episodes, now) { unwatchedCounts(shows, userData.watched, episodes, now) }
    val selected: Show? = shows.firstOrNull { it.id == selectedId }

    BackHandler(enabled = selected != null || adding) { if (selected != null) selectedId = null else adding = false }

    LaunchedEffect(selected?.id) { selected?.let { vm.loadEpisodes(it) } }
    LaunchedEffect(loaded, tab, selected == null, adding) {
        if (loaded && selected == null && !adding && tab == Tab.HOME) runCatching { firstFocus.requestFocus() }
    }

    val clock = DateTimeFormatter.ofPattern("EEE h:mm a", Locale.US)
    val footer = "Your list is saved on this TV · Data from TVMaze (CC BY-SA)"

    Box(Modifier.fillMaxSize().background(Palette.Ground)) {
        if (selected != null) {
            val addedId = userData.added.firstOrNull { it.showId == selected.id }?.tvmazeId
            DetailScreen(
                show = selected,
                installed = installed,
                episodes = selected.tvmaze?.id?.let { episodes[it] },
                mark = userData.watched[selected.id],
                now = now,
                zone = zone,
                onSetWatched = { vm.setWatched(selected, it) },
                status = addedId?.let { id -> userData.added.first { it.tvmazeId == id }.status },
                onSetStatus = { s -> addedId?.let { vm.setStatus(it, s) } },
                onRemove = addedId?.let { id -> { vm.removeShow(id); selectedId = null } },
                onBack = { selectedId = null },
            )
        } else if (adding) {
            AddShowScreen(
                trackedMazeIds = shows.mapNotNull { it.tvmaze?.id }.toSet(),
                onAdd = { show, platform -> vm.addShow(show, platform); adding = false; tab = Tab.HOME },
                onBack = { adding = false },
            )
        } else Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("My TV Shows", color = Palette.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 16.dp))
                Tab.entries.forEach { t -> Pill(t.label, onClick = { tab = t }, selected = tab == t) }
                Pill("+ Add", onClick = { adding = true })
                Box(Modifier.weight(1f))
                Text(now.atZone(zone).format(clock), color = Palette.Muted, fontSize = 14.sp)
                Pill(if (refreshing) "Refreshing..." else "Refresh", onClick = { vm.refresh() })
            }

            when {
                !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading your shows...", color = Palette.Muted, fontSize = 20.sp)
                }
                tab == Tab.HOME -> HomeScreen(groupRows(shows, now, unwatched), installed, now, zone, unwatched, footer, firstFocus,
                    onAdd = { adding = true }) { selectedId = it.id }
                tab == Tab.SERVICES -> ServicesScreen(groupByService(shows, now, unwatched), now, zone, unwatched, footer) { selectedId = it.id }
                else -> WeekScreen(groupByDay(shows, episodes, now, zone), zone, footer) { selectedId = it.id }
            }
        }
    }
}
