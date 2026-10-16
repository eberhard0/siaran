package com.iameberhard.siaran.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Everything the app browses, built from two iptv-org playlists. */
class Catalog(
    /** index.m3u: one entry per stream, groups = categories. */
    val all: List<Channel>,
    /** Country name → its channels (index.country.m3u). */
    val byCountry: Map<String, List<Channel>>,
    /** Category name → its channels. */
    val byCategory: Map<String, List<Channel>>,
    /** Country name → flag emoji, from the iptv-org API. */
    val flags: Map<String, String>,
    val fetchedAt: Long,
) {
    /** Stream URL → its categories, so country lists can be filtered by category. */
    val categoriesOf: Map<String, List<String>> = all.associate { it.url to it.groups }

    fun flag(country: String): String = flags[country].orEmpty()
}

@Serializable
private data class ApiCountry(val name: String, val code: String, val flag: String = "")

/**
 * Downloads and caches the iptv-org playlists. Files are kept for a day so opening
 * the app is instant; the lists change daily upstream, so older copies are refreshed
 * in the background of the next load.
 */
class CatalogRepository(context: Context, private val http: OkHttpClient) {
    private val dir = File(context.filesDir, "playlists").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(forceRefresh: Boolean = false): Catalog = withContext(Dispatchers.IO) {
        val index = fetch("index.m3u", "$BASE/index.m3u", forceRefresh)
        val countries = fetch("index.country.m3u", "$BASE/index.country.m3u", forceRefresh)
        val flags = runCatching {
            val text = fetch("countries.json", "$API/countries.json", forceRefresh)
            json.decodeFromString<List<ApiCountry>>(text).associate { it.name to it.flag }
        }.getOrDefault(emptyMap())

        val all = M3uParser.parse(index)
        val byCategory = HashMap<String, MutableList<Channel>>()
        all.forEach { ch -> ch.groups.forEach { g -> byCategory.getOrPut(g) { ArrayList() } += ch } }

        val byCountry = HashMap<String, MutableList<Channel>>()
        M3uParser.parse(countries).forEach { ch ->
            ch.groups.forEach { g -> byCountry.getOrPut(g) { ArrayList() } += ch }
        }

        Catalog(
            all = all.sortedBy { it.displayName.lowercase() },
            byCountry = byCountry.mapValues { (_, v) -> v.sortedBy { it.displayName.lowercase() } },
            byCategory = byCategory.mapValues { (_, v) -> v.sortedBy { it.displayName.lowercase() } },
            flags = flags,
            fetchedAt = File(dir, "index.m3u").lastModified(),
        )
    }

    /** Cached copy if fresh; otherwise download, falling back to a stale copy offline. */
    private fun fetch(file: String, url: String, force: Boolean): String {
        val cached = File(dir, file)
        val fresh = cached.exists() && System.currentTimeMillis() - cached.lastModified() < MAX_AGE_MS
        if (fresh && !force) return cached.readText()
        return try {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} for $url")
                val body = resp.body?.string() ?: throw IOException("Empty response for $url")
                val tmp = File(dir, "$file.tmp")
                tmp.writeText(body)
                tmp.renameTo(cached)
                body
            }
        } catch (e: IOException) {
            if (cached.exists()) cached.readText() else throw e
        }
    }

    companion object {
        const val BASE = "https://iptv-org.github.io/iptv"
        const val API = "https://iptv-org.github.io/api"
        private val MAX_AGE_MS = TimeUnit.HOURS.toMillis(24)
    }
}
