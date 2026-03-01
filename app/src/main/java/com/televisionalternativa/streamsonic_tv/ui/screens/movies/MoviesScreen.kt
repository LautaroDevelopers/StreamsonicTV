package com.televisionalternativa.streamsonic_tv.ui.screens.movies

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.televisionalternativa.streamsonic_tv.data.model.Movie
import com.televisionalternativa.streamsonic_tv.data.repository.StreamsonicRepository
import com.televisionalternativa.streamsonic_tv.ui.theme.*
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

// ============================================================
// Focus area enum
// ============================================================
private enum class BrowseFocus { HERO, CAROUSEL }

// ============================================================
// MoviesScreen
// ============================================================

@OptIn(UnstableApi::class)
@Composable
fun MoviesScreen(
    repository: StreamsonicRepository,
    onBack: () -> Unit,
    onPlayMovie: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }

    // ===== DATA STATE =====
    var movies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // ===== BROWSING STATE =====
    var focusedMovie by remember { mutableStateOf<Movie?>(null) }
    var categoryIndex by remember { mutableIntStateOf(0) }
    var movieIndex by remember { mutableIntStateOf(0) }
    var browseFocus by remember { mutableStateOf(BrowseFocus.CAROUSEL) }

    // ===== PLAYER STATE =====
    var isPlayerVisible by remember { mutableStateOf(false) }
    var playingMovie by remember { mutableStateOf<Movie?>(null) }
    var playerError by remember { mutableStateOf<String?>(null) }

    // ===== DERIVED =====
    val grouped = remember(movies) {
        movies.filter { it.isActive }
            .groupBy { it.category ?: "Otros" }
            .toSortedMap(compareBy { if (it == "Otros") "zzz" else it })
    }
    val categories = remember(grouped) { grouped.keys.toList() }

    // Update focused movie when indices change
    LaunchedEffect(categoryIndex, movieIndex, categories, grouped) {
        if (categories.isNotEmpty()) {
            val cat = categories.getOrNull(categoryIndex) ?: return@LaunchedEffect
            val moviesInCat = grouped[cat] ?: return@LaunchedEffect
            val safeIdx = movieIndex.coerceIn(0, (moviesInCat.size - 1).coerceAtLeast(0))
            focusedMovie = moviesInCat.getOrNull(safeIdx)
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
                                "La película no está disponible."
                            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
                                "Formato no soportado."
                            else -> "Error de reproducción: ${error.message}"
                        }
                    }
                })
            }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    // ===== DATA LOADING =====
    LaunchedEffect(Unit) {
        isLoading = true
        repository.getMovies().fold(
            onSuccess = {
                movies = it
                isLoading = false
            },
            onFailure = {
                errorMessage = it.message
                isLoading = false
            }
        )
    }

    // ===== FOCUS =====
    val mainFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isLoading) {
        if (!isLoading && movies.isNotEmpty()) {
            mainFocusRequester.requestFocus()
        }
    }

    // ===== PLAY MOVIE =====
    fun playMovie(movie: Movie) {
        val url = movie.streamUrl ?: return
        playingMovie = movie
        isPlayerVisible = true
        playerError = null
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun stopPlayer() {
        exoPlayer.stop()
        isPlayerVisible = false
        playingMovie = null
        playerError = null
    }

    // ===== UI =====
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        when {
            isLoading -> {
                // Loading state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = OrangeGlow,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Cargando películas...",
                            color = TextSecondary,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            errorMessage != null -> {
                // Error state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            errorMessage ?: "Error desconocido",
                            color = TextSecondary,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            movies.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No hay películas disponibles",
                        color = TextSecondary,
                        fontSize = 16.sp
                    )
                }
            }

            else -> {
                // ===== MAIN BROWSE LAYOUT =====
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(mainFocusRequester)
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                            if (isPlayerVisible) {
                                when (event.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                                        stopPlayer()
                                        true
                                    }
                                    else -> false
                                }
                            } else {
                                handleBrowseKeyEvent(
                                    keyCode = event.nativeKeyEvent.keyCode,
                                    categories = categories,
                                    grouped = grouped,
                                    categoryIndex = categoryIndex,
                                    movieIndex = movieIndex,
                                    browseFocus = browseFocus,
                                    onCategoryIndexChange = { categoryIndex = it },
                                    onMovieIndexChange = { movieIndex = it },
                                    onBrowseFocusChange = { browseFocus = it },
                                    onPlayMovie = { movie -> playMovie(movie) },
                                    onBack = onBack
                                )
                            }
                        }
                ) {
                    // ===== HERO SECTION =====
                    HeroSection(
                        movie = focusedMovie,
                        imageLoader = imageLoader,
                        isFocused = browseFocus == BrowseFocus.HERO
                    )

                    // ===== CAROUSELS =====
                    CarouselSection(
                        categories = categories,
                        grouped = grouped,
                        categoryIndex = categoryIndex,
                        movieIndex = movieIndex,
                        imageLoader = imageLoader
                    )
                }
            }
        }

        // ===== FULLSCREEN PLAYER OVERLAY =====
        AnimatedVisibility(
            visible = isPlayerVisible,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Movie title overlay
                playingMovie?.let { movie ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Text(
                            movie.title,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Error overlay
                playerError?.let { error ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(error, color = TextSecondary, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Presioná BACK para volver",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // ===== BACK HINT (when browsing) =====
        if (!isPlayerVisible && movies.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, DarkBackground)
                        )
                    )
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "◄► Navegar • ▲▼ Categorías • OK Reproducir • BACK Volver",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ============================================================
// D-Pad Navigation Handler
// ============================================================

private fun handleBrowseKeyEvent(
    keyCode: Int,
    categories: List<String>,
    grouped: Map<String, List<Movie>>,
    categoryIndex: Int,
    movieIndex: Int,
    browseFocus: BrowseFocus,
    onCategoryIndexChange: (Int) -> Unit,
    onMovieIndexChange: (Int) -> Unit,
    onBrowseFocusChange: (BrowseFocus) -> Unit,
    onPlayMovie: (Movie) -> Unit,
    onBack: () -> Unit
): Boolean {
    if (categories.isEmpty()) return false

    val currentCat = categories.getOrNull(categoryIndex) ?: return false
    val moviesInCat = grouped[currentCat] ?: return false

    return when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> {
            if (movieIndex > 0) {
                onMovieIndexChange(movieIndex - 1)
            }
            true
        }

        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            if (movieIndex < moviesInCat.size - 1) {
                onMovieIndexChange(movieIndex + 1)
            }
            true
        }

        KeyEvent.KEYCODE_DPAD_UP -> {
            if (categoryIndex > 0) {
                onCategoryIndexChange(categoryIndex - 1)
                // Clamp movie index to new category size
                val newCat = categories[categoryIndex - 1]
                val newCatSize = grouped[newCat]?.size ?: 0
                if (movieIndex >= newCatSize) {
                    onMovieIndexChange((newCatSize - 1).coerceAtLeast(0))
                }
            }
            true
        }

        KeyEvent.KEYCODE_DPAD_DOWN -> {
            if (categoryIndex < categories.size - 1) {
                onCategoryIndexChange(categoryIndex + 1)
                // Clamp movie index to new category size
                val newCat = categories[categoryIndex + 1]
                val newCatSize = grouped[newCat]?.size ?: 0
                if (movieIndex >= newCatSize) {
                    onMovieIndexChange((newCatSize - 1).coerceAtLeast(0))
                }
            }
            true
        }

        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
            val movie = moviesInCat.getOrNull(movieIndex)
            if (movie?.streamUrl != null) {
                onPlayMovie(movie)
            }
            true
        }

        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
            onBack()
            true
        }

        else -> false
    }
}

// ============================================================
// Hero Section
// ============================================================

@Composable
private fun HeroSection(
    movie: Movie?,
    imageLoader: ImageLoader,
    isFocused: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
    ) {
        // Background — blurred poster effect
        movie?.imageUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                imageLoader = imageLoader,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        // Dark overlay
                        drawRect(Color.Black.copy(alpha = 0.6f))
                    }
            )
        }

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            DarkBackground.copy(alpha = 0.95f),
                            DarkBackground.copy(alpha = 0.7f),
                            DarkBackground.copy(alpha = 0.4f)
                        )
                    )
                )
        )

        // Bottom gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, DarkBackground)
                    )
                )
        )

        // Content
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Poster
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(listOf(OrangeGlow, PinkGlow)),
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                if (movie?.imageUrl != null) {
                    AsyncImage(
                        model = movie.imageUrl,
                        contentDescription = movie.title,
                        imageLoader = imageLoader,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(CardBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Movie,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(28.dp))

            // Info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                // Category badge
                movie?.category?.let { cat ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(OrangeGlow.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            cat.uppercase(),
                            color = OrangeGlow,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Title
                Text(
                    movie?.title ?: "Seleccioná una película",
                    color = TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Description
                Text(
                    movie?.description ?: "",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Play indicator
                if (movie?.streamUrl != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.linearGradient(listOf(OrangeGlow, PinkGlow))
                                )
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "REPRODUCIR",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Title "Películas" top-left
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 40.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Movie,
                contentDescription = null,
                tint = OrangeGlow,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Películas",
                color = OrangeGlow,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ============================================================
// Carousel Section
// ============================================================

@Composable
private fun ColumnScope.CarouselSection(
    categories: List<String>,
    grouped: Map<String, List<Movie>>,
    categoryIndex: Int,
    movieIndex: Int,
    imageLoader: ImageLoader
) {
    val carouselListState = rememberLazyListState()

    // Auto-scroll to focused category
    LaunchedEffect(categoryIndex) {
        carouselListState.animateScrollToItem(categoryIndex.coerceAtLeast(0))
    }

    LazyColumn(
        state = carouselListState,
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        itemsIndexed(categories) { catIdx, category ->
            val moviesInCat = grouped[category] ?: emptyList()
            val isActiveCat = catIdx == categoryIndex

            CategoryCarousel(
                categoryName = category,
                movies = moviesInCat,
                isActiveCategory = isActiveCat,
                activeMovieIndex = if (isActiveCat) movieIndex else -1,
                imageLoader = imageLoader
            )
        }
    }
}

// ============================================================
// Category Carousel Row
// ============================================================

@Composable
private fun CategoryCarousel(
    categoryName: String,
    movies: List<Movie>,
    isActiveCategory: Boolean,
    activeMovieIndex: Int,
    imageLoader: ImageLoader
) {
    val rowListState = rememberLazyListState()

    // Auto-scroll to focused movie within the row
    LaunchedEffect(activeMovieIndex) {
        if (activeMovieIndex >= 0) {
            rowListState.animateScrollToItem(activeMovieIndex.coerceAtLeast(0))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        // Category label
        Text(
            categoryName,
            color = if (isActiveCategory) OrangeGlow else TextSecondary,
            fontSize = 14.sp,
            fontWeight = if (isActiveCategory) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(start = 40.dp, bottom = 6.dp)
        )

        // Movies row
        LazyRow(
            state = rowListState,
            contentPadding = PaddingValues(horizontal = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(movies) { idx, movie ->
                MovieCard(
                    movie = movie,
                    isFocused = isActiveCategory && idx == activeMovieIndex,
                    imageLoader = imageLoader
                )
            }
        }
    }
}

// ============================================================
// Movie Card
// ============================================================

@Composable
private fun MovieCard(
    movie: Movie,
    isFocused: Boolean,
    imageLoader: ImageLoader
) {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = tween(200),
        label = "cardScale"
    )

    val borderAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(200),
        label = "borderAlpha"
    )

    Box(
        modifier = Modifier
            .width(130.dp)
            .height(185.dp)
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                OrangeGlow.copy(alpha = borderAlpha),
                                PinkGlow.copy(alpha = borderAlpha)
                            )
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                } else Modifier
            )
    ) {
        // Poster image
        if (movie.imageUrl != null) {
            AsyncImage(
                model = movie.imageUrl,
                contentDescription = movie.title,
                imageLoader = imageLoader,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CardBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Movie,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Bottom gradient with title
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                ),
            contentAlignment = Alignment.BottomStart
        ) {
            Text(
                movie.title,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }

        // Play icon when focused
        if (isFocused) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(OrangeGlow.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Reproducir",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
