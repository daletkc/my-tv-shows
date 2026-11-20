package com.dalethomas.mytvshows.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.dalethomas.mytvshows.data.Poster
import com.dalethomas.mytvshows.data.Show
import com.dalethomas.mytvshows.data.TvMaze
import com.dalethomas.mytvshows.data.TvMazeApi
import com.dalethomas.mytvshows.domain.Services
import kotlinx.coroutines.launch

/** Search TVMaze, pick a show, pick where you watch it. */
@Composable
fun AddShowScreen(
    trackedMazeIds: Set<Long>,
    onAdd: (TvMazeApi.MazeShow, String) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<TvMazeApi.MazeShow>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<TvMazeApi.MazeShow?>(null) }
    var fieldFocused by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val pickFocus = remember { FocusRequester() }
    val resultsFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
    LaunchedEffect(picked) { if (picked != null) runCatching { pickFocus.requestFocus() } }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        picked = null
        status = "Searching..."
        scope.launch {
            runCatching { TvMazeApi.search(q) }
                .onSuccess { results = it; status = if (it.isEmpty()) "No shows found for \"$q\"." else null }
                .onFailure { status = "Search failed. Check the connection and try again." }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Palette.Ground).padding(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Add a show", color = Palette.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.width(520.dp)
                    .background(Palette.Raised, RoundedCornerShape(12.dp))
                    .border(3.dp, if (fieldFocused) Palette.Accent else Palette.Line, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                if (query.isEmpty()) Text("Type a title, or use the mic on the remote", color = Palette.Muted, fontSize = 17.sp)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Palette.Text, fontSize = 17.sp),
                    cursorBrush = SolidColor(Palette.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    modifier = Modifier.width(488.dp)
                        .focusRequester(fieldFocus)
                        .onFocusChanged { fieldFocused = it.isFocused }
                        // Text fields keep the D-pad by default; let Right/Down leave it.
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
                                // Down goes to the first result, not the one nearest the cursor.
                                Key.DirectionDown -> if (results.isNotEmpty() && picked == null) {
                                    runCatching { resultsFocus.requestFocus() }.isSuccess
                                } else focusManager.moveFocus(FocusDirection.Down)
                                Key.DirectionRight -> focusManager.moveFocus(FocusDirection.Right)
                                else -> false
                            }
                        },
                )
            }
            Pill("Search", onClick = { search() }, selected = true)
            Pill("Back", onClick = onBack)
        }
        status?.let { Text(it, color = Palette.Muted, fontSize = 16.sp) }

        val chosen = picked
        if (chosen != null) {
            WherePicker(chosen, pickFocus, onPick = { onAdd(chosen, it) }, onCancel = { picked = null })
        } else if (results.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                itemsIndexed(results, key = { _, r -> r.id }) { i, r ->
                    val already = r.id in trackedMazeIds
                    PosterCard(
                        show = r.asPreviewShow(),
                        caption = if (already) "Already tracked" else listOfNotNull(r.year, r.channel).joinToString(" · "),
                        captionHighlighted = already,
                        modifier = Modifier.endOfRow(i == results.lastIndex)
                            .then(if (i == 0) Modifier.focusRequester(resultsFocus) else Modifier),
                    ) { if (!already) picked = r }
                }
            }
        }
        Text("Search by TVMaze (CC BY-SA)", color = Palette.Muted, fontSize = 13.sp)
    }
}

@Composable
private fun WherePicker(show: TvMazeApi.MazeShow, firstFocus: FocusRequester, onPick: (String) -> Unit, onCancel: () -> Unit) {
    val guessed = Services.forPlatform(show.channel.orEmpty())
    val ordered = guessed + (Services.all - guessed.toSet())
    Column(
        Modifier.background(Palette.Panel, RoundedCornerShape(16.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Poster(show.asPreviewShow(), Modifier.size(70.dp, 100.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Where do you watch ${show.name}?", color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(show.year, show.channel, show.status).joinToString(" · "), color = Palette.Muted, fontSize = 15.sp)
            }
        }
        // Horizontal padding leaves room for the focused tile's zoom.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
            itemsIndexed(ordered, key = { _, s -> s.packageName }) { i, service ->
                AppTile(service, onClick = { onPick(service.name) },
                    modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier)
            }
            show.channel?.takeIf { guessed.isEmpty() }?.let { ch ->
                item(key = "other") { Pill(ch, onClick = { onPick(ch) }) }
            }
            item(key = "cancel") { Pill("Cancel", onClick = onCancel, modifier = Modifier.endOfRow(true)) }
        }
    }
}

/** A minimal Show so search results can reuse the poster components. */
private fun TvMazeApi.MazeShow.asPreviewShow() = Show(
    id = "maze-$id",
    title = name,
    tvmaze = TvMaze(id = id, name = name, poster = posterUrl?.let { Poster(medium = it) }),
)
