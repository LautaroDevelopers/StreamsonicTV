package com.televisionalternativa.streamsonic_tv.update

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object UpdateDownloader {

    private const val TAG = "UpdateDownloader"
    private const val APK_FILENAME = "streamsonic_tv_update.apk"

    private val executor = Executors.newSingleThreadExecutor()

    fun getApkFile(context: Context): File = File(context.cacheDir, APK_FILENAME)

    fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: (progress: Int, downloadedMb: String, totalMb: String) -> Unit,
        onComplete: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        executor.execute {
            try {
                val file = downloadApkSync(context, apkUrl, onProgress)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onComplete(file)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading APK", e)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onError(e.message ?: "Error de descarga desconocido")
                }
            }
        }
    }

    private fun downloadApkSync(
        context: Context,
        apkUrl: String,
        onProgress: (progress: Int, downloadedMb: String, totalMb: String) -> Unit
    ): File {
        val outputFile = getApkFile(context)

        if (outputFile.exists()) {
            outputFile.delete()
        }

        val url = URL(apkUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "StreamsonicTV")
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000

        try {
            connection.connect()
            val totalBytes = connection.contentLength.toLong()
            var downloadedBytes = 0L
            val buffer = ByteArray(8192)

            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val progress = if (totalBytes > 0) {
                            ((downloadedBytes * 100) / totalBytes).toInt()
                        } else 0

                        val dlMb = String.format("%.1f", downloadedBytes / (1024.0 * 1024.0))
                        val ttMb = String.format("%.1f", totalBytes / (1024.0 * 1024.0))

                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            onProgress(progress, dlMb, ttMb)
                        }
                    }
                }
            }

            Log.d(TAG, "APK downloaded: ${outputFile.absolutePath} (${downloadedBytes} bytes)")
            return outputFile
        } finally {
            connection.disconnect()
        }
    }

    fun cleanupApk(context: Context) {
        val file = getApkFile(context)
        if (file.exists()) {
            file.delete()
        }
    }
}
