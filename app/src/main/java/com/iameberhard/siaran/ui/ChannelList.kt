package com.iameberhard.siaran.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.iameberhard.siaran.data.Channel

/** What a list needs from the screen around it. */
class ChannelActions(
    val nowPlayingUrl: String?,
    val isFavorite: (Channel) -> Boolean,
    val onToggleFavorite: (Channel) -> Unit,
    val onPlay: (Channel, List<Channel>) -> Unit,
)

@Composable
fun ChannelList(
    channels: List<Channel>,
    actions: ChannelActions,
    modifier: Modifier = Modifier,
    empty: String = "No channels",
    header: (LazyListScope.() -> Unit)? = null,
) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        header?.invoke(this)
        if (channels.isEmpty()) {
            item {
                Text(
                    empty,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
        }
        channelItems(channels, actions)
    }
}

fun LazyListScope.channelItems(channels: List<Channel>, actions: ChannelActions, keyPrefix: String = "") {
    // Index in the key: the playlists occasionally list the same stream twice, and
    // duplicate keys crash a LazyColumn.
    itemsIndexed(channels, key = { i, ch -> "$keyPrefix$i|${ch.url}" }) { _, ch ->
        ChannelRow(
            channel = ch,
            playing = ch.url == actions.nowPlayingUrl,
            favorite = actions.isFavorite(ch),
            onToggleFavorite = { actions.onToggleFavorite(ch) },
            onClick = { actions.onPlay(ch, channels) },
        )
    }
}

@Composable
fun ChannelRow(
    channel: Channel,
    playing: Boolean,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (playing) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(channel, Modifier.size(width = 56.dp, height = 40.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                channel.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
                color = if (playing) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details = listOfNotNull(
                channel.quality,
                channel.groups.take(2).joinToString(" · ").ifBlank { null },
                if (channel.notAlwaysOn) "Not 24/7" else null,
                if (channel.geoBlocked) "Geo-blocked" else null,
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = if (favorite) "Remove from favourites" else "Add to favourites",
                tint = if (favorite) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ChannelLogo(channel: Channel, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val initials: @Composable () -> Unit = {
            Text(
                channel.displayName.split(' ').filter { it.isNotBlank() }.take(2)
                    .joinToString("") { it.take(1) }.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (channel.logo == null) {
            initials()
        } else {
            SubcomposeAsyncImage(
                model = channel.logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                error = { initials() },
                loading = { initials() },
            )
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

