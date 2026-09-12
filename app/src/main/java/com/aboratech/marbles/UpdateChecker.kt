package com.aboratech.marbles

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed class UpdateResult {
    object UpToDate : UpdateResult()
    data class Available(val version: String) : UpdateResult()
    object Error : UpdateResult()
}

/**
 * Checks GitHub Releases for a newer build than the one installed, and
 * installs it via the system package installer if the user accepts.
 * Relies on git tags and [BuildConfig.VERSION_NAME] staying in sync
 * ("vX.Y.Z" tag <-> "X.Y.Z" versionName) -- that match is the only
 * "is this newer" signal used.
 */
object UpdateChecker {
    private const val OWNER_REPO = "chucksmarthouse/marbles"
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/$OWNER_REPO/releases/latest"
    private const val APK_DOWNLOAD_URL =
        "https://github.com/$OWNER_REPO/releases/latest/download/marbles-debug.apk"
    private const val APK_FILENAME = "marbles-update.apk"

    fun checkForUpdate(currentVersion: String, onResult: (UpdateResult) -> Unit) {
        Thread {
            val result = try {
                val connection = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val latestTag = JSONObject(body).getString("tag_name").removePrefix("v")
                if (latestTag != currentVersion) UpdateResult.Available(latestTag) else UpdateResult.UpToDate
            } catch (_: Exception) {
                UpdateResult.Error
            }
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }.start()
    }

    /**
     * [onStatus] reports progress/failure text for the caller to show (e.g.
     * a Toast) -- this used to rely on the ACTION_DOWNLOAD_COMPLETE
     * broadcast, which is a known-unreliable mechanism across Android
     * versions/OEMs. Polling DownloadManager's own query API instead is
     * more verbose but doesn't depend on that broadcast ever arriving.
     */
    fun downloadAndInstall(context: Context, onStatus: (String) -> Unit = {}) {
        val appContext = context.applicationContext
        val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(APK_DOWNLOAD_URL))
            .setTitle("Marbles update")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, APK_FILENAME)
        val downloadId = try {
            downloadManager.enqueue(request)
        } catch (e: Exception) {
            onStatus("Couldn't start download: ${e.message}")
            return
        }
        onStatus("Downloading update…")
        pollDownload(appContext, downloadManager, downloadId, onStatus)
    }

    private fun pollDownload(
        context: Context,
        downloadManager: DownloadManager,
        downloadId: Long,
        onStatus: (String) -> Unit,
    ) {
        val handler = Handler(Looper.getMainLooper())
        val query = DownloadManager.Query().setFilterById(downloadId)

        lateinit var poll: () -> Unit
        poll = {
            downloadManager.query(query)?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    onStatus("Update download failed")
                } else {
                    when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                        DownloadManager.STATUS_SUCCESSFUL -> installDownloaded(context, onStatus)
                        DownloadManager.STATUS_FAILED -> onStatus("Update download failed")
                        else -> handler.postDelayed({ poll() }, 500)
                    }
                }
            } ?: onStatus("Update download failed")
        }
        handler.postDelayed({ poll() }, 500)
    }

    private fun installDownloaded(context: Context, onStatus: (String) -> Unit) {
        try {
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILENAME)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            onStatus("Couldn't start install: ${e.message}")
        }
    }
}
