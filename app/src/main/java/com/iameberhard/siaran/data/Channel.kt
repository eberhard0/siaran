package com.iameberhard.siaran.data

import kotlinx.serialization.Serializable

/** One stream from an iptv-org M3U playlist. */
@Serializable
data class Channel(
    val name: String,
    val url: String,
    val logo: String? = null,
    /** Categories (index.m3u) or the country (index.country.m3u). */
    val groups: List<String> = emptyList(),
    val tvgId: String? = null,
    val referrer: String? = null,
    val userAgent: String? = null,
) {
    /** iptv-org appends quality and availability to the name: "ANTV (720p) [Not 24/7]". */
    val displayName: String get() = name.replace(QUALITY, "").replace(TAGS, "").trim()
    val quality: String? get() = QUALITY.find(name)?.groupValues?.get(1)
    val notAlwaysOn: Boolean get() = name.contains("[Not 24/7]")
    val geoBlocked: Boolean get() = name.contains("[Geo-blocked]")

    private companion object {
        val QUALITY = Regex("""\s*\((\d{3,4}p)\)""")
        val TAGS = Regex("""\s*\[[^\]]*]""")
    }
}
