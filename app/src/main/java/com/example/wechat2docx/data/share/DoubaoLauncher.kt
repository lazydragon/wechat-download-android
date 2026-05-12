package com.example.wechat2docx.data.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.example.wechat2docx.R

/**
 * Best-effort launcher for opening a saved DOCX in the Doubao app.
 *
 * Strategy (first success wins):
 *   1. ACTION_SEND with EXTRA_STREAM to a resolved Doubao package (delivers the file).
 *   2. ACTION_VIEW deep-link candidates with setPackage(resolvedPkg) (opens the
 *      AI-Podcast surface so the user can manually import).
 *   3. getLaunchIntentForPackage(pkg) (cold launch).
 *   4. Toast: not installed.
 *
 * Never throws ActivityNotFoundException to the caller. All variants are guarded
 * by packageManager.resolveActivity / getPackageInfo before being fired.
 */
object DoubaoLauncher {

    private const val TAG = "DoubaoLauncher"

    private const val DOCX_MIME =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    /** Prioritized package id candidates. Easy to extend. */
    private val DOUBAO_CANDIDATES = listOf(
        "com.larus.nova",
        "com.bytedance.flow",
        "com.bytedance.aweme.doubao",
        "com.heytap.doubao",
        "com.bytedance.doubao",
    )

    /** Prioritized AI-Podcast deep links. Tried with setPackage(resolvedPkg). */
    private val DEEP_LINK_CANDIDATES = listOf(
        "doubao://ai_podcast/import",
        "doubao://podcast/create",
        "doubao://aipodcast",
        "https://www.doubao.com/ai-podcast",
        "https://www.doubao.com/chat",
    )

    /**
     * Launch Doubao with the saved DOCX. Returns the strategy that succeeded
     * (for diagnostic logging) or null if none did. Never throws.
     */
    fun openInDoubao(ctx: Context, contentUri: Uri, fileName: String): Strategy? {
        val pkg = resolveInstalledDoubao(ctx)
        if (pkg == null) {
            toast(ctx, R.string.doubao_not_installed)
            return null
        }

        // 1) ACTION_SEND with the file
        val sendIntent = buildSendIntent(pkg, contentUri, fileName)
        if (canResolve(ctx, sendIntent)) {
            return launchSafely(ctx, sendIntent, Strategy.SEND_FILE, R.string.doubao_opened_via_send)
        }

        // 2) Deep-link candidates with setPackage
        for (link in DEEP_LINK_CANDIDATES) {
            val view = Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (canResolve(ctx, view)) {
                Log.i(TAG, "deep-link resolved: $link")
                return launchSafely(ctx, view, Strategy.DEEP_LINK, R.string.doubao_opened_via_deeplink)
            }
        }

        // 3) Cold launch the package
        val launch = ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return launchSafely(ctx, launch, Strategy.COLD_LAUNCH, R.string.doubao_opened_via_launch)
        }

        // 4) Nothing worked
        toast(ctx, R.string.doubao_not_installed)
        return null
    }

    /** Returns the first installed candidate package id, or null. */
    fun resolveInstalledDoubao(ctx: Context): String? {
        val pm = ctx.packageManager
        for (pkg in DOUBAO_CANDIDATES) {
            try {
                pm.getPackageInfo(pkg, 0)
                Log.i(TAG, "doubao package resolved: $pkg")
                return pkg
            } catch (_: Throwable) {
                // not installed — try next
            }
        }
        Log.i(TAG, "no doubao package installed")
        return null
    }

    private fun buildSendIntent(pkg: String, uri: Uri, fileName: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = DOCX_MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "AI Podcast: $fileName")
            putExtra(Intent.EXTRA_TEXT, "AI Podcast")
            setPackage(pkg)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                    or Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }
    }

    private fun canResolve(ctx: Context, intent: Intent): Boolean {
        return try {
            intent.resolveActivity(ctx.packageManager) != null
        } catch (t: Throwable) {
            Log.w(TAG, "resolveActivity threw", t)
            false
        }
    }

    private fun launchSafely(
        ctx: Context,
        intent: Intent,
        strategy: Strategy,
        toastResId: Int,
    ): Strategy? {
        return try {
            ctx.startActivity(intent)
            toast(ctx, toastResId)
            strategy
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "startActivity ANF for ${strategy.name}", e)
            null
        } catch (t: Throwable) {
            Log.w(TAG, "startActivity failed for ${strategy.name}", t)
            null
        }
    }

    private fun toast(ctx: Context, resId: Int) {
        try {
            Toast.makeText(ctx, ctx.getString(resId), Toast.LENGTH_LONG).show()
        } catch (_: Throwable) {
            // no-op
        }
    }

    enum class Strategy { SEND_FILE, DEEP_LINK, COLD_LAUNCH }
}
