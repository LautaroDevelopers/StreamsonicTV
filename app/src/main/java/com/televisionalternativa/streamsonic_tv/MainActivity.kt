package com.televisionalternativa.streamsonic_tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.televisionalternativa.streamsonic_tv.data.local.TvPreferences
import com.televisionalternativa.streamsonic_tv.data.repository.StreamsonicRepository
import com.televisionalternativa.streamsonic_tv.ui.navigation.AppNavigation
import com.televisionalternativa.streamsonic_tv.ui.theme.DarkBackground
import com.televisionalternativa.streamsonic_tv.ui.theme.StreamsonicTVTheme

class MainActivity : ComponentActivity() {
    
    private lateinit var prefs: TvPreferences
    private lateinit var repository: StreamsonicRepository
    
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize dependencies
        prefs = TvPreferences(applicationContext)
        repository = StreamsonicRepository(prefs)
        
        // Fullscreen immersive mode for TV
        setupFullscreen()
        
        setContent {
            StreamsonicTVTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBackground),
                    shape = RectangleShape
                ) {
                    AppNavigation(
                        prefs = prefs,
                        repository = repository
                    )
                }
            }
        }
    }
    
    private fun setupFullscreen() {
        // Keep screen on while app is active
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Hide system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupFullscreen()
        }
    }
}
