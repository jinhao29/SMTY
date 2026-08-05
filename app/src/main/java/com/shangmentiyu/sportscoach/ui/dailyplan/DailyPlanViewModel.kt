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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    private val appExceptionHandler =
        com.shangmentiyu.sportscoach.core.CoroutineExt.createAppExceptionHandler(_toast, "DailyPlanViewModel")

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
        viewModelScope.launch(appExceptionHandler) {
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
        viewModelScope.launch(appExceptionHandler) {
            opRepo.getSchedulesByDay(dayOfWeek).collect { list ->
                _schedules.value = list
            }
        }
    }

    private fun loadLessonsForDate(date: String) {
        viewModelScope.launch(appExceptionHandler) {
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
        /** [DateTimeFormatter] 不可变且线程安全，作为单例共享，无 Calendar 状态污染 */
        private val dateFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())

        fun today(): String = LocalDate.now().format(dateFormatter)

        /** 增减指定天数（线程安全：基于 [LocalDate] 不可变对象） */
        fun shiftDays(dateStr: String, days: Int): String {
            return try {
                val date = LocalDate.parse(dateStr, dateFormatter)
                date.plusDays(days.toLong()).format(dateFormatter)
            } catch (_: Exception) { dateStr }
        }

        /** 解析日期对应的 ISO 周几（1=周一 ... 7=周日） */
        fun parseDayOfWeek(dateStr: String): Int {
            return try {
                val date = LocalDate.parse(dateStr, dateFormatter)
                // java.time.DayOfWeek: MONDAY=1 ... SUNDAY=7，与 ISO 周几完全一致
                date.dayOfWeek.value
            } catch (_: Exception) { 0 }
        }
    }
}
