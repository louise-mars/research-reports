package com.weathercalendar.widget

/**
 * Widget 显示数据模型 — 轻量级，不依赖 Hilt。
 */
data class WeatherWidgetData(
    val temperature: Int = 0,
    val weatherIcon: String = "☀️",
    val weatherLabel: String = "晴",
    val cityName: String = "—",
    val dateText: String = "",
    val lunarText: String = "",
    val nextEvent: String? = null,
    val gradientStart: Long = 0xFF4FACFE,
    val gradientEnd: Long = 0xFF00F2FE,
)
