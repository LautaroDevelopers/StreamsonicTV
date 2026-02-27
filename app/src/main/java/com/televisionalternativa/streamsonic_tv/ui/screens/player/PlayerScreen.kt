package com.televisionalternativa.streamsonic_tv.ui.screens.player

import android.app.Activity
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
import androidx.compose.ui.graphics.vector.ImageVector
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
// Tab definitions
// ============================================================

private data class TabDef(
    val label: String,
    val icon: ImageVector,
    val color: Color
)

private val tabs = listOf(
    TabDef("Canales", Icons.Filled.LiveTv, CyanGlow),
    TabDef("Radio", Icons.Filled.Radio, PurpleGlow),
    TabDef("Favoritos", Icons.Filled.Favorite, PinkGlow),
    TabDef("Buscar", Icons.Filled.Search, GreenGlow)
)

private data class FavoriteItem(
    val itemId: Int,
    val itemType: String,
    val name: String,
    val imageUrl: String?,
    val category: String?,
    val streamUrl: String,
    val listIndex: Int
)

private data class SearchResult(
    val name: String,
    val imageUrl: String?,
    val category: String?,
    val itemType: String,
    val listIndex: Int,
    val id: Int
)

// Focus areas inside the panel
private enum class PanelFocus { TABS, CONTENT, ITEMS }

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
    val activity = context as? Activity
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
    var activeTab by remember { mutableIntStateOf(0) } // 0=Canales, 1=Radio, 2=Favs, 3=Search

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

            repository.getFavorites().fold(
                onSuccess = { favorites = it },
                onFailure = { }
            )

            val lastIndex = repository.getLastChannelIndex()
            val lastType = repository.getLastContentType()
            contentType = lastType
            val maxIndex = if (lastType == "channel") channels.size - 1 else stations.size - 1
            currentIndex = lastIndex.coerceIn(0, maxIndex.coerceAtLeast(0))

            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadContent() }

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
                    else -> { }
                }
            }
        }
    }

    // ===== PLAY STREAM ON CHANGE =====
    LaunchedEffect(currentStreamUrl) {
        if (currentStreamUrl.isNotEmpty()) {
            playerError = null
            exoPlayer.setMediaItem(MediaItem.fromUri(currentStreamUrl))
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

    // Cleanup
    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    // ===== CHANNEL CHANGE =====
    fun changeChannel(newIndex: Int) {
        val maxIdx = if (isChannelMode) channels.size - 1 else stations.size - 1
        if (newIndex in 0..maxIdx) {
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
                        KeyEvent.KEYCODE_BACK -> {
                            // Close app completely
                            exoPlayer.stop()
                            exoPlayer.release()
                            activity?.finishAndRemoveTask()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            activeTab = if (isChannelMode) 0 else 1
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
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
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
        // Focus management — inside the Box that has .focusRequester()
        LaunchedEffect(Unit) {
            mainFocusRequester.requestFocus()
        }
        LaunchedEffect(showPanel) {
            if (!showPanel) {
                delay(100)
                try { mainFocusRequester.requestFocus() } catch (_: Exception) { }
            }
        }

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

        // Panel overlay — 2 panel design
        AnimatedVisibility(
            visible = showPanel,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
        ) {
            TwoPanelOverlay(
                channels = channels,
                stations = stations,
                favoriteItems = favoriteItems,
                currentIndex = currentIndex,
                contentType = contentType,
                initialTab = activeTab,
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
// Two-Panel Overlay
// ============================================================

@Composable
private fun TwoPanelOverlay(
    channels: List<Channel>,
    stations: List<Station>,
    favoriteItems: List<FavoriteItem>,
    currentIndex: Int,
    contentType: String,
    initialTab: Int,
    onItemSelect: (type: String, index: Int) -> Unit,
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
    onRefreshFavorites: () -> Unit,
    imageLoader: ImageLoader
) {
    val scope = rememberCoroutineScope()

    // ===== STATE =====
    var tabIndex by remember { mutableIntStateOf(initialTab) }
    var focus by remember { mutableStateOf(PanelFocus.TABS) }
    var contentIndex by remember { mutableIntStateOf(0) } // selected in content list
    var itemIndex by remember { mutableIntStateOf(0) } // selected in items panel
    var showItemsPanel by remember { mutableStateOf(false) }

    // Search
    var searchQuery by remember { mutableStateOf("") }
    var searchFocusOnInput by remember { mutableStateOf(true) }

    // Logout row: treated as tabIndex = 4
    val totalTabSlots = tabs.size + 1 // 4 tabs + 1 logout

    // List states
    val contentListState = rememberLazyListState()
    val itemListState = rememberLazyListState()

    // Focus requesters
    val tabsFR = remember { FocusRequester() }
    val contentFR = remember { FocusRequester() }
    val itemsFR = remember { FocusRequester() }
    val searchInputFR = remember { FocusRequester() }
    val searchResultsFR = remember { FocusRequester() }

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

    // Active data based on tab
    val activeGrouped = when (tabIndex) {
        0 -> groupedChannels
        1 -> groupedStations
        else -> emptyMap()
    }
    val activeCategories = when (tabIndex) {
        0 -> channelCategories
        1 -> stationCategories
        else -> emptyList()
    }

    val selectedCatName = activeCategories.getOrNull(contentIndex) ?: ""
    val selectedCatItems = activeGrouped[selectedCatName] ?: emptyList()

    // Search results
    val searchResults = remember(searchQuery, channels, stations) {
        if (searchQuery.isBlank()) emptyList()
        else {
            val q = searchQuery.lowercase()
            val results = mutableListOf<SearchResult>()
            channels.forEachIndexed { i, ch ->
                if (ch.name.lowercase().contains(q))
                    results.add(SearchResult(ch.name, ch.imageUrl, ch.category, "channel", i, ch.id))
            }
            stations.forEachIndexed { i, st ->
                if (st.name.lowercase().contains(q))
                    results.add(SearchResult(st.name, st.imageUrl, st.category, "station", i, st.id))
            }
            results.sortedBy { if (it.name.lowercase().startsWith(q)) 0 else 1 }
        }
    }

    // Is current tab one that has categories → items (2-level)?
    val isTwoLevel = tabIndex == 0 || tabIndex == 1

    // ===== FOCUS MANAGEMENT =====
    LaunchedEffect(focus, searchFocusOnInput) {
        delay(80)
        try {
            when (focus) {
                PanelFocus.TABS -> tabsFR.requestFocus()
                PanelFocus.CONTENT -> {
                    if (tabIndex == 3 && searchFocusOnInput) searchInputFR.requestFocus()
                    else if (tabIndex == 3 && !searchFocusOnInput) searchResultsFR.requestFocus()
                    else contentFR.requestFocus()
                }
                PanelFocus.ITEMS -> itemsFR.requestFocus()
            }
        } catch (_: Exception) { }
    }

    // Reset content index when tab changes
    LaunchedEffect(tabIndex) {
        contentIndex = 0
        itemIndex = 0
        showItemsPanel = false
        searchFocusOnInput = true

        // For Canales/Radio, try to find current playing item's category
        if (isTwoLevel) {
            val targetType = if (tabIndex == 0) "channel" else "station"
            if (contentType == targetType && activeCategories.isNotEmpty()) {
                var found = false
                activeCategories.forEachIndexed { catIdx, cat ->
                    val items = activeGrouped[cat] ?: emptyList()
                    items.forEach { indexed ->
                        if (indexed.index == currentIndex && !found) {
                            contentIndex = catIdx
                            found = true
                        }
                    }
                }
            }
        }

        // Refresh favs
        if (tabIndex == 2) onRefreshFavorites()
    }

    // ===== UI =====
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {

            // ========================================
            // PANEL A: Tabs + Content
            // ========================================
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(320.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                DarkSurface.copy(alpha = 0.98f),
                                DarkBackground.copy(alpha = 0.98f)
                            )
                        )
                    )
            ) {
                // ---------- TAB BAR ----------
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(tabsFR)
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && focus == PanelFocus.TABS) {
                                when (event.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_BACK -> {
                                        onDismiss()
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_UP -> {
                                        if (tabIndex > 0) tabIndex--
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        if (tabIndex < totalTabSlots - 1) tabIndex++
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                        if (tabIndex == 4) {
                                            // Logout
                                            onLogout()
                                        } else {
                                            focus = PanelFocus.CONTENT
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
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        // App logo
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.linearGradient(listOf(CyanGlow, PurpleGlow))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Streamsonic",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        // Tab items
                        tabs.forEachIndexed { index, tab ->
                            val isSelected = index == tabIndex && focus == PanelFocus.TABS
                            val isActive = index == tabIndex
                            val isNowPlaying = (index == 0 && contentType == "channel") ||
                                    (index == 1 && contentType == "station")

                            TabItem(
                                icon = tab.icon,
                                label = tab.label,
                                color = tab.color,
                                isSelected = isSelected,
                                isActive = isActive,
                                isNowPlaying = isNowPlaying
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                        }

                        // Divider
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(CardBorder.copy(alpha = 0.4f))
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Logout
                        @Suppress("DEPRECATION")
                        TabItem(
                            icon = Icons.Filled.ExitToApp,
                            label = "Desconectar",
                            color = ErrorRed,
                            isSelected = tabIndex == 4 && focus == PanelFocus.TABS,
                            isActive = false,
                            isNowPlaying = false
                        )
                    }
                }

                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(CardBorder.copy(alpha = 0.3f))
                )

                // ---------- CONTENT AREA ----------
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (tabIndex) {
                        // Canales / Radio → show categories
                        0, 1 -> {
                            val accentColor = if (tabIndex == 0) CyanGlow else PurpleGlow

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .focusRequester(contentFR)
                                    .focusable()
                                    .onKeyEvent { event ->
                                        if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && focus == PanelFocus.CONTENT) {
                                            when (event.nativeKeyEvent.keyCode) {
                                                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                    focus = PanelFocus.TABS
                                                    true
                                                }
                                                KeyEvent.KEYCODE_DPAD_UP -> {
                                                    if (contentIndex > 0) {
                                                        contentIndex--
                                                        scope.launch {
                                                            contentListState.animateScrollToItem(
                                                                (contentIndex - 2).coerceAtLeast(0)
                                                            )
                                                        }
                                                    }
                                                    true
                                                }
                                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                    if (contentIndex < activeCategories.size - 1) {
                                                        contentIndex++
                                                        scope.launch {
                                                            contentListState.animateScrollToItem(
                                                                (contentIndex - 2).coerceAtLeast(0)
                                                            )
                                                        }
                                                    }
                                                    true
                                                }
                                                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                                    if (selectedCatItems.isNotEmpty()) {
                                                        itemIndex = 0
                                                        showItemsPanel = true
                                                        focus = PanelFocus.ITEMS
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
                                    Row(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Category,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Categorías",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        Text(
                                            "${activeCategories.size}",
                                            fontSize = 12.sp,
                                            color = TextMuted
                                        )
                                    }

                                    LazyColumn(
                                        state = contentListState,
                                        verticalArrangement = Arrangement.spacedBy(3.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        itemsIndexed(activeCategories) { index, cat ->
                                            CategoryRow(
                                                name = cat,
                                                count = activeGrouped[cat]?.size ?: 0,
                                                isSelected = index == contentIndex && focus == PanelFocus.CONTENT,
                                                accentColor = accentColor
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Favoritos → show items directly
                        2 -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .focusRequester(contentFR)
                                    .focusable()
                                    .onKeyEvent { event ->
                                        if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && focus == PanelFocus.CONTENT) {
                                            when (event.nativeKeyEvent.keyCode) {
                                                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                    focus = PanelFocus.TABS
                                                    true
                                                }
                                                KeyEvent.KEYCODE_DPAD_UP -> {
                                                    if (contentIndex > 0) {
                                                        contentIndex--
                                                        scope.launch {
                                                            contentListState.animateScrollToItem(
                                                                (contentIndex - 2).coerceAtLeast(0)
                                                            )
                                                        }
                                                    }
                                                    true
                                                }
                                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                    if (contentIndex < favoriteItems.size - 1) {
                                                        contentIndex++
                                                        scope.launch {
                                                            contentListState.animateScrollToItem(
                                                                (contentIndex - 2).coerceAtLeast(0)
                                                            )
                                                        }
                                                    }
                                                    true
                                                }
                                                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                                    val fav = favoriteItems.getOrNull(contentIndex)
                                                    if (fav != null) onItemSelect(fav.itemType, fav.listIndex)
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
                                    Row(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Favorite, null, tint = PinkGlow, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Favoritos", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        if (favoriteItems.isNotEmpty()) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(PinkGlow.copy(alpha = 0.15f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("${favoriteItems.size}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = PinkGlow)
                                            }
                                        }
                                    }

                                    if (favoriteItems.isEmpty()) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(Icons.Default.FavoriteBorder, null, tint = TextMuted, modifier = Modifier.size(40.dp))
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text("Sin favoritos", fontSize = 14.sp, color = TextPrimary)
                                                Text("Agregá desde la app móvil", fontSize = 12.sp, color = TextMuted)
                                            }
                                        }
                                    } else {
                                        LazyColumn(
                                            state = contentListState,
                                            verticalArrangement = Arrangement.spacedBy(3.dp),
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            itemsIndexed(favoriteItems) { index, item ->
                                                val isCurrent = item.listIndex == currentIndex && item.itemType == contentType
                                                val ac = if (item.itemType == "channel") CyanGlow else PurpleGlow
                                                ItemRow(
                                                    number = item.listIndex + 1,
                                                    name = item.name,
                                                    logoUrl = item.imageUrl,
                                                    imageLoader = imageLoader,
                                                    isSelected = index == contentIndex && focus == PanelFocus.CONTENT,
                                                    isCurrent = isCurrent,
                                                    accentColor = ac,
                                                    badge = if (item.itemType == "channel") "TV" else "FM"
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Buscar → search input + results
                        3 -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Search, null, tint = GreenGlow, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Buscar", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                }

                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it; contentIndex = 0 },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .focusRequester(searchInputFR)
                                        .onPreviewKeyEvent { event ->
                                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                                when (event.nativeKeyEvent.keyCode) {
                                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                        if (searchResults.isNotEmpty()) {
                                                            searchFocusOnInput = false
                                                            focus = PanelFocus.CONTENT
                                                        }
                                                        true
                                                    }
                                                    KeyEvent.KEYCODE_BACK -> {
                                                        focus = PanelFocus.TABS
                                                        true
                                                    }
                                                    else -> false
                                                }
                                            } else false
                                        },
                                    placeholder = {
                                        androidx.compose.material3.Text("Buscar canales o radios...", color = TextMuted)
                                    },
                                    leadingIcon = {
                                        androidx.compose.material3.Icon(Icons.Default.Search, null, tint = GreenGlow)
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            androidx.compose.material3.IconButton(onClick = { searchQuery = "" }) {
                                                androidx.compose.material3.Icon(Icons.Default.Close, "Limpiar", tint = TextMuted)
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
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                when {
                                    searchQuery.isBlank() -> {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(Icons.Default.Search, null, tint = TextMuted, modifier = Modifier.size(40.dp))
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text("Escribí para buscar", fontSize = 13.sp, color = TextMuted)
                                            }
                                        }
                                    }
                                    searchResults.isEmpty() -> {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("Sin resultados para \"$searchQuery\"", fontSize = 13.sp, color = TextMuted)
                                        }
                                    }
                                    else -> {
                                        Text(
                                            "${searchResults.size} resultado${if (searchResults.size != 1) "s" else ""}",
                                            fontSize = 11.sp, color = TextMuted,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )

                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .focusRequester(searchResultsFR)
                                                .focusable()
                                                .onKeyEvent { event ->
                                                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && focus == PanelFocus.CONTENT && !searchFocusOnInput) {
                                                        when (event.nativeKeyEvent.keyCode) {
                                                            KeyEvent.KEYCODE_DPAD_UP -> {
                                                                if (contentIndex > 0) {
                                                                    contentIndex--
                                                                    scope.launch { contentListState.animateScrollToItem((contentIndex - 2).coerceAtLeast(0)) }
                                                                } else {
                                                                    searchFocusOnInput = true
                                                                }
                                                                true
                                                            }
                                                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                                if (contentIndex < searchResults.size - 1) {
                                                                    contentIndex++
                                                                    scope.launch { contentListState.animateScrollToItem((contentIndex - 2).coerceAtLeast(0)) }
                                                                }
                                                                true
                                                            }
                                                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                                                val sr = searchResults.getOrNull(contentIndex)
                                                                if (sr != null) onItemSelect(sr.itemType, sr.listIndex)
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
                                                state = contentListState,
                                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                itemsIndexed(searchResults) { index, result ->
                                                    val isCurrent = result.listIndex == currentIndex && result.itemType == contentType
                                                    val ac = if (result.itemType == "channel") CyanGlow else PurpleGlow
                                                    ItemRow(
                                                        number = result.listIndex + 1,
                                                        name = result.name,
                                                        logoUrl = result.imageUrl,
                                                        imageLoader = imageLoader,
                                                        isSelected = index == contentIndex && focus == PanelFocus.CONTENT && !searchFocusOnInput,
                                                        isCurrent = isCurrent,
                                                        accentColor = ac,
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

                // Bottom hint
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val hint = when (focus) {
                        PanelFocus.TABS -> "▲▼ Navegar • ► Entrar • BACK Cerrar"
                        PanelFocus.CONTENT -> if (isTwoLevel) "▲▼ Categoría • ► Items • ◄ Volver" else "▲▼ Navegar • OK Reproducir • ◄ Volver"
                        PanelFocus.ITEMS -> "▲▼ Navegar • OK Reproducir • ◄ Volver"
                    }
                    Text(hint, fontSize = 11.sp, color = TextMuted)
                }
            }

            // ========================================
            // PANEL B: Items (slides in for Canales/Radio)
            // ========================================
            AnimatedVisibility(
                visible = showItemsPanel && isTwoLevel,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            ) {
                val isChMode = tabIndex == 0
                val accentColor = if (isChMode) CyanGlow else PurpleGlow

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(380.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    DarkBackground.copy(alpha = 0.95f),
                                    DarkBackground.copy(alpha = 0.88f),
                                    Color.Transparent
                                ),
                                startX = 0f,
                                endX = 500f
                            )
                        )
                        .focusRequester(itemsFR)
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && focus == PanelFocus.ITEMS) {
                                when (event.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_LEFT -> {
                                        showItemsPanel = false
                                        focus = PanelFocus.CONTENT
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_UP -> {
                                        if (itemIndex > 0) {
                                            itemIndex--
                                            scope.launch { itemListState.animateScrollToItem((itemIndex - 2).coerceAtLeast(0)) }
                                        }
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        if (itemIndex < selectedCatItems.size - 1) {
                                            itemIndex++
                                            scope.launch { itemListState.animateScrollToItem((itemIndex - 2).coerceAtLeast(0)) }
                                        }
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                        val sel = selectedCatItems.getOrNull(itemIndex)
                                        if (sel != null) {
                                            val type = if (isChMode) "channel" else "station"
                                            onItemSelect(type, sel.index)
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
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isChMode) Icons.Default.Tv else Icons.Default.Radio,
                                null, tint = accentColor, modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    selectedCatName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${selectedCatItems.size} ${if (isChMode) "canales" else "estaciones"}",
                                    fontSize = 11.sp, color = TextMuted
                                )
                            }
                        }

                        LazyColumn(
                            state = itemListState,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(selectedCatItems) { localIndex, indexedItem ->
                                val name = if (isChMode) (indexedItem.value as Channel).name else (indexedItem.value as Station).name
                                val logoUrl = if (isChMode) (indexedItem.value as Channel).imageUrl else (indexedItem.value as Station).imageUrl
                                val isCurrent = indexedItem.index == currentIndex &&
                                        ((isChMode && contentType == "channel") || (!isChMode && contentType == "station"))

                                ItemRow(
                                    number = indexedItem.index + 1,
                                    name = name,
                                    logoUrl = logoUrl,
                                    imageLoader = imageLoader,
                                    isSelected = localIndex == itemIndex && focus == PanelFocus.ITEMS,
                                    isCurrent = isCurrent,
                                    accentColor = accentColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// Tab item
// ============================================================

@Composable
private fun TabItem(
    icon: ImageVector,
    label: String,
    color: Color,
    isSelected: Boolean,
    isActive: Boolean,
    isNowPlaying: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isSelected -> color.copy(alpha = 0.2f)
                    isActive -> color.copy(alpha = 0.08f)
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) color else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, contentDescription = label,
            tint = if (isSelected || isActive) color else TextMuted,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (isSelected || isActive) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isSelected || isActive) color else TextSecondary,
            modifier = Modifier.weight(1f)
        )
        if (isNowPlaying) { LiveIndicator() }
    }
}

// ============================================================
// Category row
// ============================================================

@Composable
private fun CategoryRow(
    name: String,
    count: Int,
    isSelected: Boolean,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) accentColor.copy(alpha = 0.2f) else Color.Transparent)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) accentColor else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
                .background(if (isSelected) accentColor.copy(alpha = 0.2f) else CardBackground)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text("$count", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = if (isSelected) accentColor else TextMuted)
        }
        if (isSelected) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.Default.ChevronRight, null, tint = accentColor, modifier = Modifier.size(16.dp))
        }
    }
}

// ============================================================
// Item row
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
    val bg = when {
        isSelected -> accentColor.copy(alpha = 0.2f)
        isCurrent -> OrangeGlow.copy(alpha = 0.1f)
        else -> Color.Transparent
    }
    val bc = when {
        isSelected -> accentColor
        isCurrent -> OrangeGlow.copy(alpha = 0.5f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(if (isSelected || isCurrent) 2.dp else 0.dp, bc, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(26.dp).clip(RoundedCornerShape(6.dp))
                .background(if (isSelected) accentColor else CardBackground),
            contentAlignment = Alignment.Center
        ) {
            Text("$number", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isSelected) DarkBackground else TextMuted)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(7.dp))
                .background(CardBackground).border(1.dp, CardBorder, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (!logoUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = logoUrl, contentDescription = name, imageLoader = imageLoader,
                    modifier = Modifier.fillMaxSize().padding(3.dp), contentScale = ContentScale.Fit
                )
            } else {
                Icon(Icons.Default.Tv, null, tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = name, fontSize = 13.sp,
            fontWeight = if (isSelected || isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) accentColor else if (isCurrent) OrangeGlow else TextPrimary,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        if (badge != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier.clip(RoundedCornerShape(4.dp))
                    .background(accentColor.copy(alpha = 0.15f))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(badge, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = accentColor)
            }
        }
        if (isCurrent) {
            Spacer(modifier = Modifier.width(4.dp))
            LiveIndicator()
        }
    }
}

// ============================================================
// Loading screen
// ============================================================

@Composable
private fun PlayerLoadingContent() {
    Box(Modifier.fillMaxSize().background(DarkBackground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(80.dp), color = CyanGlow.copy(alpha = 0.2f), strokeWidth = 6.dp)
                CircularProgressIndicator(Modifier.size(60.dp), color = CyanGlow, strokeWidth = 4.dp)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("Cargando contenido...", fontSize = 18.sp, color = TextSecondary)
        }
    }
}

// ============================================================
// Video player content
// ============================================================

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerContent(
    exoPlayer: ExoPlayer, title: String, category: String,
    isPlaying: Boolean, showControls: Boolean, currentIndex: Int, totalItems: Int
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(0.7f), Color.Transparent, Color.Transparent, Color.Black.copy(0.7f)))
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(32.dp).align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.background(CyanGlow, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Text("${currentIndex + 1}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkBackground)
                            }
                            Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LiveIndicator()
                            Text("En vivo", fontSize = 14.sp, color = Color.White.copy(0.8f))
                            if (category.isNotEmpty()) {
                                Text("•", fontSize = 14.sp, color = Color.White.copy(0.5f))
                                Text(category, fontSize = 14.sp, color = Color.White.copy(0.7f))
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(32.dp).align(Alignment.BottomStart),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("OK: Panel • ▲▼: Cambiar canal", fontSize = 14.sp, color = Color.White.copy(0.7f))
                    Text("${currentIndex + 1} / $totalItems", fontSize = 14.sp, color = Color.White.copy(0.5f))
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
    exoPlayer: ExoPlayer, title: String, isPlaying: Boolean, currentIndex: Int, totalItems: Int
) {
    val inf = rememberInfiniteTransition(label = "a")
    val w1 by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(800, easing = EaseInOutSine), RepeatMode.Reverse), label = "w1")
    val w2 by inf.animateFloat(0.5f, 0.8f, infiniteRepeatable(tween(600, easing = EaseInOutSine), RepeatMode.Reverse), label = "w2")
    val w3 by inf.animateFloat(0.4f, 0.9f, infiniteRepeatable(tween(1000, easing = EaseInOutSine), RepeatMode.Reverse), label = "w3")
    val ps by inf.animateFloat(1f, 1.05f, infiniteRepeatable(tween(1500, easing = EaseInOutSine), RepeatMode.Reverse), label = "p")

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(DarkBackground, DarkSurface, DarkBackground)))) {
        Box(Modifier.size(400.dp).offset((-150).dp, 100.dp).background(Brush.radialGradient(listOf(PurpleGlow.copy(0.1f), Color.Transparent))))
        Box(Modifier.size(350.dp).align(Alignment.TopEnd).offset(100.dp, (-50).dp).background(Brush.radialGradient(listOf(OrangeGlow.copy(0.08f), Color.Transparent))))

        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Estación ${currentIndex + 1} de $totalItems", fontSize = 14.sp, color = TextMuted)
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.size((220 * ps).dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(OrangeGlow.copy(0.15f), OrangeGlow.copy(0.05f), Color.Transparent)))
                    .border(3.dp, Brush.linearGradient(listOf(OrangeGlow, PurpleGlow)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(150.dp).clip(CircleShape).background(CardBackground).border(1.dp, CardBorder, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Radio, null, Modifier.size(70.dp), tint = OrangeGlow)
                }
            }
            Spacer(Modifier.height(48.dp))
            Text(title, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isPlaying) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        listOf(w1, w2, w3, w2, w1).forEach { h ->
                            Box(Modifier.width(4.dp).height((h * 24).dp).clip(RoundedCornerShape(2.dp)).background(OrangeGlow))
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                }
                Text(if (isPlaying) "Reproduciendo" else "Pausado", fontSize = 20.sp, color = if (isPlaying) OrangeGlow else TextMuted)
            }
            Spacer(Modifier.height(48.dp))
            Box(Modifier.size(100.dp).clip(CircleShape).background(Brush.linearGradient(listOf(OrangeGlow, PurpleGlow))), contentAlignment = Alignment.Center) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, Modifier.size(50.dp), tint = Color.White)
            }
            Spacer(Modifier.height(48.dp))
            Text("OK: Panel • ▲▼: Cambiar estación", fontSize = 14.sp, color = TextMuted)
        }
    }
}

// ============================================================
// Live indicator
// ============================================================

@Composable
private fun LiveIndicator() {
    val inf = rememberInfiniteTransition(label = "live")
    val alpha by inf.animateFloat(1f, 0.3f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse")
    Box(Modifier.size(10.dp).clip(CircleShape).background(Color.Red.copy(alpha)))
}

// ============================================================
// Error state
// ============================================================

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerErrorState(message: String, onRetry: () -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }

    Box(Modifier.fillMaxSize().background(DarkBackground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(100.dp).clip(CircleShape).background(PinkGlow.copy(0.15f)).border(2.dp, PinkGlow.copy(0.3f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ErrorOutline, null, tint = PinkGlow, modifier = Modifier.size(50.dp))
            }
            Spacer(Modifier.height(32.dp))
            Text("Error de reproducción", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            Text(message, fontSize = 18.sp, color = TextMuted)
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = onRetry, modifier = Modifier.focusRequester(fr),
                colors = ButtonDefaults.colors(containerColor = CyanGlow, contentColor = DarkBackground),
                shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Default.Refresh, null, Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reintentar", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            }
        }
    }
}
