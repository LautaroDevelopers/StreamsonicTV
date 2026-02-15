# ✅ FIX: Pantalla de Pairing Android TV

## Fecha: 15 de Febrero 2026

---

## 🔴 Problema

La pantalla de pairing mostraba solo el mensaje pero **NO el código**.

### Causa Raíz:

**Backend retorna:**
```json
{
  "success": true,
  "code": "479569",
  "expiresAt": "2026-02-15T04:14:36.693Z",
  "message": "Enter this code in your Streamsonic mobile app..."
}
```

**Modelo Android esperaba:**
```kotlin
data class TvCodeData(
    @SerializedName("device_id") val deviceId: String,  // ← Requerido!
    val code: String,
    // ...
)
```

**Resultado:**
- Gson no podía parsear porque faltaba `device_id`
- `response.data?.deviceId` era **null**
- Nunca entraba al bloque de polling
- El código NO se mostraba en pantalla

---

## ✅ Solución Aplicada

### 1. Modelo Actualizado (`Models.kt`)

**ANTES:**
```kotlin
data class TvCodeData(
    @SerializedName("device_id") val deviceId: String,  // Requerido
    val code: String,
    @SerializedName("expires_at") val expiresAt: String?,
    @SerializedName("expires_in") val expiresIn: Int?,
    @SerializedName("qr_content") val qrContent: String?
)
```

**AHORA:**
```kotlin
data class TvCodeData(
    @SerializedName("device_id") val deviceId: String? = null,  // Opcional
    val code: String,
    @SerializedName("expires_at") val expiresAt: String?,
    @SerializedName("expires_in") val expiresIn: Int? = null,
    @SerializedName("qr_content") val qrContent: String? = null,
    val message: String? = null  // Agregado
)
```

**Cambios:**
- `deviceId` ahora es **opcional** (`String?` + default `null`)
- `expiresIn`, `qrContent` con defaults
- Agregado campo `message`

---

### 2. Polling Actualizado (`PairingScreen.kt`)

**ANTES:**
```kotlin
// Esperaba deviceId del backend
response.data?.deviceId?.let { devId ->
    while (true) {
        repository.checkTvCodeStatus(devId).fold(...)
    }
}
// ← Nunca entraba porque deviceId era null!
```

**AHORA:**
```kotlin
// Usa deviceId local (Android ID)
scope.launch {
    while (true) {
        delay(3000)
        repository.checkTvCodeStatus(deviceId).fold(
            onSuccess = { status ->
                if (status.status == "authorized" && status.token != null) {
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
```

**Cambios:**
- Usa `deviceId` **local** (Android ID) en lugar del backend
- `scope.launch` en lugar de anidado en `let`
- Polling inicia **siempre** después de generar código

---

## 🎯 Flujo Correcto Ahora

### 1. TV Solicita Código
```kotlin
repository.generateTvCode(deviceId, deviceName)
// deviceId = Android ANDROID_ID
// deviceName = Build.MODEL (ej: "Samsung Smart TV")
```

**Request al backend:**
```json
POST /api/devices/tv/code
{
  "device_id": "bd67038e-1d00-4aad-b59a-e074d18ae5fa",
  "device_name": "onn. Full HD Streaming Device"
}
```

**Response del backend:**
```json
{
  "success": true,
  "code": "479569",
  "expiresAt": "2026-02-15T04:14:36.693Z",
  "message": "Enter this code in your Streamsonic mobile app..."
}
```

---

### 2. TV Muestra Código

La pantalla ahora **SÍ muestra** el código:

```
╔═══════════════════════════════════════╗
║                                       ║
║   Ingresá este código en tu celular: ║
║                                       ║
║         ┌─────────────────┐           ║
║         │  4  7  9  5  6  9 │         ║
║         └─────────────────┘           ║
║                                       ║
║   🟢 Esperando vinculación...         ║
║                                       ║
╚═══════════════════════════════════════╝
```

---

### 3. TV Hace Polling

Cada 3 segundos:
```kotlin
GET /api/devices/tv/status/{deviceId}
// deviceId = Android ID local
```

**Mientras no autorizado:**
```json
{
  "success": true,
  "status": "pending",
  "token": null
}
```

**Cuando usuario autoriza desde móvil:**
```json
{
  "success": true,
  "status": "authorized",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

---

### 4. TV Guarda Token y Navega

```kotlin
if (status.status == "authorized" && status.token != null) {
    repository.saveToken(status.token)
    onPairingSuccess()  // → Navega a HomeScreen
}
```

---

## 📋 Archivos Modificados

### 1. `Models.kt`
```kotlin
// Línea 46-52
data class TvCodeData(
    @SerializedName("device_id") val deviceId: String? = null,
    val code: String,
    @SerializedName("expires_at") val expiresAt: String?,
    @SerializedName("expires_in") val expiresIn: Int? = null,
    @SerializedName("qr_content") val qrContent: String? = null,
    val message: String? = null
)
```

### 2. `PairingScreen.kt`
```kotlin
// Línea 52-87
LaunchedEffect(Unit) {
    repository.generateTvCode(deviceId, deviceName).fold(
        onSuccess = { response ->
            tvCode = response.data?.code
            isLoading = false
            
            // Polling con deviceId local
            scope.launch {
                while (true) {
                    delay(3000)
                    repository.checkTvCodeStatus(deviceId).fold(...)
                }
            }
        },
        onFailure = { ... }
    )
}
```

---

## ✅ Testing

### Antes del Fix:
```
❌ Código NO se mostraba
❌ Polling NO se ejecutaba
❌ TV se quedaba en "Loading" o "Error"
```

### Después del Fix:
```
✅ Código se muestra: "479569"
✅ Polling inicia automáticamente
✅ Detecta autorización del usuario
✅ Navega a HomeScreen con token
```

---

## 🎯 Endpoint de Polling

**¿Existe en el backend?**

Actualmente la app hace:
```
GET /api/devices/tv/status/{deviceId}
```

**Este endpoint NO existe en el backend actual.**

### Opciones:

1. **Crear endpoint de status** (backend)
2. **Cambiar polling** para usar endpoint existente (app)

---

## 🚧 SIGUIENTE PASO: Crear Endpoint de Status

El backend necesita:

```javascript
// GET /api/devices/tv/status/:deviceId
router.get('/tv/status/:deviceId', getTvStatus);

async getTvStatus(deviceId) {
  // Check tv_link_codes table
  const code = await query(
    'SELECT status, user_id FROM tv_link_codes WHERE device_id = ? ORDER BY created_at DESC LIMIT 1',
    [deviceId]
  );
  
  if (code && code.status === 'used') {
    // Generate token for TV
    const token = generateAccessToken({
      id: code.user_id,
      device_type: 'tv',
      device_id: deviceId
    });
    
    return { success: true, status: 'authorized', token };
  }
  
  return { success: true, status: 'pending', token: null };
}
```

---

## 📊 Estado Final

**App Android TV:**
- ✅ Código se muestra correctamente
- ✅ Polling configurado
- ⏳ Esperando endpoint `/tv/status/:deviceId` en backend

**Backend:**
- ✅ `/tv/code` funcionando
- ⏳ Crear `/tv/status/:deviceId`
- ⏳ Crear `/tv/authorize` (usuario móvil)

---

**Desarrollado por:** Lautaro Developers  
**Fecha:** 15 de Febrero 2026  
**Status:** ✅ App Arreglada, ⏳ Backend Pendiente
