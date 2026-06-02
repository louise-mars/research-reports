package com.weathercalendar.data.remote

import com.weathercalendar.data.model.WeatherCondition

/**
 * 将 WMO 天气代码映射到 UI 层的 WeatherCondition。
 * 参考: https://open-meteo.com/en/docs#weathervariables
 */
object WeatherCodeMapper {

    fun fromWmoCode(code: Int): WeatherCondition = when (code) {
        0 -> WeatherCondition.SUNNY
        1 -> WeatherCondition.SUNNY
        2 -> WeatherCondition.PARTLY_CLOUDY
        3 -> WeatherCondition.CLOUDY
        45, 48 -> WeatherCondition.FOGGY
        51, 53, 55 -> WeatherCondition.DRIZZLE
        56, 57 -> WeatherCondition.DRIZZLE
        61, 63, 65 -> WeatherCondition.RAINY
        66, 67 -> WeatherCondition.RAINY
        71, 73, 75 -> WeatherCondition.SNOWY
        77 -> WeatherCondition.SNOWY
        80, 81, 82 -> WeatherCondition.RAINY
        85, 86 -> WeatherCondition.SNOWY
        95 -> WeatherCondition.STORMY
        96, 99 -> WeatherCondition.STORMY
        else -> WeatherCondition.CLOUDY
    }
}
