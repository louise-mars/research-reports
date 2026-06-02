package com.weathercalendar.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo Geocoding API — 城市搜索，免费无需 Key。
 */
interface GeocodingApi {

    @GET("v1/search")
    suspend fun searchCity(
        @Query("name") name: String,
        @Query("count") count: Int = 10,
        @Query("language") language: String = "zh",
        @Query("format") format: String = "json",
    ): GeocodingResponse

    companion object {
        const val BASE_URL = "https://geocoding-api.open-meteo.com/"
    }
}
