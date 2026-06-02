package com.weathercalendar.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.weathercalendar.data.model.WeatherCondition
import com.weathercalendar.data.remote.WeatherApi
import com.weathercalendar.data.remote.WeatherCodeMapper
import com.weathercalendar.util.LunarCalendar
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Widget 数据获取器 — 独立于 Hilt，直接创建网络客户端。
 * Widget 进程可能和 App 进程不同，不能依赖 DI。
 */
object WeatherWidgetDataProvider {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val weatherApi: WeatherApi by lazy {
        Retrofit.Builder()
            .baseUrl(WeatherApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(WeatherApi::class.java)
    }

    suspend fun fetchData(context: Context): WeatherWidgetData {
        val today = LocalDate.now()
        val dayOfWeek = today.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINESE)
        val dateText = today.format(DateTimeFormatter.ofPattern("M月d日")) + " $dayOfWeek"
        val lunarText = LunarCalendar.getDisplayText(today)

        // 读取用户保存的城市（从 DataStore）
        val cityInfo = readSavedCity(context)
        val cityName = cityInfo.first
        val lat = cityInfo.second
        val lon = cityInfo.third

        return try {
            val response = weatherApi.getForecast(lat, lon)
            val current = response.current ?: return fallbackData(dateText, lunarText, cityName)
            val condition = WeatherCodeMapper.fromWmoCode(current.weatherCode)
            val gradient = conditionToGradient(condition)

            WeatherWidgetData(
                temperature = current.temperature.toInt(),
                weatherIcon = condition.icon,
                weatherLabel = condition.label,
                cityName = cityName,
                dateText = dateText,
                lunarText = lunarText,
                nextEvent = null, // 日历事件需要权限，Widget 暂不读取
                gradientStart = gradient.first,
                gradientEnd = gradient.second,
            )
        } catch (_: Exception) {
            fallbackData(dateText, lunarText, cityName)
        }
    }

    private fun fallbackData(dateText: String, lunarText: String, cityName: String) =
        WeatherWidgetData(
            dateText = dateText,
            lunarText = lunarText,
            cityName = cityName,
            weatherLabel = "加载失败",
            weatherIcon = "—",
        )

    /**
     * 从 DataStore 读取用户选择的城市。
     * 不依赖 Hilt，直接读 SharedPreferences 文件。
     */
    private fun readSavedCity(context: Context): Triple<String, Double, Double> {
        return try {
            val prefs = context.getSharedPreferences("user_prefs_fallback", Context.MODE_PRIVATE)
            val name = prefs.getString("widget_city_name", null)
            val lat = prefs.getString("widget_city_lat", null)?.toDoubleOrNull()
            val lon = prefs.getString("widget_city_lon", null)?.toDoubleOrNull()
            if (name != null && lat != null && lon != null) {
                Triple(name, lat, lon)
            } else {
                // 默认北京
                Triple("北京", 39.9042, 116.4074)
            }
        } catch (_: Exception) {
            Triple("北京", 39.9042, 116.4074)
        }
    }

    /** 保存城市信息供 Widget 使用（App 端调用） */
    fun saveCityForWidget(context: Context, name: String, lat: Double, lon: Double) {
        context.getSharedPreferences("user_prefs_fallback", Context.MODE_PRIVATE)
            .edit()
            .putString("widget_city_name", name)
            .putString("widget_city_lat", lat.toString())
            .putString("widget_city_lon", lon.toString())
            .apply()
    }

    private fun conditionToGradient(condition: WeatherCondition): Pair<Long, Long> {
        return when (condition) {
            WeatherCondition.SUNNY, WeatherCondition.PARTLY_CLOUDY ->
                0xFF4FACFE to 0xFF00F2FE
            WeatherCondition.CLOUDY ->
                0xFF89A0B0 to 0xFF546E7A
            WeatherCondition.FOGGY ->
                0xFFB8C6D0 to 0xFF6E8898
            WeatherCondition.DRIZZLE, WeatherCondition.RAINY ->
                0xFF5F9EA0 to 0xFF2F4F4F
            WeatherCondition.SNOWY ->
                0xFFD4E4F1 to 0xFF8EAFC2
            WeatherCondition.STORMY ->
                0xFF3D4E5C to 0xFF1A252F
        }
    }
}
