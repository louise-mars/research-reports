package com.weathercalendar.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.weathercalendar.MainActivity

/**
 * 天气日历桌面 Widget — Jetpack Glance 实现。
 *
 * 布局：
 * ┌──────────────────────────────┐
 * │ ☀️  20°        北京          │
 * │ 晴             4月15日 周三   │
 * │                农历 廿八      │
 * └──────────────────────────────┘
 */
class WeatherWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WeatherWidgetDataProvider.fetchData(context)
        provideContent {
            GlanceTheme {
                WidgetContent(data)
            }
        }
    }
}

@Composable
private fun WidgetContent(data: WeatherWidgetData) {
    val white = ColorProvider(androidx.compose.ui.graphics.Color.White)
    val whiteAlpha = ColorProvider(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(data.gradientStart))
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧：天气图标 + 温度
            Text(
                text = data.weatherIcon,
                style = TextStyle(fontSize = 28.sp),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = "${data.temperature}°",
                style = TextStyle(
                    color = white,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )

            Spacer(GlanceModifier.width(16.dp))

            // 右侧：城市 + 日期 + 农历
            Column(
                modifier = GlanceModifier.defaultWeight(),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = data.cityName,
                    style = TextStyle(
                        color = white,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = data.dateText,
                    style = TextStyle(
                        color = whiteAlpha,
                        fontSize = 12.sp,
                    ),
                )
                if (data.lunarText.isNotEmpty()) {
                    Text(
                        text = data.lunarText,
                        style = TextStyle(
                            color = whiteAlpha,
                            fontSize = 11.sp,
                        ),
                    )
                }
            }
        }

        // 天气描述 + 下一个事件
        Spacer(GlanceModifier.height(4.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
        ) {
            Text(
                text = data.weatherLabel,
                style = TextStyle(
                    color = whiteAlpha,
                    fontSize = 13.sp,
                ),
            )
            if (data.nextEvent != null) {
                Spacer(GlanceModifier.width(12.dp))
                Text(
                    text = "📅 ${data.nextEvent}",
                    style = TextStyle(
                        color = whiteAlpha,
                        fontSize = 12.sp,
                    ),
                )
            }
        }
    }
}
