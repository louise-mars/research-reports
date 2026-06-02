package com.weathercalendar.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weathercalendar.data.location.LocationService
import com.weathercalendar.data.model.CalendarDayCell
import com.weathercalendar.data.model.CalendarEvent
import com.weathercalendar.data.model.WeatherCondition
import com.weathercalendar.data.repository.CalendarRepository
import com.weathercalendar.data.repository.CityRepository
import com.weathercalendar.data.repository.WeatherRepository
import com.weathercalendar.util.LunarCalendar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

data class CalendarUiState(
    val isLoading: Boolean = true,
    val monthLabel: String = "",
    val currentMonth: YearMonth = YearMonth.now(),
    val days: List<CalendarDayCell> = emptyList(),
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val eventsMap: Map<LocalDate, List<CalendarEvent>> = emptyMap(),
    val weatherMap: Map<LocalDate, Pair<WeatherCondition, Pair<Int, Int>>> = emptyMap(),
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val calendarRepository: CalendarRepository,
    private val cityRepository: CityRepository,
    private val locationService: LocationService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    // 缓存已加载的月份数据，避免重复网络请求
    private val monthCache = ConcurrentHashMap<YearMonth, MonthData>()

    // 缓存坐标，避免每次切月都重新定位
    private var cachedLat: Double? = null
    private var cachedLon: Double? = null

    private data class MonthData(
        val days: List<CalendarDayCell>,
        val eventsMap: Map<LocalDate, List<CalendarEvent>>,
        val weatherMap: Map<LocalDate, Pair<WeatherCondition, Pair<Int, Int>>>,
    )

    init {
        loadMonth(YearMonth.now())
    }

    fun changeMonth(delta: Int) {
        val newMonth = _uiState.value.currentMonth.plusMonths(delta.toLong())
        loadMonth(newMonth)
    }

    private fun loadMonth(month: YearMonth) {
        // 如果缓存中有数据，立即显示
        val cached = monthCache[month]
        if (cached != null) {
            val monthLabel = "${month.year}年${month.monthValue}月"
            val startDate = month.atDay(1)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    currentMonth = month,
                    monthLabel = monthLabel,
                    days = cached.days,
                    firstDayOfWeek = startDate.dayOfWeek,
                    eventsMap = cached.eventsMap,
                    weatherMap = cached.weatherMap,
                )
            }
            return
        }

        // 第一步：立即渲染日历格子（纯计算，无网络）
        val startDate = month.atDay(1)
        val endDate = month.atEndOfMonth()
        val monthLabel = "${month.year}年${month.monthValue}月"

        val days = (1..endDate.dayOfMonth).map { day ->
            val date = month.atDay(day)
            CalendarDayCell(
                date = date,
                lunarText = LunarCalendar.getDisplayText(date),
                isLunarFestival = LunarCalendar.isFestival(date) || LunarCalendar.isSolarTerm(date),
                weatherIcon = null,
                hasEvents = false,
            )
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                currentMonth = month,
                monthLabel = monthLabel,
                days = days,
                firstDayOfWeek = startDate.dayOfWeek,
                eventsMap = emptyMap(),
                weatherMap = emptyMap(),
            )
        }

        // 第二步：异步加载天气和事件，加载完后更新 UI
        viewModelScope.launch {
            val eventsMap = try {
                calendarRepository.getEventsGroupedByDate(startDate, endDate)
            } catch (_: Exception) {
                emptyMap()
            }

            val weatherMap = mutableMapOf<LocalDate, Pair<WeatherCondition, Pair<Int, Int>>>()
            try {
                val (lat, lon) = getCoordinates()
                if (lat != 0.0 || lon != 0.0) {
                    val weatherData = weatherRepository.getWeather(lat, lon).getOrNull()
                    weatherData?.daily?.forEach { daily ->
                        weatherMap[daily.date] = daily.condition to (daily.tempMin to daily.tempMax)
                    }
                }
            } catch (_: Exception) {
                // 天气加载失败不影响日历显示
            }

            // 用天气和事件数据更新日期格子
            val updatedDays = days.map { cell ->
                cell.copy(
                    weatherIcon = weatherMap[cell.date]?.first?.icon,
                    hasEvents = eventsMap.containsKey(cell.date),
                )
            }

            // 确保用户没有在加载期间切走
            if (_uiState.value.currentMonth == month) {
                _uiState.update {
                    it.copy(
                        days = updatedDays,
                        eventsMap = eventsMap,
                        weatherMap = weatherMap,
                    )
                }
            }

            // 缓存结果
            monthCache[month] = MonthData(updatedDays, eventsMap, weatherMap)
        }
    }

    /**
     * 获取坐标，优先使用缓存，避免每次切月都重新定位。
     */
    private suspend fun getCoordinates(): Pair<Double, Double> {
        val lat = cachedLat
        val lon = cachedLon
        if (lat != null && lon != null) return lat to lon

        val savedCity = cityRepository.selectedCity.first()
        return if (savedCity != null && savedCity.name.isNotEmpty()) {
            (savedCity.latitude to savedCity.longitude).also {
                cachedLat = it.first
                cachedLon = it.second
            }
        } else {
            val loc = locationService.getCurrentLocation().getOrNull()
            val result = (loc?.latitude ?: 0.0) to (loc?.longitude ?: 0.0)
            cachedLat = result.first
            cachedLon = result.second
            result
        }
    }
}
