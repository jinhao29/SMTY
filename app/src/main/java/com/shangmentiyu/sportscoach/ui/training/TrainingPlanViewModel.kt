package com.shangmentiyu.sportscoach.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangmentiyu.sportscoach.core.AbilityAnalyzer
import com.shangmentiyu.sportscoach.core.TrainingPlanGenerator
import com.shangmentiyu.sportscoach.data.model.ExerciseItem
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.data.repo.LessonRepository
import com.shangmentiyu.sportscoach.data.repo.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * 训练计划 ViewModel（协调层）。
 *
 * 协调 StudentRepository、LessonRepository 与 TrainingPlanGenerator，
 * 完成"加载学员数据 → 生成训练计划 → 一键应用到新课时"的完整流程。
 */
class TrainingPlanViewModel(
    private val studentRepo: StudentRepository,
    private val lessonRepo: LessonRepository
) : ViewModel() {

    private val _student = MutableStateFlow<Student?>(null)
    val student: StateFlow<Student?> = _student.asStateFlow()

    private val _radar = MutableStateFlow(AbilityAnalyzer.AbilityRadar())
    val radar: StateFlow<AbilityAnalyzer.AbilityRadar> = _radar.asStateFlow()

    private val _plan = MutableStateFlow<TrainingPlanGenerator.TrainingPlan?>(null)
    val plan: StateFlow<TrainingPlanGenerator.TrainingPlan?> = _plan.asStateFlow()

    /** 是否正在应用计划到课时 */
    private val _applying = MutableStateFlow(false)
    val applying: StateFlow<Boolean> = _applying.asStateFlow()

    /** 应用结果（lessonId 非空表示成功） */
    private val _appliedLessonId = MutableStateFlow<String?>(null)
    val appliedLessonId: StateFlow<String?> = _appliedLessonId.asStateFlow()

    /**
     * 加载学员数据并生成训练计划。
     * @param studentName 学员姓名
     */
    fun loadAndGenerate(studentName: String) {
        viewModelScope.launch {
            val s = studentRepo.getByName(studentName)
            _student.value = s

            // 收集一次课时数据用于计算雷达
            lessonRepo.getLessonsByStudent(studentName).collect { lessons ->
                val scores = AbilityAnalyzer.extractScores(lessons)
                val radar = AbilityAnalyzer.computeRadar(scores)
                _radar.value = radar

                val name = s?.name ?: studentName
                _plan.value = TrainingPlanGenerator.generate(name, radar)
                return@collect
            }
        }
    }

    /** 重新生成计划（用于刷新） */
    fun regenerate() {
        val s = _student.value ?: return
        _plan.value = TrainingPlanGenerator.generate(s.name, _radar.value)
    }

    /**
     * 一键应用：创建新课时并将计划动作填入训练内容。
     * @param coach 教练姓名
     * @return 创建的课时 ID（通过 appliedLessonId 状态返回）
     */
    fun applyToNewLesson(coach: String = "") {
        val s = _student.value ?: return
        val plan = _plan.value ?: return
        _applying.value = true
        viewModelScope.launch {
            try {
                val lessonId = lessonRepo.createLesson(s.name, coach)
                lessonRepo.getById(lessonId)?.let { lesson ->
                    val updated = lesson.copy(
                        content = exercisesToJson(plan.exercises),
                        lessonType = "训练课",
                        summary = "AI 推荐训练计划：${plan.weakDimensions.joinToString("、")}"
                    )
                    lessonRepo.updateLesson(updated)
                }
                _appliedLessonId.value = lessonId
            } finally {
                _applying.value = false
            }
        }
    }

    /** 清除应用结果状态（用于导航后重置） */
    fun clearApplied() {
        _appliedLessonId.value = null
    }

    /** 将推荐动作列表序列化为课时 content 字段 JSON */
    private fun exercisesToJson(items: List<TrainingPlanGenerator.RecommendedExercise>): String {
        val arr = JSONArray()
        for (re in items) {
            val ex = re.exercise
            arr.put(JSONObject().apply {
                put("name", ex.name)
                put("sets", ex.sets)
                put("reps", ex.reps)
                put("intensity", "中")
                put("done", false)
                put("note", ex.note)
            })
        }
        return arr.toString()
    }
}
