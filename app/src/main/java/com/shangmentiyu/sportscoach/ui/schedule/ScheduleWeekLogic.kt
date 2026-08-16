package com.shangmentiyu.sportscoach.ui.schedule

import com.shangmentiyu.sportscoach.data.model.Schedule
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 课表页纯逻辑层（v53 拆分自 ScheduleScreen）：
 * 周日期构建、选中日期换算、当日课程过滤、日历红点集合计算等无 UI 依赖的纯函数。
 */

/**
 * 日期条目：Keep 风格日期选择条使用。
 *
 * @param dayOfWeek ISO 周几（1=周一 ... 7=周日）
 * @param dayName 周几文本（如"周一"）
 * @param date 对应日期
 * @param dateLabel 日期文本（如"03-04"，用于展示）
 * @param dateStr 完整日期字符串（yyyy-MM-dd，用于 LazyRow 唯一 key，杜绝切周文字错乱）
 */
internal data class DayItem(
    val dayOfWeek: Int,
    val dayName: String,
    val date: Date,
    val dateLabel: String,
    val dateStr: String
)

internal val WEEK_DAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

private val FULL_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
private val SHORT_DATE_FMT = DateTimeFormatter.ofPattern("MM-dd", Locale.getDefault())

/**
 * 排课模板在指定日期是否生效 —— 日历红点与下方列表共享的唯一过滤条件：
 * 活跃 + 非体验课(isTrial=0) + 日期落在模板生效期 startDate~endDate 内（空边界视为不限）。
 * 数据源均为 OperationViewModel.schedules（同一 Room Flow），保证红点与列表恒一致。
 */
internal fun isScheduleEffective(s: Schedule, dateStr: String): Boolean {
    return s.isActive && !s.isTrial &&
        (s.startDate.isBlank() || dateStr >= s.startDate) &&
        (s.endDate.isBlank() || dateStr <= s.endDate)
}

/**
 * 计算本周 7 天对应的条目（1=周一 ... 7=周日）。
 * 日期格式化线程安全：Date→LocalDate 转换后用 [DateTimeFormatter] 格式化。
 */
internal fun buildWeekDays(weekStart: Date): List<DayItem> {
    val cal = Calendar.getInstance().apply { time = weekStart }
    val zone = ZoneId.systemDefault()
    return (1..7).map { dayOfWeek ->
        val date = cal.time
        val localDate = date.toInstant().atZone(zone).toLocalDate()
        val item = DayItem(
            dayOfWeek = dayOfWeek,
            dayName = WEEK_DAY_NAMES[dayOfWeek - 1],
            date = date,
            dateLabel = SHORT_DATE_FMT.format(localDate),
            // 完整日期字符串作为 LazyRow key：跨周/跨年全局唯一，杜绝旧状态复用错乱
            dateStr = FULL_DATE_FMT.format(localDate)
        )
        cal.add(Calendar.DATE, 1)
        item
    }
}

/** 周起始日 + 周几（1=周一 ... 7=周日）换算为 [LocalDate]，异常时回退今天。 */
internal fun selectedDateFromWeek(weekStart: Date, selectedDayOfWeek: Int): LocalDate {
    return try {
        val zone = ZoneId.systemDefault()
        val weekStartLocal = weekStart.toInstant().atZone(zone).toLocalDate()
        weekStartLocal.plusDays((selectedDayOfWeek - 1).toLong())
    } catch (e: Exception) {
        android.util.Log.e("CalendarCrash", "计算选中日期失败", e)
        LocalDate.now()
    }
}

/** 周起始日 + 周几换算为 yyyy-MM-dd 字符串，异常时回退今天的字符串。 */
internal fun selectedDateStrFromWeek(weekStart: Date, selectedDayOfWeek: Int): String {
    return try {
        val zone = ZoneId.systemDefault()
        val weekStartLocal = weekStart.toInstant().atZone(zone).toLocalDate()
        weekStartLocal.plusDays((selectedDayOfWeek - 1).toLong()).format(FULL_DATE_FMT)
    } catch (e: Exception) {
        android.util.Log.e("CalendarCrash", "计算选中日期字符串失败", e)
        LocalDate.now().format(FULL_DATE_FMT)
    }
}

/** 今天在给定周条目中的 dayOfWeek；不在本周时返回 1（周一）。 */
internal fun todayDayOfWeekInWeek(weekDays: List<DayItem>): Int {
    val todayCal = Calendar.getInstance()
    val todayIdx = weekDays.indexOfFirst { day ->
        val d = Calendar.getInstance().apply { time = day.date }
        d.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
            d.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
    }
    return if (todayIdx >= 0) weekDays[todayIdx].dayOfWeek else 1
}

/**
 * 当前选中日期的课程列表（按开始时间升序，已暂停置底）。
 * 与日历红点共享完全一致的过滤条件（isScheduleEffective），
 * 杜绝"日历有红点但下方列表为空"。
 */
internal fun daySchedulesFor(
    schedules: List<Schedule>,
    selectedDayOfWeek: Int,
    selectedDateLocal: LocalDate
): List<Schedule> {
    val selStr = selectedDateLocal.format(FULL_DATE_FMT)
    return schedules
        .filter {
            it.dayOfWeek == selectedDayOfWeek &&
                isScheduleEffective(it, selStr)
        }
        .sortedWith(compareBy({ if (it.isActive) 0 else 1 }, { it.startTime }))
}

/**
 * 日历红点集合：遍历当前显示月（42 格含上下邻月填充），
 * 仅对"生效期内活跃非体验课模板"命中的日期标红。
 */
internal fun scheduledDatesFor(schedules: List<Schedule>, selectedDateStr: String): Set<String> {
    val base = try {
        LocalDate.parse(selectedDateStr, FULL_DATE_FMT)
    } catch (e: Exception) {
        LocalDate.now()
    }
    val month = YearMonth.from(base)
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value - 1
    val gridStart = first.minusDays(leading.toLong())
    return (0 until 42).map { gridStart.plusDays(it.toLong()) }
        .filter { date -> schedules.any { s -> isScheduleEffective(s, date.format(FULL_DATE_FMT)) && s.dayOfWeek == date.dayOfWeek.value } }
        .map { it.format(FULL_DATE_FMT) }
        .toSet()
}

/**
 * 日历点击后需要切换的周偏移量（-7 / 0 / +7）与目标周几。
 * @return Pair(周偏移量, 目标 dayOfWeek)；解析失败返回 null
 */
internal fun weekShiftForCalendarClick(weekStart: Date, dateStr: String): Pair<Int, Int>? {
    return try {
        val clickedDate = LocalDate.parse(dateStr, FULL_DATE_FMT)
        val zone = ZoneId.systemDefault()
        val currentWeekStart = weekStart.toInstant().atZone(zone).toLocalDate()
        val daysDiff = ChronoUnit.DAYS.between(currentWeekStart, clickedDate)
        val shift = when {
            daysDiff < 0 -> -7
            daysDiff >= 7 -> 7
            else -> 0
        }
        Pair(shift, clickedDate.dayOfWeek.value)
    } catch (e: Exception) {
        android.util.Log.e("CalendarCrash", "切换日期失败", e)
        null
    }
}

/**
 * 计算本周日期范围文本（MM.dd - MM.dd）。
 * 线程安全：基于 [java.time.LocalDate] + [DateTimeFormatter]，替代 [java.text.SimpleDateFormat]。
 */
internal fun weekRangeText(weekStart: Date): String {
    // 修复：原格式"yyyy年MM月dd日 ~ yyyy年MM月dd日"过长导致"2026年0..."被截断。
    // 改为简洁的"MM.dd - MM.dd"格式，年份信息不在此处显示，避免溢出。
    val zone = ZoneId.systemDefault()
    val start = weekStart.toInstant().atZone(zone).toLocalDate()
    val end = start.plusDays(6)
    return "${start.format(SHORT_DATE_FMT)} - ${end.format(SHORT_DATE_FMT)}"
}
