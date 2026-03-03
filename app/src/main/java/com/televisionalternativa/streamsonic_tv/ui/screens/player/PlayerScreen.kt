package com.televisionalternativa.streamsonic_tv.ui.screens.player

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.KeyEvent
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

// ============================================================
// Tab definitions
// ============================================================

internal data class TabDef(
    val label: String,
    val icon: ImageVector,
    val color: Color
)

internal val tabs = listOf(
    TabDef("Canales", Icons.Filled.LiveTv, CyanGlow),
    TabDef("Radio", Icons.Filled.Radio, PurpleGlow),
    TabDef("Favoritos", Icons.Filled.Favorite, PinkGlow),
    TabDef("Buscar", Icons.Filled.Search, GreenGlow),
    TabDef("Películas", Icons.Filled.Movie, OrangeGlow)
)

internal data class FavoriteItem(
    val itemId: Int,
    val itemType: String,
    val name: String,
    val imageUrl: String?,
    val category: String?,
    val streamUrl: String,
    val listIndex: Int
)

internal data class SearchResult(
    val name: String,
    val imageUrl: String?,
    val category: String?,
    val itemType: String,
    val listIndex: Int,
    val id: Int
)

// Focus areas inside the panel
internal enum class PanelFocus { TABS, CONTENT }

// Panel navigation levels (for sliding panel design)
// CONTENT: Shows all channels/radios
// CATEGORIES: Shows category list
// FILTERED: Shows channels/radios filtered by selected category
internal enum class PanelLevel { CONTENT, CATEGORIES, FILTERED }

// ============================================================
// Main PlayerScreen
// ============================================================

@Composable
fun PlayerScreen(
    repository: StreamsonicRepository,
    onLogout: () -> Unit,
    onNavigateToMovies: () -> Unit = {}
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

    // ===== AUTO-RETRY STATE =====
    var retryCount by remember { mutableIntStateOf(0) }
    var isRetrying by remember { mutableStateOf(false) }
    val maxRetries = 5

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

    // ===== VLC =====
    // Holder class — NOT mutableStateOf, so changes don't trigger recomposition
    class VlcHolder {
        var libVlc: LibVLC? = null
        var mediaPlayer: MediaPlayer? = null
    }
    val vlcHolder = remember { VlcHolder() }
    var vlcReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val vlc = withContext(Dispatchers.IO) {
            LibVLC(context, arrayListOf(
                "--network-caching=1500",
                "--no-drop-late-frames",
                "--no-skip-frames",
                "--rtsp-tcp",
                "--aout=opensles",
                "--audio-time-stretch",
            ))
        }
        val player = MediaPlayer(vlc)
        player.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    isPlaying = true
                    retryCount = 0
                    isRetrying = false
                }
                MediaPlayer.Event.Paused,
                MediaPlayer.Event.Stopped -> {
                    isPlaying = false
                }
                MediaPlayer.Event.EncounteredError -> {
                    retryCount++
                    isRetrying = true
                }
            }
        }
        vlcHolder.libVlc = vlc
        vlcHolder.mediaPlayer = player
        vlcReady = true
    }

    // ===== DATA LOADING =====
    fun loadContent() {
        scope.launch {
            isLoading = true
            errorMessage = null

            repository.getChannels().fold(
                onSuccess = { channels = it },
                onFailure = { errorMessage = "No se pudo cargar el contenido. Verificá tu conexión." }
            )

            repository.getStations().fold(
                onSuccess = { stations = it },
                onFailure = { if (errorMessage == null) errorMessage = "No se pudo cargar el contenido. Verificá tu conexión." }
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
    LaunchedEffect(currentStreamUrl, vlcReady) {
        if (!vlcReady) return@LaunchedEffect
        val player = vlcHolder.mediaPlayer ?: return@LaunchedEffect
        val vlc = vlcHolder.libVlc ?: return@LaunchedEffect
        if (currentStreamUrl.isNotEmpty()) {
            playerError = null
            retryCount = 0
            isRetrying = false
            val media = Media(vlc, android.net.Uri.parse(currentStreamUrl))
            player.media = media
            media.release()
            player.play()
        }
    }

    // ===== AUTO-RETRY LOGIC =====
    LaunchedEffect(retryCount) {
        val player = vlcHolder.mediaPlayer ?: return@LaunchedEffect
        val vlc = vlcHolder.libVlc ?: return@LaunchedEffect
        if (retryCount > 0 && currentStreamUrl.isNotEmpty()) {
            if (retryCount <= maxRetries) {
                delay((1000L + retryCount * 1000L))
                playerError = null
                val media = Media(vlc, android.net.Uri.parse(currentStreamUrl))
                player.media = media
                media.release()
                player.play()
            } else {
                // All retries exhausted — skip to next channel/station
                isRetrying = false
                retryCount = 0
                val maxIdx = if (isChannelMode) channels.size - 1 else stations.size - 1
                if (maxIdx > 0) {
                    val nextIndex = if (currentIndex < maxIdx) currentIndex + 1 else 0
                    currentIndex = nextIndex
                    showControls = true
                    scope.launch { repository.saveLastChannel(nextIndex, contentType) }
                } else {
                    playerError = "No se pudo reproducir después de $maxRetries intentos."
                }
            }
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
        onDispose {
            vlcHolder.mediaPlayer?.stop()
            vlcHolder.mediaPlayer?.release()
            vlcHolder.libVlc?.release()
        }
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
                            vlcHolder.mediaPlayer?.stop()
                            vlcHolder.mediaPlayer?.release()
                            vlcHolder.libVlc?.release()
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
                            val p = vlcHolder.mediaPlayer
                            if (p?.isPlaying == true) p.pause() else p?.play()
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
                        val player = vlcHolder.mediaPlayer ?: return@PlayerErrorState
                        val vlc = vlcHolder.libVlc ?: return@PlayerErrorState
                        playerError = null
                        retryCount = 0
                        isRetrying = false
                        val media = Media(vlc, android.net.Uri.parse(currentStreamUrl))
                        player.media = media
                        media.release()
                        player.play()
                    }
                )
            }
            isChannelMode -> {
                VideoPlayerContent(
                    mediaPlayer = vlcHolder.mediaPlayer,
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
                    mediaPlayer = vlcHolder.mediaPlayer,
                    title = currentTitle,
                    isPlaying = isPlaying,
                    currentIndex = safeIndex,
                    totalItems = itemCount
                )
            }
        }

        // Retry overlay
        if (isRetrying && retryCount in 1..maxRetries) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = CyanGlow,
                        modifier = Modifier.size(40.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Reintentando conexión...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Intento $retryCount de $maxRetries",
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                }
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
                onNavigateToMovies = onNavigateToMovies,
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
    onNavigateToMovies: () -> Unit,
    onRefreshFavorites: () -> Unit,
    imageLoader: ImageLoader
) {
    val scope = rememberCoroutineScope()

    // Single live pulse animation for the entire panel
    val livePulseAlpha = rememberLivePulseAlpha()

    // ===== STATE =====
    var tabIndex by remember { mutableIntStateOf(initialTab) }
    var focus by remember { mutableStateOf(PanelFocus.TABS) }
    var contentIndex by remember { mutableIntStateOf(0) }
    var isScrolling by remember { mutableStateOf(false) }

    // Panel sliding levels
    var panelLevel by remember { mutableStateOf(PanelLevel.CONTENT) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    // Search
    var searchQuery by remember { mutableStateOf("") }
    var searchFocusOnInput by remember { mutableStateOf(true) }

    // Logout row: treated as tabIndex = 5 (after Películas at 4)
    val totalTabSlots = tabs.size + 1

    // List states
    val contentListState = rememberLazyListState()

    // Focus requesters
    val tabsFR = remember { FocusRequester() }
    val contentFR = remember { FocusRequester() }
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

    // ===== FOCUS MANAGEMENT =====
    LaunchedEffect(focus, searchFocusOnInput) {
        delay(120)
        try {
            when (focus) {
                PanelFocus.TABS -> tabsFR.requestFocus()
                PanelFocus.CONTENT -> {
                    if (tabIndex == 3 && searchFocusOnInput) searchInputFR.requestFocus()
                    else if (tabIndex == 3 && !searchFocusOnInput) searchResultsFR.requestFocus()
                    else contentFR.requestFocus()
                }
            }
        } catch (_: Exception) { }
    }

    // Reset content index when tab changes
    LaunchedEffect(tabIndex) {
        contentIndex = 0
        panelLevel = PanelLevel.CONTENT
        selectedCategory = null
        searchFocusOnInput = true

        // Refresh favs
        if (tabIndex == 2) onRefreshFavorites()
    }

    // Determine if current tab supports categories
    val hasCategories = tabIndex == 0 || tabIndex == 1

    // Get filtered content based on panel level
    val displayItems: List<IndexedValue<Any>> = remember(tabIndex, panelLevel, selectedCategory, channels, stations) {
        when {
            tabIndex == 0 -> {
                if (panelLevel == PanelLevel.FILTERED && selectedCategory != null) {
                    groupedChannels[selectedCategory] ?: emptyList()
                } else {
                    channels.withIndex().toList()
                }
            }
            tabIndex == 1 -> {
                if (panelLevel == PanelLevel.FILTERED && selectedCategory != null) {
                    groupedStations[selectedCategory] ?: emptyList()
                } else {
                    stations.withIndex().toList()
                }
            }
            else -> emptyList()
        }
    }

    // ===== UI =====
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {

            PanelTabsColumn(
                tabIndex = tabIndex,
                focus = focus,
                contentType = contentType,
                panelLevel = panelLevel,
                tabsFR = tabsFR,
                livePulseAlpha = livePulseAlpha,
                onTabUp = { if (tabIndex > 0) tabIndex-- },
                onTabDown = { if (tabIndex < totalTabSlots - 1) tabIndex++ },
                onTabSelect = { focus = PanelFocus.CONTENT },
                onDismiss = onDismiss,
                onLogout = onLogout,
                onNavigateToMovies = onNavigateToMovies
            )

            AnimatedVisibility(
                visible = focus == PanelFocus.CONTENT,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            ) {
                PanelContentBox(
                    tabIndex = tabIndex,
                    focus = focus,
                    contentIndex = contentIndex,
                    panelLevel = panelLevel,
                    selectedCategory = selectedCategory,
                    searchQuery = searchQuery,
                    searchFocusOnInput = searchFocusOnInput,
                    currentIndex = currentIndex,
                    contentType = contentType,
                    channels = channels,
                    stations = stations,
                    favoriteItems = favoriteItems,
                    searchResults = searchResults,
                    displayItems = displayItems,
                    activeCategories = activeCategories,
                    activeGrouped = activeGrouped,
                    contentListState = contentListState,
                    contentFR = contentFR,
                    searchInputFR = searchInputFR,
                    searchResultsFR = searchResultsFR,
                    imageLoader = imageLoader,
                    livePulseAlpha = livePulseAlpha,
                    scope = scope,
                    onContentIndexChange = { contentIndex = it },
                    onPanelLevelChange = { panelLevel = it },
                    onSelectedCategoryChange = { selectedCategory = it },
                    onFocusChange = { focus = it },
                    onSearchQueryChange = { searchQuery = it },
                    onSearchFocusOnInputChange = { searchFocusOnInput = it },
                    onItemSelect = onItemSelect
                )
            }
        }
    }
}


// ============================================================
// Tab item
// ============================================================

@Composable
internal fun TabItem(
    icon: ImageVector,
    label: String,
    color: Color,
    tabIndex: Int,
    currentTabIndex: Int,
    focus: PanelFocus,
    isNowPlaying: Boolean,
    livePulseAlpha: Float
) {
    // Direct calculation — these are cheap comparisons, no need for derivedStateOf
    val isSelected = tabIndex == currentTabIndex && focus == PanelFocus.TABS
    val isActive = tabIndex == currentTabIndex

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
        if (isNowPlaying) { LiveIndicator(alpha = livePulseAlpha) }
    }
}

// ============================================================
// Category row
// ============================================================

@Composable
internal fun CategoryRow(
    name: String,
    count: Int,
    index: Int,
    contentIndex: Int,
    focus: PanelFocus,
    accentColor: Color
) {
    val isSelected = index == contentIndex && focus == PanelFocus.CONTENT

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
internal fun ItemRow(
    number: Int,
    name: String,
    logoUrl: String?,
    imageLoader: ImageLoader,
    index: Int,
    contentIndex: Int,
    focus: PanelFocus,
    isCurrent: Boolean,
    accentColor: Color,
    badge: String? = null,
    searchFocusOnInput: Boolean = false,
    livePulseAlpha: Float = 1f
) {
    val isSelected = index == contentIndex && focus == PanelFocus.CONTENT && !searchFocusOnInput

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
            LiveIndicator(alpha = livePulseAlpha)
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

@Composable
private fun VideoPlayerContent(
    mediaPlayer: MediaPlayer?, title: String, category: String,
    isPlaying: Boolean, showControls: Boolean, currentIndex: Int, totalItems: Int
) {
    if (mediaPlayer == null) return
    val livePulseAlpha = rememberLivePulseAlpha()
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).also { layout ->
                    mediaPlayer.attachViews(layout, null, false, false)
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
                            LiveIndicator(alpha = livePulseAlpha)
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
    mediaPlayer: MediaPlayer?, title: String, isPlaying: Boolean, currentIndex: Int, totalItems: Int
) {
    if (mediaPlayer == null) return
    // Only animate when actually playing — saves CPU when paused
    val inf = rememberInfiniteTransition(label = "a")
    val w1 by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(800, easing = EaseInOutSine), RepeatMode.Reverse), label = "w1")
    val w2 by inf.animateFloat(0.5f, 0.8f, infiniteRepeatable(tween(600, easing = EaseInOutSine), RepeatMode.Reverse), label = "w2")
    val w3 by inf.animateFloat(0.4f, 0.9f, infiniteRepeatable(tween(1000, easing = EaseInOutSine), RepeatMode.Reverse), label = "w3")
    val ps by inf.animateFloat(
        initialValue = 1f, targetValue = if (isPlaying) 1.05f else 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "p"
    )

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

// Single shared transition — hoisted to avoid one per item
@Composable
private fun rememberLivePulseAlpha(): Float {
    val inf = rememberInfiniteTransition(label = "live")
    val alpha by inf.animateFloat(1f, 0.3f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse")
    return alpha
}

@Composable
private fun LiveIndicator(alpha: Float) {
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
