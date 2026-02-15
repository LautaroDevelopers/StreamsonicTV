package com.televisionalternativa.streamsonic_tv.data.api

import com.televisionalternativa.streamsonic_tv.data.model.*
import retrofit2.http.*

interface StreamsonicApi {
    
    // TV Device endpoints
    @POST("api/devices/tv/code")
    suspend fun generateTvCode(
        @Body request: GenerateTvCodeRequest
    ): TvCodeResponse
    
    @GET("api/devices/tv/status/{deviceId}")
    suspend fun checkTvCodeStatus(
        @Path("deviceId") deviceId: String
    ): TvAuthStatusResponse
    
    // Content endpoints (require auth)
    @GET("api/channels")
    suspend fun getChannels(): ChannelsResponse
    
    @GET("api/stations")
    suspend fun getStations(): StationsResponse
    
    @GET("api/auth/profile")
    suspend fun getProfile(): ProfileResponse
}

data class GenerateTvCodeRequest(
    val device_id: String,
    val device_name: String
)
