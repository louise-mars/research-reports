package com.weathercalendar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 毛玻璃风格卡片 — 半透明白底 + 轻阴影，浮在天气渐变背景之上。
 *
 * @param alpha 白色透明度
 * @param cornerRadius 圆角半径（统一 20dp）
 * @param elevation 阴影高度，让卡片"浮起来"
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    alpha: Float = 0.15f,
    cornerRadius: Dp = 20.dp,
    elevation: Dp = 8.dp,
    isDark: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val baseColor = if (isDark) Color.Black else Color.White
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.1f),
                spotColor = Color.Black.copy(alpha = 0.08f),
            )
            .clip(shape)
            .background(baseColor.copy(alpha = alpha)),
        content = content,
    )
}
