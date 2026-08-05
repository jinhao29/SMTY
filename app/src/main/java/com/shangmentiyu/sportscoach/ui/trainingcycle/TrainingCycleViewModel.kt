package com.shangmentiyu.sportscoach.ui.trainingcycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangmentiyu.sportscoach.data.model.TrainingCycle
import com.shangmentiyu.sportscoach.data.model.WeeklyPlan
import com.shangmentiyu.sportscoach.data.repo.OperationRepository
import com.shangmentiyu.sportscoach.data.repo.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 周期训练计划 ViewModel（协调层）。
 *
 * 协调 OperationRepository 与 StudentRepository，
 * 提供周期 CRUD、周计划编辑与状态切换。
 */
class TrainingCycleViewModel(
    private val opRepo: OperationRepository,
    private val studentRepo: StudentRepository
) : ViewModel() {

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    private val appExceptionHandler =
        com.shangmentiyu.sportscoach.core.CoroutineExt.createAppExceptionHandler(_toast, "TrainingCycleViewModel")

    private val _students = MutableStateFlow<List<String>>(emptyList())
    val students: StateFlow<List<String>> = _students.asStateFlow()

    private val _selectedStudent = MutableStateFlow("")
    val selectedStudent: StateFlow<String> = _selectedStudent.asStateFlow()

    private val _cycles = MutableStateFlow<List<TrainingCycle>>(emptyList())
    val cycles: StateFlow<List<TrainingCycle>> = _cycles.asStateFlow()

    private val _currentCycle = MutableStateFlow<TrainingCycle?>(null)
    val currentCycle: StateFlow<TrainingCycle?> = _currentCycle.asStateFlow()

    init {
        viewModelScope.launch(appExceptionHandler) {
            _students.value = studentRepo.getAllStudents().first().map { it.name }
        }
    }

    fun selectStudent(name: String) {
        _selectedStudent.value = name
        loadCycles(name)
    }

    private fun loadCycles(name: String) {
        viewModelScope.launch(appExceptionHandler) {
            opRepo.getCyclesByStudent(name).collect { list ->
                _cycles.value = list
            }
        }
    }

    fun openCycle(cycle: TrainingCycle) {
        _currentCycle.value = cycle
    }

    fun closeCycle() {
        _currentCycle.value = null
    }

    /** 创建新周期 */
    fun createCycle(
        studentName: String,
        name: String,
        goal: String,
        totalWeeks: Int,
        startDate: String
    ) {
        viewModelScope.launch(appExceptionHandler) {
            opRepo.createCycle(studentName, name, goal, totalWeeks, startDate)
        }
    }

    /** 更新某一周计划 */
    fun updateWeeklyPlan(weekIndex: Int, title: String, goal: String, focus: String) {
        val cycle = _currentCycle.value ?: return
        val plans = cycle.parseWeeklyPlans().map { p ->
            if (p.weekIndex == weekIndex) p.copy(title = title, goal = goal, focus = focus)
            else p
        }
        val updated = cycle.withWeeklyPlans(plans)
        viewModelScope.launch(appExceptionHandler) {
            opRepo.updateCycle(updated)
            _currentCycle.value = updated
        }
    }

    /** 标记周期完成 */
    fun markCompleted() {
        val cycle = _currentCycle.value ?: return
        viewModelScope.launch(appExceptionHandler) {
            val updated = cycle.copy(status = "已完成")
            opRepo.updateCycle(updated)
            _currentCycle.value = updated
        }
    }

    /** 删除周期 */
    fun deleteCycle(id: String) {
        viewModelScope.launch(appExceptionHandler) {
            opRepo.deleteCycle(id)
            _currentCycle.value = null
        }
    }
}
