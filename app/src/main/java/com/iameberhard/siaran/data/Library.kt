package com.iameberhard.siaran.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Favourites and recently watched, kept on the phone. Whole channel records are
 * stored (not just URLs) so they still show if a channel later leaves the playlist.
 */
class Library(context: Context) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _favorites = MutableStateFlow(read(KEY_FAV))
    val favorites: StateFlow<List<Channel>> = _favorites.asStateFlow()

    private val _recent = MutableStateFlow(read(KEY_RECENT))
    val recent: StateFlow<List<Channel>> = _recent.asStateFlow()

    var homeCountry: String
        get() = prefs.getString(KEY_COUNTRY, "Indonesia") ?: "Indonesia"
        set(value) = prefs.edit().putString(KEY_COUNTRY, value).apply()

    /** Leaving the app while playing shrinks the video to a floating window. */
    var pipEnabled: Boolean
        get() = prefs.getBoolean(KEY_PIP, true)
        set(value) = prefs.edit().putBoolean(KEY_PIP, value).apply()

    fun isFavorite(ch: Channel) = _favorites.value.any { it.url == ch.url }

    fun toggleFavorite(ch: Channel) {
        val list = _favorites.value
        val next = if (list.any { it.url == ch.url }) list.filterNot { it.url == ch.url } else list + ch
        _favorites.value = next
        write(KEY_FAV, next)
    }

    fun markWatched(ch: Channel) {
        val next = (listOf(ch) + _recent.value.filterNot { it.url == ch.url }).take(30)
        _recent.value = next
        write(KEY_RECENT, next)
    }

    private fun read(key: String): List<Channel> =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString<List<Channel>>(it) }.getOrNull() }
            ?: emptyList()

    private fun write(key: String, list: List<Channel>) =
        prefs.edit().putString(key, json.encodeToString(list)).apply()

    private companion object {
        const val KEY_FAV = "favorites"
        const val KEY_RECENT = "recent"
        const val KEY_COUNTRY = "home_country"
        const val KEY_PIP = "pip_enabled"
    }
}
