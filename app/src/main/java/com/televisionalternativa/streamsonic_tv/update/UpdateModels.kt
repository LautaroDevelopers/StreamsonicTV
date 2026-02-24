package com.televisionalternativa.streamsonic_tv.update

data class UpdateInfo(
    val version: String,
    val tagName: String,
    val apkUrl: String,
    val releaseNotes: String,
    val apkSize: Long
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(val info: UpdateInfo) : UpdateCheckResult()
    data object NoUpdateAvailable : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Int, val downloadedMb: String, val totalMb: String) : DownloadState()
    data object Installing : DownloadState()
    data class Failed(val message: String) : DownloadState()
}
