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

    @GET("api/movies")
    suspend fun getMovies(): MoviesResponse
    
    @GET("api/auth/profile")
    suspend fun getProfile(): ProfileResponse

    // Favorites
    @GET("api/favorites/{userId}")
    suspend fun getFavorites(
        @Path("userId") userId: String
    ): FavoritesResponse

    @POST("api/favorites")
    suspend fun addFavorite(
        @Body request: AddFavoriteRequest
    ): ApiResponse<Any>

    @DELETE("api/favorites/{userId}/{itemType}/{itemId}")
    suspend fun deleteFavorite(
        @Path("userId") userId: String,
        @Path("itemType") itemType: String,
        @Path("itemId") itemId: Int
    ): ApiResponse<Any>

    @GET("api/favorites/check/{userId}/{itemType}/{itemId}")
    suspend fun checkFavorite(
        @Path("userId") userId: String,
        @Path("itemType") itemType: String,
        @Path("itemId") itemId: Int
    ): CheckFavoriteResponse
}

data class GenerateTvCodeRequest(
    val device_id: String,
    val device_name: String
)

data class AddFavoriteRequest(
    val user_id: String,
    val item_id: Int,
    val item_type: String
)
