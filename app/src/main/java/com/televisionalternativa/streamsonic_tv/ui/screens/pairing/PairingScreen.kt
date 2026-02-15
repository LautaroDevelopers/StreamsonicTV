package com.televisionalternativa.streamsonic_tv.ui.screens.pairing

import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.televisionalternativa.streamsonic_tv.data.repository.StreamsonicRepository
import com.televisionalternativa.streamsonic_tv.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PairingScreen(
    repository: StreamsonicRepository,
    onPairingSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var tvCode by remember { mutableStateOf<String?>(null) }
    var backendDeviceId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Device ID (unique per TV)
    val deviceId = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }
    val deviceName = remember {
        android.os.Build.MODEL ?: "Smart TV"
    }
    
    // Generate code and start polling
    LaunchedEffect(Unit) {
        repository.generateTvCode(deviceId, deviceName).fold(
            onSuccess = { response ->
                tvCode = response.data?.code
                backendDeviceId = response.data?.deviceId
                isLoading = false
                android.util.Log.d("PairingScreen", "Code generated: ${response.data?.code}")
                android.util.Log.d("PairingScreen", "Using deviceId: $deviceId (local Android ID)")
                
                // Start polling for authorization
                // Use local deviceId (Android ID) since backend doesn't return it
                scope.launch {
                    while (true) {
                        delay(3000)
                        repository.checkTvCodeStatus(deviceId).fold(
                            onSuccess = { status ->
                                android.util.Log.d("PairingScreen", "Poll response - status: ${status.status}, token: ${status.token != null}")
                                if (status.status == "authorized" && status.token != null) {
                                    android.util.Log.d("PairingScreen", "AUTHORIZED! Token received")
                                    repository.saveToken(status.token)
                                    onPairingSuccess()
                                    return@launch
                                }
                            },
                            onFailure = { e ->
                                android.util.Log.e("PairingScreen", "Poll error: ${e.message}")
                            }
                        )
                    }
                }
            },
            onFailure = { e ->
                errorMessage = e.message ?: "Error de conexión"
                isLoading = false
            }
        )
    }
    
    // Animated gradient
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradient"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(DarkSurface, DarkBackground)
                )
            )
    ) {
        // Background orbs
        Box(
            modifier = Modifier
                .size(400.dp)
                .offset(x = (-100).dp, y = (-100).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            PurpleGlow.copy(alpha = 0.1f * gradientOffset),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 100.dp, y = 100.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            CyanGlow.copy(alpha = 0.1f * (1 - gradientOffset)),
                            Color.Transparent
                        )
                    )
                )
        )
        
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side - Info
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Stream",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "sonic",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanGlow
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "TV",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                    color = TextMuted,
                    letterSpacing = 8.sp
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // Instructions
                InstructionStep(
                    number = 1,
                    text = "Abrí la app Streamsonic en tu celular",
                    color = CyanGlow
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                InstructionStep(
                    number = 2,
                    text = "Tocá \"Vincular TV\" en el menú",
                    color = PurpleGlow
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                InstructionStep(
                    number = 3,
                    text = "Ingresá el código que aparece en pantalla",
                    color = PinkGlow
                )
            }
            
            // Right side - Code Display
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    isLoading -> {
                        LoadingState()
                    }
                    errorMessage != null -> {
                    ErrorState(
                        message = errorMessage!!,
                        onRetry = {
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                repository.generateTvCode(deviceId, deviceName).fold(
                                    onSuccess = { response ->
                                        tvCode = response.data?.code
                                        backendDeviceId = response.data?.deviceId
                                        isLoading = false
                                    },
                                    onFailure = { e ->
                                        errorMessage = e.message ?: "Error de conexión"
                                        isLoading = false
                                    }
                                )
                            }
                        }
                    )
                    }
                    tvCode != null -> {
                        CodeDisplay(code = tvCode!!)
                    }
                }
            }
        }
    }
}

@Composable
private fun InstructionStep(
    number: Int,
    text: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(0.8f)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f))
                .border(
                    width = 2.dp,
                    color = color.copy(alpha = 0.5f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        
        Spacer(modifier = Modifier.width(20.dp))
        
        Text(
            text = text,
            fontSize = 18.sp,
            color = TextSecondary
        )
    }
}

@Composable
private fun CodeDisplay(code: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "code")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "border"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // QR Coming Soon placeholder
        Box(
            modifier = Modifier
                .size(280.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(CardBackground)
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            CyanGlow.copy(alpha = borderAlpha),
                            PurpleGlow.copy(alpha = borderAlpha)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.QrCode2,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(80.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Escaneo QR",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                
                Text(
                    text = "Próximamente",
                    fontSize = 16.sp,
                    color = CyanGlow
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Manual code
        Text(
            text = "Ingresá este código en tu celular:",
            fontSize = 16.sp,
            color = TextMuted
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Code box
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            CyanGlow.copy(alpha = 0.1f),
                            PurpleGlow.copy(alpha = 0.1f)
                        )
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(CyanGlow, PurpleGlow)
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 48.dp, vertical = 24.dp)
        ) {
            Text(
                text = code,
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = 8.sp
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Waiting indicator
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            PulsingDot(color = GreenGlow)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Esperando vinculación...",
                fontSize = 16.sp,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        modifier = Modifier
            .size((12 * scale).dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
private fun LoadingState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(280.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(CardBackground)
                .border(
                    width = 2.dp,
                    color = CardBorder,
                    shape = RoundedCornerShape(24.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(80.dp),
                        color = CyanGlow.copy(alpha = 0.3f),
                        strokeWidth = 4.dp
                    )
                    CircularProgressIndicator(
                        modifier = Modifier.size(60.dp),
                        color = CyanGlow,
                        strokeWidth = 3.dp
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Generando código...",
                    fontSize = 18.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(280.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(CardBackground)
                .border(
                    width = 2.dp,
                    color = PinkGlow.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(24.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(PinkGlow.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = PinkGlow,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Error de conexión",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = message,
                    fontSize = 14.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onRetry,
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
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Reintentar",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
