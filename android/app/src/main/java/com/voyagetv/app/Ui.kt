package com.voyagetv.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.voyagetv.app.data.AppSettings
import com.voyagetv.app.data.Channel
import com.voyagetv.app.data.Playlist
import com.voyagetv.app.data.formatBytes
import kotlinx.coroutines.delay
import java.net.Inet4Address
import java.net.Inet6Address

// APTV 的配色：纯黑背景、深灰面板、白色主文字、红色强调、绿色直播标记
private val Bg = Color(0xFF000000)
private val Panel = Color(0xFF111113)
private val PanelAlt = Color(0xFF1C1C1F)
private val TextMain = Color(0xFFF2F2F7)
private val TextDim = Color(0xFF8E8E93)
private val Accent = Color(0xFFE5484D)
private val LiveDot = Color(0xFF31D07C)
private val Danger = Color(0xFFFF5A5F)
private val Divider = Color(0xFF232326)

@Composable
fun VoyageTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            background = Bg,
            onBackground = TextMain,
            surface = Panel,
            onSurface = TextMain,
            error = Danger
        ),
        content = content
    )
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val state by vm.uiState.collectAsState()
    val settings by vm.settings.collectAsState()
    var tab by remember { mutableIntStateOf(settings.defaultTab) }

    LaunchedEffect(state.playlists.isEmpty()) {
        if (state.playlists.isEmpty()) tab = 2
    }

    if (state.current != null) {
        PlayerScreen(vm, state, settings)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                1 -> ChannelScreen(vm, state, settings, favoritesOnly = true, onOpenConfig = { tab = 2 })
                2 -> ConfigScreen(vm, state, onDone = { tab = 0 })
                3 -> SettingsScreen(vm, settings, state)
                else -> ChannelScreen(vm, state, settings, favoritesOnly = false, onOpenConfig = { tab = 2 })
            }
        }
        BottomBar(tab) { tab = it }
    }
}

@Composable
private fun BottomBar(tab: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Bg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .background(PanelAlt, RoundedCornerShape(26.dp))
                .padding(horizontal = 4.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BarItem("频道", Icons.Rounded.LiveTv, tab == 0) { onSelect(0) }
            BarItem("收藏", Icons.Rounded.Favorite, tab == 1) { onSelect(1) }
            BarItem("配置", Icons.Rounded.Settings, tab == 2) { onSelect(2) }
            BarItem("设置", Icons.Rounded.Category, tab == 3) { onSelect(3) }
        }
    }
}

@Composable
private fun BarItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) Accent else TextDim
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Color(0xFF0B0B0C) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = tint, fontSize = 11.sp)
    }
}

// MARK: - 频道页

@Composable
private fun ChannelScreen(
    vm: AppViewModel,
    state: UiState,
    settings: AppSettings,
    favoritesOnly: Boolean,
    onOpenConfig: () -> Unit
) {
    val context = LocalContext.current
    val isTv = remember(context) {
        runCatching {
            context.findActivity()?.packageManager?.hasSystemFeature(PackageManager.FEATURE_LEANBACK) == true
        }.getOrDefault(false)
    }
    val firstFocus = remember { FocusRequester() }
    var menu by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }

    val source = if (favoritesOnly) {
        state.channels.filter { it.id in state.favorites }
    } else {
        vm.visibleChannels()
    }
    val filtered = if (state.query.isBlank()) source else source.filter {
        it.name.contains(state.query.trim(), ignoreCase = true)
    }
    val visible = if (settings.channelOrder == "name") filtered.sortedBy { it.name } else filtered

    LaunchedEffect(isTv, favoritesOnly, visible.size) {
        if (isTv && visible.isNotEmpty()) {
            delay(300)
            runCatching { firstFocus.requestFocus() }
        }
    }

    val activePlaylist = state.playlists.firstOrNull { it.id == state.selectedPlaylistId }

    Column(Modifier.fillMaxSize()) {
        // 顶部工具条：全部频道下拉 + 播放 / 配置 / 更多
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Pill(
                    icon = Icons.Rounded.List,
                    text = activePlaylist?.name ?: "全部频道"
                ) { picker = true }
                DropdownMenu(expanded = picker, onDismissRequest = { picker = false }) {
                    DropdownMenuItem(
                        text = { Text("全部频道") },
                        onClick = {
                            vm.selectPlaylist("")
                            picker = false
                        }
                    )
                    state.playlists.forEach { playlist ->
                        DropdownMenuItem(
                            text = { Text(playlist.name) },
                            onClick = {
                                vm.selectPlaylist(playlist.id)
                                picker = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            RoundButton(Icons.Rounded.PlayCircle, "播放") { vm.resumeLast() }
            Spacer(Modifier.width(8.dp))
            RoundButton(Icons.Rounded.Settings, "配置", onClick = onOpenConfig)
            Spacer(Modifier.width(8.dp))
            Box {
                RoundButton(Icons.Rounded.MoreVert, "更多") { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("按序号排序") },
                        onClick = {
                            vm.updateSettings { it.copy(channelOrder = "index") }
                            menu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("按名称排序") },
                        onClick = {
                            vm.updateSettings { it.copy(channelOrder = "name") }
                            menu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("刷新全部配置") },
                        onClick = {
                            vm.refresh()
                            menu = false
                        }
                    )
                }
            }
        }

        Column(Modifier.padding(horizontal = 18.dp)) {
            Text(
                text = if (favoritesOnly) "我的收藏" else (activePlaylist?.name ?: state.playlists.firstOrNull()?.name ?: "IPTV"),
                color = TextMain,
                fontSize = if (isTv) 30.sp else 27.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (favoritesOnly) "${state.favorites.size} 个收藏" else "共 ${state.channels.size} 个频道",
                color = TextDim,
                fontSize = 11.sp
            )
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = { vm.setQuery(it) },
            placeholder = { Text("搜索") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = TextDim) },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp)
        )

        if (!favoritesOnly) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                items(vm.groups()) { group ->
                    FilterChip(
                        selected = state.selectedGroup == group,
                        onClick = { vm.selectGroup(group) },
                        label = { Text(group, fontSize = 13.sp) }
                    )
                }
            }
        }

        state.importError?.let {
            Text(
                it,
                color = Danger,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
            )
        }

        when {
            state.loadingChannels && state.channels.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Accent) }

            visible.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (favoritesOnly) "长按频道可收藏" else "这里还没有频道",
                    color = TextDim,
                    fontSize = 14.sp
                )
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(if (isTv) 4 else 2),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(count = visible.size, key = { index -> visible[index].id }) { index ->
                    val channel = visible[index]
                    ChannelCard(
                        channel = channel,
                        index = index,
                        favourite = channel.id in state.favorites,
                        settings = settings,
                        isTv = isTv,
                        latencyMs = state.latency[channel.id],
                        infoLine = when {
                            state.current?.id == channel.id && settings.showResolution && state.videoInfo != null ->
                                state.videoInfo
                            else -> vm.programmeLine(channel) ?: "无节目单"
                        },
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                        onClick = { vm.play(channel) },
                        onToggleFavourite = { vm.toggleFavorite(channel) }
                    )
                }
            }
        }
    }
}

@Composable
private fun Pill(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .background(PanelAlt, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = TextMain, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = TextMain, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(PanelAlt)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = TextMain, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelCard(
    channel: Channel,
    index: Int,
    favourite: Boolean,
    settings: AppSettings,
    isTv: Boolean,
    latencyMs: Long?,
    infoLine: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onToggleFavourite: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onToggleFavourite
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(if (focused) Color(0xFF26262B) else Color(0xFF0C0C0D))
                .then(
                    if (focused) Modifier.border(2.dp, Accent, RoundedCornerShape(10.dp)) else Modifier
                )
        ) {
            if (settings.showLogo) {
                ChannelLogo(
                    url = channel.logoUrl,
                    fallback = channel.name,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(if (isTv) 76.dp else 52.dp),
                    fallbackSize = if (isTv) 30 else 22
                )
            } else {
                Text(
                    channel.name,
                    color = TextMain,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 12.dp)
                )
            }

            if (settings.showIndex) {
                Text(
                    text = "%03d".format(index + 1),
                    color = Color(0xE6FFFFFF),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color(0xB3000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }

            if (settings.showDelay) {
                val tint = when {
                    latencyMs == null -> Color(0x99FFFFFF)
                    latencyMs < 500 -> LiveDot
                    latencyMs < 1200 -> Color(0xFFFFC53D)
                    else -> Accent
                }
                Text(
                    text = latencyMs?.let { "$it ms" } ?: "— ms",
                    color = tint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color(0xB3000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            if (settings.showGroup) {
                Text(
                    text = channel.displayGroup,
                    color = Color(0xFFB8B8BE),
                    fontSize = 10.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color(0x99101010), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = channel.name,
                color = TextMain,
                fontSize = if (isTv) 16.sp else 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (favourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "收藏",
                tint = if (favourite) Accent else Color(0x66FFFFFF),
                modifier = Modifier
                    .size(if (isTv) 22.dp else 18.dp)
                    .clickable(onClick = onToggleFavourite)
            )
        }
        Text(
            text = infoLine,
            color = TextDim,
            fontSize = if (isTv) 12.sp else 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// MARK: - 配置页

@Composable
private fun ConfigScreen(vm: AppViewModel, state: UiState, onDone: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(state.playlists.isEmpty()) }
    var m3uInput by remember { mutableStateOf("") }
    var m3uName by remember { mutableStateOf("") }
    var xtreamBase by remember { mutableStateOf("") }
    var xtreamUser by remember { mutableStateOf("") }
    var xtreamPass by remember { mutableStateOf("") }

    val configs = state.playlists.filter {
        query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) || it.sourceUrl.contains(query.trim())
    }
    val broken = state.playlists.count { it.error != null }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(1f))
            RoundButton(Icons.Rounded.Refresh, "刷新全部") { vm.refresh() }
            Spacer(Modifier.width(8.dp))
            RoundButton(Icons.Rounded.Settings, "添加") { showAdd = !showAdd }
        }

        Column(Modifier.padding(horizontal = 18.dp)) {
            Text("配置中心", color = TextMain, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("共 ${state.playlists.size} 项配置", color = TextDim, fontSize = 11.sp)
                if (broken > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text("已失效 $broken 项配置", color = Accent, fontSize = 11.sp)
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索配置") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = TextDim) },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp)
        )

        configs.forEachIndexed { index, playlist ->
            ConfigCard(
                playlist = playlist,
                index = state.playlists.indexOfFirst { it.id == playlist.id } + 1,
                onToggle = { vm.togglePlaylistEnabled(playlist.id) },
                onDuplicate = { vm.duplicatePlaylist(playlist.id) },
                onDelete = { vm.removePlaylist(playlist.id) }
            )
        }

        if (state.playlists.isEmpty()) {
            Text(
                "还没有配置，添加一个 M3U 订阅或 Xtream 账号",
                color = TextDim,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
            )
        }

        if (showAdd) {
            Column(Modifier.padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(6.dp))
                TabRow(selectedTabIndex = tab, containerColor = Panel, contentColor = Accent) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("M3U 订阅") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Xtream") })
                }
                Spacer(Modifier.height(10.dp))
                if (tab == 0) {
                    OutlinedTextField(
                        value = m3uInput,
                        onValueChange = { m3uInput = it },
                        label = { Text("订阅地址或 M3U 内容") },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = m3uName,
                        onValueChange = { m3uName = it },
                        label = { Text("名称（可空）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = xtreamBase,
                        onValueChange = { xtreamBase = it },
                        label = { Text("服务器地址") },
                        placeholder = { Text("http://host:port") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = xtreamUser,
                        onValueChange = { xtreamUser = it },
                        label = { Text("用户名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = xtreamPass,
                        onValueChange = { xtreamPass = it },
                        label = { Text("密码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            if (tab == 0) vm.importM3U(m3uInput, m3uName)
                            else vm.importXtream(xtreamBase, xtreamUser, xtreamPass, null)
                        },
                        enabled = !state.importing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (state.importing) "导入中…" else "导入")
                    }
                }
                state.importError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Danger, fontSize = 12.sp)
                }
                state.importNotice?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = LiveDot, fontSize = 12.sp)
                }
                if (state.channels.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onDone) { Text("去看频道", color = Accent) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ConfigCard(
    playlist: Playlist,
    index: Int,
    onToggle: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val enabled = playlist.enabled
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PanelAlt)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(if (enabled) Accent else Color(0xFF5A5A5F), CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                playlist.name,
                color = if (enabled) TextMain else TextDim,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = enabled, onCheckedChange = { onToggle() })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("#$index", color = TextDim, fontSize = 11.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                playlist.kindLabel,
                color = TextDim,
                fontSize = 11.sp,
                modifier = Modifier
                    .background(Color(0xFF2A2A2E), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            if (playlist.error != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "已失效",
                    color = Color(0xFFFFB4B6),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .background(Color(0xFF4A1F22), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${playlist.channelCount} 个频道", color = TextDim, fontSize = 12.sp)
            Spacer(Modifier.width(12.dp))
            Text("${playlist.groupCount} 个分组", color = TextDim, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text(relativeTime(playlist.lastRefresh), color = TextDim, fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            playlist.sourceUrl,
            color = Color(0xFF6E6E73),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        playlist.error?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = Danger, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDuplicate) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = TextDim, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("复制", color = TextDim, fontSize = 12.sp)
            }
            TextButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = null, tint = Danger, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("删除", color = Danger, fontSize = 12.sp)
            }
        }
    }
}

// MARK: - 设置页

@Composable
private fun SettingsScreen(vm: AppViewModel, settings: AppSettings, state: UiState) {
    val context = LocalContext.current
    var cacheBytes by remember { mutableStateOf(vm.cacheSize()) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var epgDraft by remember { mutableStateOf(settings.defaultEpgUrl) }
    val networkType = remember { networkType(context) }
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
            Text("设置", color = TextMain, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("VoyageTV $version", color = TextDim, fontSize = 11.sp)
        }

        SettingsGroup("通用") {
            SettingRow(Icons.Rounded.Language, "语言", value = "简体中文") { dialog = "language" }
            SettingRow(Icons.Rounded.DarkMode, "个性化", value = "深色") { dialog = "theme" }
            SettingRow(
                Icons.Rounded.Storage,
                "缓存",
                value = formatBytes(cacheBytes)
            ) {
                vm.clearCache()
                cacheBytes = vm.cacheSize()
            }
            SettingRow(
                Icons.Rounded.List,
                "标签栏",
                value = tabName(settings.defaultTab)
            ) { dialog = "tab" }
            SettingRow(Icons.Rounded.Info, "关于 VoyageTV", value = version) { dialog = "about" }
        }

        SettingsGroup("视频") {
            SettingSwitchRow(
                Icons.Rounded.PlayCircle,
                "允许后台播放",
                settings.allowBackground
            ) { checked -> vm.updateSettings { it.copy(allowBackground = checked) } }
            SettingSwitchRow(
                Icons.Rounded.SkipNext,
                "出错自动下一台",
                settings.autoNextOnError
            ) { checked -> vm.updateSettings { it.copy(autoNextOnError = checked) } }
            SettingRow(
                Icons.Rounded.Crop,
                "默认纵横比",
                value = aspectName(settings.aspectRatio)
            ) { dialog = "aspect" }
        }

        SettingsGroup("频道") {
            SettingSwitchRow(Icons.Rounded.Speed, "显示延迟", settings.showDelay) {
                vm.updateSettings { s -> s.copy(showDelay = it) }
            }
            SettingSwitchRow(Icons.Rounded.List, "显示序号", settings.showIndex) {
                vm.updateSettings { s -> s.copy(showIndex = it) }
            }
            SettingSwitchRow(Icons.Rounded.Group, "显示分组", settings.showGroup) {
                vm.updateSettings { s -> s.copy(showGroup = it) }
            }
            SettingSwitchRow(Icons.Rounded.Image, "显示台标", settings.showLogo) {
                vm.updateSettings { s -> s.copy(showLogo = it) }
            }
            SettingSwitchRow(Icons.Rounded.Category, "显示分辨率和帧率", settings.showResolution) {
                vm.updateSettings { s -> s.copy(showResolution = it) }
            }
        }

        SettingsGroup("节目单") {
            SettingSwitchRow(Icons.Rounded.DateRange, "自动加载 EPG", settings.autoEpg) {
                vm.updateSettings { s -> s.copy(autoEpg = it) }
            }
            SettingRow(
                Icons.Rounded.Link,
                "默认 EPG",
                value = settings.defaultEpgUrl
            ) {
                epgDraft = settings.defaultEpgUrl
                dialog = "epg"
            }
        }

        SettingsGroup("其他") {
            SettingRow(Icons.Rounded.Refresh, "恢复所有设置", value = null) { dialog = "reset" }
        }

        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text("当前网络类型：$networkType", color = TextDim, fontSize = 11.sp)
            Text(
                "为保证检测结果准确，请关闭所有网络代理",
                color = Accent,
                fontSize = 11.sp
            )
            Text(
                "已载入 ${state.channels.size} 个频道 / ${state.playlists.count { it.enabled }} 个启用配置",
                color = TextDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
    }

    when (dialog) {
        "language", "theme" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(if (dialog == "language") "语言" else "个性化") },
            text = {
                Text(
                    if (dialog == "language") "目前只有简体中文一种界面语言。" else "界面固定深色，IPTV 播放器用浅色会看不清画面。",
                    fontSize = 14.sp
                )
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("知道了") } }
        )

        "tab" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("默认标签栏") },
            text = {
                Column {
                    listOf("频道", "收藏", "配置", "设置").forEachIndexed { index, name ->
                        Text(
                            text = name,
                            color = if (settings.defaultTab == index) Accent else TextMain,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.updateSettings { it.copy(defaultTab = index) }
                                    dialog = null
                                }
                                .padding(vertical = 10.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("关闭") } }
        )

        "aspect" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("默认纵横比") },
            text = {
                Column {
                    listOf("fit" to "适应（保持比例，留黑边）", "fill" to "填充（拉满屏幕）", "zoom" to "拉伸（裁掉多余）")
                        .forEach { (key, label) ->
                            Text(
                                text = label,
                                color = if (settings.aspectRatio == key) Accent else TextMain,
                                fontSize = 15.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.updateSettings { it.copy(aspectRatio = key) }
                                        dialog = null
                                    }
                                    .padding(vertical = 10.dp)
                            )
                        }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("关闭") } }
        )

        "epg" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("默认 EPG 地址") },
            text = {
                OutlinedTextField(
                    value = epgDraft,
                    onValueChange = { epgDraft = it },
                    singleLine = true,
                    label = { Text("XMLTV 地址") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateSettings { it.copy(defaultEpgUrl = epgDraft.trim()) }
                    dialog = null
                    vm.refresh()
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("取消") } }
        )

        "about" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("关于 VoyageTV") },
            text = {
                Text(
                    "VoyageTV $version\n一个自用的 IPTV 播放器：M3U / Xtream 订阅、节目单、收藏、后台播放。",
                    fontSize = 14.sp
                )
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("知道了") } }
        )

        "reset" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("恢复所有设置") },
            text = { Text("界面选项会回到默认值。订阅、收藏、最近观看不会被删掉。", fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetSettings()
                    cacheBytes = vm.cacheSize()
                    dialog = null
                }) { Text("恢复") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        color = TextDim,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 6.dp)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PanelAlt)
    ) {
        content()
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    value: String?,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, color = TextMain, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(
                value,
                color = TextDim,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.2f, fill = false)
            )
        }
        if (onClick != null) {
            Text("  ›", color = TextDim, fontSize = 14.sp)
        }
    }
    HorizontalDivider(color = Divider, modifier = Modifier.padding(start = 46.dp))
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, color = TextMain, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
    HorizontalDivider(color = Divider, modifier = Modifier.padding(start = 46.dp))
}

// MARK: - 播放页

@Composable
private fun PlayerScreen(vm: AppViewModel, state: UiState, settings: AppSettings) {
    val channel = state.current ?: return
    val activity = LocalContext.current.findActivity()
    val isTv = remember(activity) {
        runCatching { activity?.packageManager?.hasSystemFeature(PackageManager.FEATURE_LEANBACK) == true }
            .getOrDefault(false)
    }
    var fullscreen by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    val playFocus = remember { FocusRequester() }

    val resizeMode = when (settings.aspectRatio) {
        "fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        "zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    fun applyFullscreen(on: Boolean) {
        fullscreen = on
        chromeVisible = true
        val act = activity ?: return
        val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
        if (on) {
            if (!isTv) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            WindowCompat.setDecorFitsSystemWindows(act.window, false)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowCompat.setDecorFitsSystemWindows(act.window, true)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val act = activity
            if (act != null) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.setDecorFitsSystemWindows(act.window, true)
                WindowCompat.getInsetsController(act.window, act.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(fullscreen, chromeVisible, isTv) {
        if (fullscreen && chromeVisible && !isTv) {
            delay(4000)
            chromeVisible = false
        }
    }

    LaunchedEffect(state.isPlaying, state.currentStart) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    LaunchedEffect(isTv) {
        if (isTv) {
            delay(200)
            runCatching { playFocus.requestFocus() }
        }
    }

    BackHandler(enabled = fullscreen) { applyFullscreen(false) }
    BackHandler(enabled = !fullscreen) { vm.closePlayer() }

    val showChrome = !fullscreen || chromeVisible

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    this.resizeMode = resizeMode
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    player = vm.player()
                }
            },
            update = { view ->
                view.player = vm.player()
                view.resizeMode = resizeMode
            }
        )

        if (state.isBuffering) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0x99FFFFFF))
            }
        }

        if (showChrome) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color(0xCC000000), Color(0x00000000)))
                    )
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.closePlayer() }) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            settings.showResolution && state.videoInfo != null -> state.videoInfo
                            isTv -> "遥控器 ↑↓ 换台"
                            else -> state.currentProgramme ?: channel.displayGroup
                        },
                        color = Color(0xB3FFFFFF),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { vm.toggleFavorite(channel) }) {
                    Icon(
                        imageVector = if (channel.id in state.favorites) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "收藏",
                        tint = if (channel.id in state.favorites) Accent else Color.White
                    )
                }
                IconButton(onClick = { applyFullscreen(!fullscreen) }) {
                    Icon(
                        imageVector = if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        contentDescription = if (fullscreen) "退出全屏" else "全屏",
                        tint = Color.White
                    )
                }
            }
        }

        if (showChrome) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color(0x00000000), Color(0xF2000000)))
                    )
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                state.playerError?.let { message ->
                    Box(
                        modifier = Modifier
                            .background(Danger.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(message, color = Color(0xFFFFD5D6), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                }

                if (state.currentStart > 0 && state.currentEnd > state.currentStart) {
                    val total = (state.currentEnd - state.currentStart).toFloat().coerceAtLeast(1f)
                    val progress = ((nowMs - state.currentStart) / total).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = Accent,
                        trackColor = Color(0x33FFFFFF)
                    )
                }

                state.currentProgramme?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = Color(0xE6FFFFFF), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                state.nextProgramme?.let {
                    Text("接下来 $it", color = Color(0x99FFFFFF), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.playPrevious() }, modifier = Modifier.size(52.dp)) {
                        Icon(
                            Icons.Rounded.SkipPrevious,
                            contentDescription = "上一台",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(Modifier.width(18.dp))
                    IconButton(
                        onClick = { vm.togglePlayPause() },
                        modifier = Modifier
                            .size(62.dp)
                            .focusRequester(playFocus)
                            .background(Color(0x26FFFFFF), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isPlaying) "暂停" else "播放",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.width(18.dp))
                    IconButton(onClick = { vm.playNext() }, modifier = Modifier.size(52.dp)) {
                        Icon(
                            Icons.Rounded.SkipNext,
                            contentDescription = "下一台",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }
    }
}

// MARK: - 小工具

private fun Context.findActivity(): Activity? {
    var context: Context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

private fun tabName(index: Int): String = listOf("频道", "收藏", "配置", "设置")
    .getOrElse(index) { "频道" }

private fun aspectName(key: String): String = when (key) {
    "fill" -> "填充"
    "zoom" -> "拉伸"
    else -> "适应"
}

private fun relativeTime(millis: Long): String {
    if (millis <= 0L) return "从未刷新"
    val diff = System.currentTimeMillis() - millis
    val minutes = diff / 60_000L
    return when {
        minutes < 1 -> "刚刚刷新"
        minutes < 60 -> "刷新于 ${minutes} 分钟前"
        minutes < 60 * 24 -> "刷新于 ${minutes / 60} 小时前"
        else -> "刷新于 ${minutes / (60 * 24)} 天前"
    }
}

private fun networkType(context: Context): String {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return "未知"
    val network = manager.activeNetwork ?: return "无网络"
    val properties = manager.getLinkProperties(network) ?: return "未知"
    val hasV4 = properties.linkAddresses.any { it.address is Inet4Address }
    val hasV6 = properties.linkAddresses.any { it.address is Inet6Address }
    return when {
        hasV4 && hasV6 -> "IPv4 / IPv6"
        hasV6 -> "IPv6"
        hasV4 -> "IPv4"
        else -> "未知"
    }
}
