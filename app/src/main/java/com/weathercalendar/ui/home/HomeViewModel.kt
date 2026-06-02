package com.weathercalendar.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weathercalendar.data.location.LocationService
import com.weathercalendar.data.model.CurrentWeather
import com.weathercalendar.data.model.DayInfo
import com.weathercalendar.data.model.HourlyForecast
import com.weathercalendar.data.model.WeatherCondition
import com.weathercalendar.data.model.WeatherDetails
import com.weathercalendar.data.repository.CalendarRepository
import com.weathercalendar.data.repository.CityRepository
import com.weathercalendar.data.repository.SavedCity
import com.weathercalendar.data.repository.TemperatureUnit
import com.weathercalendar.data.repository.UserPrefsRepository
import com.weathercalendar.data.repository.WeatherRepository
import com.weathercalendar.util.LunarCalendar
import com.weathercalendar.widget.WeatherWidgetDataProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val cityName: String = "定位中...",
    val dateText: String = "",
    val lunarText: String = "",
    val currentWeather: CurrentWeather = CurrentWeather(0, 0, WeatherCondition.SUNNY),
    val hourlyForecast: List<HourlyForecast> = emptyList(),
    val threeDays: List<DayInfo> = emptyList(),
    val weatherDetails: WeatherDetails = WeatherDetails(0, 0, "—", "—"),
    val fromCache: Boolean = false,
    val tempUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val calendarRepository: CalendarRepository,
    private val cityRepository: CityRepository,
    private val userPrefsRepository: UserPrefsRepository,
    private val locationService: LocationService,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // 更新日期和农历
            val today = LocalDate.now()
            val dayOfWeek = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINESE)
            val dateText = today.format(DateTimeFormatter.ofPattern("M月d日")) + " $dayOfWeek"
            val lunarText = LunarCalendar.getDisplayText(today)
            _uiState.update { it.copy(dateText = dateText, lunarText = lunarText) }

            // 确定坐标：已选城市 > GPS 定位 > 默认城市（fallback）
            val savedCity = cityRepository.selectedCity.first()
            val userPrefs = userPrefsRepository.prefs.first()
            val lat: Double
            val lon: Double
            val cityName: String

            if (savedCity != null && savedCity.name.isNotEmpty()) {
                // 用户手动选择的城市
                lat = savedCity.latitude
                lon = savedCity.longitude
                cityName = savedCity.name
            } else if (userPrefs.useLocation) {
                // 尝试 GPS 定位
                val locationResult = locationService.getCurrentLocation()
                val location = locationResult.getOrNull()
                if (location != null) {
                    lat = location.latitude
                    lon = location.longitude
                    cityName = location.cityName
                } else {
                    // 定位失败 → fallback 到默认城市
                    lat = userPrefs.defaultCityLat
                    lon = userPrefs.defaultCityLon
                    cityName = userPrefs.defaultCityName
                }
            } else {
                // 用户关闭了定位 → 使用默认城市
                lat = userPrefs.defaultCityLat
                lon = userPrefs.defaultCityLon
                cityName = userPrefs.defaultCityName
            }

            _uiState.update { it.copy(cityName = cityName) }

            // 同步城市信息给 Widget
            WeatherWidgetDataProvider.saveCityForWidget(appContext, cityName, lat, lon)

            // 获取天气（缓存优先）
            val weatherResult = weatherRepository.getWeather(lat, lon)
            val weatherData = weatherResult.getOrElse { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = "天气加载失败: ${e.message}")
                }
                return@launch
            }

            // 获取日历事件（3天），失败不影响主流程
            val endDate = today.plusDays(2)
            val eventsMap = try {
                calendarRepository.getEventsGroupedByDate(today, endDate)
            } catch (_: Exception) {
                emptyMap()
            }

            // 构建 3 天融合数据
            val threeDays = weatherData.daily.take(3).map { daily ->
                val dayEvents = eventsMap[daily.date] ?: emptyList()
                DayInfo(
                    date = daily.date,
                    weather = daily,
                    events = dayEvents,
                    lunarDate = LunarCalendar.getDisplayText(daily.date),
                    lunarFestival = if (LunarCalendar.isFestival(daily.date))
                        LunarCalendar.getDisplayText(daily.date) else null,
                )
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    currentWeather = weatherData.current,
                    hourlyForecast = weatherData.hourly,
                    threeDays = threeDays,
                    weatherDetails = weatherData.details,
                    fromCache = weatherData.fromCache,
                    tempUnit = userPrefs.temperatureUnit,
                )
            }
        }
    }

    fun selectCity(city: SavedCity) {
        viewModelScope.launch {
            cityRepository.setSelectedCity(city)
            _uiState.update { it.copy(cityName = city.name) }
            loadData()
        }
    }

    fun useCurrentLocation() {
        viewModelScope.launch { loadData() }
    }
}
