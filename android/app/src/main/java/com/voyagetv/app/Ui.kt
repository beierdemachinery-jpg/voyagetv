package com.voyagetv.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

private val Accent = Color(0xFF4CC2FF)
private val Bg = Color(0xFF070B12)
private val CardBg = Color(0xFF111827)
private val Danger = Color(0xFFFF5A5F)

@Composable
fun VoyageTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.Black,
            background = Bg,
            onBackground = Color(0xFFE8EEF9),
            surface = CardBg,
            onSurface = Color(0xFFE8EEF9),
            error = Danger
        ),
        content = content
    )
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val state by vm.uiState.collectAsState()
    var showImport by remember { mutableStateOf(false) }

    when {
        state.current != null -> PlayerScreen(vm, state)
        showImport || state.playlists.isEmpty() -> ImportScreen(
            vm = vm,
            state = state,
            closable = state.playlists.isNotEmpty(),
            onClose = {
                showImport = false
                vm.clearNotices()
            }
        )

        else -> ChannelsScreen(vm, state, onImport = { showImport = true })
    }
}

// MARK: - 导入

@Composable
private fun ImportScreen(
    vm: AppViewModel,
    state: UiState,
    closable: Boolean,
    onClose: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    var m3uInput by remember { mutableStateOf("") }
    var m3uName by remember { mutableStateOf("") }
    var xtreamBase by remember { mutableStateOf("") }
    var xtreamUser by remember { mutableStateOf("") }
    var xtreamPass by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "VoyageTV",
                color = Color(0xFFE8EEF9),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (closable) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = Color(0xFF8A93A6))
                }
            }
        }
        Text(
            text = "导入你自己的订阅，频道内容由你提供",
            color = Color(0xFF8A93A6),
            fontSize = 13.sp
        )
        Spacer(Modifier.height(18.dp))

        TabRow(selectedTabIndex = tab, containerColor = CardBg, contentColor = Accent) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("M3U / M3U8") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Xtream Codes") })
        }
        Spacer(Modifier.height(16.dp))

        if (tab == 0) {
            OutlinedTextField(
                value = m3uInput,
                onValueChange = { m3uInput = it },
                label = { Text("订阅地址或 M3U 内容") },
                placeholder = { Text("http://nas.example.com:1122/ysp.m3u") },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
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
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = xtreamUser,
                onValueChange = { xtreamUser = it },
                label = { Text("用户名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = xtreamPass,
                onValueChange = { xtreamPass = it },
                label = { Text("密码") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                if (tab == 0) {
                    vm.importM3U(m3uInput, m3uName)
                } else {
                    vm.importXtream(xtreamBase, xtreamUser, xtreamPass, null)
                }
            },
            enabled = !state.importing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.importing) "导入中…" else "导入")
        }

        state.importError?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Danger, fontSize = 13.sp)
        }
        state.importNotice?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Accent, fontSize = 13.sp)
        }

        if (state.playlists.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("已导入的订阅", color = Color(0xFF8A93A6), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            state.playlists.forEach { playlist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(playlist.name, color = Color(0xFFE8EEF9), fontSize = 15.sp)
                        Text(
                            playlist.sourceUrl,
                            color = Color(0xFF8A93A6),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = { vm.removePlaylist(playlist.id) }) {
                        Text("删除", color = Danger)
                    }
                }
                HorizontalDivider(color = Color(0xFF1F2937))
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { vm.refresh() }) { Text("重新拉取频道", color = Accent) }
        }
    }
}

// MARK: - 频道列表

@Composable
private fun ChannelsScreen(vm: AppViewModel, state: UiState, onImport: () -> Unit) {
    val visible = vm.visibleChannels()
    val groups = vm.groups()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "VoyageTV",
                color = Color(0xFFE8EEF9),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { vm.refresh() }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "刷新", tint = Color(0xFF8A93A6))
            }
            IconButton(onClick = onImport) {
                Icon(Icons.Rounded.Add, contentDescription = "添加订阅", tint = Accent)
            }
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = { vm.setQuery(it) },
            placeholder = { Text("搜索频道") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )

        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(groups) { group ->
                FilterChip(
                    selected = state.selectedGroup == group,
                    onClick = { vm.selectGroup(group) },
                    label = { Text(group, fontSize = 13.sp) }
                )
            }
        }

        state.importError?.let {
            Text(
                it,
                color = Danger,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        if (state.loadingChannels && state.channels.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
            return@Column
        }

        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("这里还没有频道", color = Color(0xFF8A93A6), fontSize = 14.sp)
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(visible, key = { it.id }) { channel ->
                ChannelRow(
                    channel = channel,
                    favourite = channel.id in state.favorites,
                    subtitle = vm.programmeLine(channel),
                    onClick = { vm.play(channel) },
                    onToggleFavourite = { vm.toggleFavorite(channel) }
                )
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: Channel,
    favourite: Boolean,
    subtitle: String?,
    onClick: () -> Unit,
    onToggleFavourite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(CardBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = channel.name.take(1).uppercase(),
                color = Accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = channel.name,
                color = Color(0xFFE8EEF9),
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle ?: channel.displayGroup,
                color = Color(0xFF8A93A6),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onToggleFavourite) {
            Icon(
                imageVector = if (favourite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = "收藏",
                tint = if (favourite) Accent else Color(0xFF4B5563)
            )
        }
    }
    HorizontalDivider(color = Color(0xFF16202E))
}

// MARK: - 播放页

@Composable
private fun PlayerScreen(vm: AppViewModel, state: UiState) {
    val channel = state.current ?: return
    val activity = LocalContext.current.findActivity()
    var fullscreen by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }

    fun applyFullscreen(on: Boolean) {
        fullscreen = on
        chromeVisible = true
        val act = activity ?: return
        val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
        if (on) {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
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

    // 离开播放页时把屏幕方向和系统栏恢复回去，否则列表页会停留在横屏全屏
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

    // 全屏时控件 4 秒后自动隐藏，点屏幕再唤出
    LaunchedEffect(fullscreen, chromeVisible) {
        if (fullscreen && chromeVisible) {
            delay(4000)
            chromeVisible = false
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

        // 全屏时点屏幕唤出/收起控件。PlayerView 没挂 controller 时不处理点击，所以这里单独铺一层
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (fullscreen) chromeVisible = !chromeVisible
                }
        )

        if (showChrome) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { vm.closePlayer() },
                    modifier = Modifier.background(Color(0x33FFFFFF), CircleShape)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = channel.name,
                    color = Color.White,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { vm.toggleFavorite(channel) },
                    modifier = Modifier.background(Color(0x33FFFFFF), CircleShape)
                ) {
                    Icon(
                        imageVector = if (channel.id in state.favorites) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = "收藏",
                        tint = Color.White
                    )
                }
            }
        }

        if (state.isBuffering) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0x99FFFFFF))
            }
        }

        if (showChrome) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                state.playerError?.let { message ->
                    Box(
                        modifier = Modifier
                            .background(Danger.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(message, color = Color(0xFFFFD5D6), fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                }

                state.nextProgramme?.let { next ->
                    Text("接下来", color = Color(0x99FFFFFF), fontSize = 12.sp)
                    Text(next, color = Color(0xE6FFFFFF), fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                } ?: state.currentProgramme?.let { current ->
                    Text(current, color = Color(0xE6FFFFFF), fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                }

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { vm.playPrevious() }, modifier = Modifier.size(56.dp)) {
                        Icon(
                            Icons.Rounded.SkipPrevious,
                            contentDescription = "上一台",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    IconButton(
                        onClick = { vm.togglePlayPause() },
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color(0x33FFFFFF), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isPlaying) "暂停" else "播放",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    IconButton(onClick = { vm.playNext() }, modifier = Modifier.size(56.dp)) {
                        Icon(
                            Icons.Rounded.SkipNext,
                            contentDescription = "下一台",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    IconButton(
                        onClick = { applyFullscreen(!fullscreen) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                            contentDescription = if (fullscreen) "退出全屏" else "全屏",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
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
