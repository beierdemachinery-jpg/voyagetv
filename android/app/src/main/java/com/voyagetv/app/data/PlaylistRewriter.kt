package com.voyagetv.app.data

/**
 * HLS 清单规范化。规则与 iOS 版 HLSResourceLoader 一致：
 *
 * 1. 丢掉源站的私有/非标准标签（`#EXT-X-REFRESH`、`#EXT-QQHLS-*`、`#EXT-SID`、
 *    `#EXT-X-ALLOW-CACHE`、`#EXT-X-PROGRAM-DATE-TIME` 等）；
 * 2. 修 `-X-VERSION:3` 这种少一个 `#` 的坏行；
 * 3. 相对地址按清单地址补成绝对；
 * 4. 主清单（含 #EXT-X-STREAM-INF）不裁剪，只补绝对地址。
 */
object PlaylistRewriter {

    private val allowedTags = listOf(
        "#EXTM3U",
        "#EXT-X-VERSION",
        "#EXT-X-TARGETDURATION",
        "#EXT-X-MEDIA-SEQUENCE",
        "#EXT-X-DISCONTINUITY-SEQUENCE",
        "#EXT-X-INDEPENDENT-SEGMENTS",
        "#EXT-X-PLAYLIST-TYPE",
        "#EXT-X-KEY",
        "#EXT-X-MAP",
        "#EXT-X-BYTERANGE",
        "#EXT-X-DISCONTINUITY",
        "#EXT-X-GAP",
        "#EXT-X-ENDLIST",
        "#EXTINF"
    )

    fun rewrite(raw: ByteArray, baseUrl: String): ByteArray? {
        val text = String(raw, Charsets.UTF_8)
        if (!text.contains("#EXTM3U") && !text.contains("#EXT-X-")) return null

        val normalized = text.replace("\r\n", "\n").replace("\r", "\n")
        val isMaster = normalized.contains("#EXT-X-STREAM-INF")
        val out = ArrayList<String>()

        for (line in normalized.split("\n")) {
            var trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("-") && !trimmed.startsWith("//")) {
                val repaired = "#EXT$trimmed"
                if (allowedTags.any { repaired.startsWith(it) }) trimmed = repaired
            }

            if (trimmed.startsWith("#")) {
                if (isMaster) {
                    out.add(trimmed)
                } else if (allowedTags.any { trimmed.startsWith(it) }) {
                    out.add(trimmed)
                }
                continue
            }

            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                out.add(trimmed)
            } else if (trimmed.contains(":")) {
                continue
            } else {
                out.add(resolve(baseUrl, trimmed))
            }
        }

        if (out.isEmpty()) return null
        return (out.joinToString("\n") + "\n").toByteArray(Charsets.UTF_8)
    }

    private fun resolve(base: String, relative: String): String = try {
        java.net.URI(base).resolve(relative).toString()
    } catch (e: Exception) {
        relative
    }
}
