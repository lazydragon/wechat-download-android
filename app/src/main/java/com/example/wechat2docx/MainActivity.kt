package com.example.wechat2docx

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import com.example.wechat2docx.data.url.UrlExtractor
import com.example.wechat2docx.ui.nav.AppNav
import com.example.wechat2docx.ui.nav.Routes
import com.example.wechat2docx.ui.theme.WeChat2DocxTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** Holds the URL pulled from the latest intent (or null). Compose observes this. */
    private val pendingUrl = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingUrl.value = extractUrl(intent)
        warnIfActionWithoutUrl(intent)

        setContent {
            WeChat2DocxTheme {
                val nav = rememberNavController()
                val pending: String? by pendingUrl.collectAsState()
                LaunchedEffect(pending) {
                    val url = pending
                    if (url != null) {
                        nav.navigate(Routes.convert(url))
                        pendingUrl.value = null
                    }
                }
                AppNav(nav)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val extracted = extractUrl(intent)
        warnIfActionWithoutUrl(intent)
        if (extracted != null) {
            pendingUrl.value = extracted
        }
    }

    private fun extractUrl(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                UrlExtractor.extract(text)
            }
            Intent.ACTION_VIEW -> {
                val s = intent.dataString
                if (s != null && UrlExtractor.isWeChatUrl(s)) s else null
            }
            else -> null
        }
    }

    private fun warnIfActionWithoutUrl(intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        if (action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (UrlExtractor.extract(text) == null) {
                Toast.makeText(this, getString(R.string.error_no_url), Toast.LENGTH_LONG).show()
            }
        } else if (action == Intent.ACTION_VIEW) {
            val s = intent.dataString
            if (s != null && !UrlExtractor.isWeChatUrl(s)) {
                Toast.makeText(this, getString(R.string.error_not_wechat_url), Toast.LENGTH_LONG).show()
            }
        }
    }
}
