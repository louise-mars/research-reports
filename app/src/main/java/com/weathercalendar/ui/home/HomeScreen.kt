package com.weathercalendar.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weathercalendar.data.mock.MockData
import com.weathercalendar.data.model.CurrentWeather
import com.weathercalendar.data.model.DayInfo
import com.weathercalendar.data.model.HourlyForecast
import com.weathercalendar.data.model.WeatherCondition
import com.weathercalendar.data.model.WeatherDetails
import com.weathercalendar.data.repository.TemperatureUnit
import com.weathercalendar.ui.components.WeatherAnimationOverlay
import com.weathercalendar.ui.theme.WeatherCalendarTheme
import com.weathercalendar.ui.theme.WeatherColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    cityName: String,
    dateText: String,
    lunarText: String,
    currentWeather: CurrentWeather,
    hourlyForecast: List<HourlyForecast>,
    threeDays: List<DayInfo>,
    weatherDetails: WeatherDetails,
    isLoading: Boolean = false,
    error: String? = null,
    tempUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    onCityClick: () -> Unit = {},
    onCalendarClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    // 当前聚焦的天（用于 pager 和渐变色）
    val pageCount = threeDays.size.coerceIn(1, 3)
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val focusedDay = threeDays.getOrNull(pagerState.currentPage)

    // 渐变色跟随聚焦天的天气动画过渡
    val focusedCondition = focusedDay?.weather?.condition ?: currentWeather.condition
    val gradient = WeatherColors.gradientFor(focusedCondition, currentWeather.isDay)
    val animatedStart by animateColorAsState(gradient.start, tween(500), label = "gradStart")
    val animatedEnd by animateColorAsState(gradient.end, tween(500), label = "gradEnd")
    val textColor = WeatherColors.textColorOn(focusedCondition, currentWeather.isDay)

    // 融合卡展开索引跟随 pager
    var fusionExpandedIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(pagerState.currentPage) {
        fusionExpandedIndex = pagerState.currentPage
    }

    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = onRefresh,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(animatedStart, animatedEnd))),
        ) {
            // 天气动态粒子动画层
            WeatherAnimationOverlay(
                condition = focusedCondition,
                isDay = currentWeather.isDay,
            )

            when {
                isLoading && hourlyForecast.isEmpty() -> {
                    LoadingContent(textColor = textColor)
                }
                error != null && hourlyForecast.isEmpty() -> {
                    ErrorContent(error = error, textColor = textColor, onRetry = onRefresh)
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Spacer(Modifier.height(16.dp))

                        HeaderBar(
                            cityName = cityName,
                            dateText = dateText,
                            lunarText = lunarText,
                            textColor = textColor,
                            onCityClick = onCityClick,
                            onCalendarClick = onCalendarClick,
                            onSettingsClick = onSettingsClick,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )

                        Spacer(Modifier.height(20.dp))

                        // ── Hero Card: 左右滑切换日期 ──
                        if (threeDays.isNotEmpty()) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxWidth(),
                            ) { page ->
                                val day = threeDays[page]
                                CurrentWeatherCard(
                                    weather = if (page == 0) currentWeather else CurrentWeather(
                                        temperature = (day.weather.tempMax + day.weather.tempMin) / 2,
                                        feelsLike = (day.weather.tempMax + day.weather.tempMin) / 2,
                                        condition = day.weather.condition,
                                        isDay = currentWeather.isDay,
                                    ),
                                    textColor = textColor,
                                    tempUnit = tempUnit,
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                )
                            }

                            // 页面指示器
                            Spacer(Modifier.height(12.dp))
                            PageIndicator(
                                pageCount = pageCount,
                                currentPage = pagerState.currentPage,
                                textColor = textColor,
                            )
                        } else {
                            CurrentWeatherCard(
                                weather = currentWeather,
                                textColor = textColor,
                                tempUnit = tempUnit,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }

                        Spacer(Modifier.height(24.dp))

                        if (hourlyForecast.isNotEmpty()) {
                            SectionLabel("小时预报", textColor)
                            Spacer(Modifier.height(12.dp))
                            HourlyForecastRow(
                                items = hourlyForecast,
                                textColor = textColor,
                                tempUnit = tempUnit,
                            )
                            Spacer(Modifier.height(24.dp))
                        }

                        if (threeDays.isNotEmpty()) {
                            SectionLabel("近三天", textColor)
                            Spacer(Modifier.height(12.dp))
                            DailyWeatherCalendarCard(
                                days = threeDays,
                                textColor = textColor,
                                expandedIndex = fusionExpandedIndex,
                                onExpandedChange = { fusionExpandedIndex = it },
                                tempUnit = tempUnit,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                            Spacer(Modifier.height(24.dp))
                        }

                        ExpandableDetailsCard(
                            details = weatherDetails,
                            textColor = textColor,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )

                        Spacer(Modifier.height(32.dp))
                        Spacer(Modifier.navigationBarsPadding())
                    }
                }
            }
        }
    }
}

@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int, textColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (index == currentPage) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == currentPage) textColor
                        else textColor.copy(alpha = 0.3f)
                    ),
            )
        }
    }
}

@Composable
private fun LoadingContent(textColor: Color) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = textColor, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("正在获取天气数据...", color = textColor.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ErrorContent(error: String, textColor: Color, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "😔", fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))
        Text(error, color = textColor, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("重试")
        }
    }
}

@Composable
private fun SectionLabel(title: String, textColor: Color) {
    Text(
        text = title, color = textColor,
        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

// ── Previews ──

@Preview(showBackground = true, showSystemUi = true, name = "HomeScreen — 晴天")
@Composable
private fun HomeScreenPreview() {
    WeatherCalendarTheme(dynamicColor = false) {
        HomeScreen(
            cityName = "北京", dateText = "4月16日 星期三", lunarText = "三月十九",
            currentWeather = MockData.currentWeather, hourlyForecast = MockData.hourlyForecast,
            threeDays = MockData.threeDays, weatherDetails = MockData.weatherDetails,
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "HomeScreen — Loading")
@Composable
private fun HomeScreenLoadingPreview() {
    WeatherCalendarTheme(dynamicColor = false) {
        HomeScreen(
            cityName = "定位中...", dateText = "", lunarText = "",
            currentWeather = CurrentWeather(0, 0, WeatherCondition.SUNNY),
            hourlyForecast = emptyList(), threeDays = emptyList(),
            weatherDetails = WeatherDetails(0, 0, "—", "—"), isLoading = true,
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "HomeScreen — Error")
@Composable
private fun HomeScreenErrorPreview() {
    WeatherCalendarTheme(dynamicColor = false) {
        HomeScreen(
            cityName = "北京", dateText = "4月16日 星期三", lunarText = "三月十九",
            currentWeather = CurrentWeather(0, 0, WeatherCondition.SUNNY),
            hourlyForecast = emptyList(), threeDays = emptyList(),
            weatherDetails = WeatherDetails(0, 0, "—", "—"),
            error = "网络连接失败，请检查网络后重试",
        )
    }
}
