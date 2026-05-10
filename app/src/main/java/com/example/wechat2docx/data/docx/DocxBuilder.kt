package com.example.wechat2docx.data.docx

import android.graphics.BitmapFactory
import com.example.wechat2docx.data.parser.Block
import com.example.wechat2docx.data.parser.InlineRun
import com.example.wechat2docx.data.parser.ParsedArticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.util.Units
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

object DocxBuilder {

    private const val MAX_IMAGE_WIDTH_PX = 500

    suspend fun build(
        article: ParsedArticle,
        images: Map<String, ByteArray>,
        embedImages: Boolean,
    ): ByteArray = withContext(Dispatchers.IO) {
        val doc = XWPFDocument()

        // Title
        val titlePara = doc.createParagraph()
        titlePara.alignment = ParagraphAlignment.CENTER
        titlePara.spacingAfter = 240
        val tr = titlePara.createRun()
        tr.setText(article.title)
        tr.isBold = true
        tr.fontSize = 22

        // Meta line: author · publish time
        if (article.author != null || article.publishTime != null) {
            val meta = doc.createParagraph()
            meta.alignment = ParagraphAlignment.CENTER
            val mr = meta.createRun()
            mr.setText(listOfNotNull(article.author, article.publishTime).joinToString("  ·  "))
            mr.isItalic = true
            mr.fontSize = 10
            mr.color = "888888"
        }

        // Source URL
        val src = doc.createParagraph()
        src.alignment = ParagraphAlignment.CENTER
        src.spacingAfter = 240
        try {
            val link = src.createHyperlinkRun(article.sourceUrl)
            link.setText(article.sourceUrl)
            link.fontSize = 9
            link.color = "0000EE"
            link.underline = UnderlinePatterns.SINGLE
        } catch (t: Throwable) {
            val srcRun = src.createRun()
            srcRun.setText(article.sourceUrl)
            srcRun.fontSize = 9
            srcRun.color = "0000EE"
        }

        // Blocks
        for (block in article.blocks) {
            when (block) {
                is Block.Heading -> renderHeading(doc, block)
                is Block.Paragraph -> {
                    val p = doc.createParagraph()
                    renderRuns(p, block.runs)
                }
                is Block.BulletItem -> {
                    val p = doc.createParagraph()
                    p.indentationLeft = 480
                    val bullet = p.createRun()
                    bullet.setText("•  ")
                    renderRuns(p, block.runs)
                }
                is Block.NumberedItem -> {
                    val p = doc.createParagraph()
                    p.indentationLeft = 480
                    val num = p.createRun()
                    num.setText("${block.n}.  ")
                    renderRuns(p, block.runs)
                }
                is Block.Quote -> {
                    val p = doc.createParagraph()
                    p.indentationLeft = 720
                    p.indentationRight = 720
                    renderRuns(p, block.runs.map { it.copy(italic = true) })
                }
                is Block.Image -> {
                    if (embedImages) {
                        renderImage(doc, block, images)
                    } else {
                        renderImagePlaceholder(doc, block)
                    }
                }
                is Block.MediaPlaceholder -> {
                    val p = doc.createParagraph()
                    val r = p.createRun()
                    val urlPart = if (block.url.isBlank()) "(no source)" else block.url
                    r.setText("[${block.kind}] $urlPart")
                    r.isItalic = true
                    r.color = "888888"
                }
            }
        }

        val bos = ByteArrayOutputStream()
        doc.use { it.write(bos) }
        bos.toByteArray()
    }

    private fun renderHeading(doc: XWPFDocument, h: Block.Heading) {
        val p = doc.createParagraph()
        p.spacingBefore = 240
        p.spacingAfter = 120
        val sizeFor = when (h.level) {
            1 -> 24
            2 -> 20
            3 -> 18
            4 -> 16
            5 -> 14
            else -> 12
        }
        for (run in h.runs) {
            val r = if (run.href != null) {
                try {
                    p.createHyperlinkRun(run.href)
                } catch (t: Throwable) {
                    p.createRun()
                }
            } else {
                p.createRun()
            }
            applyText(r, run.text)
            r.isBold = true
            r.fontSize = sizeFor
            if (run.italic) r.isItalic = true
            if (run.href != null) {
                r.color = "0000EE"
                r.underline = UnderlinePatterns.SINGLE
            }
        }
    }

    private fun renderRuns(p: XWPFParagraph, runs: List<InlineRun>) {
        for (run in runs) {
            val r = if (run.href != null) {
                try {
                    p.createHyperlinkRun(run.href)
                } catch (t: Throwable) {
                    p.createRun()
                }
            } else {
                p.createRun()
            }
            applyText(r, run.text)
            if (run.bold) r.isBold = true
            if (run.italic) r.isItalic = true
            if (run.href != null) {
                r.color = "0000EE"
                r.underline = UnderlinePatterns.SINGLE
            }
        }
    }

    /** Splits embedded "\n" so XWPF renders them as line breaks within the paragraph. */
    private fun applyText(r: XWPFRun, text: String) {
        if (text.isEmpty()) return
        val parts = text.split("\n")
        parts.forEachIndexed { i, segment ->
            if (i > 0) r.addBreak()
            if (segment.isNotEmpty()) r.setText(segment)
        }
    }

    private fun renderImage(
        doc: XWPFDocument,
        block: Block.Image,
        images: Map<String, ByteArray>,
    ) {
        val bytes = images[block.url]
        if (bytes == null || bytes.isEmpty()) {
            renderImagePlaceholder(doc, block)
            return
        }
        val (pictureType, ext) = detectPictureType(bytes, block.url)
        if (pictureType == 0) {
            renderImagePlaceholder(doc, block)
            return
        }
        // Determine display size
        val (w, h) = decodeDims(bytes)
        val targetWidthPx = if (w <= 0) MAX_IMAGE_WIDTH_PX
        else minOf(MAX_IMAGE_WIDTH_PX, w)
        val targetHeightPx = if (w <= 0 || h <= 0) (targetWidthPx * 0.6).toInt()
        else (h.toDouble() * targetWidthPx / w).toInt().coerceAtLeast(1)

        val widthEmu = Units.toEMU(targetWidthPx.toDouble())
        val heightEmu = Units.toEMU(targetHeightPx.toDouble())

        val p = doc.createParagraph()
        p.alignment = ParagraphAlignment.CENTER
        p.spacingBefore = 120
        p.spacingAfter = 120
        val r = p.createRun()
        try {
            ByteArrayInputStream(bytes).use { stream ->
                r.addPicture(stream, pictureType, "img.$ext", widthEmu, heightEmu)
            }
        } catch (t: Throwable) {
            // Fall back to URL placeholder if POI rejects the image
            val pp = doc.createParagraph()
            val pr = pp.createRun()
            pr.setText("[image] ${block.url}")
            pr.isItalic = true
            pr.color = "888888"
        }
    }

    private fun renderImagePlaceholder(doc: XWPFDocument, block: Block.Image) {
        val p = doc.createParagraph()
        val r = p.createRun()
        r.setText("[image] ${block.url}")
        r.isItalic = true
        r.color = "888888"
    }

    private fun detectPictureType(bytes: ByteArray, url: String): Pair<Int, String> {
        if (bytes.size >= 4) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            val b2 = bytes[2].toInt() and 0xFF
            val b3 = bytes[3].toInt() and 0xFF
            if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) {
                return XWPFDocument.PICTURE_TYPE_PNG to "png"
            }
            if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) {
                return XWPFDocument.PICTURE_TYPE_JPEG to "jpg"
            }
            if (b0 == 0x47 && b1 == 0x49 && b2 == 0x46) {
                return XWPFDocument.PICTURE_TYPE_GIF to "gif"
            }
            // BMP
            if (b0 == 0x42 && b1 == 0x4D) {
                return XWPFDocument.PICTURE_TYPE_BMP to "bmp"
            }
        }
        // Fall back to URL extension
        val lower = url.lowercase()
        return when {
            lower.contains(".png") -> XWPFDocument.PICTURE_TYPE_PNG to "png"
            lower.contains(".jpg") || lower.contains(".jpeg") ->
                XWPFDocument.PICTURE_TYPE_JPEG to "jpg"
            lower.contains(".gif") -> XWPFDocument.PICTURE_TYPE_GIF to "gif"
            lower.contains(".bmp") -> XWPFDocument.PICTURE_TYPE_BMP to "bmp"
            else -> 0 to ""
        }
    }

    private fun decodeDims(bytes: ByteArray): Pair<Int, Int> = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        opts.outWidth to opts.outHeight
    } catch (t: Throwable) {
        0 to 0
    }
}
