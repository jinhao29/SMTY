package com.shangmentiyu.sportscoach.ui.stagesummary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangmentiyu.sportscoach.data.repo.LessonRepository
import com.shangmentiyu.sportscoach.data.repo.OperationRepository
import com.shangmentiyu.sportscoach.data.repo.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 阶段性总结 ViewModel（协调层）。
 *
 * 协调 StudentRepository / LessonRepository / OperationRepository，
 * 选择学员 + 时间范围后计算阶段总结数据。
 */
class StageSummaryViewModel(
    private val studentRepo: StudentRepository,
    private val lessonRepo: LessonRepository,
    private val opRepo: OperationRepository
) : ViewModel() {

    private val _students = MutableStateFlow<List<String>>(emptyList())
    val students: StateFlow<List<String>> = _students.asStateFlow()

    private val _selectedStudent = MutableStateFlow("")
    val selectedStudent: StateFlow<String> = _selectedStudent.asStateFlow()

    /** 阶段范围选项：最近7天/30天/90天/全部 */
    private val _rangeOption = MutableStateFlow(1)  // 0=7天 1=30天 2=90天 3=全部
    val rangeOption: StateFlow<Int> = _rangeOption.asStateFlow()

    private val _summary = MutableStateFlow<OperationRepository.StageSummary?>(null)
    val summary: StateFlow<OperationRepository.StageSummary?> = _summary.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        viewModelScope.launch {
            _students.value = studentRepo.getAllStudents().first().map { it.name }
        }
    }

    fun selectStudent(name: String) {
        _selectedStudent.value = name
        recompute()
    }

    fun selectRange(option: Int) {
        _rangeOption.value = option
        recompute()
    }

    /** 重新计算阶段总结 */
    fun recompute() {
        val student = _selectedStudent.value
        if (student.isBlank()) return
        _loading.value = true
        viewModelScope.launch {
            try {
                val all = lessonRepo.getByStudentOnce(student)
                val (start, end) = computeRange(_rangeOption.value, all)
                val result = opRepo.computeStageSummary(student, start, end, all)
                _summary.value = result
            } finally {
                _loading.value = false
            }
        }
    }

    /** 计算时间范围 */
    private fun computeRange(option: Int, lessons: List<com.shangmentiyu.sportscoach.data.model.Lesson>): Pair<String, String> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = sdf.format(Date())
        return when (option) {
            0 -> shiftDays(today, -6) to today                      // 最近7天（含今日）
            1 -> shiftDays(today, -29) to today                     // 最近30天
            2 -> shiftDays(today, -89) to today                     // 最近90天
            else -> {                                                // 全部
                val first = lessons.minByOrNull { it.date }?.date ?: today
                val last = lessons.maxByOrNull { it.date }?.date ?: today
                first to last
            }
        }
    }

    private fun shiftDays(dateStr: String, days: Int): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val cal = Calendar.getInstance().apply {
                time = sdf.parse(dateStr) ?: Date()
                add(Calendar.DAY_OF_MONTH, days)
            }
            sdf.format(cal.time)
        } catch (_: Exception) { dateStr }
    }
}
