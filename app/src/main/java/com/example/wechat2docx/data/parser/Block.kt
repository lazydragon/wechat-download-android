package com.example.wechat2docx.data.parser

/** A formatted text run inside a block. */
data class InlineRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val href: String? = null,
)

/** Top-level block in the parsed article. */
sealed class Block {
    data class Heading(val level: Int, val runs: List<InlineRun>) : Block()
    data class Paragraph(val runs: List<InlineRun>) : Block()
    data class BulletItem(val runs: List<InlineRun>) : Block()
    data class NumberedItem(val n: Int, val runs: List<InlineRun>) : Block()
    data class Quote(val runs: List<InlineRun>) : Block()
    data class Image(val url: String, val alt: String?) : Block()
    data class MediaPlaceholder(val kind: String, val url: String) : Block()
}

/** Result of parsing a WeChat article HTML. */
data class ParsedArticle(
    val title: String,
    val author: String?,
    val publishTime: String?,
    val sourceUrl: String,
    val blocks: List<Block>,
)
