package com.weathercalendar.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo 天气 API — 免费，无需 API Key。
 */
interface WeatherApi {

    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_PARAMS,
        @Query("hourly") hourly: String = HOURLY_PARAMS,
        @Query("daily") daily: String = DAILY_PARAMS,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 7,
    ): OpenMeteoResponse

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"

        private const val CURRENT_PARAMS =
            "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day"
        private const val HOURLY_PARAMS =
            "temperature_2m,weather_code"
        private const val DAILY_PARAMS =
            "weather_code,temperature_2m_max,temperature_2m_min,uv_index_max,wind_speed_10m_max"
    }
}
