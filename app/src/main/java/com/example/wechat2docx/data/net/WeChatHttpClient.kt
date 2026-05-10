package com.example.wechat2docx.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object WeChatHttpClient {

    const val UA: String =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Safari/537.36"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetchHtml(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            r.body?.string() ?: error("Empty response body")
        }
    }

    suspend fun fetchBytes(url: String, referer: String? = null): ByteArray =
        withContext(Dispatchers.IO) {
            val b = Request.Builder().url(url).header("User-Agent", UA)
            if (referer != null) b.header("Referer", referer)
            client.newCall(b.build()).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                r.body?.bytes() ?: error("Empty body")
            }
        }
}
