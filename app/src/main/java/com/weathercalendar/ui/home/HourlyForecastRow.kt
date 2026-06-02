package com.weathercalendar.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.weathercalendar.data.model.HourlyForecast
import com.weathercalendar.data.repository.TemperatureUnit
import com.weathercalendar.ui.components.GlassCard
import com.weathercalendar.ui.theme.WeatherCalendarTheme
import com.weathercalendar.util.formatTempShort
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun HourlyForecastRow(
    items: List<HourlyForecast>,
    textColor: Color,
    modifier: Modifier = Modifier,
    tempUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.time }) { item ->
            HourlyChip(item = item, textColor = textColor, tempUnit = tempUnit)
        }
    }
}

@Composable
private fun HourlyChip(
    item: HourlyForecast,
    textColor: Color,
    tempUnit: TemperatureUnit,
) {
    GlassCard(
        alpha = if (item.isNow) 0.35f else 0.10f,
        cornerRadius = 20.dp,
        elevation = if (item.isNow) 8.dp else 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 时间标签
            Text(
                text = if (item.isNow) "现在" else item.time.format(timeFormatter),
                color = if (item.isNow) textColor else textColor.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (item.isNow) FontWeight.Bold else FontWeight.Normal,
            )
            Spacer(Modifier.height(8.dp))
            // 天气图标
            Text(text = item.condition.icon, fontSize = 22.sp)
            Spacer(Modifier.height(8.dp))
            // 温度
            Text(
                text = item.temperature.formatTempShort(tempUnit),
                color = textColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (item.isNow) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0288D1)
@Composable
private fun HourlyForecastRowPreview() {
    WeatherCalendarTheme(dynamicColor = false) {
        HourlyForecastRow(items = MockData.hourlyForecast, textColor = Color.White)
    }
}
