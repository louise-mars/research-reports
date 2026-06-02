package com.weathercalendar.data.mock

import com.weathercalendar.data.model.*
import java.time.LocalDate
import java.time.LocalTime

/**
 * Mock 数据，用于 UI 原型预览和开发阶段。
 */
object MockData {

    val currentWeather = CurrentWeather(
        temperature = 23,
        feelsLike = 21,
        condition = WeatherCondition.SUNNY,
        isDay = true,
    )

    val hourlyForecast = listOf(
        HourlyForecast(LocalTime.of(8, 0), 12, WeatherCondition.SUNNY),
        HourlyForecast(LocalTime.of(9, 0), 13, WeatherCondition.SUNNY),
        HourlyForecast(LocalTime.of(10, 0), 15, WeatherCondition.SUNNY, isNow = true),
        HourlyForecast(LocalTime.of(11, 0), 17, WeatherCondition.PARTLY_CLOUDY),
        HourlyForecast(LocalTime.of(12, 0), 19, WeatherCondition.PARTLY_CLOUDY),
        HourlyForecast(LocalTime.of(13, 0), 21, WeatherCondition.CLOUDY),
        HourlyForecast(LocalTime.of(14, 0), 23, WeatherCondition.CLOUDY),
        HourlyForecast(LocalTime.of(15, 0), 22, WeatherCondition.PARTLY_CLOUDY),
        HourlyForecast(LocalTime.of(16, 0), 20, WeatherCondition.SUNNY),
        HourlyForecast(LocalTime.of(17, 0), 18, WeatherCondition.SUNNY),
        HourlyForecast(LocalTime.of(18, 0), 16, WeatherCondition.SUNNY),
        HourlyForecast(LocalTime.of(19, 0), 14, WeatherCondition.SUNNY),
    )

    private val today = LocalDate.of(2026, 4, 16)

    val threeDays = listOf(
        DayInfo(
            date = today,
            weather = DailyWeather(today, WeatherCondition.SUNNY, 8, 23),
            events = listOf(
                CalendarEvent(1, "Team Meeting", today, LocalTime.of(10, 0), 0xFF2196F3),
                CalendarEvent(2, "Gym", today, LocalTime.of(18, 0), 0xFF4CAF50),
            ),
            lunarDate = "三月十九",
        ),
        DayInfo(
            date = today.plusDays(1),
            weather = DailyWeather(today.plusDays(1), WeatherCondition.RAINY, 6, 12),
            events = listOf(
                CalendarEvent(3, "Dentist Appointment", today.plusDays(1), LocalTime.of(14, 30), 0xFFFF9800),
            ),
            lunarDate = "三月二十",
        ),
        DayInfo(
            date = today.plusDays(2),
            weather = DailyWeather(today.plusDays(2), WeatherCondition.PARTLY_CLOUDY, 9, 16),
            events = emptyList(),
            lunarDate = "三月廿一",
        ),
    )

    val weatherDetails = WeatherDetails(
        humidity = 65,
        windSpeed = 12,
        uvIndex = "中等",
        airQuality = "良",
    )

    val cities = listOf(
        City(name = "北京", latitude = 39.9042, longitude = 116.4074, temperature = 23, condition = WeatherCondition.SUNNY, isCurrentLocation = true),
        City(name = "上海", latitude = 31.2304, longitude = 121.4737, temperature = 19, condition = WeatherCondition.PARTLY_CLOUDY),
        City(name = "深圳", latitude = 22.5431, longitude = 114.0579, temperature = 26, condition = WeatherCondition.CLOUDY),
        City(name = "东京", latitude = 35.6762, longitude = 139.6503, temperature = 15, condition = WeatherCondition.RAINY),
    )

    // 日历月视图 mock（4月部分日期）
    fun calendarMonth(): List<CalendarDayCell> {
        val month = today.withDayOfMonth(1)
        val lunarDays = listOf(
            "初三", "初四", "初五", "清明", "初七", "初八", "初九",
            "初十", "十一", "十二", "十三", "十四", "十五", "十六",
            "十七", "十八", "十九", "二十", "廿一", "廿二", "廿三",
            "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十",
            "四月", "初二",
        )
        val weatherDays = mapOf(
            16 to "☀️", 17 to "🌧️", 18 to "🌤️", 19 to "☁️",
            20 to "☀️", 21 to "🌧️", 22 to "☀️",
        )
        val eventDays = setOf(16, 17, 20, 25)

        return (1..30).map { day ->
            val date = month.withDayOfMonth(day)
            CalendarDayCell(
                date = date,
                lunarText = lunarDays.getOrElse(day - 1) { "" },
                isLunarFestival = lunarDays.getOrElse(day - 1) { "" } == "清明",
                weatherIcon = weatherDays[day],
                hasEvents = day in eventDays,
            )
        }
    }
}
