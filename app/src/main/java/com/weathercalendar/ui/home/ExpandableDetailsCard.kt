package com.weathercalendar.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.weathercalendar.data.mock.MockData
import com.weathercalendar.data.model.WeatherDetails
import com.weathercalendar.ui.components.GlassCard
import com.weathercalendar.ui.theme.WeatherCalendarTheme

/**
 * 可折叠详情区 — 默认收起，展开显示湿度/风速/UV/空气质量。
 * 原则：默认隐藏所有"非决策信息"。
 */
@Composable
fun ExpandableDetailsCard(
    details: WeatherDetails,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                )
            ),
        alpha = 0.12f,
        cornerRadius = 16.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp),
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "详细信息",
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess
                    else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = textColor.copy(alpha = 0.5f),
                )
            }

            // 展开内容
            if (expanded) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    DetailItem("湿度", "${details.humidity}%", textColor)
                    DetailItem("风速", "${details.windSpeed} km/h", textColor)
                    DetailItem("UV", details.uvIndex, textColor)
                    DetailItem("空气", details.airQuality, textColor)
                }
            }
        }
    }
}

@Composable
private fun DetailItem(
    label: String,
    value: String,
    textColor: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = textColor,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = textColor.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0288D1)
@Composable
private fun ExpandableDetailsCardPreview() {
    WeatherCalendarTheme(dynamicColor = false) {
        ExpandableDetailsCard(
            details = MockData.weatherDetails,
            textColor = Color.White,
        )
    }
}
