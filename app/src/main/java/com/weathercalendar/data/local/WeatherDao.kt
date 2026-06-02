package com.weathercalendar.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WeatherDao {

    @Query("SELECT * FROM weather_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun get(key: String): WeatherEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WeatherEntity)

    @Query("DELETE FROM weather_cache WHERE updatedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
