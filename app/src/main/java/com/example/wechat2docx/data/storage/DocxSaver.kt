package com.example.wechat2docx.data.storage

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed class SaveResult {
    /**
     * @param displayLocation human-readable location string for UI.
     * @param contentUri      a content:// URI safe to share to other apps via
     *                        FLAG_GRANT_READ_URI_PERMISSION. For SAF saves this
     *                        is the DocumentFile.uri; for the app-private
     *                        fallback this is wrapped via FileProvider.
     */
    data class Success(
        val displayLocation: String,
        val contentUri: Uri,
    ) : SaveResult()
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
            SaveResult.Success(
                displayLocation = "$locName / $sanitized",
                contentUri = out.uri,
            )
        } else {
            val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: return@withContext SaveResult.Error("no external files dir")
            val f = File(dir, sanitized)
            try {
                f.writeBytes(bytes)
            } catch (t: Throwable) {
                return@withContext SaveResult.Error("write failed: ${t.message}")
            }
            val authority = "${ctx.packageName}.fileprovider"
            val uri = try {
                FileProvider.getUriForFile(ctx, authority, f)
            } catch (t: Throwable) {
                return@withContext SaveResult.Error("FileProvider failed: ${t.message}")
            }
            SaveResult.Success(
                displayLocation = f.absolutePath,
                contentUri = uri,
            )
        }
    }

    fun sanitize(s: String): String =
        s.replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "_")
            .trim()
            .ifEmpty { "article" }
}
