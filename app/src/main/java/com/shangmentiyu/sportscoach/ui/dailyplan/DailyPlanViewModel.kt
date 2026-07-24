package com.shangmentiyu.sportscoach.ui.dailyplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.Schedule
import com.shangmentiyu.sportscoach.data.repo.LessonRepository
import com.shangmentiyu.sportscoach.data.repo.OperationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 每日计划编排 ViewModel（协调层）。
 *
 * 协调 LessonRepository 与 OperationRepository：
 * 显示选定日期的排课（Schedule）+ 已签到的课时记录（Lesson），
 * 教练可基于排课快速签到与查看进度。
 */
class DailyPlanViewModel(
    private val lessonRepo: LessonRepository,
    private val opRepo: OperationRepository
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(today())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _schedules = MutableStateFlow<List<Schedule>>(emptyList())
    val schedules: StateFlow<List<Schedule>> = _schedules.asStateFlow()

    private val _lessons = MutableStateFlow<List<Lesson>>(emptyList())
    val lessons: StateFlow<List<Lesson>> = _lessons.asStateFlow()

    /** 选中日期对应的周几（1=周一 ... 7=周日） */
    private val _dayOfWeek = MutableStateFlow(0)
    val dayOfWeek: StateFlow<Int> = _dayOfWeek.asStateFlow()

    init {
        viewModelScope.launch {
            _selectedDate.collect { date ->
                _dayOfWeek.value = parseDayOfWeek(date)
                loadSchedulesForDay(parseDayOfWeek(date))
                loadLessonsForDate(date)
            }
        }
    }

    fun selectDate(date: String) {
        _selectedDate.value = date
    }

    fun previousDay() {
        _selectedDate.value = shiftDays(_selectedDate.value, -1)
    }

    fun nextDay() {
        _selectedDate.value = shiftDays(_selectedDate.value, 1)
    }

    fun goToday() {
        _selectedDate.value = today()
    }

    private fun loadSchedulesForDay(dayOfWeek: Int) {
        viewModelScope.launch {
            opRepo.getSchedulesByDay(dayOfWeek).collect { list ->
                _schedules.value = list
            }
        }
    }

    private fun loadLessonsForDate(date: String) {
        viewModelScope.launch {
            lessonRepo.getAllLessons().collect { all ->
                _lessons.value = all.filter { it.date == date }
            }
        }
    }

    /** 检查排课对应的学员在选定日期是否已签到 */
    fun findSignedLesson(schedule: Schedule, date: String): Lesson? {
        return _lessons.value.firstOrNull { l ->
            l.studentName == schedule.studentName &&
                l.date == date &&
                l.time.startsWith(schedule.startTime.substring(0, 2))
        }
    }

    companion object {
        /**
         * 注意：[SimpleDateFormat] 非线程安全，禁止作为成员变量持有；
         * 每次调用新建实例，避免多协程并发解析时 Calendar 状态污染。
         */
        fun today(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        fun shiftDays(dateStr: String, days: Int): String {
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val cal = Calendar.getInstance().apply {
                    time = sdf.parse(dateStr) ?: Date()
                    add(Calendar.DAY_OF_MONTH, days)
                }
                sdf.format(cal.time)
            } catch (_: Exception) { dateStr }
        }

        /** 解析日期对应的 ISO 周几（1=周一 ... 7=周日） */
        fun parseDayOfWeek(dateStr: String): Int {
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val cal = Calendar.getInstance().apply {
                    time = sdf.parse(dateStr) ?: Date()
                }
                // Calendar.MONDAY=2 ... SUNDAY=1 → 转 ISO: 1=周一 ... 7=周日
                val c = cal.get(Calendar.DAY_OF_WEEK)
                when (c) {
                    Calendar.MONDAY -> 1
                    Calendar.TUESDAY -> 2
                    Calendar.WEDNESDAY -> 3
                    Calendar.THURSDAY -> 4
                    Calendar.FRIDAY -> 5
                    Calendar.SATURDAY -> 6
                    Calendar.SUNDAY -> 7
                    else -> 0
                }
            } catch (_: Exception) { 0 }
        }
    }
}
