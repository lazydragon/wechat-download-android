package com.example.wechat2docx.data.url

import android.net.Uri

object UrlExtractor {

    private val URL_RE = Regex(
        "https?://mp\\.weixin\\.qq\\.com/[\\w\\-./?=&%#~+:@!$,;]+"
    )

    /** Pulls a WeChat article URL out of arbitrary share text. */
    fun extract(text: String?): String? = text?.let { URL_RE.find(it)?.value }

    /** Returns true if the URL is an http(s) URL on mp.weixin.qq.com. */
    fun isWeChatUrl(url: String): Boolean {
        if (!url.startsWith("http", ignoreCase = true)) return false
        return runCatching { Uri.parse(url).host?.equals("mp.weixin.qq.com", ignoreCase = true) == true }
            .getOrDefault(false)
    }
}
