package com.shangmentiyu.sportscoach.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangmentiyu.sportscoach.core.JsonSafe
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.data.repo.LessonRepository
import com.shangmentiyu.sportscoach.data.repo.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 成绩记录 ViewModel（协调层）。
 *
 * 协调 StudentRepository 与 LessonRepository，从 Lesson 表的 scores JSON 中
 * 解析学员真实的体测成绩记录，按项目维度聚合展示历史时间线。
 *
 * 不再使用 DataAnalyzer 进行 AI 虚拟分析（趋势预测/分位对比/雷达聚合）。
 */
class AnalyticsViewModel(
    private val studentRepo: StudentRepository,
    private val lessonRepo: LessonRepository
) : ViewModel() {

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    private val appExceptionHandler =
        com.shangmentiyu.sportscoach.core.CoroutineExt.createAppExceptionHandler(_toast, "AnalyticsViewModel")

    /** 所有学员 */
    private val _students = MutableStateFlow<List<Student>>(emptyList())
    val students: StateFlow<List<Student>> = _students.asStateFlow()

    /** 当前选中的学员姓名 */
    private val _selectedStudent = MutableStateFlow<String?>(null)
    val selectedStudent: StateFlow<String?> = _selectedStudent.asStateFlow()

    /** 当前学员的所有课时（按日期升序） */
    private val _lessons = MutableStateFlow<List<Lesson>>(emptyList())
    val lessons: StateFlow<List<Lesson>> = _lessons.asStateFlow()

    /** 当前学员按项目分组的历史成绩（按项目名 → 时间倒序的成绩列表） */
    private val _recordsByProject = MutableStateFlow<Map<String, List<ScoreRecord>>>(emptyMap())
    val recordsByProject: StateFlow<Map<String, List<ScoreRecord>>> = _recordsByProject.asStateFlow()

    /** 概览统计 */
    private val _overview = MutableStateFlow(OverviewStats())
    val overview: StateFlow<OverviewStats> = _overview.asStateFlow()

    /** 加载状态 */
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        loadStudents()
    }

    /** 加载学员列表并默认选中第一个 */
    private fun loadStudents() {
        viewModelScope.launch(appExceptionHandler) {
            try {
                studentRepo.getAllStudents().collectLatest { list ->
                    _students.value = list
                    _loading.value = false
                    if (_selectedStudent.value == null && list.isNotEmpty()) {
                        selectStudent(list.first().name)
                    }
                }
            } catch (e: Exception) {
                // === 断流修复：Flow 订阅/查询异常时记录日志，避免静默失败 ===
                // 异常后 collectLatest 终止，学生列表不再更新；日志便于定位根因
                android.util.Log.e("ScoreError", "加载学员列表失败", e)
            } finally {
                // === 断流修复：任何异常路径都必须复位加载态，杜绝"一直加载中" ===
                _loading.value = false
            }
        }
    }

    /** 选择学员，加载其课时并按项目聚合 */
    fun selectStudent(name: String) {
        _selectedStudent.value = name
        viewModelScope.launch(appExceptionHandler) {
            val list = try {
                val studentId = _students.value.firstOrNull { it.name == name }?.studentId
                lessonRepo.getLessonsByStudentDual(studentId, name).first()
            } catch (_: Exception) {
                emptyList()
            }
            _lessons.value = list
            rebuildRecords(list)
        }
    }

    /** 从课时列表解析成绩记录，按项目分组 */
    private fun rebuildRecords(lessons: List<Lesson>) {
        val map = mutableMapOf<String, MutableList<ScoreRecord>>()
        for (lesson in lessons) {
            if (lesson.scores.isBlank() || lesson.scores == "{}") continue
            // 使用 JsonSafe 兜底：脏数据不会导致整页崩溃
            val obj = JsonSafe.parseObject(lesson.scores) ?: continue
            for (key in obj.keys()) {
                // 单条项目解析失败时跳过，不影响其他有效项
                val item = obj.optJSONObject(key) ?: continue
                val value = item.optString("value", "")
                val score = if (item.has("score") && !item.isNull("score")) item.optDouble("score", 0.0) else 0.0
                val grade = item.optString("grade", "")
                if (value.isBlank()) continue
                map.getOrPut(key) { mutableListOf() }.add(
                    ScoreRecord(
                        projectName = key,
                        date = lesson.date,
                        value = value,
                        score = score,
                        grade = grade,
                        lessonId = lesson.id
                    )
                )
            }
        }
        // 每个项目按日期降序（最近在前）
        val sortedMap = map.mapValues { (_, v) -> v.sortedByDescending { it.date } }
        _recordsByProject.value = sortedMap

        // 概览统计
        val allRecords = sortedMap.values.flatten()
        _overview.value = OverviewStats(
            totalCount = allRecords.size,
            projectCount = sortedMap.size,
            latestDate = allRecords.maxByOrNull { it.date }?.date ?: "—"
        )
    }

    /**
     * 删除指定课时的指定项目成绩。
     *
     * 从 Lesson.scores JSON 中移除对应项目的 key，然后更新课时记录。
     * 若移除后 scores 为空 JSON，则写入 "{}"。
     *
     * @param lessonId 课时 ID
     * @param projectName 项目名（scores JSON 的 key）
     * @param onDone 完成回调（主线程），参数为是否成功
     */
    fun deleteScore(lessonId: String, projectName: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch(appExceptionHandler) {
            try {
                val lesson = lessonRepo.getById(lessonId)
                if (lesson == null) {
                    onDone(false)
                    return@launch
                }
                val obj = JsonSafe.parseObject(lesson.scores) ?: JSONObject()
                obj.remove(projectName)
                val newScores = obj.toString()
                lessonRepo.updateLesson(lesson.copy(scores = newScores))
                // 刷新当前学员的成绩列表
                _selectedStudent.value?.let { selectStudent(it) }
                onDone(true)
            } catch (_: Exception) {
                onDone(false)
            }
        }
    }

    /** 单条真实成绩记录 */
    data class ScoreRecord(
        val projectName: String,   // 项目名（如 1分钟跳绳）
        val date: String,          // 测试日期 YYYY-MM-DD
        val value: String,         // 实测值（如 138）
        val score: Double,         // 分数 0-100
        val grade: String,         // 等级（优秀/良好/及格/不及格）
        val lessonId: String = ""  // 来源课时 ID（用于编辑/删除定位）
    )

    /** 概览统计 */
    data class OverviewStats(
        val totalCount: Int = 0,    // 总成绩条数
        val projectCount: Int = 0,  // 参与项目数
        val latestDate: String = "—" // 最近测试日期
    )
}
