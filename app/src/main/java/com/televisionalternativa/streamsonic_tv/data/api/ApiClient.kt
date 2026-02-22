package com.televisionalternativa.streamsonic_tv.data.api

import com.televisionalternativa.streamsonic_tv.data.local.TvPreferences
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

  private const val BASE_URL = "https://api.streamsonic.televisionalternativa.com.ar/"

  private var authToken: String? = null
  private var tvPreferences: TvPreferences? = null
  private var onSessionExpired: (() -> Unit)? = null

  fun init(prefs: TvPreferences, onExpired: () -> Unit) {
    tvPreferences = prefs
    onSessionExpired = onExpired
  }

  fun setAuthToken(token: String?) {
    authToken = token
  }

  private val authInterceptor = Interceptor { chain ->
    val originalRequest = chain.request()
    val requestBuilder = originalRequest.newBuilder()

    authToken?.let { token -> requestBuilder.addHeader("Authorization", "Bearer $token") }

    chain.proceed(requestBuilder.build())
  }

  // Interceptor para detectar 403 y hacer logout automático
  private val sessionInterceptor = Interceptor { chain ->
    val response = chain.proceed(chain.request())

    // Si recibimos 403, la sesión expiró o el dispositivo fue desvinculado
    if (response.code == 403) {
      CoroutineScope(Dispatchers.IO).launch {
        tvPreferences?.clearAuth()
        authToken = null

        // Notificar a la UI para navegar a Pairing
        onSessionExpired?.invoke()
      }
    }

    response
  }

  private val loggingInterceptor =
          HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }

  private val okHttpClient =
          OkHttpClient.Builder()
                  .addInterceptor(authInterceptor)
                  .addInterceptor(sessionInterceptor)
                  .addInterceptor(loggingInterceptor)
                  .connectTimeout(30, TimeUnit.SECONDS)
                  .readTimeout(30, TimeUnit.SECONDS)
                  .writeTimeout(30, TimeUnit.SECONDS)
                  .build()

  private val retrofit =
          Retrofit.Builder()
                  .baseUrl(BASE_URL)
                  .client(okHttpClient)
                  .addConverterFactory(GsonConverterFactory.create())
                  .build()

  val api: StreamsonicApi = retrofit.create(StreamsonicApi::class.java)
}
