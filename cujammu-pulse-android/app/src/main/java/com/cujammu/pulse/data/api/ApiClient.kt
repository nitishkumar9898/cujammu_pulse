package com.cujammu.pulse.data.api

import com.cujammu.pulse.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private fun validatedBaseUrl(): String {
        return ApiEndpointPolicy.validate(BuildConfig.API_BASE_URL, BuildConfig.DEBUG)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val apiService: CUJammuApiService by lazy {
        Retrofit.Builder()
            .baseUrl(validatedBaseUrl())
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CUJammuApiService::class.java)
    }
}
