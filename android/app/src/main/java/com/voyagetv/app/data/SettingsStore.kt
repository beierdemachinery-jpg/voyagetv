package com.voyagetv.app.data

import android.content.Context
import java.io.File

/**
 * 设置页背后的那份数据。照 APTV 的分组来（通用 / 视频 / 频道 / 节目单 / 其他）。
 * 全部落在 SharedPreferences，重启后保留。
 */
data class AppSettings(
    val defaultTab: Int = 0,                 // 0 频道 1 收藏 2 配置 3 设置
    val allowBackground: Boolean = true,     // 允许后台播放
    val autoNextOnError: Boolean = false,    // 播放出错自动下一台（APTV 的「自动下一频道」）
    val aspectRatio: String = "fit",         // fit 适应 / fill 填充 / zoom 拉伸
    val channelOrder: String = "index",      // index 按序号 / name 按名称
    val showDelay: Boolean = true,           // 显示延迟（真实测量出来的首字节耗时）
    val showIndex: Boolean = true,           // 显示序号
    val showGroup: Boolean = true,           // 显示分组
    val showLogo: Boolean = true,            // 显示台标
    val showResolution: Boolean = true,      // 显示分辨率和帧率（当前播放的频道才有）
    val autoEpg: Boolean = true,             // 自动加载 EPG
    val defaultEpgUrl: String = "https://epg.aptv.app/"
)

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("voyagetv_settings", Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            defaultTab = prefs.getInt("defaultTab", d.defaultTab),
            allowBackground = prefs.getBoolean("allowBackground", d.allowBackground),
            autoNextOnError = prefs.getBoolean("autoNextOnError", d.autoNextOnError),
            aspectRatio = prefs.getString("aspectRatio", d.aspectRatio) ?: d.aspectRatio,
            channelOrder = prefs.getString("channelOrder", d.channelOrder) ?: d.channelOrder,
            showDelay = prefs.getBoolean("showDelay", d.showDelay),
            showIndex = prefs.getBoolean("showIndex", d.showIndex),
            showGroup = prefs.getBoolean("showGroup", d.showGroup),
            showLogo = prefs.getBoolean("showLogo", d.showLogo),
            showResolution = prefs.getBoolean("showResolution", d.showResolution),
            autoEpg = prefs.getBoolean("autoEpg", d.autoEpg),
            defaultEpgUrl = prefs.getString("defaultEpgUrl", d.defaultEpgUrl) ?: d.defaultEpgUrl
        )
    }

    fun save(settings: AppSettings) {
        prefs.edit()
            .putInt("defaultTab", settings.defaultTab)
            .putBoolean("allowBackground", settings.allowBackground)
            .putBoolean("autoNextOnError", settings.autoNextOnError)
            .putString("aspectRatio", settings.aspectRatio)
            .putString("channelOrder", settings.channelOrder)
            .putBoolean("showDelay", settings.showDelay)
            .putBoolean("showIndex", settings.showIndex)
            .putBoolean("showGroup", settings.showGroup)
            .putBoolean("showLogo", settings.showLogo)
            .putBoolean("showResolution", settings.showResolution)
            .putBoolean("autoEpg", settings.autoEpg)
            .putString("defaultEpgUrl", settings.defaultEpgUrl)
            .apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    fun cacheSize(): Long {
        val dirs = listOfNotNull(appContext.cacheDir, appContext.externalCacheDir)
        return dirs.sumOf { dir -> dir.walkTopDown().filter { it.isFile }.map { it.length() }.toList().sum() }
    }

    fun clearCache(): Long {
        val before = cacheSize()
        listOfNotNull(appContext.cacheDir, appContext.externalCacheDir).forEach { dir ->
            runCatching { dir.listFiles()?.forEach { it.deleteRecursively() } }
        }
        // 台标那份内存缓存也跟着丢，免得清完了界面还显示旧图
        return before
    }
}

/** 字节数变成「51.77M」这种样子。 */
fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "0B"
    bytes < 1024L -> "${bytes}B"
    bytes < 1024L * 1024L -> "%.1fK".format(bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> "%.2fM".format(bytes / (1024.0 * 1024.0))
    else -> "%.2fG".format(bytes / (1024.0 * 1024.0 * 1024.0))
}

/** 文件是否存在、多大，给缓存用。 */
fun fileSize(file: File): Long = if (file.exists()) file.length() else 0L
