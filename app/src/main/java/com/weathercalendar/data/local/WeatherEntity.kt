package com.weathercalendar.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room 缓存实体 — 按城市坐标缓存天气数据。
 * JSON 字段存储完整 API 响应，避免复杂的关系表。
 */
@Entity(tableName = "weather_cache")
data class WeatherEntity(
    @PrimaryKey
    val cacheKey: String,           // "lat,lon" 作为唯一键
    val responseJson: String,       // 完整 OpenMeteoResponse JSON
    val updatedAt: Long,            // System.currentTimeMillis()
) {
    companion object {
        /** 缓存过期时间：30 分钟 */
        const val CACHE_DURATION_MS = 30 * 60 * 1000L

        fun key(lat: Double, lon: Double): String =
            "%.2f,%.2f".format(lat, lon)
    }

    val isExpired: Boolean
        get() = System.currentTimeMillis() - updatedAt > CACHE_DURATION_MS
}
