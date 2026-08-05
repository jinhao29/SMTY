package com.shangmentiyu.sportscoach.ui.diet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangmentiyu.sportscoach.data.model.DietTemplateEntity
import com.shangmentiyu.sportscoach.data.model.MealItem
import com.shangmentiyu.sportscoach.data.model.StudentDietRecord
import com.shangmentiyu.sportscoach.data.repo.DietRepository
import com.shangmentiyu.sportscoach.data.repo.DietRepository.DietNotes
import com.shangmentiyu.sportscoach.data.repo.DietRepository.DietMeals
import com.shangmentiyu.sportscoach.data.repo.StudentRepository
import com.shangmentiyu.sportscoach.domain.ActivityLevel
import com.shangmentiyu.sportscoach.domain.TdeeProcessor
import com.shangmentiyu.sportscoach.domain.TdeeResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 饮食管理 ViewModel。
 *
 * 职责：
 * - 加载 3+2 饮食模板与学员已绑定方案，回显到 UI
 * - 5 餐备注与 UI 双向绑定，点击"应用方案"写入数据库
 * - 集成 TDEE 计算器：从学员档案自动读取性别 / 年龄 / 身高 / 体重，教练可覆写
 * - 任一 TDEE 输入变化 → 自动重新计算 [TdeeResult]
 *
 * @param dietRepo     饮食仓储
 * @param studentRepo  学员仓储（用于自动填充 TDEE 输入框）
 * @param tdeeProcessor TDEE 纯算法处理器
 */
class DietViewModel(
    private val dietRepo: DietRepository,
    private val studentRepo: StudentRepository,
    private val tdeeProcessor: TdeeProcessor = TdeeProcessor()
) : ViewModel() {

    /** 3 套预置模板 */
    private val _templates = MutableStateFlow<List<DietTemplateEntity>>(emptyList())
    val templates: StateFlow<List<DietTemplateEntity>> = _templates.asStateFlow()

    /** 当前选中的模板 ID */
    private val _selectedTemplateId = MutableStateFlow(DietRepository.TemplateIds.REGULAR)
    val selectedTemplateId: StateFlow<String> = _selectedTemplateId.asStateFlow()

    /** 当前选中模板的实体（便于 UI 取训练前后提示） */
    private val _selectedTemplate = MutableStateFlow<DietTemplateEntity?>(null)
    val selectedTemplate: StateFlow<DietTemplateEntity?> = _selectedTemplate.asStateFlow()

    // === 5 餐教练备注（与 UI 双向绑定） ===
    private val _breakfastNote = MutableStateFlow("")
    val breakfastNote: StateFlow<String> = _breakfastNote.asStateFlow()

    private val _morningSnackNote = MutableStateFlow("")
    val morningSnackNote: StateFlow<String> = _morningSnackNote.asStateFlow()

    private val _lunchNote = MutableStateFlow("")
    val lunchNote: StateFlow<String> = _lunchNote.asStateFlow()

    private val _afternoonSnackNote = MutableStateFlow("")
    val afternoonSnackNote: StateFlow<String> = _afternoonSnackNote.asStateFlow()

    private val _dinnerNote = MutableStateFlow("")
    val dinnerNote: StateFlow<String> = _dinnerNote.asStateFlow()

    // === 5 餐自定义食材内容（与 UI 双向绑定，空串表示用模板默认） ===
    private val _breakfastMeals = MutableStateFlow("")
    val breakfastMeals: StateFlow<String> = _breakfastMeals.asStateFlow()

    private val _morningSnackMeals = MutableStateFlow("")
    val morningSnackMeals: StateFlow<String> = _morningSnackMeals.asStateFlow()

    private val _lunchMeals = MutableStateFlow("")
    val lunchMeals: StateFlow<String> = _lunchMeals.asStateFlow()

    private val _afternoonSnackMeals = MutableStateFlow("")
    val afternoonSnackMeals: StateFlow<String> = _afternoonSnackMeals.asStateFlow()

    private val _dinnerMeals = MutableStateFlow("")
    val dinnerMeals: StateFlow<String> = _dinnerMeals.asStateFlow()

    /** 当前学员已绑定的方案（用于回显） */
    private val _boundRecord = MutableStateFlow<StudentDietRecord?>(null)
    val boundRecord: StateFlow<StudentDietRecord?> = _boundRecord.asStateFlow()

    // === TDEE 计算器输入与输出 ===
    private val _gender = MutableStateFlow("男")
    val gender: StateFlow<String> = _gender.asStateFlow()

    private val _age = MutableStateFlow("")
    val age: StateFlow<String> = _age.asStateFlow()

    private val _heightCm = MutableStateFlow("")
    val heightCm: StateFlow<String> = _heightCm.asStateFlow()

    private val _weightKg = MutableStateFlow("")
    val weightKg: StateFlow<String> = _weightKg.asStateFlow()

    private val _activityLevel = MutableStateFlow(ActivityLevel.DEFAULT)
    val activityLevel: StateFlow<ActivityLevel> = _activityLevel.asStateFlow()

    private val _tdeeResult = MutableStateFlow<TdeeResult?>(null)
    val tdeeResult: StateFlow<TdeeResult?> = _tdeeResult.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    private val appExceptionHandler =
        com.shangmentiyu.sportscoach.core.CoroutineExt.createAppExceptionHandler(_toast, "DietViewModel")

    /** 当前操作的学员姓名 */
    private var currentStudentName: String = ""

    /**
     * 加载学员饮食数据：先取模板列表，再查学员已绑定记录回显，
     * 最后从学员档案自动填充 TDEE 输入框。
     *
     * @param studentName 学员姓名
     */
    fun load(studentName: String) {
        currentStudentName = studentName
        viewModelScope.launch(appExceptionHandler) {
            // 1. 加载所有模板
            val tpls = dietRepo.getAllTemplates()
            _templates.value = tpls

            // 2. 查学员已绑定记录
            val record = dietRepo.getStudentRecord(studentName)
            _boundRecord.value = record

            // 3. 回显绑定方案：选中模板 + 5 餐备注 + 5 餐自定义食材
            if (record != null) {
                _selectedTemplateId.value = record.templateId
                _breakfastNote.value = record.breakfastNote
                _morningSnackNote.value = record.morningSnackNote
                _lunchNote.value = record.lunchNote
                _afternoonSnackNote.value = record.afternoonSnackNote
                _dinnerNote.value = record.dinnerNote
                _breakfastMeals.value = record.breakfastMeals
                _morningSnackMeals.value = record.morningSnackMeals
                _lunchMeals.value = record.lunchMeals
                _afternoonSnackMeals.value = record.afternoonSnackMeals
                _dinnerMeals.value = record.dinnerMeals
            }

            // 4. 同步选中模板实体
            _selectedTemplate.value = tpls.firstOrNull { it.id == _selectedTemplateId.value }

            // 5. 从学员档案自动填充 TDEE 输入（年龄 / 性别 / 身高 / 体重）
            val student = studentRepo.getByName(studentName)
            if (student != null) {
                _gender.value = student.gender.ifBlank { "男" }
                if (student.age > 0) _age.value = student.age.toString()
                if (student.heightCm > 0) _heightCm.value = student.heightCm.toString()
                if (student.weightKg > 0f) _weightKg.value = student.weightKg.toString()
            }
            recalcTdee()
        }
    }

    /**
     * 切换模板：仅更新选中状态，保留教练已输入的备注（避免切换丢输入）。
     */
    fun selectTemplate(templateId: String) {
        _selectedTemplateId.value = templateId
        _selectedTemplate.value = _templates.value.firstOrNull { it.id == templateId }
    }

    // === 备注更新方法（UI 调用） ===
    fun updateBreakfastNote(v: String) { _breakfastNote.value = v }
    fun updateMorningSnackNote(v: String) { _morningSnackNote.value = v }
    fun updateLunchNote(v: String) { _lunchNote.value = v }
    fun updateAfternoonSnackNote(v: String) { _afternoonSnackNote.value = v }
    fun updateDinnerNote(v: String) { _dinnerNote.value = v }

    // === 自定义食材更新方法（UI 调用，空串表示恢复模板默认） ===
    fun updateBreakfastMeals(v: String) { _breakfastMeals.value = v }
    fun updateMorningSnackMeals(v: String) { _morningSnackMeals.value = v }
    fun updateLunchMeals(v: String) { _lunchMeals.value = v }
    fun updateAfternoonSnackMeals(v: String) { _afternoonSnackMeals.value = v }
    fun updateDinnerMeals(v: String) { _dinnerMeals.value = v }

    /**
     * 获取某餐次显示用的 meals 列表：
     * - 优先使用教练自定义食材（非空）
     * - 否则回退到模板默认
     */
    fun mealsFor(
        templateMeals: String,
        customMeals: String
    ): List<MealItem> {
        val json = customMeals.ifBlank { templateMeals }
        return dietRepo.parseMeals(json)
    }

    /**
     * 将教练输入的纯文本食材列表（每行一条）序列化为 JSON。
     * 空输入返回空串（表示用模板默认）。
     *
     * 输入格式示例：
     *   主食|全麦面包 2 片
     *   优质蛋白|水煮蛋 1 个
     * 或无类别直接：
     *   全麦面包 2 片
     *   水煮蛋 1 个
     */
    fun encodeMealsFromText(text: String): String {
        if (text.isBlank()) return ""
        val items = text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                val parts = line.split("|", limit = 2)
                if (parts.size == 2) {
                    MealItem(category = parts[0].trim(), content = parts[1].trim())
                } else {
                    MealItem(category = "", content = line)
                }
            }
        return if (items.isEmpty()) "" else dietRepo.serializeMeals(items)
    }

    /**
     * 将 JSON 餐次内容反序列化为可编辑文本（每行一条）。
     * 用于 UI 输入框回显。
     */
    fun decodeMealsToText(json: String): String {
        val items = dietRepo.parseMeals(json)
        return items.joinToString("\n") { item ->
            if (item.category.isBlank()) item.content
            else "${item.category}|${item.content}"
        }
    }

    // === TDEE 输入更新方法（UI 调用，任一变化触发自动重算） ===
    fun updateGender(v: String) { _gender.value = v; recalcTdee() }
    fun updateAge(v: String) { _age.value = v; recalcTdee() }
    fun updateHeightCm(v: String) { _heightCm.value = v; recalcTdee() }
    fun updateWeightKg(v: String) { _weightKg.value = v; recalcTdee() }
    fun updateActivityLevel(v: ActivityLevel) { _activityLevel.value = v; recalcTdee() }

    /**
     * 重新计算 TDEE。
     *
     * 任一输入非法（非数字 / 空值 / <=0）时清空结果，UI 隐藏结果卡。
     */
    private fun recalcTdee() {
        val weight = _weightKg.value.toDoubleOrNull() ?: 0.0
        val height = _heightCm.value.toDoubleOrNull() ?: 0.0
        val ageInt = _age.value.toIntOrNull() ?: 0
        _tdeeResult.value = tdeeProcessor.calculate(
            gender = _gender.value,
            weightKg = weight,
            heightCm = height,
            age = ageInt,
            activityLevel = _activityLevel.value
        )
    }

    fun clearToast() { _toast.value = null }

    /**
     * 应用当前方案给学员（覆盖旧绑定）。
     *
     * @param onDone 完成回调，true=成功
     */
    fun applyTemplate(onDone: (Boolean) -> Unit) {
        if (currentStudentName.isBlank()) {
            _toast.value = "学员未加载"
            onDone(false)
            return
        }
        val template = _selectedTemplate.value ?: run {
            _toast.value = "模板未加载"
            onDone(false)
            return
        }
        viewModelScope.launch(appExceptionHandler) {
            try {
                dietRepo.applyTemplate(
                    studentName = currentStudentName,
                    templateId = template.id,
                    templateName = template.name,
                    notes = DietNotes(
                        breakfast = _breakfastNote.value,
                        morningSnack = _morningSnackNote.value,
                        lunch = _lunchNote.value,
                        afternoonSnack = _afternoonSnackNote.value,
                        dinner = _dinnerNote.value
                    ),
                    meals = DietMeals(
                        breakfast = _breakfastMeals.value,
                        morningSnack = _morningSnackMeals.value,
                        lunch = _lunchMeals.value,
                        afternoonSnack = _afternoonSnackMeals.value,
                        dinner = _dinnerMeals.value
                    )
                )
                // 重新加载绑定记录，刷新 UI
                _boundRecord.value = dietRepo.getStudentRecord(currentStudentName)
                _toast.value = "已应用「${template.name}」到该学员"
                onDone(true)
            } catch (e: Exception) {
                _toast.value = "保存失败：${e.message}"
                onDone(false)
            }
        }
    }

    /**
     * 解析餐次 JSON 为 [MealItem] 列表（供 UI 渲染）。
     */
    fun parseMeals(json: String): List<MealItem> = dietRepo.parseMeals(json)
}
