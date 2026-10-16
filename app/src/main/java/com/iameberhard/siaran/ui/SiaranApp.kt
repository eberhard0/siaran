@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.iameberhard.siaran.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iameberhard.siaran.AppViewModel
import com.iameberhard.siaran.CatalogState
import com.iameberhard.siaran.data.Catalog
import com.iameberhard.siaran.data.Channel

private enum class Tab(val label: String, val icon: ImageVector) {
    FAVORITES("Favourites", Icons.Filled.Star),
    HOME("Home", Icons.Filled.Home),
    BROWSE("Browse", Icons.Filled.Public),
    SEARCH("Search", Icons.Filled.Search),
}

/**
 * Cover screen: video on top, channels below, tabs at the bottom.
 * Unfolded (or landscape): channels on the left, video on the right.
 */
@Composable
fun SiaranApp(
    vm: AppViewModel,
    inPictureInPicture: Boolean,
    fullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
) {
    val catalogState by vm.catalog.collectAsStateWithLifecycle()
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    val favorites by vm.library.favorites.collectAsStateWithLifecycle()
    // Open on Favourites once there are some; until then Home has something to pick.
    var tab by rememberSaveable { mutableStateOf(if (favorites.isEmpty()) Tab.HOME else Tab.FAVORITES) }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    var showSettings by remember { mutableStateOf(false) }

    val actions = ChannelActions(
        nowPlayingUrl = nowPlaying?.channel?.url,
        isFavorite = { ch -> favorites.any { it.url == ch.url } },
        onToggleFavorite = vm.library::toggleFavorite,
        onPlay = vm::play,
    )

    val playing = nowPlaying

    // Picture-in-picture and fullscreen: the video and nothing else.
    if (playing != null && (inPictureInPicture || fullscreen)) {
        BackHandler(enabled = fullscreen) { onFullscreenChange(false) }
        VideoSurface(
            player = vm.player,
            nowPlaying = playing,
            showControls = !inPictureInPicture,
            onToggleFullscreen = { onFullscreenChange(false) },
            onRetry = vm::retry,
            onNext = vm::next,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val video: @Composable (Modifier) -> Unit = { modifier ->
        if (playing != null) {
            Column(modifier) {
                VideoSurface(
                    player = vm.player,
                    nowPlaying = playing,
                    showControls = true,
                    onToggleFullscreen = { onFullscreenChange(true) },
                    onRetry = vm::retry,
                    onNext = vm::next,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
                NowPlayingBar(
                    nowPlaying = playing,
                    favorite = favorites.any { it.url == playing.channel.url },
                    onToggleFavorite = { vm.library.toggleFavorite(playing.channel) },
                    onPrevious = vm::previous,
                    onNext = vm::next,
                    onFullscreen = { onFullscreenChange(true) },
                    onClose = vm::stop,
                )
            }
        }
    }

    val content: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier) {
            when (val state = catalogState) {
                CatalogState.Loading -> Loading()
                is CatalogState.Failed -> LoadFailed(state.message) { vm.loadCatalog(force = true) }
                is CatalogState.Ready -> Column {
                    if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                    when (tab) {
                        Tab.HOME -> HomeTab(state.catalog, vm, actions, onOpenSettings = { showSettings = true })
                        Tab.BROWSE -> BrowseTab(state.catalog, actions)
                        Tab.SEARCH -> SearchTab(state.catalog, actions)
                        Tab.FAVORITES -> FavoritesTab(favorites, actions, onOpenSettings = { showSettings = true })
                    }
                }
            }
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        if (wide) {
            Row(Modifier.fillMaxSize().systemBarsPadding()) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    Spacer(Modifier.height(8.dp))
                    Tab.entries.forEach { t ->
                        NavigationRailItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
                content(Modifier.weight(0.42f).fillMaxHeight())
                VerticalDivider()
                Box(Modifier.weight(0.58f).fillMaxHeight()) {
                    if (playing != null) {
                        video(Modifier.fillMaxWidth())
                    } else {
                        Text(
                            "Pick a channel to start watching",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                video(Modifier.fillMaxWidth())
                content(Modifier.weight(1f).fillMaxWidth())
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(vm = vm, onDismiss = { showSettings = false })
    }
}

@Composable
private fun Loading() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(
            "Downloading the channel list…",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun LoadFailed(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Couldn't load channels", style = MaterialTheme.typography.titleMedium)
        Text(
            message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Button(onClick = onRetry) { Text("Try again") }
    }
}

/** The home country's channels, filterable by category. */
@Composable
private fun HomeTab(
    catalog: Catalog,
    vm: AppViewModel,
    actions: ChannelActions,
    onOpenSettings: () -> Unit,
) {
    var country by remember { mutableStateOf(vm.library.homeCountry) }
    var pickCountry by remember { mutableStateOf(false) }
    var category by rememberSaveable(country) { mutableStateOf<String?>(null) }

    val channels = catalog.byCountry[country].orEmpty()
    val categories = remember(catalog, country) {
        channels.flatMap { catalog.categoriesOf[it.url].orEmpty() }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }
            .filter { it != "Undefined" }
    }
    val shown = remember(channels, category) {
        if (category == null) channels
        else channels.filter { catalog.categoriesOf[it.url].orEmpty().contains(category) }
    }

    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${catalog.flag(country)} $country".trim(),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { pickCountry = true }) { Text("Change") }
            IconButton(onClick = { vm.loadCatalog(force = true) }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh channel list")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                FilterChip(
                    selected = category == null,
                    onClick = { category = null },
                    label = { Text("All ${channels.size}") },
                )
            }
            items(categories) { c ->
                FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
            }
        }
        ChannelList(shown, actions, empty = "No channels listed for $country")
    }

    if (pickCountry) {
        CountryPicker(
            catalog = catalog,
            onPick = {
                country = it
                vm.library.homeCountry = it
                pickCountry = false
            },
            onDismiss = { pickCountry = false },
        )
    }
}

@Composable
private fun CountryPicker(catalog: Catalog, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val countries = remember(catalog) { catalog.byCountry.keys.sorted() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Home country") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search countries") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.height(360.dp)) {
                    items(countries.filter { it.contains(query.trim(), ignoreCase = true) }) { c ->
                        Text(
                            "${catalog.flag(c)}  $c  (${catalog.byCountry[c]?.size ?: 0})",
                            modifier = Modifier.fillMaxWidth().clickable { onPick(c) }.padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Every country and category, each opening its channel list. */
@Composable
private fun BrowseTab(catalog: Catalog, actions: ChannelActions) {
    var byCountry by rememberSaveable { mutableStateOf(true) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    val selected = open
    if (selected != null) {
        BackHandler { open = null }
        val channels = (if (byCountry) catalog.byCountry else catalog.byCategory)[selected].orEmpty()
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                IconButton(onClick = { open = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    (if (byCountry) "${catalog.flag(selected)} " else "") + selected,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ChannelList(channels, actions)
        }
        return
    }

    val groups = remember(catalog, byCountry) {
        val map = if (byCountry) catalog.byCountry else catalog.byCategory
        map.entries.sortedBy { it.key }.map { it.key to it.value.size }
    }
    Column {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            SegmentedButton(
                selected = byCountry,
                onClick = { byCountry = true },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("Countries") }
            SegmentedButton(
                selected = !byCountry,
                onClick = { byCountry = false },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("Categories") }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            items(groups, key = { it.first }) { (name, count) ->
                Row(
                    Modifier.fillMaxWidth().clickable { open = name }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (byCountry) {
                        Text(catalog.flag(name).ifEmpty { "🌐" })
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$count", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}

@Composable
private fun SearchTab(catalog: Catalog, actions: ChannelActions) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val results = remember(catalog, q) {
        if (q.length < 2) emptyList()
        else catalog.all.filter { it.displayName.contains(q, ignoreCase = true) }.take(500)
    }
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search ${catalog.all.size} channels") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        ChannelList(
            results,
            actions,
            empty = if (q.length < 2) "Type a channel name, e.g. Kompas, BBC, Al Jazeera" else "Nothing found for “$q”",
        )
    }
}

/** Only the starred channels, in the order they were added. */
@Composable
private fun FavoritesTab(favorites: List<Channel>, actions: ChannelActions, onOpenSettings: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Favourites",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        }
        ChannelList(
            favorites,
            actions,
            empty = "No favourites yet. Tap ☆ next to any channel and it will appear here.",
        )
    }
}

@Composable
private fun SettingsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var pip by remember { mutableStateOf(vm.library.pipEnabled) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Row(
                Modifier.fillMaxWidth().clickable {
                    pip = !pip
                    vm.library.pipEnabled = pip
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Floating window", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (pip) "Leaving the app keeps the channel playing in a small window."
                        else "Leaving the app stops the channel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = pip,
                    onCheckedChange = {
                        pip = it
                        vm.library.pipEnabled = it
                    },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
