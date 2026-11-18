package com.voyagetv.app.data

import org.json.JSONArray
import java.io.IOException

/** Xtream Codes：只要直播频道列表，够 MVP 用。 */
object Xtream {

    data class LiveStream(val id: String, val name: String, val icon: String?, val categoryId: String?)

    fun baseUrl(raw: String): String {
        var base = raw.trim().trimEnd('/')
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://$base"
        }
        return base
    }

    fun fetchStreams(base: String, user: String, pass: String): List<LiveStream> {
        val streamsJson = Http.getText(
            "$base/player_api.php?username=${enc(user)}&password=${enc(pass)}&action=get_live_streams"
        )
        if (!streamsJson.trimStart().startsWith("[")) {
            throw IOException("Xtream 返回的不是频道列表：${streamsJson.take(80)}")
        }
        val array = JSONArray(streamsJson)
        val result = ArrayList<LiveStream>(array.length())
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val id = obj.optString("stream_id")
            if (id.isBlank()) continue
            result.add(
                LiveStream(
                    id = id,
                    name = obj.optString("name").ifBlank { "频道 $id" },
                    icon = obj.optString("stream_icon").ifBlank { null },
                    categoryId = obj.optString("category_id").ifBlank { null }
                )
            )
        }
        return result
    }

    fun fetchCategoryNames(base: String, user: String, pass: String): Map<String, String> {
        val json = runCatching {
            Http.getText("$base/player_api.php?username=${enc(user)}&password=${enc(pass)}&action=get_live_categories")
        }.getOrNull() ?: return emptyMap()
        if (!json.trimStart().startsWith("[")) return emptyMap()
        val array = JSONArray(json)
        val map = HashMap<String, String>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val id = obj.optString("category_id")
            val name = obj.optString("category_name")
            if (id.isNotBlank() && name.isNotBlank()) map[id] = name
        }
        return map
    }

    fun streamUrl(base: String, user: String, pass: String, streamId: String): String =
        "$base/live/${enc(user)}/${enc(pass)}/$streamId.m3u8"

    fun toChannels(
        streams: List<LiveStream>,
        categories: Map<String, String>,
        base: String,
        user: String,
        pass: String,
        playlistId: String
    ): List<Channel> = streams.map { stream ->
        Channel(
            id = "$playlistId/${stream.id}",
            name = stream.name,
            group = categories[stream.categoryId] ?: "未分组",
            logoUrl = stream.icon,
            streamUrl = streamUrl(base, user, pass, stream.id),
            playlistId = playlistId
        )
    }

    private fun enc(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
}
