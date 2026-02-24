package com.televisionalternativa.streamsonic_tv.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val GITHUB_OWNER = "LautaroDevelopers"
    private const val GITHUB_REPO = "StreamsonicTV"
    private const val API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    private val executor = Executors.newSingleThreadExecutor()

    fun checkForUpdate(context: Context, callback: (UpdateCheckResult) -> Unit) {
        executor.execute {
            try {
                val result = fetchLatestRelease(context)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    callback(result)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for update", e)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    callback(UpdateCheckResult.Error(e.message ?: "Error desconocido"))
                }
            }
        }
    }

    private fun fetchLatestRelease(context: Context): UpdateCheckResult {
        val url = URL(API_URL)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
        connection.setRequestProperty("User-Agent", "StreamsonicTV")
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000

        try {
            val responseCode = connection.responseCode
            if (responseCode != 200) {
                return UpdateCheckResult.Error("GitHub API respondió con código $responseCode")
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseBody)

            val tagName = json.getString("tag_name")
            val remoteVersion = tagName.removePrefix("v")
            val localVersion = getLocalVersion(context)

            Log.d(TAG, "Local version: $localVersion, Remote version: $remoteVersion")

            if (!isNewerVersion(remoteVersion, localVersion)) {
                return UpdateCheckResult.NoUpdateAvailable
            }

            val releaseNotes = json.optString("body", "Nueva versión disponible")
            val assets = json.getJSONArray("assets")

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (name.endsWith(".apk")) {
                    val apkUrl = asset.getString("browser_download_url")
                    val apkSize = asset.getLong("size")

                    return UpdateCheckResult.UpdateAvailable(
                        UpdateInfo(
                            version = remoteVersion,
                            tagName = tagName,
                            apkUrl = apkUrl,
                            releaseNotes = releaseNotes,
                            apkSize = apkSize
                        )
                    )
                }
            }

            return UpdateCheckResult.Error("No se encontró APK en la release")
        } finally {
            connection.disconnect()
        }
    }

    private fun getLocalVersion(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "0.0.0"
        } catch (e: Exception) {
            "0.0.0"
        }
    }

    fun isNewerVersion(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val localParts = local.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(remoteParts.size, localParts.size)

        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}
