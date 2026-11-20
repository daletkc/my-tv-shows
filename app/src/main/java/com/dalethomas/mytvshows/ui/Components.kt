package com.dalethomas.mytvshows.ui

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.dalethomas.mytvshows.data.Show
import com.dalethomas.mytvshows.domain.Service

object Palette {
    val Ground = Color(0xFF0E1116)
    val Panel = Color(0xFF161B22)
    val Raised = Color(0xFF262D38)
    val Line = Color(0xFF2A313C)
    val Text = Color(0xFFF2F4F7)
    val Muted = Color(0xFFA3ACB9)
    val Accent = Color(0xFFF5B544)
}

/** A focusable surface with the app's amber focus ring and a small zoom. */
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    container: Color = Color.Transparent,
    focusedContainer: Color = container,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            focusedContainerColor = focusedContainer,
            pressedContainerColor = focusedContainer,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Palette.Accent), shape = shape)),
    ) { content() }
}

@Composable
fun Pill(label: String, onClick: () -> Unit, selected: Boolean = false, modifier: Modifier = Modifier) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        container = if (selected) Palette.Text else Palette.Raised,
        focusedContainer = if (selected) Palette.Text else Color(0xFF3A4352),
    ) {
        Text(
            label,
            color = if (selected) Palette.Ground else Palette.Text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun AppTile(
    service: Service,
    onClick: () -> Unit,
    width: Dp = 112.dp,
    height: Dp = 64.dp,
    modifier: Modifier = Modifier,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.size(width, height),
        container = Color(service.background),
    ) {
        Box(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.Center) {
            Text(
                service.name,
                color = Color(service.foreground),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Poster from TVMaze, or a title card with initials when the feed has no poster. */
@Composable
fun Poster(show: Show, modifier: Modifier) {
    val url = show.posterUrl
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = "${show.title} poster",
            contentScale = ContentScale.Crop,
            modifier = modifier.background(Palette.Raised),
        )
    } else {
        Box(modifier.background(Palette.Raised), contentAlignment = Alignment.Center) {
            Text(
                show.title.split(" ").filter { it.firstOrNull()?.isUpperCase() == true }.take(2)
                    .joinToString("") { it.first().toString() },
                color = Palette.Text,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun PosterCard(
    show: Show,
    caption: String?,
    captionHighlighted: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(Modifier.width(118.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        FocusSurface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(10.dp)) {
            Poster(show, Modifier.size(118.dp, 168.dp))
        }
        Text(show.title, color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (caption != null) {
            Text(caption, color = if (captionHighlighted) Palette.Accent else Palette.Muted, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Pressing Right on the last item of a row stays put instead of jumping to the top bar. */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.endOfRow(isLast: Boolean): Modifier =
    if (isLast) focusProperties { right = FocusRequester.Cancel } else this

/** Launches an installed app's TV launcher activity. Returns false if it is not installed. */
fun launchApp(context: Context, packageName: String): Boolean {
    val intent = context.packageManager.getLeanbackLaunchIntentForPackage(packageName)
        ?: context.packageManager.getLaunchIntentForPackage(packageName)
        ?: return false
    context.startActivity(intent)
    return true
}

/**
 * Opens Google TV's search for a show title. Its results page links straight into the show
 * in each streaming app, which the apps' own deep links can't do without their internal ids.
 */
fun searchGoogleTv(context: Context, query: String): Boolean {
    val intent = Intent("android.search.action.GLOBAL_SEARCH")
        .putExtra(SearchManager.QUERY, query)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(intent) }.isSuccess
}

fun isInstalled(context: Context, packageName: String): Boolean =
    context.packageManager.getLeanbackLaunchIntentForPackage(packageName) != null ||
        context.packageManager.getLaunchIntentForPackage(packageName) != null
