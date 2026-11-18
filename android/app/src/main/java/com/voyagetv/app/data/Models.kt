package com.voyagetv.app.data

/** 一个频道。id 由播放地址派生，收藏/最近都用它做键。 */
data class Channel(
    val id: String,
    val name: String,
    val group: String,
    val logoUrl: String?,
    val streamUrl: String,
    val playlistId: String
) {
    val displayGroup: String get() = group.ifBlank { "未分组" }
}

/** 一个订阅。kind = "m3u" 或 "xtream"。 */
data class Playlist(
    val id: String,
    val name: String,
    val kind: String,
    val sourceUrl: String,
    val epgUrl: String? = null,
    val xtreamUser: String? = null,
    val xtreamPass: String? = null
)

/** 节目单里的一条节目。 */
data class Programme(
    val title: String,
    val startMillis: Long,
    val endMillis: Long
)
