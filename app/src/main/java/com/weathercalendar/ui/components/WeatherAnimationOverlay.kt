package com.weathercalendar.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.weathercalendar.data.model.WeatherCondition
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 天气动态背景叠加层 — Canvas 粒子动画。
 * 叠在渐变背景之上、内容之下。
 */
@Composable
fun WeatherAnimationOverlay(
    condition: WeatherCondition,
    isDay: Boolean = true,
    modifier: Modifier = Modifier,
) {
    when (condition) {
        WeatherCondition.RAINY, WeatherCondition.DRIZZLE ->
            RainOverlay(
                modifier = modifier,
                intensity = if (condition == WeatherCondition.DRIZZLE) 40 else 80,
            )
        WeatherCondition.SNOWY ->
            SnowOverlay(modifier = modifier)
        WeatherCondition.CLOUDY, WeatherCondition.PARTLY_CLOUDY ->
            CloudOverlay(modifier = modifier)
        WeatherCondition.STORMY ->
            StormOverlay(modifier = modifier)
        WeatherCondition.SUNNY ->
            if (isDay) SunGlowOverlay(modifier = modifier)
        WeatherCondition.FOGGY ->
            FogOverlay(modifier = modifier)
    }
}

// ─────────────────────────────────────────────
// 雨天：细线粒子从上往下落
// ─────────────────────────────────────────────

private data class RainDrop(
    var x: Float,
    var y: Float,
    val speed: Float,
    val length: Float,
    val alpha: Float,
    val angle: Float, // 倾斜角度（弧度）
)

@Composable
private fun RainOverlay(modifier: Modifier = Modifier, intensity: Int = 80) {
    var frameTime by remember { mutableLongStateOf(0L) }

    val drops = remember {
        List(intensity) {
            RainDrop(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                speed = Random.nextFloat() * 0.012f + 0.008f,
                length = Random.nextFloat() * 0.04f + 0.02f,
                alpha = Random.nextFloat() * 0.3f + 0.1f,
                angle = Random.nextFloat() * 0.15f + 0.05f, // 轻微倾斜
            )
        }.toMutableList()
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(16L) // ~60fps
            frameTime++
            drops.forEach { drop ->
                drop.y += drop.speed
                drop.x += drop.angle * drop.speed * 0.5f
                if (drop.y > 1.1f) {
                    drop.y = -drop.length
                    drop.x = Random.nextFloat()
                }
                if (drop.x > 1.1f) {
                    drop.x = -0.05f
                }
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drops.forEach { drop ->
            val startX = drop.x * w
            val startY = drop.y * h
            val endX = startX + drop.angle * drop.length * h
            val endY = startY + drop.length * h
            drawLine(
                color = Color.White.copy(alpha = drop.alpha),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 1.5f,
                cap = StrokeCap.Round,
            )
        }
    }
}

// ─────────────────────────────────────────────
// 雪天：白色圆点缓慢飘落 + 左右摆动
// ─────────────────────────────────────────────

private data class Snowflake(
    var x: Float,
    var y: Float,
    val speed: Float,
    val radius: Float,
    val alpha: Float,
    val swayAmplitude: Float,
    val swayFrequency: Float,
    val phase: Float,
)

@Composable
private fun SnowOverlay(modifier: Modifier = Modifier) {
    var frameTime by remember { mutableLongStateOf(0L) }

    val flakes = remember {
        List(60) {
            Snowflake(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                speed = Random.nextFloat() * 0.003f + 0.001f,
                radius = Random.nextFloat() * 3f + 1.5f,
                alpha = Random.nextFloat() * 0.5f + 0.2f,
                swayAmplitude = Random.nextFloat() * 0.02f + 0.005f,
                swayFrequency = Random.nextFloat() * 0.05f + 0.02f,
                phase = Random.nextFloat() * 2f * PI.toFloat(),
            )
        }.toMutableList()
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(16L)
            frameTime++
            flakes.forEach { flake ->
                flake.y += flake.speed
                flake.x += sin(frameTime * flake.swayFrequency + flake.phase) * flake.swayAmplitude * 0.1f
                if (flake.y > 1.05f) {
                    flake.y = -0.02f
                    flake.x = Random.nextFloat()
                }
                if (flake.x < -0.05f) flake.x = 1.05f
                if (flake.x > 1.05f) flake.x = -0.05f
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        flakes.forEach { flake ->
            drawCircle(
                color = Color.White.copy(alpha = flake.alpha),
                radius = flake.radius,
                center = Offset(flake.x * w, flake.y * h),
            )
        }
    }
}

// ─────────────────────────────────────────────
// 多云：半透明椭圆从左向右缓慢漂移
// ─────────────────────────────────────────────

private data class Cloud(
    var x: Float,
    val y: Float,
    val speed: Float,
    val scaleX: Float,
    val scaleY: Float,
    val alpha: Float,
)

@Composable
private fun CloudOverlay(modifier: Modifier = Modifier) {
    var frameTime by remember { mutableLongStateOf(0L) }

    val clouds = remember {
        List(5) {
            Cloud(
                x = Random.nextFloat() * 1.4f - 0.2f,
                y = Random.nextFloat() * 0.4f + 0.05f,
                speed = Random.nextFloat() * 0.0004f + 0.0002f,
                scaleX = Random.nextFloat() * 0.25f + 0.15f,
                scaleY = Random.nextFloat() * 0.04f + 0.03f,
                alpha = Random.nextFloat() * 0.12f + 0.05f,
            )
        }.toMutableList()
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(16L)
            frameTime++
            clouds.forEach { cloud ->
                cloud.x += cloud.speed
                if (cloud.x > 1.3f) {
                    cloud.x = -cloud.scaleX - 0.1f
                }
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        clouds.forEach { cloud ->
            drawOval(
                color = Color.White.copy(alpha = cloud.alpha),
                topLeft = Offset(cloud.x * w, cloud.y * h),
                size = androidx.compose.ui.geometry.Size(cloud.scaleX * w, cloud.scaleY * h),
            )
        }
    }
}

// ─────────────────────────────────────────────
// 雷暴：雨 + 随机闪电（屏幕闪白）
// ─────────────────────────────────────────────

@Composable
private fun StormOverlay(modifier: Modifier = Modifier) {
    // 雨层
    RainOverlay(modifier = modifier, intensity = 100)

    // 闪电层：随机闪白
    var frameTime by remember { mutableLongStateOf(0L) }
    val flashAlpha = remember { mutableListOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            // 随机间隔 2-6 秒闪一次
            delay(Random.nextLong(2000, 6000))
            // 快速闪两下
            flashAlpha[0] = 0.3f
            delay(80)
            flashAlpha[0] = 0f
            delay(100)
            flashAlpha[0] = 0.15f
            delay(60)
            flashAlpha[0] = 0f
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        if (flashAlpha[0] > 0f) {
            drawRect(color = Color.White.copy(alpha = flashAlpha[0]))
        }
    }
}

// ─────────────────────────────────────────────
// 晴天：光晕缓慢脉动
// ─────────────────────────────────────────────

@Composable
private fun SunGlowOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "sunGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )
    val glowRadius by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowRadius",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width * 0.5f
        val centerY = size.height * 0.15f
        val radius = size.width * glowRadius
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFD54F).copy(alpha = glowAlpha),
                    Color.Transparent,
                ),
                center = Offset(centerX, centerY),
                radius = radius,
            ),
            radius = radius,
            center = Offset(centerX, centerY),
        )
    }
}

// ─────────────────────────────────────────────
// 雾天：缓慢移动的半透明雾带
// ─────────────────────────────────────────────

private data class FogBand(
    var x: Float,
    val y: Float,
    val speed: Float,
    val width: Float,
    val height: Float,
    val alpha: Float,
)

@Composable
private fun FogOverlay(modifier: Modifier = Modifier) {
    var frameTime by remember { mutableLongStateOf(0L) }

    val bands = remember {
        List(4) {
            FogBand(
                x = Random.nextFloat() * 1.5f - 0.5f,
                y = Random.nextFloat() * 0.6f + 0.2f,
                speed = Random.nextFloat() * 0.0003f + 0.0001f,
                width = Random.nextFloat() * 0.6f + 0.4f,
                height = Random.nextFloat() * 0.08f + 0.04f,
                alpha = Random.nextFloat() * 0.1f + 0.03f,
            )
        }.toMutableList()
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(16L)
            frameTime++
            bands.forEach { band ->
                band.x += band.speed
                if (band.x > 1.2f) band.x = -band.width - 0.1f
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        bands.forEach { band ->
            drawOval(
                color = Color.White.copy(alpha = band.alpha),
                topLeft = Offset(band.x * w, band.y * h),
                size = androidx.compose.ui.geometry.Size(band.width * w, band.height * h),
            )
        }
    }
}
