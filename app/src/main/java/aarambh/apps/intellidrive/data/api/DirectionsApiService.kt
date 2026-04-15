package aarambh.apps.intellidrive.data.api

import aarambh.apps.intellidrive.data.model.DirectionsResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for the Google Directions REST API.
 * Base URL: https://maps.googleapis.com/
 */
interface DirectionsApiService {

    /**
     * Fetches directions from [origin] to [destination].
     *
     * @param origin      "lat,lng" string
     * @param destination "lat,lng" string
     * @param departureTime "now" → enables duration_in_traffic in the response
     * @param apiKey      Google Maps API key (from BuildConfig)
     */
    @GET("maps/api/directions/json")
    suspend fun getDirections(
        @Query("origin")         origin: String,
        @Query("destination")    destination: String,
        @Query("departure_time") departureTime: String = "now",
        @Query("key")            apiKey: String
    ): DirectionsResponse
}
