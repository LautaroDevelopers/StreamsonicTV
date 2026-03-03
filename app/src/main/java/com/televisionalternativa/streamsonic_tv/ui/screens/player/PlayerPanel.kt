package com.televisionalternativa.streamsonic_tv.ui.screens.player

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.ImageLoader
import com.televisionalternativa.streamsonic_tv.data.model.Channel
import com.televisionalternativa.streamsonic_tv.data.model.Station
import com.televisionalternativa.streamsonic_tv.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// ============================================================
// Panel A: Tab column (left side)
// ============================================================

@Composable
internal fun PanelTabsColumn(
    tabIndex: Int,
    focus: PanelFocus,
    contentType: String,
    panelLevel: PanelLevel,
    tabsFR: FocusRequester,
    livePulseAlpha: Float,
    onTabUp: () -> Unit,
    onTabDown: () -> Unit,
    onTabSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToMovies: () -> Unit
) {
    val totalTabSlots = tabs.size + 1

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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(tabsFR)
                .focusable()
                .onKeyEvent { event ->
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && focus == PanelFocus.TABS) {
                        when (event.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_BACK -> { onDismiss(); true }
                            KeyEvent.KEYCODE_DPAD_UP -> { onTabUp(); true }
                            KeyEvent.KEYCODE_DPAD_DOWN -> { onTabDown(); true }
                            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                when (tabIndex) {
                                    4 -> onNavigateToMovies()
                                    5 -> onLogout()
                                    else -> onTabSelect(tabIndex)
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
                            .background(Brush.linearGradient(listOf(CyanGlow, PurpleGlow))),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            Icons.Default.PlayArrow, contentDescription = null,
                            tint = Color.White, modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Streamsonic", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }

                // Tab items
                tabs.forEachIndexed { index, tab ->
                    val isNowPlaying = (index == 0 && contentType == "channel") ||
                            (index == 1 && contentType == "station")
                    TabItem(
                        icon = tab.icon,
                        label = tab.label,
                        color = tab.color,
                        tabIndex = index,
                        currentTabIndex = tabIndex,
                        focus = focus,
                        isNowPlaying = isNowPlaying,
                        livePulseAlpha = livePulseAlpha
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(CardBorder.copy(alpha = 0.4f))
                )
                Spacer(modifier = Modifier.height(8.dp))

                @Suppress("DEPRECATION")
                TabItem(
                    icon = Icons.Filled.ExitToApp,
                    label = "Desconectar",
                    color = ErrorRed,
                    tabIndex = 5,
                    currentTabIndex = tabIndex,
                    focus = focus,
                    isNowPlaying = false,
                    livePulseAlpha = livePulseAlpha
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Bottom hint
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            val hint = when {
                focus == PanelFocus.TABS -> "▲▼ Navegar • ►/OK Abrir • BACK Cerrar"
                tabIndex == 0 || tabIndex == 1 -> when (panelLevel) {
                    PanelLevel.CONTENT -> "◄► Categorías • ▲▼ Navegar • OK Reproducir"
                    PanelLevel.CATEGORIES -> "◄ Volver • ▲▼ Categoría • ►/OK Filtrar"
                    PanelLevel.FILTERED -> "◄ Categorías • ▲▼ Navegar • OK Reproducir"
                }
                tabIndex == 2 -> "▲▼ Navegar • OK Reproducir • ◄ Volver"
                tabIndex == 3 -> "▲▼ Resultados • OK Reproducir"
                else -> ""
            }
            Text(hint, fontSize = 11.sp, color = TextMuted)
        }
    }
}

// ============================================================
// Panel B: Content (right side)
// ============================================================

@Composable
internal fun PanelContentBox(
    tabIndex: Int,
    focus: PanelFocus,
    contentIndex: Int,
    panelLevel: PanelLevel,
    selectedCategory: String?,
    searchQuery: String,
    searchFocusOnInput: Boolean,
    currentIndex: Int,
    contentType: String,
    channels: List<Channel>,
    stations: List<Station>,
    favoriteItems: List<FavoriteItem>,
    searchResults: List<SearchResult>,
    displayItems: List<IndexedValue<Any>>,
    activeCategories: List<String>,
    activeGrouped: Map<String, List<IndexedValue<Any>>>,
    contentListState: LazyListState,
    contentFR: FocusRequester,
    searchInputFR: FocusRequester,
    searchResultsFR: FocusRequester,
    imageLoader: ImageLoader,
    livePulseAlpha: Float,
    scope: CoroutineScope,
    onContentIndexChange: (Int) -> Unit,
    onPanelLevelChange: (PanelLevel) -> Unit,
    onSelectedCategoryChange: (String?) -> Unit,
    onFocusChange: (PanelFocus) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchFocusOnInputChange: (Boolean) -> Unit,
    onItemSelect: (type: String, index: Int) -> Unit
) {
    val accentColor = if (tabIndex == 0) CyanGlow else PurpleGlow
    val isChannel = tabIndex == 0

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(380.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        DarkSurface.copy(alpha = 0.95f),
                        DarkBackground.copy(alpha = 0.90f)
                    )
                )
            )
    ) {
        when (tabIndex) {
            // Canales / Radio → 3-level navigation
            0, 1 -> ChannelsRadioContent(
                isChannel = isChannel,
                accentColor = accentColor,
                focus = focus,
                panelLevel = panelLevel,
                contentIndex = contentIndex,
                displayItems = displayItems,
                activeCategories = activeCategories,
                activeGrouped = activeGrouped,
                selectedCategory = selectedCategory,
                currentIndex = currentIndex,
                contentType = contentType,
                contentListState = contentListState,
                contentFR = contentFR,
                imageLoader = imageLoader,
                livePulseAlpha = livePulseAlpha,
                scope = scope,
                onContentIndexChange = onContentIndexChange,
                onPanelLevelChange = onPanelLevelChange,
                onSelectedCategoryChange = onSelectedCategoryChange,
                onFocusChange = onFocusChange,
                onItemSelect = onItemSelect
            )

            // Favoritos
            2 -> FavoritesContent(
                focus = focus,
                contentIndex = contentIndex,
                favoriteItems = favoriteItems,
                currentIndex = currentIndex,
                contentType = contentType,
                contentListState = contentListState,
                contentFR = contentFR,
                imageLoader = imageLoader,
                livePulseAlpha = livePulseAlpha,
                scope = scope,
                onContentIndexChange = onContentIndexChange,
                onFocusChange = onFocusChange,
                onItemSelect = onItemSelect
            )

            // Buscar
            3 -> SearchContent(
                focus = focus,
                contentIndex = contentIndex,
                searchQuery = searchQuery,
                searchFocusOnInput = searchFocusOnInput,
                searchResults = searchResults,
                currentIndex = currentIndex,
                contentType = contentType,
                contentListState = contentListState,
                searchInputFR = searchInputFR,
                searchResultsFR = searchResultsFR,
                imageLoader = imageLoader,
                livePulseAlpha = livePulseAlpha,
                scope = scope,
                onContentIndexChange = onContentIndexChange,
                onFocusChange = onFocusChange,
                onSearchQueryChange = onSearchQueryChange,
                onSearchFocusOnInputChange = onSearchFocusOnInputChange,
                onItemSelect = onItemSelect
            )
        }
    }
}

// ============================================================
// Channels / Radio content
// ============================================================

@Composable
private fun ChannelsRadioContent(
    isChannel: Boolean,
    accentColor: Color,
    focus: PanelFocus,
    panelLevel: PanelLevel,
    contentIndex: Int,
    displayItems: List<IndexedValue<Any>>,
    activeCategories: List<String>,
    activeGrouped: Map<String, List<IndexedValue<Any>>>,
    selectedCategory: String?,
    currentIndex: Int,
    contentType: String,
    contentListState: LazyListState,
    contentFR: FocusRequester,
    imageLoader: ImageLoader,
    livePulseAlpha: Float,
    scope: CoroutineScope,
    onContentIndexChange: (Int) -> Unit,
    onPanelLevelChange: (PanelLevel) -> Unit,
    onSelectedCategoryChange: (String?) -> Unit,
    onFocusChange: (PanelFocus) -> Unit,
    onItemSelect: (type: String, index: Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(contentFR)
            .focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && focus == PanelFocus.CONTENT) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_BACK -> {
                            when (panelLevel) {
                                PanelLevel.CONTENT, PanelLevel.CATEGORIES -> onFocusChange(PanelFocus.TABS)
                                PanelLevel.FILTERED -> { onPanelLevelChange(PanelLevel.CATEGORIES); onContentIndexChange(0) }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            when (panelLevel) {
                                PanelLevel.CONTENT -> { onPanelLevelChange(PanelLevel.CATEGORIES); onContentIndexChange(0) }
                                PanelLevel.CATEGORIES -> onFocusChange(PanelFocus.TABS)
                                PanelLevel.FILTERED -> { onPanelLevelChange(PanelLevel.CATEGORIES); onContentIndexChange(0) }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            when (panelLevel) {
                                PanelLevel.CONTENT -> { onPanelLevelChange(PanelLevel.CATEGORIES); onContentIndexChange(0) }
                                PanelLevel.CATEGORIES -> {
                                    val cat = activeCategories.getOrNull(contentIndex)
                                    if (cat != null) { onSelectedCategoryChange(cat); onPanelLevelChange(PanelLevel.FILTERED) }
                                }
                                PanelLevel.FILTERED -> { }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (contentIndex > 0) {
                                onContentIndexChange(contentIndex - 1)
                                scope.launch { contentListState.scrollToItem((contentIndex - 3).coerceAtLeast(0)) }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            val maxIdx = when (panelLevel) {
                                PanelLevel.CONTENT, PanelLevel.FILTERED -> displayItems.size - 1
                                PanelLevel.CATEGORIES -> activeCategories.size - 1
                            }
                            if (contentIndex < maxIdx) {
                                onContentIndexChange(contentIndex + 1)
                                scope.launch { contentListState.scrollToItem((contentIndex - 1).coerceAtLeast(0)) }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            when (panelLevel) {
                                PanelLevel.CONTENT, PanelLevel.FILTERED -> {
                                    val item = displayItems.getOrNull(contentIndex)
                                    if (item != null) onItemSelect(if (isChannel) "channel" else "station", item.index)
                                }
                                PanelLevel.CATEGORIES -> {
                                    val cat = activeCategories.getOrNull(contentIndex)
                                    if (cat != null) {
                                        onSelectedCategoryChange(cat)
                                        onPanelLevelChange(PanelLevel.FILTERED)
                                        onContentIndexChange(0)
                                    }
                                }
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(
                    when (panelLevel) {
                        PanelLevel.CONTENT -> if (isChannel) Icons.Default.LiveTv else Icons.Default.Radio
                        PanelLevel.CATEGORIES -> Icons.Default.Category
                        PanelLevel.FILTERED -> if (isChannel) Icons.Default.LiveTv else Icons.Default.Radio
                    },
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    when (panelLevel) {
                        PanelLevel.CONTENT -> if (isChannel) "Todos los Canales" else "Todas las Estaciones"
                        PanelLevel.CATEGORIES -> "Categorías"
                        PanelLevel.FILTERED -> selectedCategory ?: ""
                    },
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    when (panelLevel) {
                        PanelLevel.CONTENT, PanelLevel.FILTERED -> "${displayItems.size}"
                        PanelLevel.CATEGORIES -> "${activeCategories.size}"
                    },
                    fontSize = 12.sp, color = TextMuted
                )
            }

            LazyColumn(
                state = contentListState,
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                when (panelLevel) {
                    PanelLevel.CONTENT, PanelLevel.FILTERED -> {
                        itemsIndexed(displayItems) { index, indexed ->
                            val item = indexed.value
                            val name: String
                            val logoUrl: String?
                            val isCurrent: Boolean
                            if (isChannel) {
                                val ch = item as Channel
                                name = ch.name; logoUrl = ch.imageUrl
                                isCurrent = index == currentIndex && contentType == "channel"
                            } else {
                                val st = item as Station
                                name = st.name; logoUrl = st.imageUrl
                                isCurrent = index == currentIndex && contentType == "station"
                            }
                            ItemRow(
                                number = index + 1, name = name, logoUrl = logoUrl,
                                imageLoader = imageLoader, index = index,
                                contentIndex = contentIndex, focus = focus,
                                isCurrent = isCurrent, accentColor = accentColor,
                                livePulseAlpha = livePulseAlpha
                            )
                        }
                    }
                    PanelLevel.CATEGORIES -> {
                        itemsIndexed(activeCategories) { index, cat ->
                            CategoryRow(
                                name = cat, count = activeGrouped[cat]?.size ?: 0,
                                index = index, contentIndex = contentIndex,
                                focus = focus, accentColor = accentColor
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// Favorites content
// ============================================================

@Composable
private fun FavoritesContent(
    focus: PanelFocus,
    contentIndex: Int,
    favoriteItems: List<FavoriteItem>,
    currentIndex: Int,
    contentType: String,
    contentListState: LazyListState,
    contentFR: FocusRequester,
    imageLoader: ImageLoader,
    livePulseAlpha: Float,
    scope: CoroutineScope,
    onContentIndexChange: (Int) -> Unit,
    onFocusChange: (PanelFocus) -> Unit,
    onItemSelect: (type: String, index: Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(contentFR)
            .focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && focus == PanelFocus.CONTENT) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_LEFT -> { onFocusChange(PanelFocus.TABS); true }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (contentIndex > 0) {
                                onContentIndexChange(contentIndex - 1)
                                scope.launch { contentListState.scrollToItem((contentIndex - 3).coerceAtLeast(0)) }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (contentIndex < favoriteItems.size - 1) {
                                onContentIndexChange(contentIndex + 1)
                                scope.launch { contentListState.scrollToItem((contentIndex - 1).coerceAtLeast(0)) }
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
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(Icons.Default.Favorite, null, tint = PinkGlow, modifier = Modifier.size(18.dp))
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
                        androidx.compose.material3.Icon(Icons.Default.FavoriteBorder, null, tint = TextMuted, modifier = Modifier.size(40.dp))
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
                            number = item.listIndex + 1, name = item.name, logoUrl = item.imageUrl,
                            imageLoader = imageLoader, index = index,
                            contentIndex = contentIndex, focus = focus,
                            isCurrent = isCurrent, accentColor = ac,
                            badge = if (item.itemType == "channel") "TV" else "FM",
                            livePulseAlpha = livePulseAlpha
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// Search content
// ============================================================

@Composable
internal fun SearchContent(
    focus: PanelFocus,
    contentIndex: Int,
    searchQuery: String,
    searchFocusOnInput: Boolean,
    searchResults: List<SearchResult>,
    currentIndex: Int,
    contentType: String,
    contentListState: LazyListState,
    searchInputFR: FocusRequester,
    searchResultsFR: FocusRequester,
    imageLoader: ImageLoader,
    livePulseAlpha: Float,
    scope: CoroutineScope,
    onContentIndexChange: (Int) -> Unit,
    onFocusChange: (PanelFocus) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchFocusOnInputChange: (Boolean) -> Unit,
    onItemSelect: (type: String, index: Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Icon(Icons.Default.Search, null, tint = GreenGlow, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Buscar", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { onSearchQueryChange(it); onContentIndexChange(0) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .focusRequester(searchInputFR)
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        when (event.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (searchResults.isNotEmpty()) { onSearchFocusOnInputChange(false); onFocusChange(PanelFocus.CONTENT) }
                                true
                            }
                            KeyEvent.KEYCODE_BACK -> { onFocusChange(PanelFocus.TABS); true }
                            else -> false
                        }
                    } else false
                },
            placeholder = { androidx.compose.material3.Text("Buscar canales o radios...", color = TextMuted) },
            leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Search, null, tint = GreenGlow) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    androidx.compose.material3.IconButton(onClick = { onSearchQueryChange("") }) {
                        androidx.compose.material3.Icon(Icons.Default.Close, "Limpiar", tint = TextMuted)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardBackground, unfocusedContainerColor = CardBackground,
                focusedBorderColor = GreenGlow, unfocusedBorderColor = CardBorder,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
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
                        androidx.compose.material3.Icon(Icons.Default.Search, null, tint = TextMuted, modifier = Modifier.size(40.dp))
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
                                            onContentIndexChange(contentIndex - 1)
                                            scope.launch { contentListState.scrollToItem((contentIndex - 3).coerceAtLeast(0)) }
                                        } else onSearchFocusOnInputChange(true)
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        if (contentIndex < searchResults.size - 1) {
                                            onContentIndexChange(contentIndex + 1)
                                            scope.launch { contentListState.scrollToItem((contentIndex - 1).coerceAtLeast(0)) }
                                        }
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                        val sr = searchResults.getOrNull(contentIndex)
                                        if (sr != null) onItemSelect(sr.itemType, sr.listIndex)
                                        true
                                    }
                                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_LEFT -> { onSearchFocusOnInputChange(true); true }
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
                                number = result.listIndex + 1, name = result.name, logoUrl = result.imageUrl,
                                imageLoader = imageLoader, index = index,
                                contentIndex = contentIndex, focus = focus,
                                isCurrent = isCurrent, accentColor = ac,
                                badge = if (result.itemType == "channel") "TV" else "FM",
                                searchFocusOnInput = searchFocusOnInput,
                                livePulseAlpha = livePulseAlpha
                            )
                        }
                    }
                }
            }
        }
    }
}
