package com.televisionalternativa.streamsonic_tv.data.repository

import android.util.Base64
import android.util.Log
import com.televisionalternativa.streamsonic_tv.data.api.AddFavoriteRequest
import com.televisionalternativa.streamsonic_tv.data.api.ApiClient
import com.televisionalternativa.streamsonic_tv.data.api.GenerateTvCodeRequest
import com.televisionalternativa.streamsonic_tv.data.local.TvPreferences
import com.televisionalternativa.streamsonic_tv.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class StreamsonicRepository(
    val prefs: TvPreferences
) {
    private val api = ApiClient.api

    companion object {
        private const val TAG = "StreamsonicRepository"
    }

    /**
     * Decode JWT payload and extract the user ID.
     * JWT format: header.payload.signature — payload is base64url-encoded JSON.
     * The API embeds { id, username, email, role, ... } in the payload.
     */
    private fun extractUserIdFromToken(token: String): Int {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return 0
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
            val json = JSONObject(payload)
            val userId = json.optInt("id", 0)
            Log.d(TAG, "Extracted userId=$userId from JWT")
            userId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode JWT: ${e.message}")
            0
        }
    }
    
    suspend fun initAuth() {
        prefs.getAuthTokenOnce()?.let { token ->
            ApiClient.setAuthToken(token)
            // If userId wasn't saved correctly before (was "0"), fix it now
            val storedUserId = prefs.getUserIdOnce()
            if (storedUserId == null || storedUserId == "0") {
                val userId = extractUserIdFromToken(token)
                if (userId > 0) {
                    val deviceId = prefs.getDeviceIdOnce() ?: ""
                    prefs.saveAuthData(token, userId, deviceId)
                    Log.d(TAG, "Fixed userId from JWT on init: $userId")
                }
            }
        }
    }
    
    suspend fun generateTvCode(deviceId: String, deviceName: String): Result<TvCodeResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.generateTvCode(
                    GenerateTvCodeRequest(deviceId, deviceName)
                )
                if (response.success && response.data?.deviceId != null) {
                    prefs.saveDeviceId(response.data.deviceId)
                    Result.success(response)
                } else {
                    Result.failure(Exception(response.message ?: "Error generando código o el deviceId es nulo"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun checkTvCodeStatus(deviceId: String): Result<TvAuthStatusResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.checkTvCodeStatus(deviceId)
                if (response.success && response.status == "authorized" && response.token != null) {
                    val userId = extractUserIdFromToken(response.token)
                    prefs.saveAuthData(response.token, userId, deviceId)
                    ApiClient.setAuthToken(response.token)
                }
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun saveToken(token: String) {
        val deviceId = prefs.getDeviceIdOnce() ?: ""
        val userId = extractUserIdFromToken(token)
        prefs.saveAuthData(token, userId, deviceId)
        ApiClient.setAuthToken(token)
    }
    
    suspend fun getChannels(): Result<List<Channel>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getChannels()
                if (response.success && response.data != null) {
                    Result.success(response.data)
                } else {
                    Result.failure(Exception("Error obteniendo canales"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun getStations(): Result<List<Station>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getStations()
                if (response.success && response.data != null) {
                    Result.success(response.data)
                } else {
                    Result.failure(Exception("Error obteniendo estaciones"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getMovies(): Result<List<Movie>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getMovies()
                if (response.success && response.data != null) {
                    Result.success(response.data)
                } else {
                    Result.failure(Exception("Error obteniendo películas"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun getProfile(): Result<UserProfile> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getProfile()
                if (response.success && response.data != null) {
                    Result.success(response.data)
                } else {
                    Result.failure(Exception("Error obteniendo perfil"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getFavorites(): Result<List<Favorite>> {
        return withContext(Dispatchers.IO) {
            try {
                val userId = prefs.getUserIdOnce() ?: "0"
                val response = api.getFavorites(userId)
                if (response.success && response.data != null) {
                    Result.success(response.data)
                } else {
                    Result.success(emptyList())
                }
            } catch (e: Exception) {
                Result.success(emptyList())
            }
        }
    }

    suspend fun addFavorite(itemId: Int, itemType: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val userId = prefs.getUserIdOnce() ?: "0"
                val response = api.addFavorite(AddFavoriteRequest(userId, itemId, itemType))
                Result.success(response.success)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun deleteFavorite(itemId: Int, itemType: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val userId = prefs.getUserIdOnce() ?: "0"
                val response = api.deleteFavorite(userId, itemType, itemId)
                Result.success(response.success)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun checkFavorite(itemId: Int, itemType: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val userId = prefs.getUserIdOnce() ?: "0"
                val response = api.checkFavorite(userId, itemType, itemId)
                Result.success(response.data?.isFavorite ?: false)
            } catch (e: Exception) {
                Result.success(false)
            }
        }
    }

    suspend fun saveLastChannel(index: Int, contentType: String) {
        prefs.saveLastChannel(index, contentType)
    }

    suspend fun getLastChannelIndex(): Int = prefs.getLastChannelIndex()
    suspend fun getLastContentType(): String = prefs.getLastContentType()
    
    suspend fun logout() {
        prefs.clearAuth()
        ApiClient.setAuthToken(null)
    }
}
