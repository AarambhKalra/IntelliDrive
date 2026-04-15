package aarambh.apps.intellidrive.data.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Singleton Retrofit instance for Google Maps APIs.
 * The [directionsService] property is lazily initialised on first access.
 */
object RetrofitClient {

    private const val BASE_URL = "https://maps.googleapis.com/"

    val directionsService: DirectionsApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DirectionsApiService::class.java)
    }
}
