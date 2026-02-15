package com.televisionalternativa.streamsonic_tv.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.televisionalternativa.streamsonic_tv.data.model.Channel
import com.televisionalternativa.streamsonic_tv.data.model.Station
import com.televisionalternativa.streamsonic_tv.data.repository.StreamsonicRepository
import com.televisionalternativa.streamsonic_tv.ui.theme.*
import kotlinx.coroutines.launch

// Menu items enum
enum class MenuItem(
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val label: String,
    val color: Color
) {
    CHANNELS(Icons.Outlined.LiveTv, Icons.Filled.LiveTv, "Canales", CyanGlow),
    RADIO(Icons.Outlined.Radio, Icons.Filled.Radio, "Radio", PurpleGlow),
    SEARCH(Icons.Outlined.Search, Icons.Filled.Search, "Buscar", GreenGlow),
    FAVORITES(Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite, "Favoritos", PinkGlow),
    SETTINGS(Icons.Outlined.Settings, Icons.Filled.Settings, "Ajustes", OrangeGlow),
    LOGOUT(Icons.Outlined.ExitToApp, Icons.Filled.ExitToApp, "Salir", Color(0xFFEF5350))
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: StreamsonicRepository,
    onChannelClick: (Channel, List<Channel>) -> Unit,
    onStationClick: (Station, List<Station>) -> Unit,
    onNavigateToSearch: () -> Unit = {},
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .crossfade(true)
            .build()
    }
    
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var stations by remember { mutableStateOf<List<Station>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedMenuItem by remember { mutableStateOf(MenuItem.CHANNELS) }
    var isSidebarExpanded by remember { mutableStateOf(false) }
    
    // Hero state - item actualmente enfocado
    var focusedChannel by remember { mutableStateOf<Channel?>(null) }
    var focusedStation by remember { mutableStateOf<Station?>(null) }
    
    val scope = rememberCoroutineScope()
    
    fun loadContent() {
        scope.launch {
            isLoading = true
            errorMessage = null
            
            repository.getChannels().fold(
                onSuccess = { 
                    channels = it
                    if (it.isNotEmpty()) focusedChannel = it.first()
                },
                onFailure = { errorMessage = it.message }
            )
            
            repository.getStations().fold(
                onSuccess = { 
                    stations = it
                    if (it.isNotEmpty() && focusedStation == null) focusedStation = it.first()
                },
                onFailure = { if (errorMessage == null) errorMessage = it.message }
            )
            
            isLoading = false
        }
    }
    
    LaunchedEffect(Unit) {
        loadContent()
    }
    
    // Background color based on focused item
    val heroColor = when (selectedMenuItem) {
        MenuItem.CHANNELS -> CyanGlow
        MenuItem.RADIO -> PurpleGlow
        else -> CyanGlow
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .drawBehind {
                // Gradient glow from hero
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            heroColor.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.7f, size.height * 0.2f),
                        radius = size.width * 0.5f
                    )
                )
            }
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Sidebar
            Sidebar(
                selectedItem = selectedMenuItem,
                isExpanded = isSidebarExpanded,
                onItemSelected = { item ->
                    when (item) {
                        MenuItem.LOGOUT -> onLogout()
                        MenuItem.SEARCH -> onNavigateToSearch()
                        else -> selectedMenuItem = item
                    }
                },
                onExpandChanged = { isSidebarExpanded = it }
            )
            
            // Main content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                when {
                    isLoading -> LoadingState(modifier = Modifier.fillMaxSize())
                    errorMessage != null -> ErrorState(
                        message = errorMessage!!,
                        onRetry = { loadContent() },
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> {
                        when (selectedMenuItem) {
                            MenuItem.CHANNELS -> ChannelsContent(
                                channels = channels,
                                focusedChannel = focusedChannel,
                                onChannelFocused = { focusedChannel = it },
                                onChannelClick = onChannelClick,
                                imageLoader = imageLoader,
                                onContentFocused = { isSidebarExpanded = false }
                            )
                            MenuItem.RADIO -> StationsContent(
                                stations = stations,
                                focusedStation = focusedStation,
                                onStationFocused = { focusedStation = it },
                                onStationClick = onStationClick,
                                imageLoader = imageLoader,
                                onContentFocused = { isSidebarExpanded = false }
                            )
                            MenuItem.FAVORITES -> ComingSoonContent("Favoritos")
                            MenuItem.SETTINGS -> ComingSoonContent("Ajustes")
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Sidebar(
    selectedItem: MenuItem,
    isExpanded: Boolean,
    onItemSelected: (MenuItem) -> Unit,
    onExpandChanged: (Boolean) -> Unit
) {
    val sidebarWidth by animateDpAsState(
        targetValue = if (isExpanded) 220.dp else 72.dp,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "sidebarWidth"
    )
    
    // Separate main items from secondary items
    val mainItems = listOf(MenuItem.CHANNELS, MenuItem.RADIO, MenuItem.SEARCH)
    val secondaryItems = listOf(MenuItem.FAVORITES, MenuItem.SETTINGS)
    
    Box(
        modifier = Modifier
            .width(sidebarWidth)
            .fillMaxHeight()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DarkSurface.copy(alpha = 0.95f),
                        DarkBackground.copy(alpha = 0.98f)
                    )
                )
            )
    ) {
        // Subtle right border
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(1.dp)
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            CardBorder.copy(alpha = 0.5f),
                            CardBorder.copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 20.dp, horizontal = 12.dp)
        ) {
            // Header with logo and app name
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                contentAlignment = if (isExpanded) Alignment.CenterStart else Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Logo
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(CyanGlow, PurpleGlow),
                                    start = Offset(0f, 0f),
                                    end = Offset(50f, 50f)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    
                    // App name (only when expanded)
                    if (isExpanded) {
                        Column(
                            modifier = Modifier.padding(start = 12.dp)
                        ) {
                            Text(
                                text = "Streamsonic",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "TV & Radio",
                                fontSize = 11.sp,
                                color = TextMuted,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
            
            // Section: Main navigation
            SidebarSectionHeader(
                title = "EXPLORAR",
                isExpanded = isExpanded
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            mainItems.forEach { item ->
                SidebarItem(
                    item = item,
                    isSelected = selectedItem == item,
                    isExpanded = isExpanded,
                    onClick = { onItemSelected(item) },
                    onFocusChanged = { focused ->
                        if (focused) onExpandChanged(true)
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Section: Secondary navigation
            SidebarSectionHeader(
                title = "MI CUENTA",
                isExpanded = isExpanded
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            secondaryItems.forEach { item ->
                SidebarItem(
                    item = item,
                    isSelected = selectedItem == item,
                    isExpanded = isExpanded,
                    onClick = { onItemSelected(item) },
                    onFocusChanged = { focused ->
                        if (focused) onExpandChanged(true)
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Divider before logout
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(CardBorder.copy(alpha = 0.5f))
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Logout
            SidebarItem(
                item = MenuItem.LOGOUT,
                isSelected = false,
                isExpanded = isExpanded,
                onClick = { onItemSelected(MenuItem.LOGOUT) },
                onFocusChanged = { focused ->
                    if (focused) onExpandChanged(true)
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Collapse hint (only when expanded)
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Navegar a contenido para cerrar",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarSectionHeader(
    title: String,
    isExpanded: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp),
        contentAlignment = if (isExpanded) Alignment.CenterStart else Alignment.Center
    ) {
        if (isExpanded) {
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
        } else {
            // Small dot indicator when collapsed
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(CardBorder)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SidebarItem(
    item: MenuItem,
    isSelected: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    // Fixed height for consistent layout
    val itemHeight = 52.dp
    
    Box {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .onFocusChanged { 
                    isFocused = it.isFocused
                    onFocusChanged(it.isFocused)
                },
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = when {
                    isSelected -> item.color.copy(alpha = 0.12f)
                    else -> Color.Transparent
                },
                focusedContainerColor = item.color.copy(alpha = 0.18f)
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(
                    border = BorderStroke(
                        width = 0.dp,
                        color = Color.Transparent
                    )
                ),
                focusedBorder = Border(
                    border = BorderStroke(width = 2.dp, color = item.color)
                )
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
            glow = ClickableSurfaceDefaults.glow(
                focusedGlow = Glow(elevation = 12.dp, elevationColor = item.color.copy(alpha = 0.35f))
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = if (isExpanded) Alignment.CenterStart else Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .then(
                            if (isExpanded) Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                            else Modifier
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Selection indicator bar (only when expanded)
                    if (isExpanded) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(24.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (isSelected) item.color else Color.Transparent
                                )
                        )
                        
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    
                    // Icon with background when selected
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when {
                                    isSelected -> item.color.copy(alpha = 0.2f)
                                    isFocused -> item.color.copy(alpha = 0.15f)
                                    else -> Color.Transparent
                                }
                            )
                            .then(
                                if (isSelected && !isExpanded) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = item.color.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected || isFocused) item.selectedIcon else item.icon,
                            contentDescription = item.label,
                            tint = when {
                                isSelected || isFocused -> item.color
                                else -> TextMuted
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    
                    // Label (only when expanded)
                    if (isExpanded) {
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Text(
                            text = item.label,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = when {
                                isSelected || isFocused -> item.color
                                else -> TextSecondary
                            },
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Arrow indicator for selected item
                        if (isSelected && item != MenuItem.LOGOUT) {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = item.color.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
        
        // Tooltip when collapsed and focused
        if (!isExpanded && isFocused) {
            Box(
                modifier = Modifier
                    .offset(x = 56.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurface)
                    .border(1.dp, item.color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = item.label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = item.color
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelsContent(
    channels: List<Channel>,
    focusedChannel: Channel?,
    onChannelFocused: (Channel) -> Unit,
    onChannelClick: (Channel, List<Channel>) -> Unit,
    imageLoader: ImageLoader,
    onContentFocused: () -> Unit
) {
    // Group channels by category
    val channelsByCategory = remember(channels) {
        channels.groupBy { it.category ?: "Otros" }
            .toSortedMap(compareBy { if (it == "Otros") "zzz" else it }) // "Otros" al final
    }
    
    TvLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Hero section
        item {
            HeroSection(
                title = focusedChannel?.name ?: "Canales",
                subtitle = focusedChannel?.category ?: "Selecciona un canal",
                imageUrl = focusedChannel?.imageUrl,
                accentColor = CyanGlow,
                imageLoader = imageLoader,
                itemCount = channels.size,
                itemLabel = "canales",
                categoryCount = channelsByCategory.size
            )
        }
        
        // Category rows
        channelsByCategory.forEach { (category, categoryChannels) ->
            item(key = "header_$category") {
                CategoryHeader(
                    title = category,
                    count = categoryChannels.size,
                    accentColor = CyanGlow
                )
            }
            
            item(key = "row_$category") {
                TvLazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = categoryChannels,
                        key = { it.id }
                    ) { channel ->
                        ChannelCard(
                            channel = channel,
                            isFocused = focusedChannel?.id == channel.id,
                            onFocused = { 
                                onChannelFocused(channel)
                                onContentFocused()
                            },
                            onClick = { onChannelClick(channel, channels) },
                            imageLoader = imageLoader
                        )
                    }
                }
            }
            
            item(key = "spacer_$category") {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StationsContent(
    stations: List<Station>,
    focusedStation: Station?,
    onStationFocused: (Station) -> Unit,
    onStationClick: (Station, List<Station>) -> Unit,
    imageLoader: ImageLoader,
    onContentFocused: () -> Unit
) {
    // Group stations by category
    val stationsByCategory = remember(stations) {
        stations.groupBy { it.category ?: "Otros" }
            .toSortedMap(compareBy { if (it == "Otros") "zzz" else it }) // "Otros" al final
    }
    
    TvLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Hero section
        item {
            HeroSection(
                title = focusedStation?.name ?: "Radio",
                subtitle = focusedStation?.category ?: "Selecciona una estación",
                imageUrl = focusedStation?.imageUrl,
                accentColor = PurpleGlow,
                imageLoader = imageLoader,
                itemCount = stations.size,
                itemLabel = "estaciones",
                categoryCount = stationsByCategory.size
            )
        }
        
        // Category rows
        stationsByCategory.forEach { (category, categoryStations) ->
            item(key = "header_$category") {
                CategoryHeader(
                    title = category,
                    count = categoryStations.size,
                    accentColor = PurpleGlow
                )
            }
            
            item(key = "row_$category") {
                TvLazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = categoryStations,
                        key = { it.id }
                    ) { station ->
                        StationCard(
                            station = station,
                            isFocused = focusedStation?.id == station.id,
                            onFocused = { 
                                onStationFocused(station)
                                onContentFocused()
                            },
                            onClick = { onStationClick(station, stations) },
                            imageLoader = imageLoader
                        )
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
        // Accent bar
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
        
        // Count badge
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
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Arrow hint
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun HeroSection(
    title: String,
    subtitle: String,
    imageUrl: String?,
    accentColor: Color,
    imageLoader: ImageLoader,
    itemCount: Int,
    itemLabel: String,
    categoryCount: Int = 0
) {
    val context = LocalContext.current
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            CardBackground,
                            accentColor.copy(alpha = 0.1f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(CardBorder, accentColor.copy(alpha = 0.3f))
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo grande
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurface)
                    .border(
                        width = 2.dp,
                        color = accentColor.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        imageLoader = imageLoader,
                        contentDescription = title,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Icon(
                        if (accentColor == CyanGlow) Icons.Default.LiveTv else Icons.Default.Radio,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(20.dp))
            
            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Stats
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Categories count
                if (categoryCount > 0) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$categoryCount",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "categorías",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }
                
                // Items count
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$itemCount",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    Text(
                        text = itemLabel,
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelCard(
    channel: Channel,
    isFocused: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    imageLoader: ImageLoader
) {
    val context = LocalContext.current
    var isItemFocused by remember { mutableStateOf(false) }
    
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(120.dp)
            .onFocusChanged { 
                isItemFocused = it.isFocused
                if (it.isFocused) onFocused()
            },
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
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, scale = 1f),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevation = 16.dp, elevationColor = CyanGlow.copy(alpha = 0.4f))
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Background gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(DarkSurface, CardBackground)
                        )
                    )
            )
            
            // Logo
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
            
            // Name overlay on focus
            AnimatedVisibility(
                visible = isItemFocused,
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
private fun StationCard(
    station: Station,
    isFocused: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    imageLoader: ImageLoader
) {
    val context = LocalContext.current
    var isItemFocused by remember { mutableStateOf(false) }
    
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(120.dp)
            .onFocusChanged { 
                isItemFocused = it.isFocused
                if (it.isFocused) onFocused()
            },
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
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, scale = 1f),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevation = 16.dp, elevationColor = PurpleGlow.copy(alpha = 0.4f))
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Background gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(DarkSurface, CardBackground)
                        )
                    )
            )
            
            // Logo
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
            
            // Name overlay on focus
            AnimatedVisibility(
                visible = isItemFocused,
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
private fun ComingSoonContent(feature: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Construction,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = feature,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Próximamente",
                fontSize = 14.sp,
                color = TextMuted
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
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
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.colors(
                    containerColor = CyanGlow,
                    contentColor = DarkBackground
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reintentar", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
