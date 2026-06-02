package com.weathercalendar.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * 定时刷新 Widget 数据 — 每小时一次。
 */
class WeatherWidgetWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // 刷新所有 Widget 实例
            val manager = GlanceAppWidgetManager(context)
            val widget = WeatherWidget()
            val glanceIds = manager.getGlanceIds(WeatherWidget::class.java)
            glanceIds.forEach { id ->
                widget.update(context, id)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "weather_widget_refresh"

        /** 注册定时刷新任务（App 启动时调用） */
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeatherWidgetWorker>(
                1, TimeUnit.HOURS,
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
