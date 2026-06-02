package com.weathercalendar.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.weathercalendar.data.model.City
import com.weathercalendar.data.remote.GeocodingApi
import com.weathercalendar.data.remote.GeocodingResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "city_prefs")

/** 可序列化的城市存储模型 */
@Serializable
data class SavedCity(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null,
)

@Singleton
class CityRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geocodingApi: GeocodingApi,
    private val json: Json,
) {
    private val dataStore = context.dataStore

    companion object {
        private val KEY_SAVED_CITIES = stringPreferencesKey("saved_cities")
        private val KEY_SELECTED_CITY = stringPreferencesKey("selected_city")
    }

    /** 搜索城市（调用 Open-Meteo Geocoding API） */
    suspend fun searchCities(query: String): Result<List<GeocodingResult>> {
        return try {
            val response = geocodingApi.searchCity(query)
            Result.success(response.results ?: emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 获取收藏城市列表 */
    val savedCities: Flow<List<SavedCity>> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_SAVED_CITIES] ?: "[]"
        try {
            json.decodeFromString<List<SavedCity>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 添加收藏城市 */
    suspend fun addCity(city: SavedCity) {
        dataStore.edit { prefs ->
            val current = getCurrentCities(prefs)
            if (current.none { it.latitude == city.latitude && it.longitude == city.longitude }) {
                val updated = current + city
                prefs[KEY_SAVED_CITIES] = json.encodeToString(updated)
            }
        }
    }

    /** 删除收藏城市 */
    suspend fun removeCity(city: SavedCity) {
        dataStore.edit { prefs ->
            val current = getCurrentCities(prefs)
            val updated = current.filter {
                !(it.latitude == city.latitude && it.longitude == city.longitude)
            }
            prefs[KEY_SAVED_CITIES] = json.encodeToString(updated)
        }
    }

    /** 保存当前选中城市 */
    suspend fun setSelectedCity(city: SavedCity) {
        dataStore.edit { prefs ->
            prefs[KEY_SELECTED_CITY] = json.encodeToString(city)
        }
    }

    /** 获取当前选中城市 */
    val selectedCity: Flow<SavedCity?> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_SELECTED_CITY] ?: return@map null
        try {
            json.decodeFromString<SavedCity>(raw)
        } catch (_: Exception) {
            null
        }
    }

    private fun getCurrentCities(prefs: Preferences): List<SavedCity> {
        val raw = prefs[KEY_SAVED_CITIES] ?: "[]"
        return try {
            json.decodeFromString(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
