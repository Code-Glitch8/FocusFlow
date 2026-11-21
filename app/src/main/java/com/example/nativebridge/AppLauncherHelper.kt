package com.example.nativebridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.domain.BlockPolicy

object AppLauncherHelper {

    fun isPackageInstalled(context: Context, pkg: String): Boolean {
        return runCatching {
            context.packageManager.getLaunchIntentForPackage(pkg) != null
        }.getOrDefault(false)
    }

    fun launchPackage(context: Context, pkg: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun openYoutube(context: Context, shorts: Boolean = false): Boolean {
        if (shorts) {
            val shortsUri = Uri.parse("https://www.youtube.com/shorts")
            val intent = Intent(Intent.ACTION_VIEW, shortsUri).apply {
                setPackage(BlockPolicy.YOUTUBE_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { context.startActivity(intent) }.isSuccess) return true
        }
        return launchPackage(context, BlockPolicy.YOUTUBE_PACKAGE)
    }
}
