package com.voyagetv.app.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * XMLTV 节目单。
 *
 * 只保留"这台设备上真的有对应频道"的节目，且只留 [-2h, +18h] 这一段，
 * 避免把几 MB 的节目单全塞进内存。
 */
object EpgStore {

    fun normalize(name: String): String = name
        .lowercase()
        .replace(" ", "")
        .replace("\u3000", "")
        .replace("-", "")
        .replace("＋", "+")
        .trim()

    fun load(url: String, channelNames: Set<String>): Map<String, List<Programme>> {
        val wanted = channelNames.map(::normalize).filter { it.isNotBlank() }.toSet()
        if (wanted.isEmpty()) return emptyMap()

        val now = System.currentTimeMillis()
        val windowStart = now - 2 * 3600_000L
        val windowEnd = now + 18 * 3600_000L

        val idToName = HashMap<String, String>()
        val byName = HashMap<String, MutableList<Programme>>()

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 30000
                setRequestProperty("User-Agent", "VoyageTV/1.0 (Android)")
                setRequestProperty("Accept-Encoding", "gzip")
            }
            if (connection.responseCode !in 200..299) {
                throw IOException("节目单返回 ${connection.responseCode}")
            }
            val encoding = connection.contentEncoding
            val stream = if (encoding != null && encoding.contains("gzip", ignoreCase = true)) {
                GZIPInputStream(connection.inputStream)
            } else {
                connection.inputStream
            }

            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(stream, null)

            var currentChannelId: String? = null
            var channelDisplayName: String? = null
            var programmeChannel: String? = null
            var programmeStart = 0L
            var programmeStop = 0L
            var programmeTitle: StringBuilder? = null
            var captureName = false
            var captureTitle = false

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "channel" -> {
                            currentChannelId = parser.getAttributeValue(null, "id")
                            channelDisplayName = null
                        }

                        "display-name" -> {
                            captureName = true
                            if (channelDisplayName == null) channelDisplayName = ""
                        }

                        "programme" -> {
                            programmeChannel = parser.getAttributeValue(null, "channel")
                            programmeStart = parseTime(parser.getAttributeValue(null, "start"))
                            programmeStop = parseTime(parser.getAttributeValue(null, "stop"))
                            programmeTitle = StringBuilder()
                        }

                        "title" -> captureTitle = true
                    }

                    XmlPullParser.TEXT -> {
                        val text = parser.text ?: ""
                        if (captureName && channelDisplayName == null) {
                            channelDisplayName = text
                        }
                        if (captureTitle) programmeTitle?.append(text)
                    }

                    XmlPullParser.END_TAG -> when (parser.name) {
                        "display-name" -> captureName = false

                        "title" -> captureTitle = false

                        "channel" -> {
                            val id = currentChannelId
                            val name = channelDisplayName?.let { normalize(it) }
                            if (id != null && name != null && name in wanted) idToName[id] = name
                            currentChannelId = null
                            channelDisplayName = null
                        }

                        "programme" -> {
                            val name = programmeChannel?.let { idToName[it] }
                            val title = programmeTitle?.toString()?.trim()
                            if (name != null && !title.isNullOrBlank() && programmeStop > 0 &&
                                programmeStop > windowStart && programmeStart < windowEnd
                            ) {
                                byName.getOrPut(name) { ArrayList() }.add(
                                    Programme(title, programmeStart, programmeStop)
                                )
                            }
                            programmeChannel = null
                            programmeTitle = null
                        }
                    }
                }
                event = parser.next()
            }
            stream.close()
        } finally {
            connection?.disconnect()
        }

        return byName.mapValues { (_, list) -> list.sortedBy { it.startMillis } }
    }

    /** 取出当前正在播的与下一条。 */
    fun currentAndNext(programmes: List<Programme>?, now: Long = System.currentTimeMillis()): Pair<Programme?, Programme?> {
        if (programmes.isNullOrEmpty()) return null to null
        val current = programmes.firstOrNull { now >= it.startMillis && now < it.endMillis }
        val next = programmes.firstOrNull { it.startMillis > now }
        return current to next
    }

    private val formatters = listOf(
        "yyyyMMddHHmmss Z",
        "yyyyMMddHHmmssZ",
        "yyyyMMddHHmmss"
    )

    private fun parseTime(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        for (pattern in formatters) {
            try {
                return SimpleDateFormat(pattern, Locale.US).parse(raw.trim())?.time ?: 0L
            } catch (e: Exception) {
                // 换下一个格式
            }
        }
        return 0L
    }
}
