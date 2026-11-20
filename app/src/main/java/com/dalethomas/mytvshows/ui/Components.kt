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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
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
import com.dalethomas.mytvshows.R
import com.dalethomas.mytvshows.data.Show
import com.dalethomas.mytvshows.domain.Service

object Palette {
    val Ground = Color(0xFF08090B)
    val Panel = Color(0xFF15171C)
    val Raised = Color(0xFF24262C)
    val Line = Color(0xFF34373E)
    val Text = Color(0xFFF4F1EA)
    val Muted = Color(0xFFA9A69F)
    val Accent = Color(0xFFF5B544)
    /** Translucent white for buttons over artwork. */
    val Glass = Color(0x29FFFFFF)
}

/** Manrope (SIL Open Font License), one variable font file at every weight the app uses. */
@OptIn(ExperimentalTextApi::class)
val Manrope = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(R.font.manrope, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

/** A focusable surface with a white focus ring and a small zoom. */
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
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.5.dp, Palette.Text), shape = shape)),
    ) { content() }
}

/**
 * Rounded button. Focus turns it white with dark text, the usual TV pattern. A selected
 * pill stays white (the primary action); large is the hero size.
 */
@Composable
fun Pill(
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Palette.Text else Palette.Glass,
            contentColor = if (selected) Palette.Ground else Palette.Text,
            focusedContainerColor = Palette.Text,
            focusedContentColor = Palette.Ground,
            pressedContainerColor = Palette.Text,
            pressedContentColor = Palette.Ground,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Text(
            label,
            fontSize = if (large) 12.sp else 11.sp,
            fontWeight = if (large) FontWeight.ExtraBold else FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = if (large) 20.dp else 16.dp, vertical = if (large) 11.dp else 7.dp),
        )
    }
}

/** Top-bar tab: plain text with an amber underline when selected; white when focused. */
@Composable
fun TopTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (selected) Palette.Text else Palette.Muted,
            focusedContainerColor = Palette.Text,
            focusedContentColor = Palette.Ground,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            Box(Modifier.padding(top = 3.dp).size(18.dp, 2.dp).background(if (selected) Palette.Accent else Color.Transparent, RoundedCornerShape(1.dp)))
        }
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

/** Poster for the Home rails. Focus moves the backdrop to this show; a badge over 0 shows a count. */
@Composable
fun PosterTile(
    show: Show,
    caption: String?,
    captionHighlighted: Boolean,
    badge: Int,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    Column(Modifier.width(96.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FocusSurface(
            onClick = onClick,
            modifier = modifier.onFocusChanged { if (it.isFocused || it.hasFocus) onFocus() },
            shape = RoundedCornerShape(8.dp),
        ) {
            Box {
                Poster(show, Modifier.size(96.dp, 144.dp))
                if (badge > 0) {
                    Text(
                        badge.toString(),
                        color = Color(0xFF1A1206),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.TopEnd).padding(5.dp)
                            .background(Palette.Accent, RoundedCornerShape(50))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
        Text(show.title, color = Palette.Text, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        if (caption != null) {
            Text(caption, color = if (captionHighlighted) Palette.Accent else Palette.Muted, fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
