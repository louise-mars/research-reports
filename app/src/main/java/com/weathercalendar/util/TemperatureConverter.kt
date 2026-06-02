package com.weathercalendar.util

import com.weathercalendar.data.repository.TemperatureUnit

/** Convert Celsius to the target unit. API always returns Celsius. */
fun Int.toUnit(unit: TemperatureUnit): Int = when (unit) {
    TemperatureUnit.CELSIUS -> this
    TemperatureUnit.FAHRENHEIT -> (this * 9.0 / 5.0 + 32).toInt()
}

/** Format temperature with unit symbol, e.g. "23°C" or "73°F" */
fun Int.formatTemp(unit: TemperatureUnit): String =
    "${toUnit(unit)}${unit.symbol}"

/** Format temperature without unit symbol, e.g. "23°" */
fun Int.formatTempShort(unit: TemperatureUnit): String =
    "${toUnit(unit)}°"
