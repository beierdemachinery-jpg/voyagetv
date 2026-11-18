package com.voyagetv.app.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/** 极简 HTTP 取文本。错误一律抛 IOException，并在 message 里带状态码，界面上原样展示。 */
object Http {

    fun getText(url: String, timeoutMillis: Int = 20000): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "VoyageTV/1.0 (Android)")
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Accept-Encoding", "gzip")
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("服务器返回 $code")
            }
            val encoding = connection.contentEncoding
            val stream = if (encoding != null && encoding.contains("gzip", ignoreCase = true)) {
                GZIPInputStream(connection.inputStream)
            } else {
                connection.inputStream
            }
            return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
