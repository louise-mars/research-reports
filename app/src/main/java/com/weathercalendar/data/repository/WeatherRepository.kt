package com.weathercalendar.data.repository

import com.weathercalendar.data.local.WeatherDao
import com.weathercalendar.data.local.WeatherEntity
import com.weathercalendar.data.model.CurrentWeather
import com.weathercalendar.data.model.DailyWeather
import com.weathercalendar.data.model.HourlyForecast
import com.weathercalendar.data.model.WeatherDetails
import com.weathercalendar.data.remote.OpenMeteoResponse
import com.weathercalendar.data.remote.WeatherApi
import com.weathercalendar.data.remote.WeatherCodeMapper
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class WeatherData(
    val current: CurrentWeather,
    val hourly: List<HourlyForecast>,
    val daily: List<DailyWeather>,
    val details: WeatherDetails,
    val fromCache: Boolean = false,
)

/**
 * 天气数据仓库 — 缓存优先策略：
 * 1. 先读 Room 缓存
 * 2. 缓存未过期（<30分钟）→ 直接返回
 * 3. 缓存过期或无缓存 → 请求 API
 * 4. API 成功 → 更新缓存 → 返回新数据
 * 5. API 失败 + 有旧缓存 → 返回旧缓存（标记 fromCache）
 * 6. API 失败 + 无缓存 → 返回错误
 */
@Singleton
class WeatherRepository @Inject constructor(
    private val api: WeatherApi,
    private val dao: WeatherDao,
    private val json: Json,
) {
    suspend fun getWeather(latitude: Double, longitude: Double): Result<WeatherData> {
        val cacheKey = WeatherEntity.key(latitude, longitude)

        // 1. 读缓存
        val cached = dao.get(cacheKey)

        // 2. 缓存未过期 → 直接返回
        if (cached != null && !cached.isExpired) {
            return try {
                val response = json.decodeFromString<OpenMeteoResponse>(cached.responseJson)
                Result.success(mapResponse(response, fromCache = true))
            } catch (_: Exception) {
                // 缓存损坏，继续请求 API
                fetchAndCache(latitude, longitude, cacheKey, staleCache = cached)
            }
        }

        // 3. 缓存过期或无缓存 → 请求 API
        return fetchAndCache(latitude, longitude, cacheKey, staleCache = cached)
    }

    private suspend fun fetchAndCache(
        latitude: Double,
        longitude: Double,
        cacheKey: String,
        staleCache: WeatherEntity?,
    ): Result<WeatherData> {
        return try {
            val response = api.getForecast(latitude, longitude)

            // 4. 更新缓存
            val responseJson = json.encodeToString(OpenMeteoResponse.serializer(), response)
            dao.insert(
                WeatherEntity(
                    cacheKey = cacheKey,
                    responseJson = responseJson,
                    updatedAt = System.currentTimeMillis(),
                )
            )

            // 清理超过 24 小时的旧缓存
            dao.deleteOlderThan(System.currentTimeMillis() - 24 * 60 * 60 * 1000L)

            Result.success(mapResponse(response, fromCache = false))
        } catch (e: Exception) {
            // 5. API 失败 + 有旧缓存 → 返回旧数据
            if (staleCache != null) {
                try {
                    val response = json.decodeFromString<OpenMeteoResponse>(staleCache.responseJson)
                    Result.success(mapResponse(response, fromCache = true))
                } catch (_: Exception) {
                    Result.failure(e)
                }
            } else {
                // 6. 无缓存 → 返回错误
                Result.failure(e)
            }
        }
    }

    private fun mapResponse(response: OpenMeteoResponse, fromCache: Boolean): WeatherData {
        val current = response.current!!
        val hourly = response.hourly!!
        val daily = response.daily!!

        val now = LocalDateTime.now()

        val currentWeather = CurrentWeather(
            temperature = current.temperature.toInt(),
            feelsLike = current.apparentTemperature.toInt(),
            condition = WeatherCodeMapper.fromWmoCode(current.weatherCode),
            isDay = current.isDay == 1,
        )

        val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        val hourlyForecasts = hourly.time.mapIndexedNotNull { index, timeStr ->
            val time = try {
                LocalDateTime.parse(timeStr, isoFormatter)
            } catch (_: Exception) {
                return@mapIndexedNotNull null
            }
            if (time.isBefore(now.minusHours(1)) || time.isAfter(now.plusHours(24))) {
                return@mapIndexedNotNull null
            }
            HourlyForecast(
                time = time.toLocalTime(),
                temperature = hourly.temperature.getOrElse(index) { 0.0 }.toInt(),
                condition = WeatherCodeMapper.fromWmoCode(
                    hourly.weatherCode.getOrElse(index) { 0 }
                ),
                isNow = time.hour == now.hour && time.toLocalDate() == now.toLocalDate(),
            )
        }

        val dailyForecasts = daily.time.mapIndexed { index, dateStr ->
            DailyWeather(
                date = LocalDate.parse(dateStr),
                condition = WeatherCodeMapper.fromWmoCode(
                    daily.weatherCode.getOrElse(index) { 0 }
                ),
                tempMin = daily.temperatureMin.getOrElse(index) { 0.0 }.toInt(),
                tempMax = daily.temperatureMax.getOrElse(index) { 0.0 }.toInt(),
            )
        }

        val details = WeatherDetails(
            humidity = current.humidity,
            windSpeed = current.windSpeed.toInt(),
            uvIndex = formatUvIndex(daily.uvIndexMax.firstOrNull() ?: 0.0),
            airQuality = "—",
        )

        return WeatherData(
            current = currentWeather,
            hourly = hourlyForecasts,
            daily = dailyForecasts,
            details = details,
            fromCache = fromCache,
        )
    }

    private fun formatUvIndex(uv: Double): String = when {
        uv < 3 -> "低"
        uv < 6 -> "中等"
        uv < 8 -> "高"
        uv < 11 -> "很高"
        else -> "极高"
    }
}
