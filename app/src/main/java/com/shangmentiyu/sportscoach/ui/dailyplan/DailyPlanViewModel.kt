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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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

    // === v48 终极打磨：排课列表首帧加载标记（骨架屏） ===
    // 初始 _schedules = emptyList()，Room 首帧到达前 UI 显示骨架屏，
    // 首帧后置 true（空列表也是"已加载"，显示真实空状态）。
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        // === 竞态修复：日期变化时用 flatMapLatest 取消旧订阅 ===
        // 原实现每次切日期都 launch 一个新 collector 订阅 Room Flow，且从不取消旧 collector：
        // 多个 collector 并发写入 _schedules/_lessons，后到者覆盖先到者 —— 切日期后旧 collector
        // 仍会把"旧日期的数据"写回 UI，导致课前准备清单显示错误/不刷新（重启应用后恢复正常）。
        // flatMapLatest 保证任意时刻只有最新日期的 collector 存活。
        viewModelScope.launch(appExceptionHandler) {
            _selectedDate
                .flatMapLatest { date ->
                    lessonRepo.getAllLessons().map { all -> all.filter { it.date == date } }
                }
                .collect { _lessons.value = it }
        }
        viewModelScope.launch(appExceptionHandler) {
            _selectedDate
                .flatMapLatest { date -> opRepo.getSchedulesByDay(parseDayOfWeek(date)) }
                .collect {
                    _schedules.value = it
                    _loaded.value = true
                }
        }
        viewModelScope.launch(appExceptionHandler) {
            _selectedDate.collect { date ->
                _dayOfWeek.value = parseDayOfWeek(date)
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

    /** 检查排课对应的学员在选定日期是否已签到 */
    fun findSignedLesson(schedule: Schedule, date: String): Lesson? {
        val candidates = _lessons.value.filter { l ->
            l.studentName == schedule.studentName && l.date == date
        }
        // 精确匹配：长期排课自动生成的课时 time = schedule.startTime，可直接精确对应
        return candidates.firstOrNull { it.time == schedule.startTime }
            // 兜底：手动签到课时 time = 签到时刻，无法精确对应排课时间，
            // 且不存在"小时前缀"匹配漏洞（09:50 排课不会误配 09:15 签到）
            ?: candidates.firstOrNull()
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
