package com.voyagetv.app.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/**
 * 明确按 HLS 处理，不靠 URL 后缀猜。
 *
 * 源站的流地址是 `ysp.php?id=cctv1`，没有后缀；ExoPlayer 默认会按后缀推断类型，
 * 那样会当成渐进式文件去播并失败。这里直接构造 HlsMediaSource。
 */
class HlsSourceFactory(
    private val upstream: DataSource.Factory
) : MediaSource.Factory {

    private var drmProvider: DrmSessionManagerProvider? = null
    private var errorPolicy: LoadErrorHandlingPolicy? = null

    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory {
        drmProvider = provider
        return this
    }

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory {
        errorPolicy = policy
        return this
    }

    override fun getSupportedTypes(): IntArray = intArrayOf(C.CONTENT_TYPE_HLS)

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val uri: Uri = mediaItem.localConfiguration?.uri ?: Uri.EMPTY
        val dataSourceFactory = RewritingDataSource.Factory(upstream, uri)
        val hlsFactory = HlsMediaSource.Factory(dataSourceFactory)
            .setAllowChunklessPreparation(true)
        drmProvider?.let { hlsFactory.setDrmSessionManagerProvider(it) }
        errorPolicy?.let { hlsFactory.setLoadErrorHandlingPolicy(it) }
        return hlsFactory.createMediaSource(mediaItem)
    }
}
