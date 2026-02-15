package com.televisionalternativa.streamsonic_tv.ui.screens.player

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultHttpDataSource
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
import com.televisionalternativa.streamsonic_tv.data.model.Station
import com.televisionalternativa.streamsonic_tv.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channels: List<Channel>? = null,
    stations: List<Station>? = null,
    initialIndex: Int = 0,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Image loader with SVG support
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }
    
    val isChannelMode = channels != null
    val itemCount = channels?.size ?: stations?.size ?: 0
    
    // Current playing index
    var currentIndex by remember { mutableIntStateOf(initialIndex.coerceIn(0, (itemCount - 1).coerceAtLeast(0))) }
    
    // Panel state
    var showPanel by remember { mutableStateOf(false) }
    var selectedPanelIndex by remember { mutableIntStateOf(currentIndex) }
    
    // Player state
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var playerError by remember { mutableStateOf<String?>(null) }
    
    // Focus
    val mainFocusRequester = remember { FocusRequester() }
    
    // Get current item data
    val currentTitle = if (isChannelMode) {
        channels?.getOrNull(currentIndex)?.name ?: ""
    } else {
        stations?.getOrNull(currentIndex)?.name ?: ""
    }
    
    val currentStreamUrl = if (isChannelMode) {
        channels?.getOrNull(currentIndex)?.streamUrl ?: ""
    } else {
        stations?.getOrNull(currentIndex)?.streamUrl ?: ""
    }
    
    val currentCategory = if (isChannelMode) {
        channels?.getOrNull(currentIndex)?.category ?: ""
    } else {
        stations?.getOrNull(currentIndex)?.category ?: ""
    }
    
    // Create ExoPlayer with full streaming support
    val exoPlayer = remember {
        // OkHttp client for better HTTP handling
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        
        // OkHttp data source for streams
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(Util.getUserAgent(context, "StreamsonicTV"))
        
        // Track selector with adaptive streaming support
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSizeSd() // Start with SD, upgrade if bandwidth allows
                    .setPreferredAudioLanguage("es")
            )
        }
        
        // Load control for buffering
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
    
    // Play stream when index changes
    LaunchedEffect(currentStreamUrl) {
        if (currentStreamUrl.isNotEmpty()) {
            playerError = null
            val mediaItem = MediaItem.fromUri(currentStreamUrl)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
        }
    }
    
    // Auto-hide controls (only for video mode)
    LaunchedEffect(showControls, showPanel) {
        if (showControls && isChannelMode && !showPanel) {
            delay(4000)
            showControls = false
        }
    }
    
    // Request main focus initially
    LaunchedEffect(Unit) {
        mainFocusRequester.requestFocus()
    }
    
    // Request main focus when panel closes
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
    
    // Change channel function
    fun changeChannel(newIndex: Int) {
        if (newIndex in 0 until itemCount) {
            currentIndex = newIndex
            showControls = true
        }
    }
    
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
                            onBack()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            // Show channel panel
                            showPanel = true
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                            // Previous channel (direct, no panel)
                            changeChannel(currentIndex - 1)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                            // Next channel (direct, no panel)
                            changeChannel(currentIndex + 1)
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            if (exoPlayer.isPlaying) exoPlayer.pause()
                            else exoPlayer.play()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (isChannelMode) showControls = true
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        when {
            playerError != null -> {
                PlayerErrorState(
                    message = playerError!!,
                    onRetry = {
                        playerError = null
                        exoPlayer.setMediaItem(MediaItem.fromUri(currentStreamUrl))
                        exoPlayer.prepare()
                    },
                    onBack = onBack
                )
            }
            isChannelMode -> {
                // Video Player for channels
                VideoPlayerContent(
                    exoPlayer = exoPlayer,
                    title = currentTitle,
                    category = currentCategory,
                    isPlaying = isPlaying,
                    showControls = showControls && !showPanel,
                    currentIndex = currentIndex,
                    totalItems = itemCount
                )
            }
            else -> {
                // Audio Player for radio stations
                AudioPlayerContent(
                    exoPlayer = exoPlayer,
                    title = currentTitle,
                    isPlaying = isPlaying,
                    currentIndex = currentIndex,
                    totalItems = itemCount
                )
            }
        }
        
        // Channel/Station Panel Overlay
        AnimatedVisibility(
            visible = showPanel,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
        ) {
            ChannelPanel(
                channels = channels,
                stations = stations,
                currentIndex = currentIndex,
                onChannelSelect = { index ->
                    changeChannel(index)
                    showPanel = false
                },
                onDismiss = { showPanel = false },
                imageLoader = imageLoader
            )
        }
    }
}

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
                            // Channel number badge
                            Box(
                                modifier = Modifier
                                    .background(
                                        CyanGlow,
                                        RoundedCornerShape(8.dp)
                                    )
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
                            Text(
                                "En vivo",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            
                            if (category.isNotEmpty()) {
                                Text(
                                    "•",
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                                Text(
                                    category,
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
                
                // Bottom controls hint
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                        .align(Alignment.BottomStart),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "OK: Lista de canales • ▲▼: Cambiar canal • BACK: Salir",
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

@Composable
private fun AudioPlayerContent(
    exoPlayer: ExoPlayer,
    title: String,
    isPlaying: Boolean,
    currentIndex: Int,
    totalItems: Int
) {
    // Animated waves
    val infiniteTransition = rememberInfiniteTransition(label = "audio")
    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave1"
    )
    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave2"
    )
    val wave3 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave3"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
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
                        colors = listOf(
                            PurpleGlow.copy(alpha = 0.1f),
                            Color.Transparent
                        )
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
                        colors = listOf(
                            OrangeGlow.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Station number
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
                        brush = Brush.linearGradient(
                            colors = listOf(OrangeGlow, PurpleGlow)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(CircleShape)
                        .background(CardBackground)
                        .border(
                            width = 1.dp,
                            color = CardBorder,
                            shape = CircleShape
                        ),
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
            
            // Title
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
                        Brush.linearGradient(
                            colors = listOf(OrangeGlow, PurpleGlow)
                        )
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
            
            // Controls hint
            Text(
                "OK: Lista de estaciones • ▲▼: Cambiar estación • BACK: Volver",
                fontSize = 14.sp,
                color = TextMuted
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelPanel(
    channels: List<Channel>?,
    stations: List<Station>?,
    currentIndex: Int,
    onChannelSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    imageLoader: ImageLoader
) {
    val isChannelMode = channels != null
    val scope = rememberCoroutineScope()
    
    // LazyListState for items list
    val listState = rememberLazyListState()
    
    // Group by category
    val groupedItems = remember(channels, stations) {
        if (isChannelMode && channels != null) {
            channels.groupBy { it.category ?: "Otros" }
                .toSortedMap(compareBy { if (it == "Otros") "zzz" else it })
        } else if (stations != null) {
            stations.groupBy { it.category ?: "Otros" }
                .toSortedMap(compareBy { if (it == "Otros") "zzz" else it })
        } else {
            emptyMap()
        }
    }
    
    val categories = remember(groupedItems) { groupedItems.keys.toList() }
    
    // State
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var selectedItemIndexInCategory by remember { mutableIntStateOf(0) }
    var focusOnCategories by remember { mutableStateOf(true) }
    
    // Get current category items
    val currentCategory = categories.getOrNull(selectedCategoryIndex) ?: ""
    val currentCategoryItems = groupedItems[currentCategory] ?: emptyList()
    
    // Calculate global index from category + local index
    fun getGlobalIndex(categoryIndex: Int, itemIndex: Int): Int {
        var globalIndex = 0
        categories.take(categoryIndex).forEach { cat ->
            globalIndex += groupedItems[cat]?.size ?: 0
        }
        return globalIndex + itemIndex
    }
    
    // Initialize with current playing item
    LaunchedEffect(currentIndex) {
        var accumulated = 0
        categories.forEachIndexed { catIndex, category ->
            val itemsInCategory = groupedItems[category]?.size ?: 0
            if (currentIndex < accumulated + itemsInCategory) {
                selectedCategoryIndex = catIndex
                selectedItemIndexInCategory = currentIndex - accumulated
                return@LaunchedEffect
            }
            accumulated += itemsInCategory
        }
    }
    
    // Focus requesters
    val categoryFocusRequester = remember { FocusRequester() }
    val itemFocusRequester = remember { FocusRequester() }
    
    LaunchedEffect(focusOnCategories) {
        delay(100)
        if (focusOnCategories) {
            categoryFocusRequester.requestFocus()
        } else {
            itemFocusRequester.requestFocus()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // LEFT: Categories panel
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(240.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                DarkSurface.copy(alpha = 0.98f),
                                DarkBackground.copy(alpha = 0.98f)
                            )
                        )
                    )
                    .focusRequester(categoryFocusRequester)
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && focusOnCategories) {
                            when (event.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_BACK -> {
                                    onDismiss()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    if (selectedCategoryIndex > 0) {
                                        selectedCategoryIndex--
                                        selectedItemIndexInCategory = 0
                                    }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    if (selectedCategoryIndex < categories.size - 1) {
                                        selectedCategoryIndex++
                                        selectedItemIndexInCategory = 0
                                    }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                    if (currentCategoryItems.isNotEmpty()) {
                                        focusOnCategories = false
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
                        modifier = Modifier.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Category,
                            contentDescription = null,
                            tint = CyanGlow,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Categorías",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Categories list
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(categories) { index, category ->
                            CategoryItem(
                                name = category,
                                count = groupedItems[category]?.size ?: 0,
                                isSelected = index == selectedCategoryIndex && focusOnCategories,
                                accentColor = if (isChannelMode) CyanGlow else PurpleGlow
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
                    .background(CardBorder.copy(alpha = 0.3f))
            )
            
            // RIGHT: Items panel
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(400.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                DarkBackground.copy(alpha = 0.95f),
                                DarkBackground.copy(alpha = 0.90f),
                                Color.Transparent
                            ),
                            startX = 0f,
                            endX = 500f
                        )
                    )
                    .focusRequester(itemFocusRequester)
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && !focusOnCategories) {
                            when (event.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    focusOnCategories = true
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    if (selectedItemIndexInCategory > 0) {
                                        selectedItemIndexInCategory--
                                        scope.launch {
                                            listState.animateScrollToItem(
                                                (selectedItemIndexInCategory - 2).coerceAtLeast(0)
                                            )
                                        }
                                    }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    if (selectedItemIndexInCategory < currentCategoryItems.size - 1) {
                                        selectedItemIndexInCategory++
                                        scope.launch {
                                            listState.animateScrollToItem(
                                                (selectedItemIndexInCategory - 2).coerceAtLeast(0)
                                            )
                                        }
                                    }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                    val globalIndex = getGlobalIndex(selectedCategoryIndex, selectedItemIndexInCategory)
                                    onChannelSelect(globalIndex)
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
                        modifier = Modifier.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isChannelMode) Icons.Default.Tv else Icons.Default.Radio,
                            contentDescription = null,
                            tint = if (isChannelMode) CyanGlow else PurpleGlow,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                currentCategory,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                "${currentCategoryItems.size} ${if (isChannelMode) "canales" else "estaciones"}",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Items list
                    if (currentCategoryItems.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Sin items en esta categoría",
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(currentCategoryItems) { localIndex, item ->
                                val globalIndex = getGlobalIndex(selectedCategoryIndex, localIndex)
                                val name = if (isChannelMode) (item as Channel).name else (item as Station).name
                                val logoUrl = if (isChannelMode) (item as Channel).imageUrl else (item as Station).imageUrl
                                
                                ChannelPanelItem(
                                    index = globalIndex,
                                    name = name,
                                    category = currentCategory,
                                    logoUrl = logoUrl,
                                    imageLoader = imageLoader,
                                    isSelected = localIndex == selectedItemIndexInCategory && !focusOnCategories,
                                    isCurrent = globalIndex == currentIndex
                                )
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
                if (focusOnCategories) {
                    Text(
                        "▲▼ Navegar • ► Entrar • BACK Salir",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                } else {
                    Text(
                        "▲▼ Navegar • OK Reproducir",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        "◄ Volver a categorías",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

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
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isSelected -> accentColor.copy(alpha = 0.2f)
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) accentColor else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Indicator
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isSelected) accentColor else CardBorder)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Name
        Text(
            text = name,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isSelected) accentColor else TextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Count badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isSelected) accentColor.copy(alpha = 0.2f)
                    else CardBackground
                )
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = "$count",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) accentColor else TextMuted
            )
        }
    }
}

@Composable
private fun CategoryHeaderCompact(
    title: String,
    count: Int,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Accent bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor)
        )
        
        Spacer(modifier = Modifier.width(10.dp))
        
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Count badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(accentColor.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "$count",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = accentColor
            )
        }
    }
}

@Composable
private fun ChannelPanelItem(
    index: Int,
    name: String,
    category: String,
    logoUrl: String?,
    imageLoader: ImageLoader,
    isSelected: Boolean,
    isCurrent: Boolean
) {
    val backgroundColor = when {
        isSelected -> CyanGlow.copy(alpha = 0.2f)
        isCurrent -> OrangeGlow.copy(alpha = 0.1f)
        else -> Color.Transparent
    }
    
    val borderColor = when {
        isSelected -> CyanGlow
        isCurrent -> OrangeGlow.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(
                width = if (isSelected || isCurrent) 2.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Number
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (isSelected) CyanGlow else CardBackground
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${index + 1}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) DarkBackground else TextMuted
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Logo
        Box(
            modifier = Modifier
                .size(44.dp)
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
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Name and category
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = name,
                fontSize = 15.sp,
                fontWeight = if (isSelected || isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) CyanGlow else if (isCurrent) OrangeGlow else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (category.isNotEmpty()) {
                Text(
                    text = category,
                    fontSize = 12.sp,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        // Current indicator
        if (isCurrent) {
            LiveIndicator()
        }
    }
}

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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerErrorState(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
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
                    .border(
                        width = 2.dp,
                        color = PinkGlow.copy(alpha = 0.3f),
                        shape = CircleShape
                    ),
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
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.focusRequester(retryFocusRequester),
                    colors = ButtonDefaults.colors(
                        containerColor = CyanGlow,
                        contentColor = DarkBackground
                    ),
                    shape = ButtonDefaults.shape(
                        shape = RoundedCornerShape(12.dp)
                    )
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
                
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.colors(
                        containerColor = CardBackground,
                        contentColor = TextPrimary
                    ),
                    shape = ButtonDefaults.shape(
                        shape = RoundedCornerShape(12.dp)
                    )
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Volver",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}
