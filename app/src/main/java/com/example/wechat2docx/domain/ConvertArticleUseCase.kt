package com.example.wechat2docx.domain

import android.app.Application
import android.net.Uri
import com.example.wechat2docx.R
import com.example.wechat2docx.data.docx.DocxBuilder
import com.example.wechat2docx.data.net.WeChatHttpClient
import com.example.wechat2docx.data.parser.Block
import com.example.wechat2docx.data.parser.WeChatHtmlParser
import com.example.wechat2docx.data.prefs.SettingsRepository
import com.example.wechat2docx.data.storage.DocxSaver
import com.example.wechat2docx.data.storage.SaveResult
import com.example.wechat2docx.data.url.UrlExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class ConvertArticleUseCase(
    private val app: Application,
    private val settings: SettingsRepository,
) {

    fun run(url: String): Flow<ConversionState> = flow {
        try {
            if (!UrlExtractor.isWeChatUrl(url)) {
                emit(ConversionState.Failure(app.getString(R.string.error_not_wechat_url)))
                return@flow
            }

            emit(ConversionState.Fetching(url))
            val html = try {
                WeChatHttpClient.fetchHtml(url)
            } catch (t: Throwable) {
                emit(
                    ConversionState.Failure(
                        app.getString(R.string.error_network) + ": ${t.message ?: "fetch failed"}"
                    )
                )
                return@flow
            }

            emit(ConversionState.Parsing)
            val article = try {
                WeChatHtmlParser.parse(html, url)
            } catch (t: Throwable) {
                emit(
                    ConversionState.Failure(
                        app.getString(R.string.error_parse) + ": ${t.message ?: "parse failed"}"
                    )
                )
                return@flow
            }

            val embed = settings.embedImages.first()
            val imageUrls = article.blocks.filterIsInstance<Block.Image>().map { it.url }.distinct()
            val images = mutableMapOf<String, ByteArray>()
            if (embed && imageUrls.isNotEmpty()) {
                emit(ConversionState.DownloadingImages(0, imageUrls.size))
                imageUrls.forEachIndexed { i, u ->
                    runCatching {
                        WeChatHttpClient.fetchBytes(u, "https://mp.weixin.qq.com/")
                    }.onSuccess { images[u] = it }
                    emit(ConversionState.DownloadingImages(i + 1, imageUrls.size))
                }
            }

            emit(ConversionState.BuildingDocx)
            val bytes = try {
                DocxBuilder.build(article, images, embed)
            } catch (t: Throwable) {
                emit(
                    ConversionState.Failure("DOCX build failed: ${t.message ?: t.javaClass.simpleName}")
                )
                return@flow
            }

            val sanitizedName = DocxSaver.sanitize(article.title).take(80)
            emit(ConversionState.Saving(sanitizedName))

            val treeUriStr = settings.treeUri.first()
            val treeUri = treeUriStr?.let { runCatching { Uri.parse(it) }.getOrNull() }
            val saveRes = DocxSaver.save(app, treeUri, article.title, bytes)
            when (saveRes) {
                is SaveResult.Success -> {
                    val display = "$sanitizedName.docx → ${saveRes.displayLocation}"
                    settings.setLastResult(display)
                    emit(ConversionState.Success(sanitizedName, saveRes.displayLocation))
                }
                SaveResult.NoTreeUri -> emit(
                    ConversionState.Failure(app.getString(R.string.error_no_dir))
                )
                is SaveResult.Error -> emit(
                    ConversionState.Failure(
                        app.getString(R.string.error_save) + ": ${saveRes.msg}"
                    )
                )
            }
        } catch (t: Throwable) {
            emit(ConversionState.Failure(t.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)
}
