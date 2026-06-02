package com.weathercalendar.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weathercalendar.data.mock.MockData
import com.weathercalendar.data.model.CalendarEvent
import com.weathercalendar.data.model.DayInfo
import com.weathercalendar.data.repository.TemperatureUnit
import com.weathercalendar.ui.components.GlassCard
import com.weathercalendar.ui.theme.WeatherCalendarTheme
import com.weathercalendar.util.formatTempShort
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DailyWeatherCalendarCard(
    days: List<DayInfo>,
    textColor: Color,
    modifier: Modifier = Modifier,
    expandedIndex: Int = 0,
    onExpandedChange: (Int) -> Unit = {},
    tempUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        alpha = 0.12f,
        cornerRadius = 20.dp,
        elevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            days.forEachIndexed { index, dayInfo ->
                DailyItemRow(
                    dayInfo = dayInfo,
                    textColor = textColor,
                    isExpanded = index == expandedIndex,
                    onToggle = {
                        onExpandedChange(if (expandedIndex == index) -1 else index)
                    },
                    tempUnit = tempUnit,
                )
                if (index < days.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = textColor.copy(alpha = 0.08f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyItemRow(
    dayInfo: DayInfo,
    textColor: Color,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    tempUnit: TemperatureUnit,
) {
    val today = LocalDate.now()
    val isToday = dayInfo.date == today
    val isTomorrow = dayInfo.date == today.plusDays(1)
    val hasEvents = dayInfo.events.isNotEmpty()

    val dayLabel = when {
        isToday -> "今天"
        isTomorrow -> "明天"
        else -> dayInfo.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINESE)
    }
    val dateLabel = dayInfo.date.format(DateTimeFormatter.ofPattern("M/d"))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                )
            )
            .then(if (hasEvents) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = dayLabel,
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = dateLabel,
                    color = textColor.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = dayInfo.weather.condition.icon, fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${dayInfo.weather.tempMin.formatTempShort(tempUnit)} ~ ${dayInfo.weather.tempMax.formatTempShort(tempUnit)}",
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.width(12.dp))
            if (hasEvents) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🗓 ${dayInfo.events.size}",
                        color = textColor.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = textColor.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        if (isExpanded && hasEvents) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = textColor.copy(alpha = 0.1f))
            Spacer(Modifier.height(8.dp))
            dayInfo.events.forEach { event ->
                EventRow(event = event, textColor = textColor)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun EventRow(event: CalendarEvent, textColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(event.color)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = event.time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "全天",
            color = textColor.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = event.title,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0288D1)
@Composable
private fun DailyWeatherCalendarCardPreview() {
    WeatherCalendarTheme(dynamicColor = false) {
        DailyWeatherCalendarCard(days = MockData.threeDays, textColor = Color.White)
    }
}
