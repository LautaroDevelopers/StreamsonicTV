# 📺 Streamsonic TV

Aplicación de streaming para Android TV con reproducción de canales en vivo, estaciones de radio y películas, optimizada para experiencia 10-foot con control remoto.

[![Version](https://img.shields.io/badge/version-1.4.0-blue.svg)](https://github.com/LautaroDevelopers/StreamsonicTV/releases/latest)
[![Platform](https://img.shields.io/badge/platform-Android%20TV-green.svg)](https://www.android.com/tv/)
[![API](https://img.shields.io/badge/API-21%2B-orange.svg)](https://android-arsenal.com/api?level=21)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-purple.svg)](https://kotlinlang.org/)

## 🚀 Características

- **📺 Canales en vivo**: Reproducción de TV con navegación D-Pad optimizada
- **📻 Radio**: Estaciones de radio con visualizador animado
- **🎬 Películas**: Hero spotlight + carruseles estilo Netflix
- **❤️ Favoritos**: Sistema de favoritos persistente
- **🔍 Búsqueda**: Búsqueda unificada con resultados en tiempo real
- **⌨️ Control remoto**: Navegación completa con D-Pad y canales +/-
- **🔄 Auto-retry**: Reconexión automática en caso de error (hasta 5 intentos)
- **📡 Actualizaciones OTA**: Chequeo automático de actualizaciones desde GitHub
- **🎨 UI Leanback**: Diseño optimizado para TV con Material Design

## 📸 Capturas

- Panel lateral con tabs (Canales, Radio, Favoritos, Búsqueda, Películas)
- Reproductor de video con overlay de controles
- Pantalla de películas con hero destacado y categorías
- Sistema de navegación con 3 niveles: contenido → categorías → filtrado
- Indicador "EN VIVO" pulsante en reproducción

## 🛠️ Stack Técnico

### Core
- **Kotlin** 2.1.0
- **Jetpack Compose for TV** (BOM 2024.12.01)
- **Material Design 3 for TV**
- **Coroutines** para operaciones asíncronas

### Reproducción
- **LibVLC** 3.6.0 (migrado desde ExoPlayer en v1.4.0)
- Soporte para HLS, RTSP, DASH y múltiples formatos
- **VlcHolder pattern** para optimización de performance

### Networking
- **Retrofit** 2.11.0 + **OkHttp** 4.12.0
- **Gson** para serialización JSON

### UI/UX
- **Coil** 3.0.4 con soporte SVG para logos
- **DataStore** para persistencia de favoritos y último canal
- **Compose TV Navigation** con focus management

### Arquitectura
- **Sin ViewModels**: Estado con `mutableStateOf` directo en composables
- **Sin DI framework**: Inyección manual de dependencias
- **Repository pattern** para acceso a datos
- **Panel modularizado**: Separación en archivos independientes para reducir JIT cost

### Actualizaciones
- **UpdateChecker**: Sistema custom de verificación de updates desde GitHub Releases
- **Snooze de 1 hora**: Permite posponer notificaciones de actualización

## 📋 Requisitos

- Android TV / Google TV (API 21+)
- Conexión a internet
- ~200 MB de espacio libre
- Control remoto con D-Pad

## 🔧 Instalación

### Para usuarios

1. Descargá el APK desde [Releases](https://github.com/LautaroDevelopers/StreamsonicTV/releases/latest)
2. Transferí el APK a tu Android TV (USB, red local, etc.)
3. Instalá usando un file manager como X-plore
4. Iniciá sesión con tus credenciales

**Instalación remota vía ADB:**
```bash
# Conectar a la TV (reemplazá con la IP de tu TV)
adb connect 192.168.1.7:5555

# Instalar APK
adb install -r StreamsonicTV-v1.4.0.apk
```

### Para desarrolladores

```bash
# Clonar el repositorio
git clone https://github.com/LautaroDevelopers/StreamsonicTV.git
cd StreamsonicTV

# Crear archivo keystore.properties en la raíz del proyecto
cat > keystore.properties << EOF
storeFile=path/to/your/keystore.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
EOF

# Compilar debug
./gradlew assembleDebug

# Compilar release
./gradlew assembleRelease

# Instalar en TV conectada por ADB
adb connect 192.168.1.7:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 🏗️ Estructura del Proyecto

```
app/src/main/java/com/televisionalternativa/streamsonic_tv/
├── data/
│   ├── model/          # Modelos (Channel, Station, Movie, Favorite)
│   ├── remote/         # API service con Retrofit
│   └── repository/     # Repositorio + DataStore
├── ui/
│   ├── screens/
│   │   ├── auth/       # Login screen
│   │   ├── player/     # PlayerScreen + PlayerPanel
│   │   ├── movies/     # MoviesScreen con hero spotlight
│   │   └── splash/     # SplashScreen
│   ├── components/     # Componentes reutilizables
│   └── theme/          # Tema y colores
├── update/             # Sistema de actualizaciones OTA
└── MainActivity.kt
```

## 🎮 Controles

### Durante reproducción
- **D-Pad Center / OK**: Abrir panel de navegación
- **D-Pad Up / Channel Up**: Canal/estación anterior
- **D-Pad Down / Channel Down**: Canal/estación siguiente
- **Play/Pause**: Pausar/reanudar reproducción
- **Back**: Salir de la app (cierra completamente)

### En el panel
- **D-Pad Up/Down**: Navegar entre tabs
- **D-Pad Center / OK**: Entrar al contenido del tab activo
- **D-Pad Left**: Volver a tabs
- **Back**: Cerrar panel

### Navegación multinivel (Canales/Radio)
1. **Nivel 1 (Tabs)**: Seleccionar entre Canales, Radio, Favoritos, etc.
2. **Nivel 2 (Contenido)**: Ver todos los items o categorías
3. **Nivel 3 (Filtrado)**: Ver items filtrados por categoría seleccionada

## 🎯 Endpoints

**Base URL**: `https://api.streamsonic.televisionalternativa.com.ar/api`

- `POST /auth/login` - Autenticación
- `GET /channels` - Lista de canales
- `GET /stations` - Lista de estaciones
- `GET /movies` - Lista de películas
- `GET /favorites` - Favoritos del usuario
- `POST /favorites` - Agregar favorito
- `DELETE /favorites/{id}` - Eliminar favorito

## 🔄 Changelog

### v1.4.0 (2026-03-03)
- ✨ Migración completa de ExoPlayer a LibVLC
- ✨ Implementación del patrón VlcHolder
- ⚡ Performance: Partición de TwoPanelOverlay → PlayerPanel.kt
- 🐛 Fix: Remover `derivedStateOf` roto en TabItem/ItemRow/CategoryRow

### v1.3.x
- ✨ Feature: Pantalla de películas con hero spotlight + carruseles Netflix
- ✨ Rediseño de panel con navegación deslizante en 3 niveles
- ✨ Separación de paneles: Panel A (tabs) + Panel B (contenido)

### v1.2.0
- ✨ Sistema de búsqueda unificado
- ✨ Auto-retry con reconexión automática (5 intentos)
- 🐛 Múltiples fixes de estabilidad

### v1.1.0
- ✨ Sistema de favoritos persistente
- ✨ Chequeo automático de actualizaciones OTA
- ✨ Soporte para canales +/- en control remoto

### v1.0.0
- 🎉 Release inicial para Android TV

## 🎨 Diseño

La app usa un **sistema de panel lateral deslizante** (slide-in/out) con:

- **Panel A (Tabs)**: Siempre visible cuando el panel está abierto
  - Tabs: Canales, Radio, Favoritos, Búsqueda, Películas
  - Botón de Logout al final

- **Panel B (Contenido)**: Se desliza desde la derecha cuando el usuario entra a un tab
  - Navegación multinivel para explorar contenido y categorías
  - Focus management automático con `FocusRequester`
  - Scroll automático al item actualmente reproduciendo

## ⚡ Optimizaciones

### VlcHolder Pattern
```kotlin
class VlcHolder {
    var libVlc: LibVLC? = null
    var mediaPlayer: MediaPlayer? = null
}
val vlcHolder = remember { VlcHolder() }
```
Evita recomposiciones en cascada al NO usar `mutableStateOf` para VLC.

### Panel Modularizado
El `PlayerPanel.kt` está separado del `PlayerScreen.kt` para:
- Reducir costo de compilación JIT
- Mejorar lecturabilidad
- Facilitar mantenimiento

### Live Pulse Compartido
```kotlin
@Composable
private fun rememberLivePulseAlpha(): Float {
    val inf = rememberInfiniteTransition(label = "live")
    val alpha by inf.animateFloat(1f, 0.3f, ...)
    return alpha
}
```
Una sola animación compartida para todos los indicadores "EN VIVO".

## 🤝 Contribuir

Las contribuciones son bienvenidas. Por favor:

1. Hacé fork del proyecto
2. Creá una rama para tu feature (`git checkout -b feature/AmazingFeature`)
3. Commiteá tus cambios (`git commit -m 'Add some AmazingFeature'`)
4. Pusheá a la rama (`git push origin feature/AmazingFeature`)
5. Abrí un Pull Request

## 📝 Licencia

Este proyecto es privado y pertenece a Televisión Alternativa.

## 👥 Equipo

- **Desarrollo**: Lautaro Developers
- **API Backend**: Televisión Alternativa

## 🐛 Reportar Issues

Si encontrás un bug o tenés una sugerencia, por favor abrí un [Issue](https://github.com/LautaroDevelopers/StreamsonicTV/issues).

## 🔗 Enlaces

- [Streamsonic Mobile](https://github.com/LautaroDevelopers/StreamsonicMobile) - Versión para Android móvil
- [Releases](https://github.com/LautaroDevelopers/StreamsonicTV/releases)
- [Televisión Alternativa](https://televisionalternativa.com.ar)

---

Hecho con ❤️ usando Kotlin & Jetpack Compose for TV
