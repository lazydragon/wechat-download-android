package com.example.wechat2docx.data.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Trim the DOCX download directory down to the most-recent [KEEP_RECENT] files.
 * Both code paths swallow all errors — cleanup is best-effort and must never
 * surface as a user-visible failure.
 */
object DownloadCleanup {

    const val KEEP_RECENT = 3
    private const val TAG = "DownloadCleanup"
    private const val DOCX_EXT = ".docx"

    /** Cleanup for SAF tree URIs. Pass the persisted tree URI string. */
    suspend fun cleanupTree(ctx: Context, treeUri: Uri): Unit = withContext(Dispatchers.IO) {
        runCatching {
            val tree = DocumentFile.fromTreeUri(ctx, treeUri) ?: return@runCatching
            if (!tree.canRead()) return@runCatching
            val docx = (tree.listFiles() ?: emptyArray())
                .filter { it.isFile && (it.name?.endsWith(DOCX_EXT, ignoreCase = true) == true) }
                .sortedByDescending { it.lastModified() }
            val toDelete = if (docx.size > KEEP_RECENT) docx.drop(KEEP_RECENT) else emptyList()
            toDelete.forEach { df ->
                runCatching { df.delete() }
                    .onFailure { Log.w(TAG, "delete failed: ${df.name}", it) }
            }
            Log.i(TAG, "tree cleanup: total=${docx.size} deleted=${toDelete.size}")
        }.onFailure { Log.w(TAG, "cleanupTree failed", it) }
    }

    /** Cleanup for the app-private fallback directory. */
    suspend fun cleanupDir(dir: File): Unit = withContext(Dispatchers.IO) {
        runCatching {
            if (!dir.isDirectory) return@runCatching
            val docx = (dir.listFiles { f -> f.isFile && f.name.endsWith(DOCX_EXT, ignoreCase = true) }
                ?: emptyArray())
                .sortedByDescending { it.lastModified() }
            val toDelete = if (docx.size > KEEP_RECENT) docx.drop(KEEP_RECENT) else emptyList()
            toDelete.forEach { f ->
                runCatching { f.delete() }
                    .onFailure { Log.w(TAG, "delete failed: ${f.name}", it) }
            }
            Log.i(TAG, "dir cleanup: total=${docx.size} deleted=${toDelete.size}")
        }.onFailure { Log.w(TAG, "cleanupDir failed", it) }
    }
}
