package com.shangmentiyu.sportscoach.ui.heightprediction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.data.repo.StudentRepository
import com.shangmentiyu.sportscoach.domain.HeightPredictionResult
import com.shangmentiyu.sportscoach.domain.HeightPredictionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 身高预测 ViewModel。
 *
 * 通过 [StateFlow] 与 UI 双向绑定：用户输入任一数据变化时，
 * 自动触发 [HeightPredictionUseCase] 重新计算预测结果。
 *
 * @param studentRepo 学员仓储，用于加载与保存身体数据
 * @param useCase     身高预测用例
 */
class HeightPredictionViewModel(
    private val studentRepo: StudentRepository,
    private val useCase: HeightPredictionUseCase = HeightPredictionUseCase()
) : ViewModel() {

    // === 表单输入状态（与 UI 双向绑定） ===
    private val _fatherHeight = MutableStateFlow("")
    val fatherHeight: StateFlow<String> = _fatherHeight.asStateFlow()

    private val _motherHeight = MutableStateFlow("")
    val motherHeight: StateFlow<String> = _motherHeight.asStateFlow()

    private val _avgSleepHours = MutableStateFlow("")
    val avgSleepHours: StateFlow<String> = _avgSleepHours.asStateFlow()

    private val _nutritionScore = MutableStateFlow(0)
    val nutritionScore: StateFlow<Int> = _nutritionScore.asStateFlow()

    private val _sportsMinsPerWeek = MutableStateFlow("")
    val sportsMinsPerWeek: StateFlow<String> = _sportsMinsPerWeek.asStateFlow()

    // === 预测结果 ===
    private val _result = MutableStateFlow<HeightPredictionResult?>(null)
    val result: StateFlow<HeightPredictionResult?> = _result.asStateFlow()

    // === 学员基本信息（只读） ===
    private val _studentInfo = MutableStateFlow<Student?>(null)
    val studentInfo: StateFlow<Student?> = _studentInfo.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    private val appExceptionHandler =
        com.shangmentiyu.sportscoach.core.CoroutineExt.createAppExceptionHandler(_toast, "HeightPredictionViewModel")

    /** 当前操作的学员（保存时使用） */
    private var currentStudent: Student? = null

    /**
     * 加载学员数据，初始化表单。
     *
     * @param studentName 学员姓名
     */
    fun loadStudent(studentName: String) {
        viewModelScope.launch(appExceptionHandler) {
            val student = studentRepo.getByName(studentName) ?: run {
                _toast.value = "学员不存在"
                return@launch
            }
            currentStudent = student
            _studentInfo.value = student
            _fatherHeight.value = if (student.fatherHeight > 0) student.fatherHeight.toString() else ""
            _motherHeight.value = if (student.motherHeight > 0) student.motherHeight.toString() else ""
            _avgSleepHours.value = if (student.avgSleepHours > 0) student.avgSleepHours.toString() else ""
            _nutritionScore.value = student.nutritionScore
            _sportsMinsPerWeek.value = if (student.sportsMinsPerWeek > 0) student.sportsMinsPerWeek.toString() else ""
            recalculate()
        }
    }

    // === 输入更新方法（UI 调用，自动触发重算） ===
    fun updateFatherHeight(value: String) { _fatherHeight.value = value; recalculate() }
    fun updateMotherHeight(value: String) { _motherHeight.value = value; recalculate() }
    fun updateAvgSleepHours(value: String) { _avgSleepHours.value = value; recalculate() }
    fun updateNutritionScore(value: Int) { _nutritionScore.value = value; recalculate() }
    fun updateSportsMinsPerWeek(value: String) { _sportsMinsPerWeek.value = value; recalculate() }

    fun clearToast() { _toast.value = null }

    /** 根据当前输入重新计算预测结果 */
    private fun recalculate() {
        val student = currentStudent ?: return
        val father = _fatherHeight.value.toDoubleOrNull() ?: 0.0
        val mother = _motherHeight.value.toDoubleOrNull() ?: 0.0
        val sleep = _avgSleepHours.value.toDoubleOrNull() ?: 0.0
        val nutrition = _nutritionScore.value
        val sports = _sportsMinsPerWeek.value.toIntOrNull() ?: 0

        _result.value = useCase.execute(
            gender = student.gender,
            age = student.age,
            fatherHeight = father,
            motherHeight = mother,
            avgSleepHours = sleep,
            nutritionScore = nutrition,
            sportsMinsPerWeek = sports
        )
    }

    /**
     * 保存身体数据到数据库。
     *
     * 将当前表单输入写入 Student 实体并调用 [StudentRepository.updateStudent]。
     */
    fun save(onDone: (Boolean) -> Unit) {
        val student = currentStudent ?: run {
            _toast.value = "学员数据未加载"
            onDone(false)
            return
        }
        viewModelScope.launch(appExceptionHandler) {
            try {
                val updated = student.copy(
                    fatherHeight = _fatherHeight.value.toDoubleOrNull() ?: 0.0,
                    motherHeight = _motherHeight.value.toDoubleOrNull() ?: 0.0,
                    avgSleepHours = _avgSleepHours.value.toDoubleOrNull() ?: 0.0,
                    nutritionScore = _nutritionScore.value,
                    sportsMinsPerWeek = _sportsMinsPerWeek.value.toIntOrNull() ?: 0,
                    updatedAt = System.currentTimeMillis()
                )
                studentRepo.updateStudent(updated)
                currentStudent = updated
                _toast.value = "保存成功"
                onDone(true)
            } catch (e: Exception) {
                _toast.value = "保存失败：${e.message}"
                onDone(false)
            }
        }
    }
}
