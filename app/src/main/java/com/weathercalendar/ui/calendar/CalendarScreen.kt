package com.weathercalendar.ui.calendar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weathercalendar.data.mock.MockData
import com.weathercalendar.data.model.CalendarDayCell
import com.weathercalendar.data.model.CalendarEvent
import com.weathercalendar.data.model.WeatherCondition
import com.weathercalendar.ui.components.GlassCard
import com.weathercalendar.ui.components.WeatherAnimationOverlay
import com.weathercalendar.ui.theme.WeatherCalendarTheme
import com.weathercalendar.ui.theme.WeatherColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * 日历月视图 — iOS 级极简设计 + 天气联动动画。
 *
 * 核心体验：选中日期 → 天气变化 → 背景渐变平滑过渡 + 粒子动画切换。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    monthLabel: String,
    days: List<CalendarDayCell>,
    firstDayOfWeek: DayOfWeek,
    todayEvents: Map<LocalDate, List<CalendarEvent>>,
    todayWeather: Map<LocalDate, Pair<WeatherCondition, Pair<Int, Int>>>?,
    onBack: () -> Unit = {},
    onPrevMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
) {
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val sheetState = rememberModalBottomSheetState()

    // ── 天气联动：选中日期 → 天气 → 渐变色 ──
    val selectedCondition = selectedDate?.let { date ->
        todayWeather?.get(date)?.first
    } ?: WeatherCondition.SUNNY

    val gradient = WeatherColors.calendarGradientFor(selectedCondition)

    // 平滑过渡渐变色（500ms tween）
    val animatedStart by animateColorAsState(
        targetValue = gradient.start,
        animationSpec = tween(600),
        label = "calGradStart",
    )
    val animatedEnd by animateColorAsState(
        targetValue = gradient.end,
        animationSpec = tween(600),
        label = "calGradEnd",
    )
    val animatedBottom by animateColorAsState(
        targetValue = when (selectedCondition) {
            WeatherCondition.RAINY, WeatherCondition.DRIZZLE, WeatherCondition.STORMY ->
                Color(0xFF1A2332)
            WeatherCondition.SNOWY -> Color(0xFF2A3545)
            WeatherCondition.CLOUDY -> Color(0xFF1E2A38)
            else -> Color(0xFF0D1B2A)
        },
        animationSpec = tween(600),
        label = "calGradBottom",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(animatedStart, animatedEnd, animatedBottom),
                )
            ),
    ) {
        // ── 天气粒子动画层 ──
        WeatherAnimationOverlay(
            condition = selectedCondition,
            isDay = true,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // ── 顶部导航 ──
            CalendarTopBar(
                monthLabel = monthLabel,
                onBack = onBack,
                onPrevMonth = onPrevMonth,
                onNextMonth = onNextMonth,
            )

            Spacer(Modifier.height(16.dp))

            // ── 星期标题 ──
            WeekdayHeader()

            Spacer(Modifier.height(12.dp))

            // ── 日历网格 ──
            MonthGrid(
                days = days,
                firstDayOfWeek = firstDayOfWeek,
                today = LocalDate.now(),
                selectedDate = selectedDate,
                weatherMap = todayWeather,
                onDateClick = { date -> selectedDate = date },
            )
        }

        // ── 底部浮层：选中日期详情 ──
        if (selectedDate != null) {
            ModalBottomSheet(
                onDismissRequest = { selectedDate = null },
                sheetState = sheetState,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                containerColor = Color(0xFF1E2A3A).copy(alpha = 0.95f),
                contentColor = Color.White,
            ) {
                DayDetailContent(
                    date = selectedDate!!,
                    lunarText = days.find { it.date == selectedDate }?.lunarText ?: "",
                    isLunarFestival = days.find { it.date == selectedDate }?.isLunarFestival == true,
                    weatherCondition = todayWeather?.get(selectedDate)?.first,
                    tempRange = todayWeather?.get(selectedDate)?.second,
                    events = todayEvents[selectedDate] ?: emptyList(),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// 顶部导航栏
// ─────────────────────────────────────────────

@Composable
private fun CalendarTopBar(
    monthLabel: String,
    onBack: () -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = Color.White,
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onPrevMonth) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "上月", tint = Color.White)
        }
        Text(
            text = monthLabel,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = onNextMonth) {
            Icon(Icons.Default.ChevronRight, contentDescription = "下月", tint = Color.White)
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.size(48.dp))
    }
}

// ─────────────────────────────────────────────
// 星期标题行
// ─────────────────────────────────────────────

@Composable
private fun WeekdayHeader() {
    val weekdays = listOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        weekdays.forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.SHORT, Locale.CHINESE),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
    }
}

// ─────────────────────────────────────────────
// 月视图网格
// ─────────────────────────────────────────────

@Composable
private fun MonthGrid(
    days: List<CalendarDayCell>,
    firstDayOfWeek: DayOfWeek,
    today: LocalDate,
    selectedDate: LocalDate?,
    weatherMap: Map<LocalDate, Pair<WeatherCondition, Pair<Int, Int>>>?,
    onDateClick: (LocalDate) -> Unit,
) {
    if (days.isEmpty()) return

    val startOffset = (firstDayOfWeek.value - 1)
    val totalCells = startOffset + days.size
    val rows = (totalCells + 6) / 7

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (col in 0..6) {
                    val cellIndex = row * 7 + col - startOffset
                    if (cellIndex in days.indices) {
                        val cell = days[cellIndex]
                        val hasWeather = weatherMap?.containsKey(cell.date) == true
                        DayCellView(
                            cell = cell,
                            isToday = cell.date == today,
                            isSelected = cell.date == selectedDate,
                            hasWeatherData = hasWeather,
                            onClick = { onDateClick(cell.date) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// 日期格子 — iOS 风格 + 天气联动
// ─────────────────────────────────────────────

@Composable
private fun DayCellView(
    cell: CalendarDayCell,
    isToday: Boolean,
    isSelected: Boolean,
    hasWeatherData: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "cellScale",
    )

    val circleBackground = when {
        isToday && isSelected -> Color.White
        isToday -> Color.White
        isSelected -> Color(0xFF4FC3F7).copy(alpha = 0.3f)
        else -> Color.Transparent
    }

    val dayNumberColor = when {
        isToday -> Color(0xFF1A2332)
        else -> Color.White.copy(alpha = 0.9f)
    }

    val showFestivalHint = cell.isLunarFestival

    Column(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(circleBackground),
        ) {
            Text(
                text = "${cell.date.dayOfMonth}",
                fontSize = 16.sp,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = dayNumberColor,
            )
        }

        Spacer(Modifier.height(2.dp))

        when {
            showFestivalHint -> {
                Text(
                    text = cell.lunarText,
                    fontSize = 9.sp,
                    color = WeatherColors.LunarFestival,
                    maxLines = 1,
                    fontWeight = FontWeight.Medium,
                )
            }
            cell.weatherIcon != null -> {
                Text(text = cell.weatherIcon, fontSize = 11.sp)
            }
            else -> {
                Spacer(Modifier.height(13.dp))
            }
        }

        if (cell.hasEvents) {
            Spacer(Modifier.height(1.dp))
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4FC3F7)),
            )
        }
    }
}

// ─────────────────────────────────────────────
// 底部浮层 — 选中日期详情
// ─────────────────────────────────────────────

@Composable
private fun DayDetailContent(
    date: LocalDate,
    lunarText: String,
    isLunarFestival: Boolean,
    weatherCondition: WeatherCondition?,
    tempRange: Pair<Int, Int>?,
    events: List<CalendarEvent>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = date.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINESE)),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINESE),
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.6f),
            )
        }

        if (lunarText.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "农历 $lunarText",
                fontSize = 14.sp,
                color = if (isLunarFestival) WeatherColors.LunarFestival else Color.White.copy(alpha = 0.5f),
                fontWeight = if (isLunarFestival) FontWeight.Medium else FontWeight.Normal,
            )
        }

        if (weatherCondition != null && tempRange != null) {
            Spacer(Modifier.height(16.dp))
            GlassCard(
                alpha = 0.12f,
                cornerRadius = 16.dp,
                elevation = 4.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = weatherCondition.icon, fontSize = 32.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "${weatherCondition.label}  ${tempRange.first}° ~ ${tempRange.second}°",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                        )
                        Text(
                            text = weatherCondition.tip,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        Spacer(Modifier.height(12.dp))

        if (events.isNotEmpty()) {
            Text(
                text = "日程",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.4f),
            )
            Spacer(Modifier.height(8.dp))
            events.forEach { event ->
                Row(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(event.color)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = event.time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "全天",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = event.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            Text(
                text = "暂无日程",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.3f),
            )
        }
    }
}

// ─────────────────────────────────────────────
// Preview
// ─────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun CalendarScreenPreview() {
    val mockDays = MockData.calendarMonth()
    val today = LocalDate.of(2026, 4, 16)
    val events = mapOf(
        today to listOf(
            CalendarEvent(1, "Team Meeting", today, java.time.LocalTime.of(10, 0), 0xFF2196F3),
            CalendarEvent(2, "Gym", today, java.time.LocalTime.of(18, 0), 0xFF4CAF50),
        ),
    )
    val weather = mapOf(
        today to (WeatherCondition.SUNNY to (8 to 23)),
        today.plusDays(1) to (WeatherCondition.RAINY to (6 to 12)),
    )

    WeatherCalendarTheme(dynamicColor = false) {
        CalendarScreen(
            monthLabel = "2026年4月",
            days = mockDays,
            firstDayOfWeek = DayOfWeek.WEDNESDAY,
            todayEvents = events,
            todayWeather = weather,
        )
    }
}
