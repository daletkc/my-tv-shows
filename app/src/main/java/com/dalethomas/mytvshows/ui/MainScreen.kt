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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Text
import com.dalethomas.mytvshows.data.InstalledApps
import com.dalethomas.mytvshows.data.Show
import com.dalethomas.mytvshows.data.ShowStatus
import com.dalethomas.mytvshows.domain.Services
import com.dalethomas.mytvshows.domain.groupByDay
import com.dalethomas.mytvshows.domain.groupByService
import com.dalethomas.mytvshows.domain.groupRows
import com.dalethomas.mytvshows.domain.unwatchedCounts
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Tab(val label: String) { HOME("Home"), SERVICES("By service"), UPCOMING("Coming up") }

@Composable
fun MainScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val loaded by vm.loaded.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var heroId by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var changingAppFor by rememberSaveable { mutableStateOf<Long?>(null) }
    val zone = remember { ZoneId.systemDefault() }
    val firstFocus = remember { FocusRequester() }

    // Labels like "Today" are relative, so re-render every minute.
    val now by produceState(Instant.now()) {
        while (true) { delay(60_000); value = Instant.now() }
    }
    val showAllApps by vm.showAllApps.collectAsState()
    // Every app on the TV (debug demo mode pretends all recognized services are installed).
    val installed = remember(showAllApps) { if (showAllApps) Services.all else InstalledApps.scan(context) }
    val shows by vm.shows.collectAsState()
    val userData by vm.userData.collectAsState()
    val episodes by vm.episodes.collectAsState()
    val backdrops by vm.backdrops.collectAsState()
    val unwatched = remember(shows, userData, episodes, now) { unwatchedCounts(shows, userData.watched, episodes, now) }
    val rows = remember(shows, now, unwatched) { groupRows(shows, now, unwatched) }
    // Apps stay hidden until a show on the list is watched there.
    val usedApps = remember(installed, shows) { installed.filter { app -> shows.any { app in Services.forShow(it, listOf(app)) } } }
    val selected: Show? = shows.firstOrNull { it.id == selectedId }
    val hero: Show? = rows.flatMap { it.shows }.let { all -> all.firstOrNull { it.id == heroId } ?: all.firstOrNull() }

    BackHandler(enabled = selected != null || adding) {
        when {
            changingAppFor != null -> changingAppFor = null
            selected != null -> selectedId = null
            else -> adding = false
        }
    }

    LaunchedEffect(selected?.id) { selected?.let { vm.loadEpisodes(it); vm.loadBackdrops(listOf(it)) } }
    // Focus moves quickly along a row; only fetch art and episodes once it settles on a show.
    LaunchedEffect(hero?.id) { hero?.let { delay(300); vm.loadBackdrops(listOf(it)); vm.loadEpisodes(it) } }
    LaunchedEffect(loaded, tab, selected == null, adding, hero == null) {
        if (loaded && selected == null && !adding && tab == Tab.HOME) runCatching { firstFocus.requestFocus() }
    }

    val clock = DateTimeFormatter.ofPattern("EEE h:mm a", Locale.US)
    val footer = "Your list is saved on this TV · Data from TVMaze (CC BY-SA)"

    ProvideTextStyle(TextStyle(fontFamily = Manrope)) {
        Box(Modifier.fillMaxSize()) {
            val changing = changingAppFor?.let { id -> userData.added.firstOrNull { it.tvmazeId == id } }
            if (selected != null && changing != null) {
                val current = installed.filter { it.packageName == changing.appPackage }
                Box(Modifier.fillMaxSize().background(Palette.Ground).padding(horizontal = 48.dp, vertical = 40.dp)) {
                    val pickFocus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { runCatching { pickFocus.requestFocus() } }
                    AppPicker(
                        show = selected,
                        meta = "Now: ${changing.platform}",
                        question = "Which app do you watch it on?",
                        apps = current + (installed - current.toSet()),
                        firstFocus = pickFocus,
                        onPick = { vm.setApp(changing.tvmazeId, it); changingAppFor = null },
                        onCancel = { changingAppFor = null },
                    )
                }
            } else if (selected != null) {
                val entry = userData.added.firstOrNull { it.showId == selected.id }
                DetailScreen(
                    show = selected,
                    installed = installed,
                    episodes = selected.tvmaze?.id?.let { episodes[it] },
                    mark = userData.watched[selected.id],
                    backdrop = selected.tvmaze?.id?.let { backdrops[it] },
                    now = now,
                    zone = zone,
                    onSetWatched = { vm.setWatched(selected, it) },
                    onRemove = entry?.let { e -> { vm.removeShow(e.tvmazeId); selectedId = null } },
                    onBack = { selectedId = null },
                    extra = {
                        if (entry != null) {
                            WatchOn(entry.platform) { changingAppFor = entry.tvmazeId }
                            StatusPicker(entry.status) { vm.setStatus(entry.tvmazeId, it) }
                        }
                    },
                )
            } else if (adding) {
                AddShowScreen(
                    trackedMazeIds = shows.mapNotNull { it.tvmaze?.id }.toSet(),
                    installed = installed,
                    onAdd = { show, app -> vm.addShow(show, app); adding = false; tab = Tab.HOME },
                    onBack = { adding = false },
                )
            } else {
                CinematicBackdrop(if (tab == Tab.HOME) hero?.tvmaze?.id?.let { backdrops[it] } else null)
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 18.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("My TV Shows", color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(end = 18.dp))
                        Tab.entries.forEach { t -> TopTab(t.label, selected = tab == t, onClick = { tab = t }) }
                        Box(Modifier.weight(1f))
                        Pill("+ Add", onClick = { adding = true })
                        Pill(if (refreshing) "Refreshing..." else "Refresh", onClick = { vm.refresh() })
                        Text(now.atZone(zone).format(clock), color = Palette.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp))
                    }

                    when {
                        !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Loading your shows...", color = Palette.Muted, fontSize = 16.sp)
                        }
                        tab == Tab.HOME -> HomeScreen(
                            rows = rows,
                            hero = hero,
                            installed = usedApps,
                            now = now,
                            zone = zone,
                            unwatched = unwatched,
                            episodes = episodes,
                            watched = userData.watched,
                            footer = footer,
                            firstFocus = firstFocus,
                            onHero = { heroId = it.id },
                            onSetWatched = { show, mark -> vm.setWatched(show, mark) },
                            onAdd = { adding = true },
                            onShow = { selectedId = it.id },
                        )
                        tab == Tab.SERVICES -> ServicesScreen(groupByService(shows, now, unwatched, others = usedApps), now, zone, unwatched, footer) { selectedId = it.id }
                        else -> ComingUpScreen(
                            days = groupByDay(shows, episodes, now, zone),
                            backdrops = backdrops,
                            now = now,
                            zone = zone,
                            footer = footer,
                            onVisible = { vm.loadBackdrops(it) },
                            onShow = { selectedId = it.id },
                        )
                    }
                }
            }
        }
    }
}

/** The app this show opens in, with a way to change it. */
@Composable
private fun WatchOn(appName: String, onChange: () -> Unit) {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SectionTitle("WATCH ON")
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(appName, color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Pill("Change app", onClick = onChange)
        }
    }
}

/** Where the show stands on this TV's list; picks the row it appears in. */
@Composable
private fun StatusPicker(current: String, onPick: (ShowStatus) -> Unit) {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SectionTitle("STATUS")
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ShowStatus.entries.forEach { s -> Pill(s.label, selected = s.id == current, onClick = { onPick(s) }) }
        }
    }
}
