package com.shangmentiyu.sportscoach.ui.coachreport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.data.repo.LessonRepository
import com.shangmentiyu.sportscoach.data.repo.OperationRepository
import com.shangmentiyu.sportscoach.data.repo.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 教练日报 ViewModel（协调层）。
 *
 * 按日期聚合当日所有学员的签到情况、消课记录与课时包消耗情况，
 * 帮助教练回顾一天工作并评估训练效果。
 *
 * 支持学员过滤：selectedStudent 为 null 表示查看当日全部学员，
 * 否则仅展示该学员当日的课时与训练效果。
 */
class CoachDailyReportViewModel(
    private val lessonRepo: LessonRepository,
    private val opRepo: OperationRepository,
    private val studentRepo: StudentRepository
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(today())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    /** 选中学员姓名，null = 全部学员 */
    private val _selectedStudent = MutableStateFlow<String?>(null)
    val selectedStudent: StateFlow<String?> = _selectedStudent.asStateFlow()

    /** 学员列表（用于选择器） */
    val students: StateFlow<List<Student>> = studentRepo.getAllStudents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _lessons = MutableStateFlow<List<Lesson>>(emptyList())
    val lessons: StateFlow<List<Lesson>> = _lessons.asStateFlow()

    /** 当日消课统计 */
    private val _stats = MutableStateFlow(DailyStats())
    val stats: StateFlow<DailyStats> = _stats.asStateFlow()

    init {
        viewModelScope.launch {
            _selectedDate.collect { date ->
                loadStats(date, _selectedStudent.value)
            }
        }
        viewModelScope.launch {
            _selectedStudent.collect { name ->
                loadStats(_selectedDate.value, name)
            }
        }
    }

    private fun loadStats(date: String, studentName: String?) {
        viewModelScope.launch {
            lessonRepo.getAllLessons().collect { all ->
                val todayLessons = all
                    .filter { it.date == date }
                    .let { list ->
                        if (studentName.isNullOrBlank()) list
                        else list.filter { it.studentName == studentName }
                    }
                _lessons.value = todayLessons

                val total = todayLessons.size
                val signed = todayLessons.count { it.packageId.isNotBlank() }
                val avgPerf = if (total > 0) todayLessons.map { it.performance }.average().toFloat() else 0f
                val avgDuration = if (total > 0) todayLessons.map { it.duration }.average().toInt() else 0
                val attendanceDist = todayLessons.groupingBy { it.attendance }.eachCount()
                val uniqueStudents = todayLessons.map { it.studentName }.distinct().size

                _stats.value = DailyStats(
                    date = date,
                    totalLessons = total,
                    consumedLessons = signed,
                    uniqueStudents = uniqueStudents,
                    avgPerformance = avgPerf,
                    avgDuration = avgDuration,
                    attendanceDistribution = attendanceDist
                )
            }
        }
    }

    fun selectDate(date: String) { _selectedDate.value = date }
    fun previousDay() { _selectedDate.value = shiftDays(_selectedDate.value, -1) }
    fun nextDay() { _selectedDate.value = shiftDays(_selectedDate.value, 1) }
    fun goToday() { _selectedDate.value = today() }

    /** 选择学员（传 null 表示查看全部） */
    fun selectStudent(name: String?) { _selectedStudent.value = name }

    /** 评估单节课的训练效果（基于完成度+表现） */
    fun evaluateLesson(lesson: Lesson): TrainingEffect {
        val contentJson = lesson.content
        var totalEx = 0
        var doneEx = 0
        if (contentJson.isNotBlank() && contentJson != "[]") {
            try {
                val arr = org.json.JSONArray(contentJson)
                totalEx = arr.length()
                for (i in 0 until arr.length()) {
                    if (arr.optJSONObject(i)?.optBoolean("done", false) == true) doneEx++
                }
            } catch (_: Exception) {}
        }
        val completionRate = if (totalEx > 0) doneEx.toFloat() / totalEx else 0f
        val perfScore = lesson.performance.toFloat() / 10f
        val overall = (completionRate * 0.4f + perfScore * 0.6f)
        val level = when {
            overall >= 0.85f -> "优秀"
            overall >= 0.7f -> "良好"
            overall >= 0.5f -> "一般"
            else -> "需改进"
        }
        return TrainingEffect(
            lessonId = lesson.id,
            studentName = lesson.studentName,
            totalExercises = totalEx,
            doneExercises = doneEx,
            completionRate = completionRate,
            performance = lesson.performance,
            overallScore = overall,
            level = level
        )
    }

    data class DailyStats(
        val date: String = "",
        val totalLessons: Int = 0,
        val consumedLessons: Int = 0,
        val uniqueStudents: Int = 0,
        val avgPerformance: Float = 0f,
        val avgDuration: Int = 0,
        val attendanceDistribution: Map<String, Int> = emptyMap()
    )

    data class TrainingEffect(
        val lessonId: String,
        val studentName: String,
        val totalExercises: Int,
        val doneExercises: Int,
        val completionRate: Float,
        val performance: Int,
        val overallScore: Float,
        val level: String
    )

    companion object {
        /**
         * 注意：[SimpleDateFormat] 非线程安全，禁止作为成员变量持有；
         * 每次调用新建实例，避免多协程并发解析时 Calendar 状态污染。
         */
        fun today(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        fun shiftDays(dateStr: String, days: Int): String = try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val cal = java.util.Calendar.getInstance().apply {
                time = sdf.parse(dateStr) ?: Date()
                add(java.util.Calendar.DAY_OF_MONTH, days)
            }
            sdf.format(cal.time)
        } catch (_: Exception) { dateStr }
    }
}
