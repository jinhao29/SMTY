package com.shangmentiyu.sportscoach.ui.growth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangmentiyu.sportscoach.core.AbilityAnalyzer
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.data.repo.LessonRepository
import com.shangmentiyu.sportscoach.data.repo.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 成长档案 ViewModel：基于真实成绩数据提供统计与展示。
 *
 * 设计原则：数据驱动，不依赖五维齐全。即使学员只有 1-2 次成绩，
 * 也能展示有意义的"最近成绩"和"个人最佳"，避免数据稀疏时的失真分析。
 */
class GrowthViewModel(
    private val studentRepo: StudentRepository,
    private val lessonRepo: LessonRepository
) : ViewModel() {

    private val _student = MutableStateFlow<Student?>(null)
    val student: StateFlow<Student?> = _student.asStateFlow()

    private val _lessons = MutableStateFlow<List<Lesson>>(emptyList())
    val lessons: StateFlow<List<Lesson>> = _lessons.asStateFlow()

    /** 全部成绩条目（按日期升序） */
    private val _scores = MutableStateFlow<List<AbilityAnalyzer.ScoreEntry>>(emptyList())
    val scores: StateFlow<List<AbilityAnalyzer.ScoreEntry>> = _scores.asStateFlow()

    /** 最近一次课堂的成绩条目 */
    private val _latestScores = MutableStateFlow<List<AbilityAnalyzer.ScoreEntry>>(emptyList())
    val latestScores: StateFlow<List<AbilityAnalyzer.ScoreEntry>> = _latestScores.asStateFlow()

    /** 各项目的个人最佳（按分数降序） */
    private val _personalBests = MutableStateFlow<List<AbilityAnalyzer.ScoreEntry>>(emptyList())
    val personalBests: StateFlow<List<AbilityAnalyzer.ScoreEntry>> = _personalBests.asStateFlow()

    /** 统计信息 */
    private val _stats = MutableStateFlow(GrowthStats())
    val stats: StateFlow<GrowthStats> = _stats.asStateFlow()

    /** 加载学员的全部数据 */
    fun load(studentName: String) {
        viewModelScope.launch {
            _student.value = studentRepo.getByName(studentName)
            lessonRepo.getLessonsByStudent(studentName).collect { lessonList ->
                _lessons.value = lessonList
                val scoreList = AbilityAnalyzer.extractScores(lessonList)
                _scores.value = scoreList

                // 最近一次课堂的成绩：取最新日期的所有成绩
                val latestDate = lessonList.maxByOrNull { it.date }?.date ?: ""
                _latestScores.value = if (latestDate.isEmpty()) emptyList()
                                      else scoreList.filter { it.date == latestDate }

                // 个人最佳：每个项目取分数最高的一次
                _personalBests.value = scoreList
                    .groupBy { it.projectName }
                    .mapNotNull { (_, entries) -> entries.maxByOrNull { it.score } }
                    .sortedByDescending { it.score }

                // 统计
                _stats.value = GrowthStats(
                    totalLessons = lessonList.size,
                    totalMinutes = lessonList.sumOf { it.duration },
                    latestDate = latestDate,
                    onTimeRate = if (lessonList.isNotEmpty()) {
                        lessonList.count { it.attendance == "准时" }.toFloat() / lessonList.size
                    } else 0f
                )
            }
        }
    }
}

/**
 * 成长档案统计数据。
 *
 * @param totalLessons 累计课时数
 * @param totalMinutes 总训练时长（分钟）
 * @param latestDate 最近训练日期 YYYY-MM-DD
 * @param onTimeRate 出勤准时率（0-1）
 */
data class GrowthStats(
    val totalLessons: Int = 0,
    val totalMinutes: Int = 0,
    val latestDate: String = "",
    val onTimeRate: Float = 0f
) {
    /** 训练总时长（小时） */
    val totalHours: Float get() = totalMinutes / 60f

    /** 最近训练日期短格式（MM-DD） */
    val latestDateShort: String get() = if (latestDate.length >= 10) latestDate.takeLast(5) else latestDate
}
