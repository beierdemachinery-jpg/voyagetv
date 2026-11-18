package com.voyagetv.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.voyagetv.app.data.Channel
import kotlinx.coroutines.delay

// APTV 的配色：纯黑背景、内容面板深灰、白色主文字、红色强调、绿色「直播」标记
private val Bg = Color(0xFF000000)
private val Panel = Color(0xFF111113)
private val PanelAlt = Color(0xFF1C1C1F)
private val TextMain = Color(0xFFF2F2F7)
private val TextDim = Color(0xFF8E8E93)
private val Accent = Color(0xFFE5484D)
private val LiveDot = Color(0xFF31D07C)
private val Danger = Color(0xFFFF5A5F)

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
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.playlists.isEmpty()) {
        if (state.playlists.isEmpty()) tab = 2
    }

    if (state.current != null) {
        PlayerScreen(vm, state)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                1 -> ChannelListScreen(vm, state, favoritesOnly = true)
                2 -> ConfigScreen(vm, state, onDone = { tab = 0 })
                else -> ChannelListScreen(vm, state, favoritesOnly = false)
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
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .background(PanelAlt, RoundedCornerShape(26.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BarItem("频道", Icons.Rounded.LiveTv, tab == 0) { onSelect(0) }
            BarItem("收藏", Icons.Rounded.Favorite, tab == 1) { onSelect(1) }
            BarItem("配置", Icons.Rounded.Settings, tab == 2) { onSelect(2) }
        }
    }
}

@Composable
private fun BarItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) Accent else TextDim
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Color(0xFF0B0B0C) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = tint, fontSize = 11.sp)
    }
}

// MARK: - 频道网格

@Composable
private fun ChannelListScreen(vm: AppViewModel, state: UiState, favoritesOnly: Boolean) {
    val context = LocalContext.current
    val isTv = remember(context) {
        runCatching {
            context.findActivity()?.packageManager?.hasSystemFeature(PackageManager.FEATURE_LEANBACK) == true
        }.getOrDefault(false)
    }
    val firstFocus = remember { FocusRequester() }

    val source = if (favoritesOnly) {
        state.channels.filter { it.id in state.favorites }
    } else {
        vm.visibleChannels()
    }
    val visible = if (state.query.isBlank()) source else source.filter {
        it.name.contains(state.query.trim(), ignoreCase = true)
    }

    LaunchedEffect(isTv, favoritesOnly, visible.size) {
        if (isTv && visible.isNotEmpty()) {
            delay(300)
            runCatching { firstFocus.requestFocus() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 6.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (favoritesOnly) {
                        "我的收藏"
                    } else {
                        state.playlists.firstOrNull()?.name ?: "IPTV"
                    },
                    color = TextMain,
                    fontSize = if (isTv) 30.sp else 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (favoritesOnly) {
                        "${state.favorites.size} 个收藏"
                    } else {
                        "共 ${state.channels.size} 个频道"
                    },
                    color = TextDim,
                    fontSize = 11.sp
                )
            }
            IconButton(onClick = { vm.refresh() }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "刷新", tint = TextDim)
            }
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = { vm.setQuery(it) },
            placeholder = { Text("搜索频道") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = TextDim) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp)
        )

        if (!favoritesOnly) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
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
                    text = if (favoritesOnly) "还没有收藏，点频道名右侧的星标收藏" else "这里还没有频道",
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
                        subtitle = vm.programmeLine(channel),
                        isTv = isTv,
                        modifier = if (index == 0) {
                            Modifier.focusRequester(firstFocus)
                        } else {
                            Modifier
                        },
                        onClick = { vm.play(channel) },
                        onToggleFavourite = { vm.toggleFavorite(channel) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(
    channel: Channel,
    index: Int,
    favourite: Boolean,
    subtitle: String?,
    isTv: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onToggleFavourite: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
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
            ChannelLogo(
                url = channel.logoUrl,
                fallback = channel.name,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(if (isTv) 76.dp else 52.dp),
                fallbackSize = if (isTv) 30 else 22
            )

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

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color(0xB3000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(5.dp)
                        .background(LiveDot, CircleShape)
                )
                Spacer(Modifier.width(4.dp))
                Text("直播", color = LiveDot, fontSize = 10.sp)
            }

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
            text = subtitle ?: "无节目单",
            color = TextDim,
            fontSize = if (isTv) 12.sp else 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// MARK: - 配置

@Composable
private fun ConfigScreen(vm: AppViewModel, state: UiState, onDone: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var m3uInput by remember { mutableStateOf("") }
    var m3uName by remember { mutableStateOf("") }
    var xtreamBase by remember { mutableStateOf("") }
    var xtreamUser by remember { mutableStateOf("") }
    var xtreamPass by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("配置中心", color = TextMain, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    "共 ${state.playlists.size} 个配置",
                    color = TextDim,
                    fontSize = 12.sp
                )
            }
            if (state.channels.isNotEmpty()) {
                TextButton(onClick = onDone) { Text("去看频道", color = Accent) }
            }
        }

        Spacer(Modifier.height(10.dp))

        TabRow(selectedTabIndex = tab, containerColor = Panel, contentColor = Accent) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("M3U 订阅") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Xtream") })
        }

        Spacer(Modifier.height(12.dp))

        if (tab == 0) {
            OutlinedTextField(
                value = m3uInput,
                onValueChange = { m3uInput = it },
                label = { Text("订阅地址或 M3U 内容") },
                placeholder = { Text("http://host:port/xxx.m3u") },
                minLines = 3,
                maxLines = 6,
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

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (tab == 0) vm.importM3U(m3uInput, m3uName)
                else vm.importXtream(xtreamBase, xtreamUser, xtreamPass, null)
            },
            enabled = !state.importing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.importing) "导入中…" else "导入")
        }

        state.importError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Danger, fontSize = 12.sp)
        }
        state.importNotice?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = LiveDot, fontSize = 12.sp)
        }

        Spacer(Modifier.height(18.dp))
        if (state.playlists.isNotEmpty()) {
            Text("已添加的配置", color = TextDim, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            state.playlists.forEach { playlist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(playlist.name, color = TextMain, fontSize = 15.sp)
                        Text(
                            playlist.sourceUrl,
                            color = TextDim,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = { vm.removePlaylist(playlist.id) }) {
                        Text("删除", color = Danger)
                    }
                }
                HorizontalDivider(color = Color(0xFF232326))
            }
        }
    }
}

// MARK: - 播放页

@Composable
private fun PlayerScreen(vm: AppViewModel, state: UiState) {
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

    fun applyFullscreen(on: Boolean) {
        fullscreen = on
        chromeVisible = true
        val act = activity ?: return
        val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
        if (on) {
            // 电视本来就是横屏，别去改方向
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

    // 节目进度条每秒推进一格
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
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    player = vm.player()
                }
            },
            update = { it.player = vm.player() }
        )

        if (state.isBuffering) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0x99FFFFFF))
            }
        }

        // 顶部：返回 / 频道名 + 节目 / 收藏 / 全屏（APTV 的顶栏样式）
        if (showChrome) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xCC000000), Color(0x00000000))
                        )
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
                        text = if (isTv) "遥控器 ↑↓ 换台" else (state.currentProgramme ?: channel.displayGroup),
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

        // 底部：节目进度 + 上一台/播放/下一台
        if (showChrome) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0x00000000), Color(0xF2000000))
                        )
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

private fun Context.findActivity(): Activity? {
    var context: Context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
