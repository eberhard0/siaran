package com.iameberhard.siaran.data

/**
 * Parses the iptv-org flavour of extended M3U:
 *
 *     #EXTINF:-1 tvg-id="X" tvg-logo="…" http-referrer="…" group-title="News;General",Name (720p)
 *     #EXTVLCOPT:http-referrer=https://…
 *     #EXTVLCOPT:http-user-agent=Mozilla/5.0 …
 *     https://host/stream.m3u8
 */
object M3uParser {
    private val ATTR = Regex("""([a-zA-Z-]+)="([^"]*)"""")

    fun parse(text: String): List<Channel> {
        val out = ArrayList<Channel>(12_000)
        var attrs: Map<String, String> = emptyMap()
        var name: String? = null
        var referrer: String? = null
        var userAgent: String? = null

        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF") -> {
                    val comma = findNameComma(line)
                    val head = if (comma >= 0) line.substring(0, comma) else line
                    name = if (comma >= 0) line.substring(comma + 1).trim() else ""
                    attrs = ATTR.findAll(head).associate { it.groupValues[1] to it.groupValues[2] }
                    referrer = attrs["http-referrer"]?.ifBlank { null }
                    userAgent = attrs["http-user-agent"]?.ifBlank { null }
                }
                line.startsWith("#EXTVLCOPT:http-referrer=") ->
                    referrer = line.substringAfter('=').ifBlank { null }
                line.startsWith("#EXTVLCOPT:http-user-agent=") ->
                    userAgent = line.substringAfter('=').ifBlank { null }
                line.startsWith("#") -> Unit
                else -> {
                    val n = name
                    if (n != null) {
                        out += Channel(
                            name = n.ifBlank { line },
                            url = line,
                            logo = attrs["tvg-logo"]?.ifBlank { null },
                            groups = attrs["group-title"].orEmpty()
                                .split(';').map { it.trim() }.filter { it.isNotEmpty() },
                            tvgId = attrs["tvg-id"]?.ifBlank { null },
                            referrer = referrer,
                            userAgent = userAgent,
                        )
                    }
                    name = null
                    attrs = emptyMap()
                    referrer = null
                    userAgent = null
                }
            }
        }
        return out
    }

    /** The name follows the first comma that is outside a quoted attribute value. */
    private fun findNameComma(line: String): Int {
        var quoted = false
        for (i in line.indices) {
            when (line[i]) {
                '"' -> quoted = !quoted
                ',' -> if (!quoted) return i
            }
        }
        return -1
    }
}
