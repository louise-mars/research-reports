package com.weathercalendar.ui.permission

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weathercalendar.ui.theme.WeatherCalendarTheme
import com.weathercalendar.ui.theme.WeatherColors

/**
 * 权限请求页面 — 首次启动时展示。
 *
 * 设计原则：
 * - 先解释为什么需要权限，再请求
 * - 定位权限必须，日历权限可选
 * - 风格与 app 一致（天气渐变背景 + 毛玻璃）
 */
@Composable
fun PermissionScreen(
    onAllGranted: () -> Unit,
    onSkipLocation: () -> Unit = onAllGranted,
) {
    val context = LocalContext.current
    var locationGranted by remember { mutableStateOf(false) }
    var calendarGranted by remember { mutableStateOf(false) }
    var locationDeniedPermanently by remember { mutableStateOf(false) }

    // 日历权限请求
    val calendarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        calendarGranted = granted
    }

    // 定位权限请求（先粗略再精确）
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        locationGranted = fine || coarse
        if (!locationGranted) {
            // 如果两个都被拒绝，可能是永久拒绝
            locationDeniedPermanently = true
        }
    }

    // 两个权限都拿到后自动进入主页
    LaunchedEffect(locationGranted) {
        if (locationGranted) {
            onAllGranted()
        }
    }

    val gradient = WeatherColors.gradientFor(
        com.weathercalendar.data.model.WeatherCondition.SUNNY, isDay = true
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(gradient.start, gradient.end)))
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // App icon
            Text(text = "🌤️", fontSize = 64.sp)

            Spacer(Modifier.height(16.dp))

            Text(
                text = "天气日历",
                color = Color.White,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp),
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "需要以下权限来为你提供服务",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(40.dp))

            // 权限卡片
            PermissionItem(
                icon = Icons.Default.LocationOn,
                title = "位置信息",
                description = "获取你所在城市的天气数据",
                isGranted = locationGranted,
                isRequired = true,
            )

            Spacer(Modifier.height(12.dp))

            PermissionItem(
                icon = Icons.Default.CalendarMonth,
                title = "日历读取",
                description = "在天气卡片中显示你的日程",
                isGranted = calendarGranted,
                isRequired = false,
            )

            Spacer(Modifier.height(40.dp))

            // 操作按钮
            if (!locationGranted) {
                if (locationDeniedPermanently) {
                    // 被永久拒绝，引导去设置
                    Text(
                        text = "定位权限已被拒绝，请在系统设置中开启",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White,
                        ),
                    ) {
                        Text("打开系统设置", modifier = Modifier.padding(vertical = 4.dp))
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { onSkipLocation() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White.copy(alpha = 0.7f),
                        ),
                    ) {
                        Text("跳过，使用默认城市", modifier = Modifier.padding(vertical = 4.dp))
                    }
                } else {
                    Button(
                        onClick = {
                            locationLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = gradient.end,
                        ),
                    ) {
                        Text(
                            "授权定位",
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }

            if (locationGranted && !calendarGranted) {
                Button(
                    onClick = {
                        calendarLauncher.launch(Manifest.permission.READ_CALENDAR)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = gradient.end,
                    ),
                ) {
                    Text(
                        "授权日历（可选）",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { onAllGranted() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White,
                    ),
                ) {
                    Text("跳过，稍后设置", modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    isRequired: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(16.dp),
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isGranted) Color(0xFF66BB6A) else Color.White,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                if (isRequired) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "必需",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (isGranted) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "已授权",
                tint = Color(0xFF66BB6A),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun PermissionScreenPreview() {
    WeatherCalendarTheme(dynamicColor = false) {
        PermissionScreen(onAllGranted = {})
    }
}
