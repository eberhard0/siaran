@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.iameberhard.siaran.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.iameberhard.siaran.NowPlaying

/** The video itself, with an overlay for buffering and errors. */
@Composable
fun VideoSurface(
    player: ExoPlayer,
    nowPlaying: NowPlaying,
    showControls: Boolean,
    onToggleFullscreen: () -> Unit,
    onRetry: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowFastForwardButton(false)
                    setShowRewindButton(false)
                    setFullscreenButtonClickListener { onToggleFullscreen() }
                }
            },
            update = { view ->
                view.player = player
                view.useController = showControls
                view.keepScreenOn = nowPlaying.error == null
            },
            modifier = Modifier.fillMaxSize(),
        )

        when {
            nowPlaying.error != null -> Column(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Can't play ${nowPlaying.channel.displayName}",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    nowPlaying.error,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRetry) { Text("Try again") }
                    Button(onClick = onNext) { Text("Next channel") }
                }
            }

            nowPlaying.buffering -> CircularProgressIndicator(color = Color.White)
        }
    }
}

/** Title, favourite, previous / next and fullscreen under the video. */
@Composable
fun NowPlayingBar(
    nowPlaying: NowPlaying,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFullscreen: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(nowPlaying.channel, Modifier.size(width = 44.dp, height = 32.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                nowPlaying.channel.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(
                if (nowPlaying.error == null && !nowPlaying.buffering) "● Live" else null,
                nowPlaying.channel.quality,
                nowPlaying.channel.groups.firstOrNull(),
            ).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (sub.startsWith("●")) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = "Favourite",
                tint = if (favorite) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onPrevious) { Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous channel") }
        IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, contentDescription = "Next channel") }
        IconButton(onClick = onFullscreen) { Icon(Icons.Filled.Fullscreen, contentDescription = "Full screen") }
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Stop") }
    }
}
