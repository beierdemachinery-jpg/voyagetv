package com.voyagetv.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 本地持久化：订阅、收藏、最近观看。用 SharedPreferences + JSON，够轻量。 */
class Store(context: Context) {

    private val prefs = context.getSharedPreferences("voyagetv", Context.MODE_PRIVATE)

    fun playlists(): List<Playlist> {
        val raw = prefs.getString(KEY_PLAYLISTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                Playlist(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    kind = obj.optString("kind", "m3u"),
                    sourceUrl = obj.getString("sourceUrl"),
                    epgUrl = obj.optString("epgUrl").ifBlank { null },
                    xtreamUser = obj.optString("xtreamUser").ifBlank { null },
                    xtreamPass = obj.optString("xtreamPass").ifBlank { null },
                    enabled = obj.optBoolean("enabled", true),
                    lastRefresh = obj.optLong("lastRefresh", 0L),
                    channelCount = obj.optInt("channelCount", 0),
                    groupCount = obj.optInt("groupCount", 0),
                    error = obj.optString("error").ifBlank { null }
                )
            }
        }.getOrDefault(emptyList())
    }

    fun savePlaylists(list: List<Playlist>) {
        val array = JSONArray()
        list.forEach { playlist ->
            array.put(
                JSONObject().apply {
                    put("id", playlist.id)
                    put("name", playlist.name)
                    put("kind", playlist.kind)
                    put("sourceUrl", playlist.sourceUrl)
                    put("epgUrl", playlist.epgUrl ?: "")
                    put("xtreamUser", playlist.xtreamUser ?: "")
                    put("xtreamPass", playlist.xtreamPass ?: "")
                    put("enabled", playlist.enabled)
                    put("lastRefresh", playlist.lastRefresh)
                    put("channelCount", playlist.channelCount)
                    put("groupCount", playlist.groupCount)
                    put("error", playlist.error ?: "")
                }
            )
        }
        prefs.edit().putString(KEY_PLAYLISTS, array.toString()).apply()
    }

    fun favorites(): Set<String> {
        val raw = prefs.getString(KEY_FAVORITES, null) ?: return emptySet()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }.toSet()
        }.getOrDefault(emptySet())
    }

    fun saveFavorites(ids: Set<String>) {
        val array = JSONArray()
        ids.forEach { array.put(it) }
        prefs.edit().putString(KEY_FAVORITES, array.toString()).apply()
    }

    fun recents(): List<Channel> {
        val raw = prefs.getString(KEY_RECENTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                Channel(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    group = obj.optString("group"),
                    logoUrl = obj.optString("logo").ifBlank { null },
                    streamUrl = obj.getString("streamUrl"),
                    playlistId = obj.optString("playlistId")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun pushRecent(channel: Channel) {
        val list = recents().filterNot { it.id == channel.id }.toMutableList()
        list.add(0, channel)
        while (list.size > 30) list.removeAt(list.size - 1)
        val array = JSONArray()
        list.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("group", item.group)
                    put("logo", item.logoUrl ?: "")
                    put("streamUrl", item.streamUrl)
                    put("playlistId", item.playlistId)
                }
            )
        }
        prefs.edit().putString(KEY_RECENTS, array.toString()).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_PLAYLISTS = "playlists"
        const val KEY_FAVORITES = "favorites"
        const val KEY_RECENTS = "recents"
    }
}
