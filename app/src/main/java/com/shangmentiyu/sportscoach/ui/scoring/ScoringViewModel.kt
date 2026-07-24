package com.shangmentiyu.sportscoach.ui.scoring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangmentiyu.sportscoach.core.JsonSafe
import com.shangmentiyu.sportscoach.core.Scorer
import com.shangmentiyu.sportscoach.core.ScoreResult
import com.shangmentiyu.sportscoach.core.Standards
import com.shangmentiyu.sportscoach.core.Std
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.ScoreItem
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.data.repo.LessonRepository
import com.shangmentiyu.sportscoach.data.repo.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class ScoringViewModel(
    private val lessonRepo: LessonRepository,
    private val studentRepo: StudentRepository
) : ViewModel() {

    private val _students = MutableStateFlow<List<Student>>(emptyList())
    val students: StateFlow<List<Student>> = _students.asStateFlow()

    private val _selectedStudent = MutableStateFlow<Student?>(null)
    val selectedStudent: StateFlow<Student?> = _selectedStudent.asStateFlow()

    private val _standards = MutableStateFlow<List<Std>>(emptyList())
    val standards: StateFlow<List<Std>> = _standards.asStateFlow()

    /** 自定义项目名集合（不在体测标准中，仅记录原值，不计算得分） */
    private val _customProjects = MutableStateFlow<Set<String>>(emptySet())
    val customProjects: StateFlow<Set<String>> = _customProjects.asStateFlow()

    // 当前输入的成绩 {项目名: 输入文本}
    private val _scoreInputs = MutableStateFlow<Map<String, String>>(emptyMap())
    val scoreInputs: StateFlow<Map<String, String>> = _scoreInputs.asStateFlow()

    // 计算后的成绩 {项目名: ScoreResult}
    private val _scoreResults = MutableStateFlow<Map<String, ScoreResult>>(emptyMap())
    val scoreResults: StateFlow<Map<String, ScoreResult>> = _scoreResults.asStateFlow()

    private var currentLesson: Lesson? = null
    private var lessonId: String? = null

    init {
        viewModelScope.launch {
            studentRepo.getAllStudents().collect { _students.value = it }
        }
    }

    fun loadLesson(id: String) {
        lessonId = id
        viewModelScope.launch {
            val lesson = lessonRepo.getById(id)
            if (lesson != null) {
                currentLesson = lesson
                // 找到对应学员
                val student = studentRepo.getByName(lesson.studentName)
                if (student != null) {
                    selectStudent(student)
                }
                // 加载已有成绩
                if (lesson.scores.isNotBlank() && lesson.scores != "{}") {
                    loadExistingScores(lesson.scores)
                }
            }
        }
    }

    private fun loadExistingScores(scoresJson: String) {
        // 使用 JsonSafe 兜底：脏数据不会导致页面崩溃
        val obj = JsonSafe.parseObject(scoresJson) ?: return
        val inputs = mutableMapOf<String, String>()
        val results = mutableMapOf<String, ScoreResult>()
        val customs = mutableSetOf<String>()
        // 使用显式 while 循环避免 for-in 的 iterator() 歧义
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            // 单条项目解析失败时跳过
            val item = obj.optJSONObject(key) ?: continue
            val value = item.optString("value", "")
            inputs[key] = value
            val score = if (item.has("score") && !item.isNull("score")) item.optDouble("score", 0.0) else 0.0
            val grade = item.optString("grade", "")
            results[key] = ScoreResult(score, grade, null, true, "")
            // 不在体测标准中的项目标记为自定义
            if (Standards.findStd(_standards.value, key) == null) {
                customs.add(key)
            }
        }
        _scoreInputs.value = inputs
        _scoreResults.value = results
        _customProjects.value = customs
    }

    fun selectStudent(student: Student) {
        _selectedStudent.value = student
        _standards.value = Standards.getStandardsByGrade(student.grade)
        _customProjects.value = emptySet()
    }

    /**
     * 添加自定义项目：用户可录入不在体测标准中的项目（仅记录原值，不计算得分）。
     * @param name 项目名（不能与已有标准项目重复）
     */
    fun addCustomProject(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return false
        // 不能与体测标准项目重复
        if (Standards.findStd(_standards.value, trimmed) != null) return false
        // 不能与已有自定义项目重复
        if (_customProjects.value.contains(trimmed)) return false
        _customProjects.value = _customProjects.value + trimmed
        return true
    }

    /** 移除自定义项目 */
    fun removeCustomProject(name: String) {
        _customProjects.value = _customProjects.value - name
        val newInputs = _scoreInputs.value.toMutableMap()
        newInputs.remove(name)
        _scoreInputs.value = newInputs
        val newResults = _scoreResults.value.toMutableMap()
        newResults.remove(name)
        _scoreResults.value = newResults
    }

    fun updateScore(projectName: String, rawValue: String) {
        val student = _selectedStudent.value ?: return
        val stds = _standards.value
        val std = Standards.findStd(stds, projectName)

        val newInputs = _scoreInputs.value.toMutableMap()
        if (rawValue.isBlank()) {
            newInputs.remove(projectName)
        } else {
            newInputs[projectName] = rawValue
        }
        _scoreInputs.value = newInputs

        val newResults = _scoreResults.value.toMutableMap()
        if (rawValue.isBlank()) {
            newResults.remove(projectName)
        } else if (std != null) {
            // 标准项目：计算得分
            val result = Scorer.calcScore(std, student.gender, rawValue)
            newResults[projectName] = result
        } else {
            // 自定义项目：不计算得分，仅标记为自定义
            newResults[projectName] = ScoreResult(null, "自定义", null, true, "")
        }
        _scoreResults.value = newResults
    }

    fun save(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val student = _selectedStudent.value
        val results = _scoreResults.value

        if (student == null) {
            onError("请先选择学员")
            return
        }
        if (results.isEmpty()) {
            onError("请至少输入一项成绩")
            return
        }

        viewModelScope.launch {
            // 构建 scores JSON
            val scoresObj = JSONObject()
            for ((name, result) in results) {
                if (result.ok) {
                    val item = JSONObject()
                    item.put("value", _scoreInputs.value[name] ?: "")
                    // 自定义项目 score 为 null 时存 0.0，grade 标记为"自定义"
                    item.put("score", result.score ?: 0.0)
                    item.put("grade", result.grade)
                    scoresObj.put(name, item)
                }
            }

            val lid = lessonId
            if (lid != null) {
                val lesson = lessonRepo.getById(lid)
                if (lesson != null) {
                    lessonRepo.updateLesson(lesson.copy(scores = scoresObj.toString()))
                }
            } else {
                // 无关联课时，创建新记录
                val newId = lessonRepo.createLesson(student.name, "")
                val lesson = lessonRepo.getById(newId)
                if (lesson != null) {
                    lessonRepo.updateLesson(lesson.copy(scores = scoresObj.toString()))
                }
            }
            onSuccess()
        }
    }
}
