package com.televisionalternativa.streamsonic_tv.ui.navigation

import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.televisionalternativa.streamsonic_tv.data.api.ApiClient
import com.televisionalternativa.streamsonic_tv.data.local.TvPreferences
import com.televisionalternativa.streamsonic_tv.data.repository.StreamsonicRepository
import com.televisionalternativa.streamsonic_tv.ui.screens.pairing.PairingScreen
import com.televisionalternativa.streamsonic_tv.ui.screens.player.PlayerScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

sealed class Screen(val route: String) {
    object Pairing : Screen("pairing")
    object Player : Screen("player")
}

@Composable
fun AppNavigation(
    prefs: TvPreferences,
    repository: StreamsonicRepository
) {
    val navController = rememberNavController()

    // Check auth state
    val isAuthenticated = runBlocking {
        prefs.authToken.first() != null
    }

    val startDestination = if (isAuthenticated) Screen.Player.route else Screen.Pairing.route

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
                    navController.navigate(Screen.Player.route) {
                        popUpTo(Screen.Pairing.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Player.route) {
            PlayerScreen(
                repository = repository,
                onLogout = {
                    runBlocking { repository.logout() }
                    navController.navigate(Screen.Pairing.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
