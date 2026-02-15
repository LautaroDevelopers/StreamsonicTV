package com.televisionalternativa.streamsonic_tv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "streamsonic_tv_prefs")

class TvPreferences(private val context: Context) {
    
    companion object {
        private val AUTH_TOKEN = stringPreferencesKey("auth_token")
        private val DEVICE_ID = stringPreferencesKey("device_id")
        private val USER_ID = stringPreferencesKey("user_id")
    }
    
    val authToken: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[AUTH_TOKEN]
    }
    
    val deviceId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[DEVICE_ID]
    }
    
    val isAuthenticated: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTH_TOKEN] != null
    }
    
    suspend fun getAuthTokenOnce(): String? {
        return context.dataStore.data.first()[AUTH_TOKEN]
    }
    
    suspend fun getDeviceIdOnce(): String? {
        return context.dataStore.data.first()[DEVICE_ID]
    }
    
    suspend fun saveAuthData(token: String, userId: Int, deviceId: String) {
        context.dataStore.edit { prefs ->
            prefs[AUTH_TOKEN] = token
            prefs[USER_ID] = userId.toString()
            prefs[DEVICE_ID] = deviceId
        }
    }
    
    suspend fun saveDeviceId(deviceId: String) {
        context.dataStore.edit { prefs ->
            prefs[DEVICE_ID] = deviceId
        }
    }
    
    suspend fun clearAuth() {
        context.dataStore.edit { prefs ->
            prefs.remove(AUTH_TOKEN)
            prefs.remove(USER_ID)
        }
    }
    
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
