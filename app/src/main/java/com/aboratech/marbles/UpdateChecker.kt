package com.aboratech.marbles

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

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

    fun checkForUpdate(currentVersion: String, onUpdateAvailable: (String) -> Unit) {
        Thread {
            try {
                val connection = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val latestTag = JSONObject(body).getString("tag_name").removePrefix("v")
                if (latestTag != currentVersion) {
                    Handler(Looper.getMainLooper()).post { onUpdateAvailable(latestTag) }
                }
            } catch (_: Exception) {
                // Offline, GitHub unreachable, rate-limited, etc. -- skip silently.
            }
        }.start()
    }

    fun downloadAndInstall(context: Context) {
        val appContext = context.applicationContext
        val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(APK_DOWNLOAD_URL))
            .setTitle("Marbles update")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, APK_FILENAME)
        val downloadId = downloadManager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != downloadId) return
                appContext.unregisterReceiver(this)

                val file = File(
                    appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    APK_FILENAME,
                )
                val uri = FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    file,
                )
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(installIntent)
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}
