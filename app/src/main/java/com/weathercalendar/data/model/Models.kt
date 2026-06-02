package com.weathercalendar.data.model

import java.time.LocalDate
import java.time.LocalTime

// ─────────────────────────────────────────────
// 天气相关
// ─────────────────────────────────────────────

/** 天气状态枚举，驱动图标、描述、渐变色 */
enum class WeatherCondition(
    val icon: String,
    val label: String,
    val tip: String,
) {
    SUNNY("☀️", "晴", "适合外出"),
    PARTLY_CLOUDY("🌤️", "多云转晴", "适合外出"),
    CLOUDY("☁️", "阴天", "可能需要外套"),
    FOGGY("🌫️", "雾", "注意能见度"),
    DRIZZLE("🌦️", "小雨", "建议带伞"),
    RAINY("🌧️", "雨", "记得带伞"),
    SNOWY("🌨️", "雪", "注意保暖"),
    STORMY("⛈️", "雷暴", "尽量待在室内"),
}

/** 当前天气 */
data class CurrentWeather(
    val temperature: Int,
    val feelsLike: Int,
    val condition: WeatherCondition,
    val isDay: Boolean = true,
)

/** 小时预报条目 */
data class HourlyForecast(
    val time: LocalTime,
    val temperature: Int,
    val condition: WeatherCondition,
    val isNow: Boolean = false,
)

/** 每日天气 */
data class DailyWeather(
    val date: LocalDate,
    val condition: WeatherCondition,
    val tempMin: Int,
    val tempMax: Int,
)

/** 详细气象指标 */
data class WeatherDetails(
    val humidity: Int,
    val windSpeed: Int,
    val uvIndex: String,
    val airQuality: String,
)

// ─────────────────────────────────────────────
// 日历相关
// ─────────────────────────────────────────────

/** 日历事件 */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val date: LocalDate = LocalDate.now(),  // 事件所属日期
    val time: LocalTime?,                   // null = 全天事件
    val color: Long = 0xFF4CAF50,
)

// ─────────────────────────────────────────────
// 融合模型
// ─────────────────────────────────────────────

/** 天气+日历融合：一天的完整信息 */
data class DayInfo(
    val date: LocalDate,
    val weather: DailyWeather,
    val events: List<CalendarEvent> = emptyList(),
    val lunarDate: String = "",         // 农历日期
    val lunarFestival: String? = null,  // 农历节日/节气（优先显示）
)

// ─────────────────────────────────────────────
// 城市
// ─────────────────────────────────────────────

data class City(
    val name: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val temperature: Int? = null,
    val condition: WeatherCondition? = null,
    val isCurrentLocation: Boolean = false,
)

// ─────────────────────────────────────────────
// 日历月视图
// ─────────────────────────────────────────────

data class CalendarDayCell(
    val date: LocalDate,
    val lunarText: String,
    val isLunarFestival: Boolean = false,
    val weatherIcon: String? = null,
    val hasEvents: Boolean = false,
)
