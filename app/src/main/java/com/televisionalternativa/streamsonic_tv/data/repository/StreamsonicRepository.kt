package com.televisionalternativa.streamsonic_tv.data.repository

import com.televisionalternativa.streamsonic_tv.data.api.ApiClient
import com.televisionalternativa.streamsonic_tv.data.api.GenerateTvCodeRequest
import com.televisionalternativa.streamsonic_tv.data.local.TvPreferences
import com.televisionalternativa.streamsonic_tv.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StreamsonicRepository(
    private val prefs: TvPreferences
) {
    private val api = ApiClient.api
    
    suspend fun initAuth() {
        prefs.getAuthTokenOnce()?.let { token ->
            ApiClient.setAuthToken(token)
        }
    }
    
    suspend fun generateTvCode(deviceId: String, deviceName: String): Result<TvCodeResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.generateTvCode(
                    GenerateTvCodeRequest(deviceId, deviceName)
                )
                if (response.success && response.data != null) {
                    // Save the device_id returned by backend (NOT the local Android ID)
                    prefs.saveDeviceId(response.data.deviceId)
                    Result.success(response)
                } else {
                    Result.failure(Exception(response.message ?: "Error generando código"))
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
                    // Save auth data
                    prefs.saveAuthData(response.token, 0, deviceId)
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
        prefs.saveAuthData(token, 0, deviceId)
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
    
    suspend fun logout() {
        prefs.clearAuth()
        ApiClient.setAuthToken(null)
    }
}
