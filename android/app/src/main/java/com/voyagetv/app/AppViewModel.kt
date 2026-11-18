package com.voyagetv.app

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.voyagetv.app.data.Channel
import com.voyagetv.app.data.EpgStore
import com.voyagetv.app.data.Http
import com.voyagetv.app.data.M3UParser
import com.voyagetv.app.data.Playlist
import com.voyagetv.app.data.Programme
import com.voyagetv.app.data.Store
import com.voyagetv.app.data.Xtream
import com.voyagetv.app.player.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UiState(
    val playlists: List<Playlist> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val recents: List<Channel> = emptyList(),
    val selectedGroup: String = GROUP_ALL,
    val query: String = "",
    val importing: Boolean = false,
    val importError: String? = null,
    val importNotice: String? = null,
    val loadingChannels: Boolean = false,
    val hasEpg: Boolean = false,
    val current: Channel? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val playerError: String? = null,
    val currentProgramme: String? = null,
    val nextProgramme: String? = null,
    val currentStart: Long = 0L,
    val currentEnd: Long = 0L
) {
    companion object {
        const val GROUP_ALL = "全部"
        const val GROUP_FAVORITES = "收藏"
        const val GROUP_RECENT = "最近"
    }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var controller: MediaController? = null
    private var epg: Map<String, List<Programme>> = emptyMap()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _uiState.update {
                it.copy(isBuffering = playbackState == Player.STATE_BUFFERING)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.update {
                it.copy(playerError = describe(error), isPlaying = false, isBuffering = false)
            }
        }
    }

    init {
        _uiState.update {
            it.copy(
                playlists = store.playlists(),
                favorites = store.favorites(),
                recents = store.recents()
            )
        }
        connectPlayer()
        refresh()
        startTicker()
    }

    // MARK: - 播放器连接

    private fun connectPlayer() {
        val app = getApplication<Application>()
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            runCatching { future.get() }.onSuccess { mediaController ->
                controller = mediaController
                mediaController.addListener(playerListener)
            }
        }, ContextCompat.getMainExecutor(app))
    }

    // MARK: - 订阅导入

    fun importM3U(input: String, name: String?) {
        val raw = input.trim()
        if (raw.isEmpty()) {
            _uiState.update { it.copy(importError = "请粘贴订阅地址或 M3U 内容") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(importing = true, importError = null, importNotice = null) }
            try {
                val text = if (raw.startsWith("http://") || raw.startsWith("https://")) {
                    withContext(Dispatchers.IO) { Http.getText(raw) }
                } else {
                    raw
                }
                val parsed = M3UParser.parse(text, "")
                if (parsed.channels.isEmpty()) {
                    _uiState.update {
                        it.copy(importing = false, importError = "解析成功但没有找到任何频道，请检查清单格式（需要 #EXTINF + 地址）")
                    }
                    return@launch
                }

                val playlist = Playlist(
                    id = "pl-" + System.currentTimeMillis(),
                    name = name?.takeIf { it.isNotBlank() }
                        ?: hostOf(raw) ?: "M3U 订阅",
                    kind = "m3u",
                    sourceUrl = raw,
                    epgUrl = parsed.epgUrl
                )
                val list = store.playlists() + playlist
                store.savePlaylists(list)
                _uiState.update { it.copy(playlists = list) }
                refresh()
                _uiState.update {
                    it.copy(importing = false, importNotice = "已导入 ${parsed.channels.size} 个频道")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(importing = false, importError = describeImport(e)) }
            }
        }
    }

    fun importXtream(base: String, user: String, pass: String, name: String?) {
        if (base.isBlank() || user.isBlank() || pass.isBlank()) {
            _uiState.update { it.copy(importError = "服务器地址、用户名、密码都要填") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(importing = true, importError = null, importNotice = null) }
            try {
                val normalized = Xtream.baseUrl(base)
                val streams = withContext(Dispatchers.IO) { Xtream.fetchStreams(normalized, user, pass) }
                if (streams.isEmpty()) {
                    _uiState.update { it.copy(importing = false, importError = "登录成功但账号下没有直播频道") }
                    return@launch
                }
                val playlist = Playlist(
                    id = "pl-" + System.currentTimeMillis(),
                    name = name?.takeIf { it.isNotBlank() } ?: hostOf(normalized) ?: "Xtream",
                    kind = "xtream",
                    sourceUrl = normalized,
                    xtreamUser = user,
                    xtreamPass = pass
                )
                val list = store.playlists() + playlist
                store.savePlaylists(list)
                _uiState.update { it.copy(playlists = list) }
                refresh()
                _uiState.update {
                    it.copy(importing = false, importNotice = "已导入 ${streams.size} 个频道")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(importing = false, importError = describeImport(e)) }
            }
        }
    }

    fun removePlaylist(id: String) {
        val list = store.playlists().filterNot { it.id == id }
        store.savePlaylists(list)
        _uiState.update { it.copy(playlists = list) }
        refresh()
    }

    // MARK: - 拉取频道

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingChannels = true, importError = null) }
            val playlists = store.playlists()
            if (playlists.isEmpty()) {
                _uiState.update {
                    it.copy(loadingChannels = false, channels = emptyList(), playlists = emptyList())
                }
                return@launch
            }

            val collected = ArrayList<Channel>()
            var epgUrl: String? = null
            var failure: String? = null
            var updated: List<Playlist> = playlists

            for (playlist in playlists) {
                try {
                    if (playlist.kind == "xtream") {
                        val base = Xtream.baseUrl(playlist.sourceUrl)
                        val user = playlist.xtreamUser.orEmpty()
                        val pass = playlist.xtreamPass.orEmpty()
                        val streams = withContext(Dispatchers.IO) { Xtream.fetchStreams(base, user, pass) }
                        val categories = withContext(Dispatchers.IO) { Xtream.fetchCategoryNames(base, user, pass) }
                        collected.addAll(Xtream.toChannels(streams, categories, base, user, pass, playlist.id))
                    } else {
                        val text = withContext(Dispatchers.IO) { Http.getText(playlist.sourceUrl) }
                        val parsed = M3UParser.parse(text, playlist.id)
                        collected.addAll(parsed.channels)
                        if (parsed.epgUrl != null) {
                            if (epgUrl == null) epgUrl = parsed.epgUrl
                            if (playlist.epgUrl == null) {
                                updated = updated.map {
                                    if (it.id == playlist.id) it.copy(epgUrl = parsed.epgUrl) else it
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    failure = "${playlist.name}：${describeImport(e)}"
                }
            }

            if (updated != playlists) store.savePlaylists(updated)

            val deduped = collected.distinctBy { it.streamUrl }
            _uiState.update {
                it.copy(
                    channels = deduped,
                    playlists = updated,
                    loadingChannels = false,
                    importError = failure
                )
            }
            epgUrl?.let { loadEpg(it, deduped) }
        }
    }

    private fun loadEpg(url: String, channels: List<Channel>) {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { EpgStore.load(url, channels.map { it.name }.toSet()) }.getOrNull()
            }
            epg = loaded ?: emptyMap()
            _uiState.update {
                it.copy(hasEpg = epg.isNotEmpty())
            }
            refreshProgramme()
        }
    }

    // MARK: - 播放

    fun play(channel: Channel) {
        _uiState.update { it.copy(current = channel, playerError = null) }
        store.pushRecent(channel)

        val mediaController = controller
        if (mediaController == null) {
            connectPlayer()
            _uiState.update {
                it.copy(
                    recents = store.recents(),
                    playerError = "播放服务还在启动，稍等一下再点一次这个频道"
                )
            }
            return
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(channel.name)
            .setArtist(channel.displayGroup)
            .setAlbumTitle("VoyageTV")
            .apply { channel.logoUrl?.let { setArtworkUri(Uri.parse(it)) } }
            .build()

        val item = MediaItem.Builder()
            .setUri(channel.streamUrl)
            .setMediaId(channel.id)
            .setMediaMetadata(metadata)
            .build()

        controller?.apply {
            setMediaItem(item)
            prepare()
            play()
        }

        _uiState.update { it.copy(recents = store.recents(), playerError = null) }
        refreshProgramme()
    }

    fun togglePlayPause() {
        val mediaController = controller ?: return
        if (mediaController.isPlaying) mediaController.pause() else mediaController.play()
    }

    fun playNext() {
        val channel = neighbour(1) ?: return
        play(channel)
    }

    fun playPrevious() {
        val channel = neighbour(-1) ?: return
        play(channel)
    }

    private fun neighbour(delta: Int): Channel? {
        val state = _uiState.value
        val visible = visibleOf(state)
        val index = visible.indexOfFirst { it.id == state.current?.id }
        if (index < 0) return visible.firstOrNull()
        if (visible.isEmpty()) return null
        val next = (index + delta + visible.size) % visible.size
        return visible[next]
    }

    fun closePlayer() {
        controller?.stop()
        _uiState.update {
            it.copy(current = null, isPlaying = false, playerError = null, isBuffering = false)
        }
    }

    fun toggleFavorite(channel: Channel) {
        val favorites = _uiState.value.favorites.toMutableSet()
        if (!favorites.add(channel.id)) favorites.remove(channel.id)
        store.saveFavorites(favorites)
        _uiState.update { it.copy(favorites = favorites) }
    }

    fun selectGroup(group: String) {
        _uiState.update { it.copy(selectedGroup = group) }
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun clearNotices() {
        _uiState.update { it.copy(importError = null, importNotice = null) }
    }

    // MARK: - 节目单刷新

    private fun startTicker() {
        viewModelScope.launch {
            while (isActive) {
                delay(60_000)
                refreshProgramme()
            }
        }
    }

    private fun refreshProgramme() {
        val state = _uiState.value
        val channel = state.current ?: return
        val list = epg[EpgStore.normalize(channel.name)]
        val (current, next) = EpgStore.currentAndNext(list)
        _uiState.update {
            it.copy(
                currentProgramme = current?.let { programme ->
                    "${format(programme.startMillis)}-${format(programme.endMillis)} · ${programme.title}"
                },
                nextProgramme = next?.let { programme ->
                    "${format(programme.startMillis)}-${format(programme.endMillis)} · ${programme.title}"
                },
                currentStart = current?.startMillis ?: 0L,
                currentEnd = current?.endMillis ?: 0L
            )
        }
    }

    private fun format(millis: Long): String =
        SimpleDateFormat("HH:mm", Locale.US).format(Date(millis))

    private fun visibleOf(state: UiState): List<Channel> {
        val base = when (state.selectedGroup) {
            UiState.GROUP_FAVORITES -> state.channels.filter { it.id in state.favorites }
            UiState.GROUP_RECENT -> state.recents
            UiState.GROUP_ALL -> state.channels
            else -> state.channels.filter { it.displayGroup == state.selectedGroup }
        }
        if (state.query.isBlank()) return base
        val query = state.query.trim()
        return base.filter { it.name.contains(query, ignoreCase = true) }
    }

    fun visibleChannels(): List<Channel> = visibleOf(_uiState.value)

    /** 列表里那一行小字：当前节目。没有节目单就返回 null。 */
    fun programmeLine(channel: Channel): String? {
        val list = epg[EpgStore.normalize(channel.name)] ?: return null
        val (current, _) = EpgStore.currentAndNext(list)
        return current?.let { "${format(it.startMillis)}-${format(it.endMillis)} · ${it.title}" }
    }

    fun player(): Player? = controller

    fun hasEpg(): Boolean = epg.isNotEmpty()

    fun groups(): List<String> {
        val state = _uiState.value
        val groups = state.channels.map { it.displayGroup }.distinct().sorted()
        return listOf(UiState.GROUP_ALL, UiState.GROUP_FAVORITES, UiState.GROUP_RECENT) + groups
    }

    private fun hostOf(raw: String): String? = runCatching {
        if (!raw.startsWith("http")) return null
        Uri.parse(raw).host
    }.getOrNull()

    private fun describeImport(error: Throwable): String = when (error) {
        is java.net.MalformedURLException -> "地址格式不对（要完整的 http:// 或 https:// 地址）"
        is java.net.UnknownHostException -> "解析不到这个域名（域名写错或网络不通）"
        is java.net.ConnectException -> "连接被拒绝（端口没开 / 服务没监听 / 端口映射掉了）"
        is java.net.SocketTimeoutException -> "连接超时（服务器没响应或被拦截）"
        is javax.net.ssl.SSLException -> "证书校验失败"
        else -> error.message ?: error.javaClass.simpleName
    }

    private fun describe(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "无法连接服务器（连接失败）"
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "连接超时"
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "服务器返回错误状态"
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "地址不存在（404）"
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> "系统禁止明文 HTTP"
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "清单或分片格式不合法"
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "播放列表不合法"
        else -> "播放失败（${error.errorCodeName} / ${error.errorCode}）"
    }
}
