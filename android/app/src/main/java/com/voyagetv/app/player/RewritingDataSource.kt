package com.voyagetv.app.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.voyagetv.app.data.PlaylistRewriter
import java.io.IOError
import java.io.IOException
import java.io.OutputStream
import java.io.ByteArrayOutputStream

/**
 * 只对"清单那一条请求"做事：把整份下载下来、按 PlaylistRewriter 规范化，再把改写后的
 * 字节喂给 ExoPlayer；分片请求完全不拦，直连上游。
 *
 * 这样 `ysp.php?id=cctv1` 这种没有 `.m3u8` 后缀、返回 text/html、混着私有标签的源，
 * 也能被 HLS 解析器正常吃下。
 */
class RewritingDataSource(
    private val upstream: DataSource,
    private val manifestUri: Uri
) : BaseDataSource(true) {

    private var payload: ByteArray? = null
    private var position = 0

    override fun getUri(): Uri? = upstream.uri

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        if (dataSpec.uri == manifestUri) {
            upstream.open(dataSpec)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = upstream.read(buffer, 0, buffer.size)
                if (read == C.RESULT_END_OF_INPUT) break
                if (read <= 0) continue
                out.write(buffer, 0, read)
            }
            val raw = out.toByteArray()
            val fixed = PlaylistRewriter.rewrite(raw, dataSpec.uri.toString()) ?: raw
            payload = fixed
            position = 0
            return fixed.size.toLong()
        }
        payload = null
        position = 0
        return upstream.open(dataSpec)
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val body = payload
        if (body != null) {
            if (position >= body.size) return C.RESULT_END_OF_INPUT
            val count = minOf(length, body.size - position)
            System.arraycopy(body, position, buffer, offset, count)
            position += count
            return count
        }
        return upstream.read(buffer, offset, length)
    }

    @Throws(IOException::class)
    override fun close() {
        payload = null
        position = 0
        upstream.close()
    }

    class Factory(
        private val upstream: DataSource.Factory,
        private val manifestUri: Uri
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            RewritingDataSource(upstream.createDataSource(), manifestUri)
    }
}
