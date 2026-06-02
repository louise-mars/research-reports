package com.weathercalendar.util

import java.time.LocalDate

/**
 * 农历计算工具 — 查表法。
 * 覆盖 1900-2100 年，包含农历日期、节日、节气。
 */
object LunarCalendar {

    data class LunarDate(
        val year: Int,
        val month: Int,
        val day: Int,
        val isLeapMonth: Boolean,
    ) {
        /** 农历日期文字（如"三月十九"） */
        val dayText: String get() = lunarDayName(day)

        /** 农历月份文字（如"三月"） */
        val monthText: String get() = lunarMonthName(month)

        /** 完整农历文字（如"三月十九"） */
        val fullText: String get() = "$monthText$dayText"
    }

    /**
     * 获取农历日期的显示文字。
     * 优先级：节日 > 节气 > 农历日（初一显示月份）
     */
    fun getDisplayText(date: LocalDate): String {
        val festival = getLunarFestival(date)
        if (festival != null) return festival

        val solarTerm = getSolarTerm(date)
        if (solarTerm != null) return solarTerm

        val lunar = toLunar(date)
        return if (lunar.day == 1) lunar.monthText else lunar.dayText
    }

    /** 是否为节日或节气（用于高亮显示） */
    fun isFestival(date: LocalDate): Boolean = getLunarFestival(date) != null

    /** 是否为节气 */
    fun isSolarTerm(date: LocalDate): Boolean = getSolarTerm(date) != null

    // ─────────────────────────────────────────
    // 农历数据表（1900-2100）
    // 每个元素编码一年的农历信息：
    // - bit[0-3]: 闰月月份（0=无闰月）
    // - bit[4-15]: 12个月大小月标记（1=30天，0=29天）
    // - bit[16]: 闰月大小（1=30天，0=29天）
    // ─────────────────────────────────────────

    private val LUNAR_INFO = intArrayOf(
        0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
        0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
        0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
        0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
        0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
        0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0,
        0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
        0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6,
        0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
        0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x05ac0, 0x0ab60, 0x096d5, 0x092e0,
        0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
        0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
        0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
        0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
        0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0,
        0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0,
        0x092e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4,
        0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0,
        0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160,
        0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a4d0, 0x0d150, 0x0f252,
        0x0d520,
    )

    private val MONTH_NAMES = arrayOf(
        "", "正月", "二月", "三月", "四月", "五月", "六月",
        "七月", "八月", "九月", "十月", "冬月", "腊月",
    )

    private val DAY_NAMES = arrayOf(
        "", "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十",
    )

    private fun lunarMonthName(month: Int): String =
        MONTH_NAMES.getOrElse(month) { "?" }

    private fun lunarDayName(day: Int): String =
        DAY_NAMES.getOrElse(day) { "?" }

    /** 某年的闰月月份，0 表示无闰月 */
    private fun leapMonth(year: Int): Int {
        val idx = year - 1900
        if (idx !in LUNAR_INFO.indices) return 0
        return LUNAR_INFO[idx] and 0xf
    }

    /** 闰月天数 */
    private fun leapDays(year: Int): Int {
        val idx = year - 1900
        if (idx !in LUNAR_INFO.indices) return 0
        return if (leapMonth(year) != 0) {
            if (LUNAR_INFO[idx] and 0x10000 != 0) 30 else 29
        } else 0
    }

    /** 某年某月的天数 */
    private fun monthDays(year: Int, month: Int): Int {
        val idx = year - 1900
        if (idx !in LUNAR_INFO.indices) return 29
        return if (LUNAR_INFO[idx] and (0x10000 shr month) != 0) 30 else 29
    }

    /** 农历某年总天数 */
    private fun yearDays(year: Int): Int {
        var sum = 348 // 12 * 29
        val idx = year - 1900
        if (idx !in LUNAR_INFO.indices) return sum
        var i = 0x8000
        while (i > 0x8) {
            if (LUNAR_INFO[idx] and i != 0) sum++
            i = i shr 1
        }
        return sum + leapDays(year)
    }

    /**
     * 公历转农历
     */
    fun toLunar(date: LocalDate): LunarDate {
        val baseDate = LocalDate.of(1900, 1, 31) // 农历1900年正月初一
        var offset = (date.toEpochDay() - baseDate.toEpochDay()).toInt()

        var lunarYear = 1900
        var daysInYear: Int
        while (lunarYear < 2101) {
            daysInYear = yearDays(lunarYear)
            if (offset < daysInYear) break
            offset -= daysInYear
            lunarYear++
        }

        val leap = leapMonth(lunarYear)
        var isLeap = false
        var lunarMonth = 1
        var daysInMonth: Int

        while (lunarMonth <= 12) {
            // 闰月
            if (leap > 0 && lunarMonth == leap + 1 && !isLeap) {
                lunarMonth--
                isLeap = true
                daysInMonth = leapDays(lunarYear)
            } else {
                daysInMonth = monthDays(lunarYear, lunarMonth)
            }

            if (offset < daysInMonth) break
            offset -= daysInMonth

            if (isLeap && lunarMonth == leap + 1) {
                isLeap = false
            }
            lunarMonth++
        }

        val lunarDay = offset + 1

        return LunarDate(
            year = lunarYear,
            month = lunarMonth,
            day = lunarDay,
            isLeapMonth = isLeap,
        )
    }

    // ─────────────────────────────────────────
    // 农历节日
    // ─────────────────────────────────────────

    private fun getLunarFestival(date: LocalDate): String? {
        val lunar = toLunar(date)
        if (lunar.isLeapMonth) return null
        return when (lunar.month * 100 + lunar.day) {
            101 -> "春节"
            115 -> "元宵"
            505 -> "端午"
            707 -> "七夕"
            715 -> "中元"
            815 -> "中秋"
            909 -> "重阳"
            1208 -> "腊八"
            1230 -> "除夕"
            else -> null
        }
    }

    // ─────────────────────────────────────────
    // 节气（简化版：基于固定日期近似）
    // ─────────────────────────────────────────

    private val SOLAR_TERMS = mapOf(
        // month to (day1 to name1, day2 to name2)
        1 to listOf(6 to "小寒", 20 to "大寒"),
        2 to listOf(4 to "立春", 19 to "雨水"),
        3 to listOf(6 to "惊蛰", 21 to "春分"),
        4 to listOf(5 to "清明", 20 to "谷雨"),
        5 to listOf(6 to "立夏", 21 to "小满"),
        6 to listOf(6 to "芒种", 21 to "夏至"),
        7 to listOf(7 to "小暑", 23 to "大暑"),
        8 to listOf(7 to "立秋", 23 to "处暑"),
        9 to listOf(8 to "白露", 23 to "秋分"),
        10 to listOf(8 to "寒露", 23 to "霜降"),
        11 to listOf(7 to "立冬", 22 to "小雪"),
        12 to listOf(7 to "大雪", 22 to "冬至"),
    )

    private fun getSolarTerm(date: LocalDate): String? {
        val terms = SOLAR_TERMS[date.monthValue] ?: return null
        // 节气日期有 ±1 天的误差，这里用近似值
        return terms.find { (day, _) ->
            date.dayOfMonth == day || date.dayOfMonth == day - 1 || date.dayOfMonth == day + 1
        }?.second?.let { name ->
            // 只在精确日期返回（±0），避免重复
            if (terms.any { it.first == date.dayOfMonth }) name else null
        }
    }
}
