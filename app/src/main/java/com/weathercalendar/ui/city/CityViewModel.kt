package com.weathercalendar.ui.city

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weathercalendar.data.location.LocationService
import com.weathercalendar.data.model.City
import com.weathercalendar.data.model.WeatherCondition
import com.weathercalendar.data.remote.GeocodingResult
import com.weathercalendar.data.repository.CityRepository
import com.weathercalendar.data.repository.SavedCity
import com.weathercalendar.data.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CityPickerUiState(
    val currentLocationCity: City? = null,
    val savedCities: List<City> = emptyList(),
    val searchResults: List<GeocodingResult> = emptyList(),
    val isSearching: Boolean = false,
)

@HiltViewModel
class CityViewModel @Inject constructor(
    private val cityRepository: CityRepository,
    private val weatherRepository: WeatherRepository,
    private val locationService: LocationService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CityPickerUiState())
    val uiState: StateFlow<CityPickerUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadCities()
    }

    private fun loadCities() {
        viewModelScope.launch {
            // 加载 GPS 定位城市
            val locResult = locationService.getCurrentLocation().getOrNull()
            if (locResult != null) {
                val weather = weatherRepository.getWeather(locResult.latitude, locResult.longitude)
                    .getOrNull()
                _uiState.update {
                    it.copy(
                        currentLocationCity = City(
                            name = locResult.cityName,
                            latitude = locResult.latitude,
                            longitude = locResult.longitude,
                            temperature = weather?.current?.temperature,
                            condition = weather?.current?.condition,
                            isCurrentLocation = true,
                        )
                    )
                }
            }

            // 加载收藏城市
            cityRepository.savedCities.collect { savedList ->
                val cities = savedList.map { saved ->
                    // 尝试获取每个城市的当前温度
                    val weather = try {
                        weatherRepository.getWeather(saved.latitude, saved.longitude).getOrNull()
                    } catch (_: Exception) {
                        null
                    }
                    City(
                        name = saved.name,
                        latitude = saved.latitude,
                        longitude = saved.longitude,
                        temperature = weather?.current?.temperature,
                        condition = weather?.current?.condition,
                        isCurrentLocation = false,
                    )
                }
                _uiState.update { it.copy(savedCities = cities) }
            }
        }
    }

    /** 搜索城市（带防抖） */
    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // 防抖
            _uiState.update { it.copy(isSearching = true) }
            val results = cityRepository.searchCities(query).getOrDefault(emptyList())
            _uiState.update { it.copy(searchResults = results, isSearching = false) }
        }
    }

    /** 添加搜索结果到收藏 */
    fun addCity(result: GeocodingResult) {
        viewModelScope.launch {
            cityRepository.addCity(
                SavedCity(
                    name = result.name,
                    latitude = result.latitude,
                    longitude = result.longitude,
                    country = result.country,
                    admin1 = result.admin1,
                )
            )
        }
    }

    /** 删除收藏城市 */
    fun removeCity(city: City) {
        viewModelScope.launch {
            cityRepository.removeCity(
                SavedCity(
                    name = city.name,
                    latitude = city.latitude,
                    longitude = city.longitude,
                )
            )
        }
    }

    /** 将 City 转为 SavedCity 供 HomeViewModel 使用 */
    fun toSavedCity(city: City): SavedCity {
        return SavedCity(
            name = city.name,
            latitude = city.latitude,
            longitude = city.longitude,
        )
    }
}
