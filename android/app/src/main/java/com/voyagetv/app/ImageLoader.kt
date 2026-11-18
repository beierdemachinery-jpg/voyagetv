package com.voyagetv.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 台标加载。没有引入 Coil，自己用 HttpURLConnection + BitmapFactory，加一个内存 LRU。
 * 台标只是装饰，失败就退化成频道名首字，不弹错误。
 */
private object LogoCache {
    private val cache = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(url: String): Bitmap? = cache.get(url)

    fun put(url: String, bitmap: Bitmap) {
        cache.put(url, bitmap)
    }
}

private suspend fun fetchBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "VoyageTV/1.0 (Android)")
        }
        connection.inputStream.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}

@Composable
fun ChannelLogo(
    url: String?,
    fallback: String,
    modifier: Modifier = Modifier,
    fallbackSize: Int = 22
) {
    var image by remember(url) {
        mutableStateOf(url?.let { LogoCache.get(it) }?.asImageBitmap())
    }

    LaunchedEffect(url) {
        if (image != null || url.isNullOrBlank()) return@LaunchedEffect
        val bitmap = fetchBitmap(url)
        if (bitmap != null) {
            LogoCache.put(url, bitmap)
            image = bitmap.asImageBitmap()
        }
    }

    val current = image
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = fallback.take(1).uppercase(),
                color = Color(0xFFE8EEF9),
                fontSize = fallbackSize.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
