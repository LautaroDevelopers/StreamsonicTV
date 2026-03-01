package com.televisionalternativa.streamsonic_tv.data.model

import com.google.gson.annotations.SerializedName

data class Channel(
    val id: Int,
    val name: String,
    @SerializedName("stream_url") val streamUrl: String,
    @SerializedName("image_url") val imageUrl: String?,
    val category: String?,
    @SerializedName("is_active") val isActive: Boolean = true
)

data class Station(
    val id: Int,
    val name: String,
    @SerializedName("stream_url") val streamUrl: String,
    @SerializedName("image_url") val imageUrl: String?,
    val category: String?,
    @SerializedName("is_active") val isActive: Boolean = true
)

data class Movie(
    val id: Int,
    val title: String,
    val description: String?,
    @SerializedName("stream_url") val streamUrl: String?,
    @SerializedName("image_url") val imageUrl: String?,
    val category: String?,
    @SerializedName("is_active") val isActive: Boolean = true
)

data class TvDevice(
    val id: Int,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("device_name") val deviceName: String?,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("user_id") val userId: Int?
)

data class UserProfile(
    val id: Int,
    val name: String,
    val email: String,
    @SerializedName("subscription_expires") val subscriptionExpires: String?
)

// API Responses
data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val message: String?
)

data class TvCodeData(
    @SerializedName("device_id") val deviceId: String? = null,
    val code: String,
    @SerializedName("expires_at") val expiresAt: String?,
    @SerializedName("expires_in") val expiresIn: Int? = null,
    @SerializedName("qr_content") val qrContent: String? = null,
    val message: String? = null
)

data class TvCodeResponse(
    val success: Boolean,
    val data: TvCodeData?,
    val message: String?
)

data class TvAuthStatusResponse(
    val success: Boolean,
    val status: String?,
    val token: String?,
    val message: String?
)

data class ChannelsResponse(
    val success: Boolean,
    val data: List<Channel>?
)

data class StationsResponse(
    val success: Boolean,
    val data: List<Station>?
)

data class MoviesResponse(
    val success: Boolean,
    val data: List<Movie>?
)

data class ProfileResponse(
    val success: Boolean,
    val data: UserProfile?
)

data class Favorite(
    val id: Int,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("item_id") val itemId: Int,
    @SerializedName("item_type") val itemType: String
)

data class FavoritesResponse(
    val success: Boolean,
    val data: List<Favorite>?
)

data class CheckFavoriteResponse(
    val success: Boolean,
    val data: CheckFavoriteData?
)

data class CheckFavoriteData(
    val isFavorite: Boolean
)
