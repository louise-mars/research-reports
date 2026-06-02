package com.weathercalendar.data.repository

import android.content.ContentResolver
import android.content.Context
import android.provider.CalendarContract
import com.weathercalendar.data.model.CalendarEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val contentResolver: ContentResolver
        get() = context.contentResolver

    /**
     * 读取指定日期范围内的系统日历事件，按日期分组返回。
     */
    suspend fun getEventsGroupedByDate(
        startDate: LocalDate,
        endDate: LocalDate,
    ): Map<LocalDate, List<CalendarEvent>> = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val startMillis = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.CALENDAR_COLOR,
        )

        val selection =
            "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} < ?"
        val selectionArgs = arrayOf(startMillis.toString(), endMillis.toString())
        val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

        val events = mutableListOf<CalendarEvent>()

        val cursor = try {
            contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection, selection, selectionArgs, sortOrder,
            )
        } catch (_: SecurityException) {
            null
        }

        cursor?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(CalendarContract.Events._ID)
            val titleIdx = c.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
            val startIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
            val allDayIdx = c.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)
            val colorIdx = c.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_COLOR)

            while (c.moveToNext()) {
                val startMs = c.getLong(startIdx)
                val isAllDay = c.getInt(allDayIdx) == 1
                val startInstant = Instant.ofEpochMilli(startMs)
                val zonedStart = startInstant.atZone(zone)
                val eventDate = zonedStart.toLocalDate()
                val eventTime = if (isAllDay) null else zonedStart.toLocalTime()

                val rawColor = c.getInt(colorIdx)
                val colorLong = 0xFF000000L or (rawColor.toLong() and 0xFFFFFFL)

                events.add(
                    CalendarEvent(
                        id = c.getLong(idIdx),
                        title = c.getString(titleIdx) ?: "(无标题)",
                        date = eventDate,
                        time = eventTime,
                        color = colorLong,
                    )
                )
            }
        }

        events.groupBy { it.date }
    }
}
