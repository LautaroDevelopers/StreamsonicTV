package com.televisionalternativa.streamsonic_tv.update

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.televisionalternativa.streamsonic_tv.ui.theme.*

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    val context = LocalContext.current
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }

    Dialog(
        onDismissRequest = {
            if (downloadState is DownloadState.Idle || downloadState is DownloadState.Failed) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = downloadState is DownloadState.Idle || downloadState is DownloadState.Failed,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = CyanGlow,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Nueva versión disponible",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "v${updateInfo.version}",
                    style = MaterialTheme.typography.titleMedium,
                    color = CyanGlow
                )

                Spacer(modifier = Modifier.height(16.dp))

                when (val state = downloadState) {
                    is DownloadState.Idle -> {
                        val sizeMb = String.format("%.1f", updateInfo.apkSize / (1024.0 * 1024.0))
                        Text(
                            text = "Tamaño: $sizeMb MB",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                downloadState = DownloadState.Downloading(0, "0", "0")
                                UpdateDownloader.downloadApk(
                                    context = context,
                                    apkUrl = updateInfo.apkUrl,
                                    onProgress = { progress, dl, total ->
                                        downloadState = DownloadState.Downloading(progress, dl, total)
                                    },
                                    onComplete = { apkFile ->
                                        downloadState = DownloadState.Installing
                                        val installed = UpdateInstaller.installApk(context, apkFile)
                                        if (!installed) {
                                            downloadState = DownloadState.Failed("No se pudo iniciar la instalación")
                                        }
                                    },
                                    onError = { error ->
                                        downloadState = DownloadState.Failed(error)
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanGlow)
                        ) {
                            Text("ACTUALIZAR AHORA", color = DarkBackground, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = onSnooze,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Recordarme en 1 hora", color = TextSecondary)
                        }
                    }

                    is DownloadState.Downloading -> {
                        Text(
                            text = "Descargando... ${state.progress}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = CyanGlow,
                            trackColor = CardBackground
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "${state.downloadedMb} MB / ${state.totalMb} MB",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }

                    is DownloadState.Installing -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(36.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Descarga completada.\nAbriendo instalador...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                    }

                    is DownloadState.Failed -> {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(36.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ErrorRed,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                            ) {
                                Text("Cerrar")
                            }

                            Button(
                                onClick = {
                                    downloadState = DownloadState.Downloading(0, "0", "0")
                                    UpdateDownloader.downloadApk(
                                        context = context,
                                        apkUrl = updateInfo.apkUrl,
                                        onProgress = { progress, dl, total ->
                                            downloadState = DownloadState.Downloading(progress, dl, total)
                                        },
                                        onComplete = { apkFile ->
                                            downloadState = DownloadState.Installing
                                            val installed = UpdateInstaller.installApk(context, apkFile)
                                            if (!installed) {
                                                downloadState = DownloadState.Failed("No se pudo iniciar la instalación")
                                            }
                                        },
                                        onError = { error ->
                                            downloadState = DownloadState.Failed(error)
                                        }
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = CyanGlow)
                            ) {
                                Text("Reintentar", color = DarkBackground)
                            }
                        }
                    }
                }
            }
        }
    }
}
