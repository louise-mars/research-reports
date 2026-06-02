package com.weathercalendar

import android.app.Application
import com.weathercalendar.widget.WeatherWidgetWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WeatherCalendarApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 注册 Widget 定时刷新（每小时）
        WeatherWidgetWorker.enqueue(this)
    }
}
