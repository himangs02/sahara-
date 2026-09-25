package com.yourteam.sahara.api

import com.yourteam.sahara.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/** Builds the Retrofit client used to talk to the Sahara FastAPI backend. Local development only
 * in Stage 3 -- see [BuildConfig.API_BASE_URL] and backend/README.md. */
object RetrofitProvider {

    private val json: Json = Json { ignoreUnknownKeys = true }

    /** [tokenProvider] is read on every request (not cached), so a freshly stored/cleared token
     * takes effect on the very next call without recreating the client. */
    fun create(baseUrl: String, tokenProvider: () -> String?): SaharaApiService {
        val authInterceptor = Interceptor { chain ->
            val token = tokenProvider()
            val request = if (token != null) {
                chain.request().newBuilder().addHeader("Authorization", "Bearer $token").build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }

        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)

        if (BuildConfig.DEBUG) {
            // BASIC only: method, URL, status, timing. Never headers or bodies -- those carry
            // passwords and bearer tokens, which must never reach Logcat even in debug builds.
            clientBuilder.addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(clientBuilder.build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create(SaharaApiService::class.java)
    }
}
