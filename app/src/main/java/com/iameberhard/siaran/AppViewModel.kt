@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.iameberhard.siaran

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.iameberhard.siaran.data.Catalog
import com.iameberhard.siaran.data.CatalogRepository
import com.iameberhard.siaran.data.Channel
import com.iameberhard.siaran.data.Library
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

sealed interface CatalogState {
    data object Loading : CatalogState
    data class Ready(val catalog: Catalog, val refreshing: Boolean = false) : CatalogState
    data class Failed(val message: String) : CatalogState
}

data class NowPlaying(
    val channel: Channel,
    val buffering: Boolean = true,
    val error: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val repository = CatalogRepository(app, http)
    val library = Library(app)

    private val _catalog = MutableStateFlow<CatalogState>(CatalogState.Loading)
    val catalog: StateFlow<CatalogState> = _catalog.asStateFlow()

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    /** The list the current channel was picked from, for next / previous. */
    private var queue: List<Channel> = emptyList()

    /** True once this channel has been retried as HLS after an unknown-format failure. */
    private var retriedAsHls = false

    val player: ExoPlayer = ExoPlayer.Builder(app).build().apply {
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                _nowPlaying.value = _nowPlaying.value?.copy(buffering = state == Player.STATE_BUFFERING)
            }

            override fun onPlayerError(error: PlaybackException) {
                val current = _nowPlaying.value ?: return
                // Many streams have no file extension; the default guess is a plain
                // file, but most are HLS. Try that once before giving up.
                if (!retriedAsHls && guessMime(current.channel.url) == null) {
                    retriedAsHls = true
                    start(current.channel, MimeTypes.APPLICATION_M3U8)
                    return
                }
                _nowPlaying.value = current.copy(buffering = false, error = describe(error))
            }
        })
    }

    init {
        loadCatalog(force = false)
    }

    fun loadCatalog(force: Boolean) {
        val previous = (_catalog.value as? CatalogState.Ready)?.catalog
        _catalog.value = previous?.let { CatalogState.Ready(it, refreshing = true) } ?: CatalogState.Loading
        viewModelScope.launch {
            _catalog.value = try {
                CatalogState.Ready(repository.load(forceRefresh = force))
            } catch (e: Exception) {
                previous?.let { CatalogState.Ready(it) }
                    ?: CatalogState.Failed(e.message ?: "Couldn't download the channel list")
            }
        }
    }

    fun play(channel: Channel, from: List<Channel>) {
        queue = from
        retriedAsHls = false
        library.markWatched(channel)
        start(channel, guessMime(channel.url))
    }

    fun retry() {
        val current = _nowPlaying.value?.channel ?: return
        retriedAsHls = false
        start(current, guessMime(current.url))
    }

    fun next() = step(+1)
    fun previous() = step(-1)

    fun stop() {
        player.stop()
        player.clearMediaItems()
        _nowPlaying.value = null
    }

    private fun step(delta: Int) {
        val current = _nowPlaying.value?.channel ?: return
        if (queue.isEmpty()) return
        val i = queue.indexOfFirst { it.url == current.url }
        val next = queue[((if (i < 0) 0 else i + delta) + queue.size) % queue.size]
        play(next, queue)
    }

    private fun start(channel: Channel, mime: String?) {
        _nowPlaying.value = NowPlaying(channel)
        val headers = buildMap {
            channel.referrer?.let { ref ->
                put("Referer", ref)
                originOf(ref)?.let { put("Origin", it) }
            }
        }
        val dataSource = OkHttpDataSource.Factory(http)
            .setUserAgent(channel.userAgent ?: DEFAULT_USER_AGENT)
            .setDefaultRequestProperties(headers)
        val item = MediaItem.Builder()
            .setUri(channel.url)
            .apply { if (mime != null) setMimeType(mime) }
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(C.TIME_UNSET).build(),
            )
            .build()
        player.setMediaSource(DefaultMediaSourceFactory(dataSource).createMediaSource(item))
        player.prepare()
        player.playWhenReady = true
    }

    override fun onCleared() {
        player.release()
    }

    private companion object {
        // Some broadcasters reject requests that don't look like a browser.
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/130.0.0.0 Mobile Safari/537.36"

        fun guessMime(url: String): String? {
            val path = Uri.parse(url).path.orEmpty().lowercase()
            return when {
                path.endsWith(".m3u8") || url.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
                path.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
                else -> null
            }
        }

        fun originOf(url: String): String? = runCatching {
            val u = Uri.parse(url)
            if (u.scheme == null || u.host == null) null
            else "${u.scheme}://${u.host}" + if (u.port > 0) ":${u.port}" else ""
        }.getOrNull()

        fun describe(e: PlaybackException): String = when (e.errorCode) {
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                "The broadcaster refused the stream. It may be offline or blocked outside its country."
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Couldn't reach the stream. Check your connection, or the channel may be down."
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ->
                "This stream's format isn't supported."
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
                "This phone can't decode this stream's video."
            else -> "This channel isn't available right now."
        }
    }
}
