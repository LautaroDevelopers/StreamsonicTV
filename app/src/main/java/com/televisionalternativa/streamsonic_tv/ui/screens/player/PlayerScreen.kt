package com.televisionalternativa.streamsonic_tv.ui.screens.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import com.televisionalternativa.streamsonic_tv.data.model.Channel
import com.televisionalternativa.streamsonic_tv.data.model.Favorite
import com.televisionalternativa.streamsonic_tv.data.model.Station
import com.televisionalternativa.streamsonic_tv.data.repository.StreamsonicRepository
import com.televisionalternativa.streamsonic_tv.ui.theme.*
import com.televisionalternativa.streamsonic_tv.update.UpdateCheckResult
import com.televisionalternativa.streamsonic_tv.update.UpdateChecker
import com.televisionalternativa.streamsonic_tv.update.UpdateDialog
import com.televisionalternativa.streamsonic_tv.update.UpdateInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

// ============================================================
// Data types
// ============================================================

enum class PanelMode(val label: String) {
    CANALES("Canales"),
    RADIO("Radio"),
    FAVORITOS("Favoritos"),
    BUSCAR("Buscar")
}

data class FavoriteItem(
    val itemId: Int,
    val itemType: String,
    val name: String,
    val imageUrl: String?,
    val category: String?,
    val streamUrl: String,
    val listIndex: Int
)

data class SearchResult(
    val name: String,
    val imageUrl: String?,
    val category: String?,
    val itemType: String,
    val listIndex: Int,
    val id: Int
)

// Mode entries for the left column
data class ModeEntry(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val mode: PanelMode? = null,
    val isLogout: Boolean = false
)

private val modeEntries = listOf(
    ModeEntry("Canales", Icons.Filled.LiveTv, CyanGlow, PanelMode.CANALES),
    ModeEntry("Radio", Icons.Filled.Radio, PurpleGlow, PanelMode.RADIO),
    ModeEntry("Favoritos", Icons.Filled.Favorite, PinkGlow, PanelMode.FAVORITOS),
    ModeEntry("Buscar", Icons.Filled.Search, GreenGlow, PanelMode.BUSCAR),
    @Suppress("DEPRECATION")
    ModeEntry("Desconectar", Icons.Filled.ExitToApp, ErrorRed, isLogout = true)
)

// ============================================================
// Main PlayerScreen
// ============================================================

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    repository: StreamsonicRepository,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }

    // ===== DATA STATE =====
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var stations by remember { mutableStateOf<List<Station>>(emptyList()) }
    var favorites by remember { mutableStateOf<List<Favorite>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // ===== PLAYBACK STATE =====
    var contentType by remember { mutableStateOf("channel") }
    var currentIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var playerError by remember { mutableStateOf<String?>(null) }

    // ===== PANEL STATE =====
    var showPanel by remember { mutableStateOf(false) }
    var panelMode by remember { mutableStateOf(PanelMode.CANALES) }

    // ===== UPDATE STATE =====
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var snoozedUntil by remember { mutableStateOf(0L) }

    // ===== FOCUS =====
    val mainFocusRequester = remember { FocusRequester() }

    // ===== DERIVED STATE =====
    val isChannelMode = contentType == "channel"
    val itemCount = if (isChannelMode) channels.size else stations.size
    val safeIndex = currentIndex.coerceIn(0, (itemCount - 1).coerceAtLeast(0))

    val currentTitle = if (isChannelMode) {
        channels.getOrNull(safeIndex)?.name ?: ""
    } else {
        stations.getOrNull(safeIndex)?.name ?: ""
    }

    val currentStreamUrl = if (isChannelMode) {
        channels.getOrNull(safeIndex)?.streamUrl ?: ""
    } else {
        stations.getOrNull(safeIndex)?.streamUrl ?: ""
    }

    val currentCategory = if (isChannelMode) {
        channels.getOrNull(safeIndex)?.category ?: ""
    } else {
        stations.getOrNull(safeIndex)?.category ?: ""
    }

    // Build favorites display list
    val favoriteItems = remember(favorites, channels, stations) {
        favorites.mapNotNull { fav ->
            when (fav.itemType) {
                "channel" -> {
                    val idx = channels.indexOfFirst { it.id == fav.itemId }
                    if (idx >= 0) {
                        val ch = channels[idx]
                        FavoriteItem(fav.itemId, "channel", ch.name, ch.imageUrl, ch.category, ch.streamUrl, idx)
                    } else null
                }
                "station" -> {
                    val idx = stations.indexOfFirst { it.id == fav.itemId }
                    if (idx >= 0) {
                        val st = stations[idx]
                        FavoriteItem(fav.itemId, "station", st.name, st.imageUrl, st.category, st.streamUrl, idx)
                    } else null
                }
                else -> null
            }
        }
    }

    // ===== EXOPLAYER =====
    val exoPlayer = remember {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(Util.getUserAgent(context, "StreamsonicTV"))

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSizeSd()
                    .setPreferredAudioLanguage("es")
            )
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        playerError = when (error.errorCode) {
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                                "Error de conexión. Verifica tu internet."
                            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                                "El stream no está disponible."
                            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
                                "Formato de stream no soportado."
                            PlaybackException.ERROR_CODE_IO_UNSPECIFIED ->
                                "Error de red. Reintentando..."
                            else -> "Error de reproducción: ${error.message}"
                        }
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }
                })
            }
    }

    // ===== DATA LOADING =====
    fun loadContent() {
        scope.launch {
            isLoading = true
            errorMessage = null

            repository.getChannels().fold(
                onSuccess = { channels = it },
                onFailure = { errorMessage = it.message }
            )

            repository.getStations().fold(
                onSuccess = { stations = it },
                onFailure = { if (errorMessage == null) errorMessage = it.message }
            )

            // Load favorites silently
            repository.getFavorites().fold(
                onSuccess = { favorites = it },
                onFailure = { /* ignore */ }
            )

            // Restore last watched
            val lastIndex = repository.getLastChannelIndex()
            val lastType = repository.getLastContentType()
            contentType = lastType
            val maxIndex = if (lastType == "channel") channels.size - 1 else stations.size - 1
            currentIndex = lastIndex.coerceIn(0, maxIndex.coerceAtLeast(0))

            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadContent()
    }

    // ===== UPDATE CHECK =====
    LaunchedEffect(Unit) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val hasNetwork = cm?.getNetworkCapabilities(cm.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        if (hasNetwork && System.currentTimeMillis() > snoozedUntil) {
            UpdateChecker.checkForUpdate(context) { result ->
                when (result) {
                    is UpdateCheckResult.UpdateAvailable -> {
                        updateInfo = result.info
                        showUpdateDialog = true
                    }
                    else -> { /* no update or error */ }
                }
            }
        }
    }

    // ===== PLAY STREAM ON CHANGE =====
    LaunchedEffect(currentStreamUrl) {
        if (currentStreamUrl.isNotEmpty()) {
            playerError = null
            val mediaItem = MediaItem.fromUri(currentStreamUrl)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
        }
    }

    // Auto-hide controls (video only)
    LaunchedEffect(showControls, showPanel) {
        if (showControls && isChannelMode && !showPanel) {
            delay(4000)
            showControls = false
        }
    }

    // Focus management
    LaunchedEffect(isLoading) {
        if (!isLoading) {
            mainFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(showPanel) {
        if (!showPanel) {
            delay(100)
            mainFocusRequester.requestFocus()
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // ===== CHANNEL CHANGE =====
    fun changeChannel(newIndex: Int) {
        val maxIndex = if (isChannelMode) channels.size - 1 else stations.size - 1
        if (newIndex in 0..maxIndex) {
            currentIndex = newIndex
            showControls = true
            scope.launch { repository.saveLastChannel(newIndex, contentType) }
        }
    }

    fun playItem(type: String, index: Int) {
        contentType = type
        currentIndex = index
        showControls = true
        showPanel = false
        scope.launch { repository.saveLastChannel(index, type) }
    }

    // ===== UPDATE DIALOG =====
    if (showUpdateDialog && updateInfo != null) {
        UpdateDialog(
            updateInfo = updateInfo!!,
            onDismiss = { showUpdateDialog = false },
            onSnooze = {
                snoozedUntil = System.currentTimeMillis() + 3_600_000L
                showUpdateDialog = false
            }
        )
    }

    // ===== LOADING STATE =====
    if (isLoading) {
        PlayerLoadingContent()
        return
    }

    // ===== FATAL ERROR STATE =====
    if (errorMessage != null && channels.isEmpty() && stations.isEmpty()) {
        PlayerErrorState(
            message = errorMessage!!,
            onRetry = { loadContent() }
        )
        return
    }

    // ===== MAIN PLAYER UI =====
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(mainFocusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && !showPanel) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_BACK -> false // let system handle (exit app)
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            panelMode = if (isChannelMode) PanelMode.CANALES else PanelMode.RADIO
                            showPanel = true
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                            changeChannel(currentIndex - 1)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                            changeChannel(currentIndex + 1)
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            if (exoPlayer.isPlaying) exoPlayer.pause()
                            else exoPlayer.play()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (isChannelMode) showControls = true
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Player content
        when {
            playerError != null -> {
                PlayerErrorState(
                    message = playerError!!,
                    onRetry = {
                        playerError = null
                        exoPlayer.setMediaItem(MediaItem.fromUri(currentStreamUrl))
                        exoPlayer.prepare()
                    }
                )
            }
            isChannelMode -> {
                VideoPlayerContent(
                    exoPlayer = exoPlayer,
                    title = currentTitle,
                    category = currentCategory,
                    isPlaying = isPlaying,
                    showControls = showControls && !showPanel,
                    currentIndex = safeIndex,
                    totalItems = itemCount
                )
            }
            else -> {
                AudioPlayerContent(
                    exoPlayer = exoPlayer,
                    title = currentTitle,
                    isPlaying = isPlaying,
                    currentIndex = safeIndex,
                    totalItems = itemCount
                )
            }
        }

        // Panel overlay
        AnimatedVisibility(
            visible = showPanel,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
        ) {
            ContentPanel(
                channels = channels,
                stations = stations,
                favoriteItems = favoriteItems,
                currentIndex = currentIndex,
                contentType = contentType,
                initialPanelMode = panelMode,
                onItemSelect = { type, index -> playItem(type, index) },
                onDismiss = { showPanel = false },
                onLogout = onLogout,
                onRefreshFavorites = {
                    scope.launch {
                        repository.getFavorites().fold(
                            onSuccess = { favorites = it },
                            onFailure = { }
                        )
                    }
                },
                imageLoader = imageLoader
            )
        }
    }
}

// ============================================================
// Loading screen
// ============================================================

@Composable
private fun PlayerLoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(80.dp),
                    color = CyanGlow.copy(alpha = 0.2f),
                    strokeWidth = 6.dp
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(60.dp),
                    color = CyanGlow,
                    strokeWidth = 4.dp
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Cargando contenido...",
                fontSize = 18.sp,
                color = TextSecondary
            )
        }
    }
}

// ============================================================
// Video player content
// ============================================================

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerContent(
    exoPlayer: ExoPlayer,
    title: String,
    category: String,
    isPlaying: Boolean,
    showControls: Boolean,
    currentIndex: Int,
    totalItems: Int
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Video surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Controls overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.7f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            ) {
                // Top bar with channel info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                        .align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(CyanGlow, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${currentIndex + 1}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkBackground
                                )
                            }
                            Text(
                                text = title,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            LiveIndicator()
                            Text("En vivo", fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
                            if (category.isNotEmpty()) {
                                Text("•", fontSize = 14.sp, color = Color.White.copy(alpha = 0.5f))
                                Text(category, fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
                            }
                        }
                    }
                }

                // Bottom hints
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                        .align(Alignment.BottomStart),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "OK: Panel • ▲▼: Cambiar canal",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        "${currentIndex + 1} / $totalItems",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

// ============================================================
// Audio player content
// ============================================================

@Composable
private fun AudioPlayerContent(
    exoPlayer: ExoPlayer,
    title: String,
    isPlaying: Boolean,
    currentIndex: Int,
    totalItems: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audio")
    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "wave1"
    )
    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(600, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "wave2"
    )
    val wave3 by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "wave3"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1500, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DarkBackground, DarkSurface, DarkBackground)
                )
            )
    ) {
        // Background orbs
        Box(
            modifier = Modifier
                .size(400.dp)
                .offset(x = (-150).dp, y = 100.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(PurpleGlow.copy(alpha = 0.1f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(350.dp)
                .align(Alignment.TopEnd)
                .offset(x = 100.dp, y = (-50).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(OrangeGlow.copy(alpha = 0.08f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Estación ${currentIndex + 1} de $totalItems",
                fontSize = 14.sp,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Visualizer circle
            Box(
                modifier = Modifier
                    .size((220 * pulseScale).dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                OrangeGlow.copy(alpha = 0.15f),
                                OrangeGlow.copy(alpha = 0.05f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(
                        width = 3.dp,
                        brush = Brush.linearGradient(colors = listOf(OrangeGlow, PurpleGlow)),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(CircleShape)
                        .background(CardBackground)
                        .border(1.dp, CardBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Radio,
                        contentDescription = null,
                        modifier = Modifier.size(70.dp),
                        tint = OrangeGlow
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = title,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Status with animated bars
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isPlaying) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        listOf(wave1, wave2, wave3, wave2, wave1).forEach { height ->
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height((height * 24).dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(OrangeGlow)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                }
                Text(
                    text = if (isPlaying) "Reproduciendo" else "Pausado",
                    fontSize = 20.sp,
                    color = if (isPlaying) OrangeGlow else TextMuted
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Play/Pause indicator
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(colors = listOf(OrangeGlow, PurpleGlow))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(50.dp),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                "OK: Panel • ▲▼: Cambiar estación",
                fontSize = 14.sp,
                color = TextMuted
            )
        }
    }
}

// ============================================================
// Content Panel (overlay with modes + content)
// ============================================================

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ContentPanel(
    channels: List<Channel>,
    stations: List<Station>,
    favoriteItems: List<FavoriteItem>,
    currentIndex: Int,
    contentType: String,
    initialPanelMode: PanelMode,
    onItemSelect: (type: String, index: Int) -> Unit,
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
    onRefreshFavorites: () -> Unit,
    imageLoader: ImageLoader
) {
    // ===== PANEL STATE =====
    var panelMode by remember { mutableStateOf(initialPanelMode) }
    var focusColumn by remember { mutableIntStateOf(0) } // 0=modes, 1=middle, 2=right
    var selectedModeIndex by remember {
        mutableIntStateOf(
            PanelMode.entries.indexOf(initialPanelMode).coerceAtLeast(0)
        )
    }

    // State for Canales/Radio mode: categories + items
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var selectedItemIndex by remember { mutableIntStateOf(0) }
    val categoryListState = rememberLazyListState()
    val itemListState = rememberLazyListState()

    // State for Favoritos mode
    var selectedFavIndex by remember { mutableIntStateOf(0) }
    val favListState = rememberLazyListState()

    // State for Buscar mode
    var searchQuery by remember { mutableStateOf("") }
    var selectedSearchIndex by remember { mutableIntStateOf(0) }
    var searchFocusOnInput by remember { mutableStateOf(true) }
    val searchListState = rememberLazyListState()

    // Focus requesters
    val modesFR = remember { FocusRequester() }
    val col1FR = remember { FocusRequester() }
    val col2FR = remember { FocusRequester() }
    val searchInputFR = remember { FocusRequester() }
    val searchResultsFR = remember { FocusRequester() }

    val scope = rememberCoroutineScope()

    // ===== GROUPED DATA =====
    val groupedChannels = remember(channels) {
        channels.withIndex().groupBy { it.value.category ?: "Otros" }
            .toSortedMap(compareBy { if (it == "Otros") "zzz" else it })
    }
    val channelCategories = remember(groupedChannels) { groupedChannels.keys.toList() }

    val groupedStations = remember(stations) {
        stations.withIndex().groupBy { it.value.category ?: "Otros" }
            .toSortedMap(compareBy { if (it == "Otros") "zzz" else it })
    }
    val stationCategories = remember(groupedStations) { groupedStations.keys.toList() }

    // Active categories/items based on mode
    val activeGrouped = when (panelMode) {
        PanelMode.CANALES -> groupedChannels
        PanelMode.RADIO -> groupedStations
        else -> emptyMap()
    }
    val activeCategories = when (panelMode) {
        PanelMode.CANALES -> channelCategories
        PanelMode.RADIO -> stationCategories
        else -> emptyList()
    }

    val activeCategoryName = activeCategories.getOrNull(selectedCategoryIndex) ?: ""
    val activeCategoryItems = activeGrouped[activeCategoryName] ?: emptyList()

    // Search results
    val searchResults = remember(searchQuery, channels, stations) {
        if (searchQuery.isBlank()) emptyList()
        else {
            val query = searchQuery.lowercase()
            val results = mutableListOf<SearchResult>()
            channels.forEachIndexed { index, ch ->
                if (ch.name.lowercase().contains(query)) {
                    results.add(SearchResult(ch.name, ch.imageUrl, ch.category, "channel", index, ch.id))
                }
            }
            stations.forEachIndexed { index, st ->
                if (st.name.lowercase().contains(query)) {
                    results.add(SearchResult(st.name, st.imageUrl, st.category, "station", index, st.id))
                }
            }
            results.sortedBy { if (it.name.lowercase().startsWith(query)) 0 else 1 }
        }
    }

    // Max column for current mode
    val maxColumn = when (panelMode) {
        PanelMode.CANALES, PanelMode.RADIO -> 2
        else -> 1
    }

    // ===== FOCUS MANAGEMENT =====
    LaunchedEffect(focusColumn, panelMode, searchFocusOnInput) {
        delay(100)
        try {
            when {
                focusColumn == 0 -> modesFR.requestFocus()
                focusColumn == 1 && panelMode == PanelMode.BUSCAR && searchFocusOnInput -> searchInputFR.requestFocus()
                focusColumn == 1 && panelMode == PanelMode.BUSCAR && !searchFocusOnInput -> searchResultsFR.requestFocus()
                focusColumn == 1 -> col1FR.requestFocus()
                focusColumn == 2 -> col2FR.requestFocus()
            }
        } catch (_: Exception) { }
    }

    // Initialize category/item to current playing item when panel opens
    LaunchedEffect(panelMode) {
        selectedCategoryIndex = 0
        selectedItemIndex = 0

        val targetIndex = currentIndex
        val targetType = contentType
        val matchesMode = (panelMode == PanelMode.CANALES && targetType == "channel") ||
                (panelMode == PanelMode.RADIO && targetType == "station")

        if (matchesMode && activeCategories.isNotEmpty()) {
            var found = false
            activeCategories.forEachIndexed { catIdx, cat ->
                val items = activeGrouped[cat] ?: emptyList()
                items.forEachIndexed { itemIdx, indexed ->
                    if (indexed.index == targetIndex && !found) {
                        selectedCategoryIndex = catIdx
                        selectedItemIndex = itemIdx
                        found = true
                    }
                }
            }
        }
    }

    // Refresh favorites when entering Favoritos mode
    LaunchedEffect(panelMode) {
        if (panelMode == PanelMode.FAVORITOS) {
            selectedFavIndex = 0
            onRefreshFavorites()
        }
    }

    // ===== UI =====
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {

            // ==========================================
            // LEFT: Mode selector column
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(160.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                DarkSurface.copy(alpha = 0.98f),
                                DarkBackground.copy(alpha = 0.98f)
                            )
                        )
                    )
                    .focusRequester(modesFR)
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && focusColumn == 0) {
                            when (event.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_BACK -> {
                                    onDismiss()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    if (selectedModeIndex > 0) {
                                        selectedModeIndex--
                                        val entry = modeEntries[selectedModeIndex]
                                        if (entry.mode != null) panelMode = entry.mode
                                    }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    if (selectedModeIndex < modeEntries.size - 1) {
                                        selectedModeIndex++
                                        val entry = modeEntries[selectedModeIndex]
                                        if (entry.mode != null) panelMode = entry.mode
                                    }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                    val entry = modeEntries[selectedModeIndex]
                                    if (entry.isLogout) {
                                        onLogout()
                                    } else {
                                        focusColumn = 1
                                    }
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    // Header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(colors = listOf(CyanGlow, PurpleGlow))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Mode items (first 4)
                    modeEntries.take(4).forEachIndexed { index, entry ->
                        val isSelected = index == selectedModeIndex && focusColumn == 0
                        val isActiveMode = entry.mode == panelMode
                        val isCurrentlyPlaying = (entry.mode == PanelMode.CANALES && contentType == "channel") ||
                                (entry.mode == PanelMode.RADIO && contentType == "station")

                        ModeItem(
                            entry = entry,
                            isSelected = isSelected,
                            isActiveMode = isActiveMode,
                            isCurrentlyPlaying = isCurrentlyPlaying
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(CardBorder.copy(alpha = 0.5f))
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Logout item
                    val logoutEntry = modeEntries.last()
                    val isLogoutSelected = selectedModeIndex == modeEntries.size - 1 && focusColumn == 0

                    ModeItem(
                        entry = logoutEntry,
                        isSelected = isLogoutSelected,
                        isActiveMode = false,
                        isCurrentlyPlaying = false
                    )
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(CardBorder.copy(alpha = 0.3f))
            )

            // ==========================================
            // CONTENT AREA (varies by mode)
            // ==========================================
            when (panelMode) {
                PanelMode.CANALES, PanelMode.RADIO -> {
                    val isChMode = panelMode == PanelMode.CANALES
                    val accentColor = if (isChMode) CyanGlow else PurpleGlow

                    // Categories column
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(200.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        DarkSurface.copy(alpha = 0.95f),
                                        DarkBackground.copy(alpha = 0.95f)
                                    )
                                )
                            )
                            .focusRequester(col1FR)
                            .focusable()
                            .onKeyEvent { event ->
                                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && focusColumn == 1) {
                                    when (event.nativeKeyEvent.keyCode) {
                                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_LEFT -> {
                                            focusColumn = 0
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_UP -> {
                                            if (selectedCategoryIndex > 0) {
                                                selectedCategoryIndex--
                                                selectedItemIndex = 0
                                                scope.launch {
                                                    categoryListState.animateScrollToItem(
                                                        (selectedCategoryIndex - 2).coerceAtLeast(0)
                                                    )
                                                }
                                            }
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                                            if (selectedCategoryIndex < activeCategories.size - 1) {
                                                selectedCategoryIndex++
                                                selectedItemIndex = 0
                                                scope.launch {
                                                    categoryListState.animateScrollToItem(
                                                        (selectedCategoryIndex - 2).coerceAtLeast(0)
                                                    )
                                                }
                                            }
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                            if (activeCategoryItems.isNotEmpty()) {
                                                focusColumn = 2
                                            }
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            // Header
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Category,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Categorías",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            LazyColumn(
                                state = categoryListState,
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(activeCategories) { index, category ->
                                    CategoryItem(
                                        name = category,
                                        count = activeGrouped[category]?.size ?: 0,
                                        isSelected = index == selectedCategoryIndex && focusColumn == 1,
                                        accentColor = accentColor
                                    )
                                }
                            }
                        }
                    }

                    // Divider
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(CardBorder.copy(alpha = 0.2f))
                    )

                    // Items column
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(380.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        DarkBackground.copy(alpha = 0.92f),
                                        DarkBackground.copy(alpha = 0.85f),
                                        Color.Transparent
                                    ),
                                    startX = 0f,
                                    endX = 500f
                                )
                            )
                            .focusRequester(col2FR)
                            .focusable()
                            .onKeyEvent { event ->
                                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && focusColumn == 2) {
                                    when (event.nativeKeyEvent.keyCode) {
                                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_LEFT -> {
                                            focusColumn = 1
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_UP -> {
                                            if (selectedItemIndex > 0) {
                                                selectedItemIndex--
                                                scope.launch {
                                                    itemListState.animateScrollToItem(
                                                        (selectedItemIndex - 2).coerceAtLeast(0)
                                                    )
                                                }
                                            }
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                                            if (selectedItemIndex < activeCategoryItems.size - 1) {
                                                selectedItemIndex++
                                                scope.launch {
                                                    itemListState.animateScrollToItem(
                                                        (selectedItemIndex - 2).coerceAtLeast(0)
                                                    )
                                                }
                                            }
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                            val selected = activeCategoryItems.getOrNull(selectedItemIndex)
                                            if (selected != null) {
                                                val type = if (isChMode) "channel" else "station"
                                                onItemSelect(type, selected.index)
                                            }
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            // Header
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (isChMode) Icons.Default.Tv else Icons.Default.Radio,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        activeCategoryName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${activeCategoryItems.size} ${if (isChMode) "canales" else "estaciones"}",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            if (activeCategoryItems.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Sin items", color = TextMuted, fontSize = 14.sp)
                                }
                            } else {
                                LazyColumn(
                                    state = itemListState,
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(activeCategoryItems) { localIndex, indexedItem ->
                                        val name = if (isChMode)
                                            (indexedItem.value as Channel).name
                                        else
                                            (indexedItem.value as Station).name
                                        val logoUrl = if (isChMode)
                                            (indexedItem.value as Channel).imageUrl
                                        else
                                            (indexedItem.value as Station).imageUrl

                                        val isCurrent = indexedItem.index == currentIndex &&
                                                ((isChMode && contentType == "channel") ||
                                                        (!isChMode && contentType == "station"))

                                        ItemRow(
                                            number = indexedItem.index + 1,
                                            name = name,
                                            logoUrl = logoUrl,
                                            imageLoader = imageLoader,
                                            isSelected = localIndex == selectedItemIndex && focusColumn == 2,
                                            isCurrent = isCurrent,
                                            accentColor = accentColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                PanelMode.FAVORITOS -> {
                    // Favorites content
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(420.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        DarkBackground.copy(alpha = 0.95f),
                                        DarkBackground.copy(alpha = 0.88f),
                                        Color.Transparent
                                    ),
                                    startX = 0f,
                                    endX = 550f
                                )
                            )
                            .focusRequester(col1FR)
                            .focusable()
                            .onKeyEvent { event ->
                                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && focusColumn == 1) {
                                    when (event.nativeKeyEvent.keyCode) {
                                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_LEFT -> {
                                            focusColumn = 0
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_UP -> {
                                            if (selectedFavIndex > 0) {
                                                selectedFavIndex--
                                                scope.launch {
                                                    favListState.animateScrollToItem(
                                                        (selectedFavIndex - 2).coerceAtLeast(0)
                                                    )
                                                }
                                            }
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                                            if (selectedFavIndex < favoriteItems.size - 1) {
                                                selectedFavIndex++
                                                scope.launch {
                                                    favListState.animateScrollToItem(
                                                        (selectedFavIndex - 2).coerceAtLeast(0)
                                                    )
                                                }
                                            }
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                            val selected = favoriteItems.getOrNull(selectedFavIndex)
                                            if (selected != null) {
                                                onItemSelect(selected.itemType, selected.listIndex)
                                            }
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            // Header
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = PinkGlow,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Favoritos",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                if (favoriteItems.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(PinkGlow.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            "${favoriteItems.size}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = PinkGlow
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (favoriteItems.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.FavoriteBorder,
                                            contentDescription = null,
                                            tint = TextMuted,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            "Sin favoritos",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Agregá favoritos desde la app móvil",
                                            fontSize = 13.sp,
                                            color = TextMuted
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    state = favListState,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(favoriteItems) { index, item ->
                                        val isCurrent = item.listIndex == currentIndex &&
                                                item.itemType == contentType
                                        val accentColor = if (item.itemType == "channel") CyanGlow else PurpleGlow

                                        ItemRow(
                                            number = item.listIndex + 1,
                                            name = item.name,
                                            logoUrl = item.imageUrl,
                                            imageLoader = imageLoader,
                                            isSelected = index == selectedFavIndex && focusColumn == 1,
                                            isCurrent = isCurrent,
                                            accentColor = accentColor,
                                            badge = if (item.itemType == "channel") "TV" else "FM"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                PanelMode.BUSCAR -> {
                    // Search content
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(460.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        DarkBackground.copy(alpha = 0.95f),
                                        DarkBackground.copy(alpha = 0.88f),
                                        Color.Transparent
                                    ),
                                    startX = 0f,
                                    endX = 600f
                                )
                            )
                            .padding(16.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = GreenGlow,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Buscar",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Search input
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                selectedSearchIndex = 0
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .focusRequester(searchInputFR)
                                .onPreviewKeyEvent { event ->
                                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                        when (event.nativeKeyEvent.keyCode) {
                                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                if (searchResults.isNotEmpty()) {
                                                    searchFocusOnInput = false
                                                }
                                                true
                                            }
                                            KeyEvent.KEYCODE_BACK -> {
                                                focusColumn = 0
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                },
                            placeholder = {
                                androidx.compose.material3.Text(
                                    "Buscar canales o radios...",
                                    color = TextMuted
                                )
                            },
                            leadingIcon = {
                                androidx.compose.material3.Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = GreenGlow
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    androidx.compose.material3.IconButton(
                                        onClick = { searchQuery = "" }
                                    ) {
                                        androidx.compose.material3.Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Limpiar",
                                            tint = TextMuted
                                        )
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CardBackground,
                                unfocusedContainerColor = CardBackground,
                                focusedBorderColor = GreenGlow,
                                unfocusedBorderColor = CardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = GreenGlow
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Search results
                        when {
                            searchQuery.isBlank() -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = null,
                                            tint = TextMuted,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Escribí para buscar",
                                            fontSize = 14.sp,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }
                            searchResults.isEmpty() -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.SearchOff,
                                            contentDescription = null,
                                            tint = TextMuted,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Sin resultados para \"$searchQuery\"",
                                            fontSize = 14.sp,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }
                            else -> {
                                // Results count
                                Text(
                                    "${searchResults.size} resultado${if (searchResults.size != 1) "s" else ""}",
                                    fontSize = 12.sp,
                                    color = TextMuted,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .focusRequester(searchResultsFR)
                                        .focusable()
                                        .onKeyEvent { event ->
                                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && focusColumn == 1 && !searchFocusOnInput) {
                                                when (event.nativeKeyEvent.keyCode) {
                                                    KeyEvent.KEYCODE_DPAD_UP -> {
                                                        if (selectedSearchIndex > 0) {
                                                            selectedSearchIndex--
                                                            scope.launch {
                                                                searchListState.animateScrollToItem(
                                                                    (selectedSearchIndex - 2).coerceAtLeast(0)
                                                                )
                                                            }
                                                        } else {
                                                            searchFocusOnInput = true
                                                        }
                                                        true
                                                    }
                                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                        if (selectedSearchIndex < searchResults.size - 1) {
                                                            selectedSearchIndex++
                                                            scope.launch {
                                                                searchListState.animateScrollToItem(
                                                                    (selectedSearchIndex - 2).coerceAtLeast(0)
                                                                )
                                                            }
                                                        }
                                                        true
                                                    }
                                                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                                        val selected = searchResults.getOrNull(selectedSearchIndex)
                                                        if (selected != null) {
                                                            onItemSelect(selected.itemType, selected.listIndex)
                                                        }
                                                        true
                                                    }
                                                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                        searchFocusOnInput = true
                                                        true
                                                    }
                                                    else -> false
                                                }
                                            } else false
                                        }
                                ) {
                                    LazyColumn(
                                        state = searchListState,
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        itemsIndexed(searchResults) { index, result ->
                                            val isCurrent = result.listIndex == currentIndex &&
                                                    result.itemType == contentType
                                            val accentColor = if (result.itemType == "channel") CyanGlow else PurpleGlow

                                            ItemRow(
                                                number = result.listIndex + 1,
                                                name = result.name,
                                                logoUrl = result.imageUrl,
                                                imageLoader = imageLoader,
                                                isSelected = index == selectedSearchIndex && focusColumn == 1 && !searchFocusOnInput,
                                                isCurrent = isCurrent,
                                                accentColor = accentColor,
                                                badge = if (result.itemType == "channel") "TV" else "FM"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Navigation hints
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when (focusColumn) {
                    0 -> {
                        Text(
                            "▲▼ Navegar • ► Entrar • BACK Salir",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                    1 -> {
                        if (panelMode == PanelMode.CANALES || panelMode == PanelMode.RADIO) {
                            Text(
                                "▲▼ Categoría • ► Items • ◄ Volver",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        } else {
                            Text(
                                "▲▼ Navegar • OK Reproducir • ◄ Volver",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                    2 -> {
                        Text(
                            "▲▼ Navegar • OK Reproducir • ◄ Volver",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// Mode item in left column
// ============================================================

@Composable
private fun ModeItem(
    entry: ModeEntry,
    isSelected: Boolean,
    isActiveMode: Boolean,
    isCurrentlyPlaying: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isSelected -> entry.color.copy(alpha = 0.2f)
                    isActiveMode -> entry.color.copy(alpha = 0.08f)
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) entry.color else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isSelected || isActiveMode) entry.color.copy(alpha = 0.15f)
                    else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                entry.icon,
                contentDescription = entry.label,
                tint = if (isSelected || isActiveMode) entry.color else TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = entry.label,
            fontSize = 13.sp,
            fontWeight = if (isSelected || isActiveMode) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isSelected || isActiveMode) entry.color else TextSecondary,
            modifier = Modifier.weight(1f)
        )

        // Currently playing indicator
        if (isCurrentlyPlaying) {
            LiveIndicator()
        }
    }
}

// ============================================================
// Category item
// ============================================================

@Composable
private fun CategoryItem(
    name: String,
    count: Int,
    isSelected: Boolean,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) accentColor.copy(alpha = 0.2f) else Color.Transparent
            )
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) accentColor else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Indicator bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isSelected) accentColor else CardBorder)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = name,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isSelected) accentColor else TextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (isSelected) accentColor.copy(alpha = 0.2f) else CardBackground
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "$count",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) accentColor else TextMuted
            )
        }
    }
}

// ============================================================
// Item row (channel/station/favorite/search result)
// ============================================================

@Composable
private fun ItemRow(
    number: Int,
    name: String,
    logoUrl: String?,
    imageLoader: ImageLoader,
    isSelected: Boolean,
    isCurrent: Boolean,
    accentColor: Color,
    badge: String? = null
) {
    val backgroundColor = when {
        isSelected -> accentColor.copy(alpha = 0.2f)
        isCurrent -> OrangeGlow.copy(alpha = 0.1f)
        else -> Color.Transparent
    }
    val borderColor = when {
        isSelected -> accentColor
        isCurrent -> OrangeGlow.copy(alpha = 0.5f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .border(
                width = if (isSelected || isCurrent) 2.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Number
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isSelected) accentColor else CardBackground),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$number",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) DarkBackground else TextMuted
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Logo
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CardBackground)
                .border(1.dp, CardBorder, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (!logoUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = name,
                    imageLoader = imageLoader,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Icon(
                    Icons.Default.Tv,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Name
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 14.sp,
                fontWeight = if (isSelected || isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) accentColor else if (isCurrent) OrangeGlow else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Badge (TV/FM for favorites and search)
        if (badge != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(accentColor.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badge,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
        }

        // Currently playing indicator
        if (isCurrent) {
            Spacer(modifier = Modifier.width(6.dp))
            LiveIndicator()
        }
    }
}

// ============================================================
// Live indicator (pulsing red dot)
// ============================================================

@Composable
private fun LiveIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "live")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(Color.Red.copy(alpha = alpha))
    )
}

// ============================================================
// Error state
// ============================================================

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerErrorState(
    message: String,
    onRetry: () -> Unit
) {
    val retryFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        retryFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(PinkGlow.copy(alpha = 0.15f))
                    .border(2.dp, PinkGlow.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = PinkGlow,
                    modifier = Modifier.size(50.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Error de reproducción",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                message,
                fontSize = 18.sp,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = onRetry,
                modifier = Modifier.focusRequester(retryFocusRequester),
                colors = ButtonDefaults.colors(
                    containerColor = CyanGlow,
                    contentColor = DarkBackground
                ),
                shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp))
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Reintentar",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )
            }
        }
    }
}
