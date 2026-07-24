package com.shangmentiyu.sportscoach.ui.bodymetric

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangmentiyu.sportscoach.data.model.BodyMetricHistory
import com.shangmentiyu.sportscoach.data.repo.BodyMetricRepository
import com.shangmentiyu.sportscoach.data.repo.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 体型变化曲线 ViewModel（协调层）。
 *
 * 协调 BodyMetricRepository 与 StudentRepository，
 * 提供学员选择、历史记录展示、新增测量记录与变化曲线数据。
 */
class BodyMetricChartViewModel(
    private val bodyMetricRepo: BodyMetricRepository,
    private val studentRepo: StudentRepository
) : ViewModel() {

    private val _students = MutableStateFlow<List<String>>(emptyList())
    val students: StateFlow<List<String>> = _students.asStateFlow()

    private val _selectedStudent = MutableStateFlow("")
    val selectedStudent: StateFlow<String> = _selectedStudent.asStateFlow()

    private val _history = MutableStateFlow<List<BodyMetricHistory>>(emptyList())
    val history: StateFlow<List<BodyMetricHistory>> = _history.asStateFlow()

    /** 首末变化对比 */
    private val _delta = MutableStateFlow<BodyMetricRepository.MetricDelta?>(null)
    val delta: StateFlow<BodyMetricRepository.MetricDelta?> = _delta.asStateFlow()

    init {
        viewModelScope.launch {
            _students.value = studentRepo.getAllStudents().first().map { it.name }
        }
    }

    fun selectStudent(name: String) {
        _selectedStudent.value = name
        loadHistory(name)
    }

    private fun loadHistory(name: String) {
        viewModelScope.launch {
            bodyMetricRepo.getByStudent(name).collect { list ->
                _history.value = list
                if (list.size >= 2) {
                    _delta.value = bodyMetricRepo.computeDelta(list.first(), list.last())
                } else {
                    _delta.value = null
                }
            }
        }
    }

    /** 新增一次测量记录 */
    fun addRecord(heightCm: Int, weightKg: Float, note: String) {
        val student = _selectedStudent.value
        if (student.isBlank()) return
        viewModelScope.launch {
            bodyMetricRepo.record(student, heightCm = heightCm, weightKg = weightKg, note = note)
        }
    }

    fun deleteRecord(id: String) {
        viewModelScope.launch { bodyMetricRepo.delete(id) }
    }
}
