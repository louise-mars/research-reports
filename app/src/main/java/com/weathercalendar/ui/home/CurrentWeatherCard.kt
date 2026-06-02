package com.weathercalendar.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weathercalendar.data.mock.MockData
import com.weathercalendar.data.model.CurrentWeather
import com.weathercalendar.data.repository.TemperatureUnit
import com.weathercalendar.ui.components.GlassCard
import com.weathercalendar.ui.theme.WeatherCalendarTheme
import com.weathercalendar.util.formatTempShort

@Composable
fun CurrentWeatherCard(
    weather: CurrentWeather,
    textColor: Color,
    modifier: Modifier = Modifier,
    tempUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        alpha = 0.18f,
        cornerRadius = 24.dp,
        elevation = 12.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 36.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 天气图标
            Text(text = weather.condition.icon, fontSize = 56.sp)

            Spacer(Modifier.height(8.dp))

            // 温度 — 大号 Light 字重 + 字间距，视觉抓眼
            Text(
                text = weather.temperature.formatTempShort(tempUnit),
                color = textColor,
                fontSize = 72.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 2.sp,
            )

            Spacer(Modifier.height(4.dp))

            // 天气描述
            Text(
                text = weather.condition.label,
                color = textColor,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )

            Spacer(Modifier.height(16.dp))

            // 体感温度 + 建议
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "体感 ${weather.feelsLike.formatTempShort(tempUnit)}",
                    color = textColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = weather.condition.tip,
                    color = textColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0288D1)
@Composable
private fun CurrentWeatherCardPreview() {
    WeatherCalendarTheme(dynamicColor = false) {
        CurrentWeatherCard(weather = MockData.currentWeather, textColor = Color.White)
    }
}
