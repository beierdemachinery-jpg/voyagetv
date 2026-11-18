package com.voyagetv.app.data

/** M3U / M3U8 解析：只认 #EXTINF 这种最小集合，够用且宽容。 */
object M3UParser {

    data class Result(val channels: List<Channel>, val epgUrl: String?)

    private data class Pending(val name: String, val group: String, val logo: String?)

    fun parse(text: String, playlistId: String): Result {
        val lines = text.replace("\r", "").split("\n")
        val channels = ArrayList<Channel>()
        var epgUrl: String? = null
        var pending: Pending? = null

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            when {
                line.startsWith("#EXTM3U") -> {
                    if (epgUrl == null) epgUrl = readEpgUrl(line)
                }

                line.startsWith("#EXTINF") -> {
                    pending = parseExtInf(line)
                }

                line.startsWith("#") -> Unit

                else -> {
                    val meta = pending
                    pending = null
                    channels.add(
                        Channel(
                            id = line.hashCode().toString() + "-" + channels.size,
                            name = meta?.name?.takeIf { it.isNotBlank() }
                                ?: line.substringAfterLast('/').takeIf { it.isNotBlank() }
                                ?: "频道 ${channels.size + 1}",
                            group = meta?.group?.takeIf { it.isNotBlank() } ?: "未分组",
                            logoUrl = meta?.logo,
                            streamUrl = line,
                            playlistId = playlistId
                        )
                    )
                }
            }
        }

        return Result(channels, epgUrl)
    }

    private fun readEpgUrl(line: String): String? {
        for (key in listOf("x-tvg-url", "url-tvg")) {
            val attr = "=\""
            val idx = line.indexOf("$key$attr")
            if (idx < 0) continue
            val start = idx + key.length + attr.length
            val end = line.indexOf('"', start)
            if (end > start) {
                return line.substring(start, end).split(',').firstOrNull()?.trim()
            }
        }
        return null
    }

    private fun parseExtInf(line: String): Pending {
        val name = line.substringAfterLast(',', "").trim()
        val group = readAttr(line, "group-title") ?: ""
        val logo = readAttr(line, "tvg-logo")
        return Pending(name, group, logo)
    }

    private fun readAttr(line: String, key: String): String? {
        val pattern = "$key=\""
        val idx = line.indexOf(pattern)
        if (idx < 0) return null
        val start = idx + pattern.length
        val end = line.indexOf('"', start)
        if (end <= start) return null
        return line.substring(start, end).trim().ifBlank { null }
    }
}
