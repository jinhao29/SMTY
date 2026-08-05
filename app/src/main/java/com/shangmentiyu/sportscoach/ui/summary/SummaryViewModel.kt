package com.shangmentiyu.sportscoach.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangmentiyu.sportscoach.core.JsonSafe
import com.shangmentiyu.sportscoach.core.Standards
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
 * 课后小结 ViewModel：加载课堂记录与学员信息，生成/编辑/保存小结。
 *
 * 安全性：训练内容与成绩解析均走 [JsonSafe] 兜底，脏数据返回空，
 * 不会因为 JSON 异常导致小结生成失败。
 */
class SummaryViewModel(
    private val lessonRepo: LessonRepository,
    private val studentRepo: StudentRepository
) : ViewModel() {

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    private val appExceptionHandler =
        com.shangmentiyu.sportscoach.core.CoroutineExt.createAppExceptionHandler(_toast, "SummaryViewModel")

    private val _lesson = MutableStateFlow<Lesson?>(null)
    val lesson: StateFlow<Lesson?> = _lesson.asStateFlow()

    private val _student = MutableStateFlow<Student?>(null)
    val student: StateFlow<Student?> = _student.asStateFlow()

    private val _summary = MutableStateFlow("")
    val summary: StateFlow<String> = _summary.asStateFlow()

    /** 按 lessonId 加载课堂与学员，若小结为空则自动生成。 */
    fun load(lessonId: String) {
        viewModelScope.launch(appExceptionHandler) {
            val l = lessonRepo.getById(lessonId)
            _lesson.value = l
            if (l != null) {
                _student.value = studentRepo.getByName(l.studentName)
                _summary.value = l.summary.ifBlank { generateSummary(l, _student.value) }
            }
        }
    }

    /** 更新小结文本。 */
    fun updateSummary(text: String) {
        _summary.value = text
    }

    /** 基于当前课堂记录重新生成小结。 */
    fun regenerate() {
        val l = _lesson.value ?: return
        _summary.value = generateSummary(l, _student.value)
    }

    /** 保存小结到数据库，完成后回调 onDone。 */
    fun save(onDone: () -> Unit) {
        val l = _lesson.value ?: return
        viewModelScope.launch(appExceptionHandler) {
            lessonRepo.updateLesson(l.copy(summary = _summary.value))
            onDone()
        }
    }

    /** 根据课堂记录与学员信息拼装小结文本。 */
    private fun generateSummary(lesson: Lesson, student: Student?): String {
        val sb = StringBuilder()
        val gradeLabel = student?.let { Standards.gradeFullLabel(it.grade) } ?: ""
        sb.append("【${lesson.studentName} $gradeLabel 课堂小结 ${lesson.date}】\n\n")

        sb.append("课时信息：${lesson.duration}分钟 · ${lesson.lessonType} · ${lesson.attendance} · ${lesson.coach.ifBlank { "未指定教练" }}")
        if (lesson.location.isNotBlank()) sb.append(" · ${lesson.location}")
        sb.append("\n\n")

        // 训练内容
        val exercises = parseExercises(lesson.content)
        if (exercises.isNotEmpty()) {
            val doneCount = exercises.count { it.done }
            sb.append("训练内容（$doneCount/${exercises.size}完成）：\n")
            for (item in exercises) {
                val mark = if (item.done) "✓" else "○"
                sb.append("$mark ${item.name} ${item.sets}组×${item.reps}（强度${item.intensity}）\n")
            }
            sb.append("\n")
        }

        // 成绩
        val scores = parseScores(lesson.scores)
        if (scores.isNotEmpty()) {
            sb.append("成绩：\n")
            for ((name, info) in scores) {
                val score = info.optDouble("score", 0.0)
                val grade = info.optString("grade", "")
                sb.append("$name: ${String.format("%.1f", score)}分（$grade）\n")
            }
            sb.append("\n")
        }

        // 评价
        sb.append("课堂评价：${lesson.attitude} · 整体表现${lesson.performance}/10\n")
        if (lesson.nextGoal.isNotBlank()) {
            sb.append("下次课目标：${lesson.nextGoal}\n")
        }

        // 教练寄语：优先使用教练自填内容，为空时给默认提示
        sb.append("\n教练寄语：")
        if (lesson.coachComment.isNotBlank()) {
            sb.append(lesson.coachComment)
        } else {
            sb.append("（教练可在课堂记录页填写寄语）")
        }

        return sb.toString()
    }

    /** 解析训练内容 JSON 数组为 ExerciseItem 列表。 */
    private fun parseExercises(json: String): List<ExerciseItem> {
        if (json.isBlank()) return emptyList()
        // 使用 JsonSafe 兜底：脏数据返回空列表，不崩溃
        val arr = JsonSafe.parseArray(json) ?: return emptyList()
        val result = mutableListOf<ExerciseItem>()
        for (i in 0 until arr.length()) {
            // 单条 item 解析失败时跳过，不影响其他有效项
            val obj = arr.optJSONObject(i) ?: continue
            result.add(ExerciseItem(
                name = obj.optString("name"),
                sets = obj.optInt("sets", 3),
                reps = obj.optString("reps"),
                intensity = obj.optString("intensity", "中"),
                done = obj.optBoolean("done", false),
                note = obj.optString("note")
            ))
        }
        return result
    }

    /** 解析成绩 JSON 对象为 (项目名, 成绩JSONObject) 列表。 */
    private fun parseScores(json: String): List<Pair<String, JSONObject>> {
        if (json.isBlank() || json == "{}") return emptyList()
        // 使用 JsonSafe 兜底：脏数据返回空列表，不崩溃
        val obj = JsonSafe.parseObject(json) ?: return emptyList()
        val result = mutableListOf<Pair<String, JSONObject>>()
        for (key in obj.keys()) {
            // 单条项目解析失败时跳过，不影响其他有效项
            val item = obj.optJSONObject(key) ?: continue
            result.add(key to item)
        }
        return result
    }
}
