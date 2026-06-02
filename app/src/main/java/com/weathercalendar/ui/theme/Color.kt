package com.weathercalendar.ui.theme

import androidx.compose.ui.graphics.Color
import com.weathercalendar.data.model.WeatherCondition

/**
 * 天气动态渐变配色 — 用于全页背景。
 * 高饱和度渐变，提升视觉冲击力。
 */
object WeatherColors {

    data class GradientPair(val start: Color, val end: Color)

    // 晴天 — 鲜亮蓝
    private val Sunny = GradientPair(Color(0xFF4FACFE), Color(0xFF00F2FE))
    // 多云 — 柔灰蓝
    private val Cloudy = GradientPair(Color(0xFF89A0B0), Color(0xFF546E7A))
    // 雨天 — 深青
    private val Rainy = GradientPair(Color(0xFF5F9EA0), Color(0xFF2F4F4F))
    // 雪天 — 冰蓝白
    private val Snowy = GradientPair(Color(0xFFE8F0FE), Color(0xFFB8D4E8))
    // 夜间 — 深蓝紫
    private val Night = GradientPair(Color(0xFF1A237E), Color(0xFF0D47A1))
    // 雷暴 — 暗灰
    private val Storm = GradientPair(Color(0xFF424242), Color(0xFF212121))
    // 雾 — 柔灰
    private val Foggy = GradientPair(Color(0xFFCFD8DC), Color(0xFF90A4AE))

    fun gradientFor(condition: WeatherCondition, isDay: Boolean = true): GradientPair {
        if (!isDay) return Night
        return when (condition) {
            WeatherCondition.SUNNY -> Sunny
            WeatherCondition.PARTLY_CLOUDY -> Sunny
            WeatherCondition.CLOUDY -> Cloudy
            WeatherCondition.FOGGY -> Foggy
            WeatherCondition.DRIZZLE, WeatherCondition.RAINY -> Rainy
            WeatherCondition.SNOWY -> Snowy
            WeatherCondition.STORMY -> Storm
        }
    }

    /** 雪天背景偏白，需要深色文字；其余用白色 */
    fun textColorOn(condition: WeatherCondition, isDay: Boolean = true): Color {
        if (!isDay) return Color.White
        return when (condition) {
            WeatherCondition.SNOWY -> Color(0xFF1A237E)
            else -> Color.White
        }
    }

    /** 日历页用：天气联动渐变（比首页稍柔和但仍有辨识度） */
    fun calendarGradientFor(condition: WeatherCondition, isDay: Boolean = true): GradientPair {
        if (!isDay) return GradientPair(Color(0xFF141E30), Color(0xFF243B55))
        return when (condition) {
            WeatherCondition.SUNNY, WeatherCondition.PARTLY_CLOUDY ->
                GradientPair(Color(0xFF4FACFE), Color(0xFF00C6FB))
            WeatherCondition.CLOUDY ->
                GradientPair(Color(0xFFBDC3C7), Color(0xFF2C3E50))
            WeatherCondition.FOGGY ->
                GradientPair(Color(0xFFB8C6D0), Color(0xFF6E8898))
            WeatherCondition.DRIZZLE, WeatherCondition.RAINY ->
                GradientPair(Color(0xFF5F9EA0), Color(0xFF2F4F4F))
            WeatherCondition.SNOWY ->
                GradientPair(Color(0xFFD4E4F1), Color(0xFF8EAFC2))
            WeatherCondition.STORMY ->
                GradientPair(Color(0xFF3D4E5C), Color(0xFF1A252F))
        }
    }

    // 农历节日红色
    val LunarFestival = Color(0xFFE53935)
    // 节气绿色
    val SolarTerm = Color(0xFF43A047)
}
