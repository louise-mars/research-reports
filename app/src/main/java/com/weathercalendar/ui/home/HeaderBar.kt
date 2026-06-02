package com.weathercalendar.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.weathercalendar.ui.theme.WeatherCalendarTheme

/**
 * 首页顶部栏：城市选择 + 日期农历 + 日历/设置按钮
 */
@Composable
fun HeaderBar(
    cityName: String,
    dateText: String,
    lunarText: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    onCityClick: () -> Unit = {},
    onCalendarClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        // 左侧：城市 + 日期
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onCityClick),
            ) {
                Text(
                    text = cityName,
                    color = textColor,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "切换城市",
                    tint = textColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
            }
            Row {
                Text(
                    text = dateText,
                    color = textColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = " · $lunarText",
                    color = textColor.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // 右侧：日历 + 设置
        Row {
            IconButton(onClick = onCalendarClick) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = "日历",
                    tint = textColor,
                    modifier = Modifier.size(24.dp),
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "设置",
                    tint = textColor,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0288D1)
@Composable
private fun HeaderBarPreview() {
    WeatherCalendarTheme(dynamicColor = false) {
        HeaderBar(
            cityName = "北京",
            dateText = "4月16日 星期三",
            lunarText = "三月十九",
            textColor = Color.White,
        )
    }
}
