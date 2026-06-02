package com.weathercalendar.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Nominatim (OpenStreetMap) 反向地理编码 API — 免费，无需 Key。
 * 用于根据经纬度获取城市名称，替代 Android Geocoder（在国内不可用）。
 * 使用规范: https://nominatim.org/release-docs/develop/api/Reverse/
 */
interface NominatimApi {

    @GET("reverse")
    suspend fun reverseGeocode(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("format") format: String = "json",
        @Query("zoom") zoom: Int = 10, // city level
        @Query("accept-language") language: String = "zh",
        @Header("User-Agent") userAgent: String = "WeatherCalendar/1.0",
    ): NominatimResponse

    companion object {
        const val BASE_URL = "https://nominatim.openstreetmap.org/"
    }
}

@Serializable
data class NominatimResponse(
    @SerialName("display_name") val displayName: String? = null,
    val address: NominatimAddress? = null,
)

@Serializable
data class NominatimAddress(
    val city: String? = null,
    val town: String? = null,
    val village: String? = null,
    val county: String? = null,
    val state: String? = null,
    val country: String? = null,
)
