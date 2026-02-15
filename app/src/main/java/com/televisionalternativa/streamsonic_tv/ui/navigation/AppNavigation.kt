package com.televisionalternativa.streamsonic_tv.ui.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.televisionalternativa.streamsonic_tv.data.api.ApiClient
import com.televisionalternativa.streamsonic_tv.data.local.TvPreferences
import com.televisionalternativa.streamsonic_tv.data.model.Channel
import com.televisionalternativa.streamsonic_tv.data.model.Station
import com.televisionalternativa.streamsonic_tv.data.repository.StreamsonicRepository
import com.televisionalternativa.streamsonic_tv.ui.screens.home.HomeScreen
import com.televisionalternativa.streamsonic_tv.ui.screens.pairing.PairingScreen
import com.televisionalternativa.streamsonic_tv.ui.screens.player.PlayerScreen
import com.televisionalternativa.streamsonic_tv.ui.screens.search.SearchScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.net.URLDecoder
import java.net.URLEncoder

sealed class Screen(val route: String) {
    object Pairing : Screen("pairing")
    object Home : Screen("home")
    object Search : Screen("search")
    object Player : Screen("player/{type}/{index}/{data}") {
        fun createRoute(type: String, index: Int, data: String): String {
            val encoded = URLEncoder.encode(data, "UTF-8")
            return "player/$type/$index/$encoded"
        }
    }
}

@Composable
fun AppNavigation(
    prefs: TvPreferences,
    repository: StreamsonicRepository
) {
    val navController = rememberNavController()
    val gson = remember { Gson() }
    
    // Check auth state
    val isAuthenticated = runBlocking {
        prefs.authToken.first() != null
    }
    
    val startDestination = if (isAuthenticated) Screen.Home.route else Screen.Pairing.route
    
    // Initialize ApiClient with session expiration callback
    LaunchedEffect(Unit) {
        ApiClient.init(prefs) {
            // Session expired - navigate to pairing
            navController.navigate(Screen.Pairing.route) {
                popUpTo(0) { inclusive = true }
            }
        }
        repository.initAuth()
    }
    
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Pairing.route) {
            PairingScreen(
                repository = repository,
                onPairingSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Pairing.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Home.route) {
            HomeScreen(
                repository = repository,
                onChannelClick = { channel, allChannels ->
                    val index = allChannels.indexOf(channel)
                    val json = gson.toJson(allChannels)
                    navController.navigate(Screen.Player.createRoute("channel", index, json))
                },
                onStationClick = { station, allStations ->
                    val index = allStations.indexOf(station)
                    val json = gson.toJson(allStations)
                    navController.navigate(Screen.Player.createRoute("station", index, json))
                },
                onNavigateToSearch = {
                    navController.navigate(Screen.Search.route)
                },
                onLogout = {
                    runBlocking { repository.logout() }
                    navController.navigate(Screen.Pairing.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Search.route) {
            SearchScreen(
                repository = repository,
                onChannelClick = { channel, allChannels ->
                    val index = allChannels.indexOf(channel)
                    val json = gson.toJson(allChannels)
                    navController.navigate(Screen.Player.createRoute("channel", index, json))
                },
                onStationClick = { station, allStations ->
                    val index = allStations.indexOf(station)
                    val json = gson.toJson(allStations)
                    navController.navigate(Screen.Player.createRoute("station", index, json))
                }
            )
        }
        
        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("index") { type = NavType.IntType },
                navArgument("data") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: "channel"
            val index = backStackEntry.arguments?.getInt("index") ?: 0
            val data = backStackEntry.arguments?.getString("data") ?: ""
            val decodedData = URLDecoder.decode(data, "UTF-8")
            
            if (type == "channel") {
                val channelListType = object : TypeToken<List<Channel>>() {}.type
                val channels: List<Channel> = gson.fromJson(decodedData, channelListType)
                
                PlayerScreen(
                    channels = channels,
                    initialIndex = index,
                    stations = null,
                    onBack = { navController.popBackStack() }
                )
            } else {
                val stationListType = object : TypeToken<List<Station>>() {}.type
                val stations: List<Station> = gson.fromJson(decodedData, stationListType)
                
                PlayerScreen(
                    channels = null,
                    stations = stations,
                    initialIndex = index,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
