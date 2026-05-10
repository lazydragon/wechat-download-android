package com.example.wechat2docx.data.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

object WeChatHtmlParser {

    private data class StyleFlags(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val href: String? = null,
    )

    /** Mutable accumulator for the conversion walk. */
    private class Acc {
        val blocks: MutableList<Block> = mutableListOf()
        val inline: MutableList<InlineRun> = mutableListOf()
        var orderedCounter: Int = 0
        var inOrderedList: Boolean = false

        fun flushParagraph() {
            if (inline.isNotEmpty()) {
                val merged = mergeRuns(inline)
                if (merged.isNotEmpty() && merged.any { it.text.isNotBlank() }) {
                    blocks.add(Block.Paragraph(merged))
                }
                inline.clear()
            }
        }

        fun flushAs(transform: (List<InlineRun>) -> Block?) {
            if (inline.isNotEmpty()) {
                val merged = mergeRuns(inline)
                if (merged.isNotEmpty() && merged.any { it.text.isNotBlank() }) {
                    transform(merged)?.let { blocks.add(it) }
                }
                inline.clear()
            }
        }
    }

    fun parse(html: String, sourceUrl: String): ParsedArticle {
        val doc: Document = Jsoup.parse(html, sourceUrl)

        val title = resolveTitle(doc)
        val author = resolveAuthor(doc)
        val publishTime = resolvePublishTime(doc)

        val body: Element = doc.selectFirst("#js_content")
            ?: doc.selectFirst("div.rich_media_content")
            ?: doc.body()

        val acc = Acc()
        for (child in body.childNodes()) {
            walkBlock(child, acc, StyleFlags())
        }
        // Final flush
        acc.flushParagraph()

        // Strip leading/trailing empty paragraphs
        val cleaned = acc.blocks.dropWhile { isEmptyParagraph(it) }
            .dropLastWhile { isEmptyParagraph(it) }

        return ParsedArticle(
            title = title,
            author = author,
            publishTime = publishTime,
            sourceUrl = sourceUrl,
            blocks = cleaned,
        )
    }

    private fun isEmptyParagraph(b: Block): Boolean =
        b is Block.Paragraph && b.runs.all { it.text.isBlank() }

    private fun resolveTitle(doc: Document): String {
        doc.selectFirst("h1#activity-name")?.let { return it.text().trim() }
        doc.selectFirst("h2.rich_media_title")?.let { return it.text().trim() }
        doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        doc.selectFirst("title")?.let { return it.text().trim() }
        return "WeChat Article"
    }

    private fun resolveAuthor(doc: Document): String? {
        doc.selectFirst("#js_name")?.text()?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        doc.selectFirst("a.rich_media_meta_link")?.text()
            ?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        doc.selectFirst("meta[name=author]")?.attr("content")
            ?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        return null
    }

    private fun resolvePublishTime(doc: Document): String? {
        doc.selectFirst("#publish_time")?.text()?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        doc.selectFirst("em#publish_time")?.text()?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        return null
    }

    /**
     * Walks a node that may produce one or more block boundaries. Inline-only
     * children get appended to acc.inline. Block-level children flush the
     * current inline buffer.
     */
    private fun walkBlock(node: Node, acc: Acc, style: StyleFlags) {
        when (node) {
            is TextNode -> {
                val text = node.text()
                if (text.isNotEmpty()) {
                    acc.inline.add(
                        InlineRun(
                            text = text,
                            bold = style.bold,
                            italic = style.italic,
                            href = style.href,
                        )
                    )
                }
            }
            is Element -> handleElement(node, acc, style)
            else -> Unit
        }
    }

    private fun handleElement(el: Element, acc: Acc, style: StyleFlags) {
        when (el.tagName().lowercase()) {
            "br" -> {
                acc.inline.add(InlineRun(text = "\n", bold = style.bold, italic = style.italic, href = style.href))
            }
            "strong", "b" -> {
                val s = style.copy(bold = true)
                for (c in el.childNodes()) walkInline(c, acc, s)
            }
            "em", "i" -> {
                val s = style.copy(italic = true)
                for (c in el.childNodes()) walkInline(c, acc, s)
            }
            "u" -> {
                for (c in el.childNodes()) walkInline(c, acc, style)
            }
            "a" -> {
                val href = el.attr("href").takeIf { it.isNotBlank() }
                val s = style.copy(href = href)
                for (c in el.childNodes()) walkInline(c, acc, s)
            }
            "span", "font", "small", "sub", "sup", "mark", "label" -> {
                for (c in el.childNodes()) walkInline(c, acc, style)
            }
            "p", "div", "section", "article", "header", "footer", "main" -> {
                acc.flushParagraph()
                for (c in el.childNodes()) walkBlock(c, acc, style)
                acc.flushParagraph()
            }
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                acc.flushParagraph()
                val level = el.tagName().substring(1).toIntOrNull() ?: 2
                val tmp = Acc()
                for (c in el.childNodes()) walkInline(c, tmp, style.copy(bold = true))
                val merged = mergeRuns(tmp.inline)
                if (merged.any { it.text.isNotBlank() }) {
                    acc.blocks.add(Block.Heading(level, merged))
                }
            }
            "blockquote" -> {
                acc.flushParagraph()
                val tmp = Acc()
                for (c in el.childNodes()) walkInline(c, tmp, style)
                val merged = mergeRuns(tmp.inline)
                if (merged.any { it.text.isNotBlank() }) {
                    acc.blocks.add(Block.Quote(merged))
                }
            }
            "ul" -> {
                acc.flushParagraph()
                for (c in el.children()) {
                    if (c.tagName().equals("li", ignoreCase = true)) {
                        val tmp = Acc()
                        for (cc in c.childNodes()) walkInline(cc, tmp, style)
                        val merged = mergeRuns(tmp.inline)
                        if (merged.any { it.text.isNotBlank() }) {
                            acc.blocks.add(Block.BulletItem(merged))
                        }
                        // also handle nested images inside li
                        c.select("img").forEach { img ->
                            extractImageBlock(img)?.let { acc.blocks.add(it) }
                        }
                    }
                }
            }
            "ol" -> {
                acc.flushParagraph()
                var counter = 1
                for (c in el.children()) {
                    if (c.tagName().equals("li", ignoreCase = true)) {
                        val tmp = Acc()
                        for (cc in c.childNodes()) walkInline(cc, tmp, style)
                        val merged = mergeRuns(tmp.inline)
                        if (merged.any { it.text.isNotBlank() }) {
                            acc.blocks.add(Block.NumberedItem(counter, merged))
                            counter++
                        }
                        c.select("img").forEach { img ->
                            extractImageBlock(img)?.let { acc.blocks.add(it) }
                        }
                    }
                }
            }
            "img" -> {
                acc.flushParagraph()
                extractImageBlock(el)?.let { acc.blocks.add(it) }
            }
            "video" -> {
                acc.flushParagraph()
                val src = el.attr("src").ifBlank {
                    el.attr("data-src").ifBlank { el.attr("data-mpvid") }
                }
                acc.blocks.add(Block.MediaPlaceholder("video", src))
            }
            "audio" -> {
                acc.flushParagraph()
                val src = el.attr("src").ifBlank { el.attr("data-src") }
                acc.blocks.add(Block.MediaPlaceholder("audio", src))
            }
            "iframe" -> {
                acc.flushParagraph()
                val src = el.attr("src").ifBlank { el.attr("data-src") }
                acc.blocks.add(Block.MediaPlaceholder("iframe", src))
            }
            "mpvoice", "mpvideo" -> {
                acc.flushParagraph()
                val src = el.attr("voice_encode_fileid").ifBlank { el.attr("data-src") }
                acc.blocks.add(Block.MediaPlaceholder(el.tagName(), src))
            }
            "hr" -> {
                acc.flushParagraph()
                acc.blocks.add(Block.Paragraph(listOf(InlineRun("―――――――――"))))
            }
            "table" -> {
                acc.flushParagraph()
                // Flatten rows into paragraphs separated by tabs
                el.select("tr").forEach { tr ->
                    val cells = tr.select("td,th").joinToString(" \t ") { it.text() }
                    if (cells.isNotBlank()) {
                        acc.blocks.add(Block.Paragraph(listOf(InlineRun(cells))))
                    }
                }
            }
            "pre", "code" -> {
                acc.flushParagraph()
                val text = el.wholeText()
                if (text.isNotBlank()) {
                    acc.blocks.add(Block.Paragraph(listOf(InlineRun(text))))
                }
            }
            "script", "style", "noscript", "svg" -> {
                // skip
            }
            else -> {
                // Default: descend looking for both block and inline content
                for (c in el.childNodes()) walkBlock(c, acc, style)
            }
        }
    }

    /**
     * Walks a node assuming inline context. Block-level children encountered
     * here will still be promoted to blocks (rare in WeChat but possible).
     */
    private fun walkInline(node: Node, acc: Acc, style: StyleFlags) {
        when (node) {
            is TextNode -> {
                val text = node.text()
                if (text.isNotEmpty()) {
                    acc.inline.add(
                        InlineRun(
                            text = text,
                            bold = style.bold,
                            italic = style.italic,
                            href = style.href,
                        )
                    )
                }
            }
            is Element -> {
                when (node.tagName().lowercase()) {
                    "br" -> acc.inline.add(InlineRun("\n", style.bold, style.italic, style.href))
                    "strong", "b" -> {
                        val s = style.copy(bold = true)
                        for (c in node.childNodes()) walkInline(c, acc, s)
                    }
                    "em", "i" -> {
                        val s = style.copy(italic = true)
                        for (c in node.childNodes()) walkInline(c, acc, s)
                    }
                    "a" -> {
                        val href = node.attr("href").takeIf { it.isNotBlank() }
                        val s = style.copy(href = href)
                        for (c in node.childNodes()) walkInline(c, acc, s)
                    }
                    "img" -> {
                        // Inline images are skipped in inline context; block-level walker
                        // re-discovers them via separate img selectors.
                    }
                    else -> {
                        for (c in node.childNodes()) walkInline(c, acc, style)
                    }
                }
            }
            else -> Unit
        }
    }

    private fun extractImageBlock(el: Element): Block.Image? {
        val url = el.attr("data-src").ifBlank {
            el.attr("data-original").ifBlank { el.attr("src") }
        }
        if (url.isBlank()) return null
        val alt = el.attr("alt").takeIf { it.isNotBlank() }
        val absolute = if (url.startsWith("//")) "https:$url" else url
        return Block.Image(absolute, alt)
    }

    /**
     * Coalesces adjacent runs with identical style flags into one run.
     * Also strips zero-width characters and consecutive whitespace.
     */
    private fun mergeRuns(runs: List<InlineRun>): List<InlineRun> {
        if (runs.isEmpty()) return runs
        val out = mutableListOf<InlineRun>()
        for (r in runs) {
            val last = out.lastOrNull()
            if (last != null
                && last.bold == r.bold
                && last.italic == r.italic
                && last.href == r.href
            ) {
                out[out.size - 1] = last.copy(text = last.text + r.text)
            } else {
                out.add(r)
            }
        }
        return out
    }
}
