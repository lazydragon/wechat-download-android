package com.example.wechat2docx.data.storage

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed class SaveResult {
    data class Success(val displayLocation: String) : SaveResult()
    data object NoTreeUri : SaveResult()
    data class Error(val msg: String) : SaveResult()
}

object DocxSaver {

    private const val MIME =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    suspend fun save(
        ctx: Context,
        treeUri: Uri?,
        fileName: String,
        bytes: ByteArray,
    ): SaveResult = withContext(Dispatchers.IO) {
        val sanitized = sanitize(fileName).take(80) + ".docx"
        if (treeUri != null) {
            val tree = try {
                DocumentFile.fromTreeUri(ctx, treeUri)
            } catch (t: Throwable) {
                null
            }
            if (tree == null || !tree.canWrite()) return@withContext SaveResult.NoTreeUri
            try {
                tree.findFile(sanitized)?.delete()
            } catch (_: Throwable) {
                // ignore — createFile will append " (1)" if delete fails
            }
            val out = try {
                tree.createFile(MIME, sanitized)
            } catch (t: Throwable) {
                return@withContext SaveResult.Error("createFile failed: ${t.message}")
            }
            if (out == null) return@withContext SaveResult.Error("createFile returned null")
            try {
                ctx.contentResolver.openOutputStream(out.uri)?.use { it.write(bytes) }
                    ?: return@withContext SaveResult.Error("openOutputStream returned null")
            } catch (t: Throwable) {
                return@withContext SaveResult.Error("write failed: ${t.message}")
            }
            val locName = tree.name ?: tree.uri.lastPathSegment ?: tree.uri.toString()
            SaveResult.Success("$locName / $sanitized")
        } else {
            val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: return@withContext SaveResult.Error("no external files dir")
            val f = File(dir, sanitized)
            try {
                f.writeBytes(bytes)
            } catch (t: Throwable) {
                return@withContext SaveResult.Error("write failed: ${t.message}")
            }
            SaveResult.Success(f.absolutePath)
        }
    }

    fun sanitize(s: String): String =
        s.replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "_")
            .trim()
            .ifEmpty { "article" }
}
