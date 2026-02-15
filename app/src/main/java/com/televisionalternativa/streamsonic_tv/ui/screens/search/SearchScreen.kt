package com.televisionalternativa.streamsonic_tv.ui.screens.search

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.google.gson.Gson
import com.televisionalternativa.streamsonic_tv.data.model.Channel
import com.televisionalternativa.streamsonic_tv.data.model.Station
import com.televisionalternativa.streamsonic_tv.data.repository.StreamsonicRepository
import com.televisionalternativa.streamsonic_tv.ui.theme.*
import kotlinx.coroutines.launch

enum class SearchFilter {
    ALL, CHANNELS, RADIOS
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    repository: StreamsonicRepository,
    onChannelClick: (Channel, List<Channel>) -> Unit,
    onStationClick: (Station, List<Station>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .crossfade(true)
            .build()
    }
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(SearchFilter.ALL) }
    
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var stations by remember { mutableStateOf<List<Station>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Load data once
    LaunchedEffect(Unit) {
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
            
            isLoading = false
        }
    }
    
    // Filter results locally
    val filteredResults = remember(searchQuery, selectedFilter, channels, stations) {
        if (searchQuery.isBlank()) {
            emptyMap<String, List<Any>>()
        } else {
            val query = searchQuery.lowercase()
            val results = mutableMapOf<String, List<Any>>()
            
            if (selectedFilter == SearchFilter.ALL || selectedFilter == SearchFilter.CHANNELS) {
                val matchedChannels = channels
                    .filter { it.name.lowercase().contains(query) }
                    .sortedBy { 
                        if (it.name.lowercase().startsWith(query)) 0 else 1 
                    }
                if (matchedChannels.isNotEmpty()) {
                    results["Canales"] = matchedChannels
                }
            }
            
            if (selectedFilter == SearchFilter.ALL || selectedFilter == SearchFilter.RADIOS) {
                val matchedStations = stations
                    .filter { it.name.lowercase().contains(query) }
                    .sortedBy { 
                        if (it.name.lowercase().startsWith(query)) 0 else 1 
                    }
                if (matchedStations.isNotEmpty()) {
                    results["Radios"] = matchedStations
                }
            }
            
            results
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with search bar
            SearchHeader(
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                selectedFilter = selectedFilter,
                onFilterSelected = { selectedFilter = it }
            )
            
            // Content
            when {
                isLoading -> {
                    LoadingState(modifier = Modifier.fillMaxSize())
                }
                errorMessage != null -> {
                    ErrorState(
                        message = errorMessage!!,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                searchQuery.isBlank() -> {
                    EmptySearchState(modifier = Modifier.fillMaxSize())
                }
                filteredResults.isEmpty() -> {
                    NoResultsState(
                        query = searchQuery,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    SearchResults(
                        results = filteredResults,
                        channels = channels,
                        stations = stations,
                        onChannelClick = onChannelClick,
                        onStationClick = onStationClick,
                        imageLoader = imageLoader
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalMaterial3Api::class)
@Composable
private fun SearchHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedFilter: SearchFilter,
    onFilterSelected: (SearchFilter) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DarkSurface.copy(alpha = 0.95f),
                        Color.Transparent
                    )
                )
            )
            .padding(24.dp)
    ) {
        // Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = CyanGlow,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Buscar",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Search TextField
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(56.dp),
            placeholder = {
                Text(
                    "Buscar canales o radios...",
                    color = TextMuted
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = CyanGlow
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
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
                focusedBorderColor = CyanGlow,
                unfocusedBorderColor = CardBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = CyanGlow
            ),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Filters
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SearchFilter.values().forEach { filter ->
                FilterChip(
                    filter = filter,
                    isSelected = selectedFilter == filter,
                    onClick = { onFilterSelected(filter) }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilterChip(
    filter: SearchFilter,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val (label, icon) = when (filter) {
        SearchFilter.ALL -> "Todo" to Icons.Outlined.Apps
        SearchFilter.CHANNELS -> "Canales" to Icons.Outlined.LiveTv
        SearchFilter.RADIOS -> "Radios" to Icons.Outlined.Radio
    }
    
    val accentColor = when (filter) {
        SearchFilter.ALL -> CyanGlow
        SearchFilter.CHANNELS -> CyanGlow
        SearchFilter.RADIOS -> PurpleGlow
    }
    
    var isFocused by remember { mutableStateOf(false) }
    
    Surface(
        onClick = onClick,
        modifier = Modifier
            .height(44.dp)
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) accentColor.copy(alpha = 0.15f) else CardBackground,
            focusedContainerColor = if (isSelected) accentColor.copy(alpha = 0.2f) else CardBackground
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) accentColor else CardBorder
                )
            ),
            focusedBorder = Border(
                border = BorderStroke(width = 2.dp, color = accentColor)
            )
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected || isFocused) accentColor else TextMuted,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isSelected || isFocused) accentColor else TextSecondary
            )
        }
    }
}

@Composable
private fun SearchResults(
    results: Map<String, List<Any>>,
    channels: List<Channel>,
    stations: List<Station>,
    onChannelClick: (Channel, List<Channel>) -> Unit,
    onStationClick: (Station, List<Station>) -> Unit,
    imageLoader: ImageLoader
) {
    TvLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        results.forEach { (category, items) ->
            item(key = "header_$category") {
                CategoryHeader(
                    title = category,
                    count = items.size,
                    accentColor = if (category == "Canales") CyanGlow else PurpleGlow
                )
            }
            
            item(key = "row_$category") {
                TvLazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = items,
                        key = { item ->
                            when (item) {
                                is Channel -> "channel_${item.id}"
                                is Station -> "station_${item.id}"
                                else -> item.hashCode()
                            }
                        }
                    ) { item ->
                        when (item) {
                            is Channel -> {
                                SearchChannelCard(
                                    channel = item,
                                    onClick = { onChannelClick(item, channels) },
                                    imageLoader = imageLoader
                                )
                            }
                            is Station -> {
                                SearchStationCard(
                                    station = item,
                                    onClick = { onStationClick(item, stations) },
                                    imageLoader = imageLoader
                                )
                            }
                        }
                    }
                }
            }
            
            item(key = "spacer_$category") {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CategoryHeader(
    title: String,
    count: Int,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor.copy(alpha = 0.15f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "$count",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = accentColor
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchChannelCard(
    channel: Channel,
    onClick: () -> Unit,
    imageLoader: ImageLoader
) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(120.dp)
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = CardBackground,
            focusedContainerColor = CardBackground
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(border = BorderStroke(1.dp, CardBorder)),
            focusedBorder = Border(
                border = BorderStroke(
                    3.dp,
                    Brush.linearGradient(listOf(CyanGlow, PurpleGlow))
                )
            )
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevation = 16.dp, elevationColor = CyanGlow.copy(alpha = 0.4f))
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(DarkSurface, CardBackground)
                        )
                    )
            )
            
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(channel.imageUrl)
                    .crossfade(true)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = channel.name,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit
            )
            
            AnimatedVisibility(
                visible = isFocused,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(8.dp)
                ) {
                    Text(
                        text = channel.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchStationCard(
    station: Station,
    onClick: () -> Unit,
    imageLoader: ImageLoader
) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(120.dp)
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = CardBackground,
            focusedContainerColor = CardBackground
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(border = BorderStroke(1.dp, CardBorder)),
            focusedBorder = Border(
                border = BorderStroke(
                    3.dp,
                    Brush.linearGradient(listOf(PurpleGlow, PinkGlow))
                )
            )
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevation = 16.dp, elevationColor = PurpleGlow.copy(alpha = 0.4f))
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(DarkSurface, CardBackground)
                        )
                    )
            )
            
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(station.imageUrl)
                    .crossfade(true)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = station.name,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit
            )
            
            AnimatedVisibility(
                visible = isFocused,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(8.dp)
                ) {
                    Text(
                        text = station.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySearchState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Buscar contenido",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Usa el teclado virtual para buscar canales o radios",
                fontSize = 14.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.6f)
            )
        }
    }
}

@Composable
private fun NoResultsState(
    query: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Outlined.SearchOff,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Sin resultados",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No se encontraron resultados para \"$query\"",
                fontSize = 14.sp,
                color = TextMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
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
                "Cargando...",
                fontSize = 16.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.WifiOff,
                contentDescription = null,
                tint = PinkGlow,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Error de conexión",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                message,
                fontSize = 14.sp,
                color = TextMuted
            )
        }
    }
}
