package com.shangmentiyu.sportscoach.core

import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.Student
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.sqrt

/**
 * 数据分析器（处理器层）。
 *
 * 纯逻辑单元：基于学员历史成绩与课时记录，提供四类高级分析：
 * 1. 进步趋势预测（线性回归外推）
 * 2. 横向分位对比（同年级同性别排名）
 * 3. 班级弱项聚合（多学员雷达平均）
 * 4. 训练效果评估（前后对比）
 *
 * 无 Android 依赖，便于单元测试。
 */
object DataAnalyzer {

    /** 预测结果 */
    data class Forecast(
        val projectName: String,
        val history: List<AbilityAnalyzer.TrendPoint>,  // 历史数据点
        val predictedScore: Double,                     // 预测分数
        val slope: Double,                              // 每次提升分数（斜率）
        val confidence: Float,                          // 置信度 0-1（基于数据量与拟合度）
        val weeksAhead: Int                             // 预测周数
    )

    /** 分位对比结果 */
    data class Percentile(
        val studentName: String,
        val projectName: String,
        val studentScore: Double,
        val rank: Int,                  // 排名（1=最高）
        val total: Int,                 // 总人数
        val percentile: Float,          // 百分位 0-100（90 表示优于 90% 的人）
        val averageScore: Double,       // 该群体平均分
        val topScore: Double            // 该群体最高分
    )

    /** 班级弱项聚合 */
    data class ClassRadar(
        val dimensionAverages: Map<String, Float>,   // 各维度平均分
        val weakestDimension: String,                 // 最弱维度
        val strongestDimension: String,               // 最强维度
        val studentCount: Int,                        // 学员数
        val dimensionDistribution: Map<String, List<Float>>  // 各维度所有学员得分分布
    )

    /** 训练效果评估 */
    data class TrainingEffect(
        val dimension: String,
        val beforeScore: Float,
        val afterScore: Float,
        val improvement: Float,           // 提升幅度（正=进步）
        val improvementPercent: Float,    // 提升百分比
        val verdict: String               // 评估结论
    )

    /**
     * 1. 进步趋势预测：基于历史成绩线性回归外推未来 N 周的预测分数。
     *
     * 算法：
     * - 取该项目的所有历史数据点
     * - 最小二乘法拟合直线 y = a*x + b
     * - 外推 weeksAhead 周后的预测值
     * - 置信度 = 数据量因子 × 拟合优度(R²)
     *
     * @param projectName 项目名
     * @param scores 学员全部成绩
     * @param weeksAhead 预测周数（默认 4 周）
     */
    fun forecastProgress(
        projectName: String,
        scores: List<AbilityAnalyzer.ScoreEntry>,
        weeksAhead: Int = 4
    ): Forecast? {
        val history = AbilityAnalyzer.getTrend(projectName, scores)
        if (history.size < 2) return null  // 数据不足

        // 将日期转为相对第 N 天的整数序列
        // 线程安全：使用 [LocalDate] + [ChronoUnit.DAYS.between]，替代 [java.text.SimpleDateFormat] 解析
        val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
        val baseDate = try { LocalDate.parse(history.first().date, dateFormat) } catch (_: Exception) { null }

        // x = 相对天数，y = 分数
        val points = history.mapNotNull { tp ->
            val date = try { LocalDate.parse(tp.date, dateFormat) } catch (_: Exception) { null }
                ?: return@mapNotNull null
            val base = baseDate ?: return@mapNotNull null
            val dayOffset = ChronoUnit.DAYS.between(base, date).toFloat()
            dayOffset to tp.score.toFloat()
        }
        if (points.size < 2) return null

        // 最小二乘法拟合
        val n = points.size
        val sumX = points.sumOf { it.first.toDouble() }
        val sumY = points.sumOf { it.second.toDouble() }
        val sumXY = points.sumOf { it.first.toDouble() * it.second.toDouble() }
        val sumX2 = points.sumOf { it.first.toDouble() * it.first.toDouble() }
        val denom = n * sumX2 - sumX * sumX
        if (denom == 0.0) return null
        val slope = (n * sumXY - sumX * sumY) / denom
        val intercept = (sumY - slope * sumX) / n

        // 拟合优度 R²
        val meanY = sumY / n
        val ssTot = points.sumOf { (it.second - meanY).let { d -> d * d } }
        val ssRes = points.sumOf {
            val predicted = slope * it.first + intercept
            val d = it.second - predicted
            d * d
        }
        val r2 = if (ssTot > 0) (1 - ssRes / ssTot).coerceIn(0.0, 1.0) else 0.0

        // 预测：当前最后一个数据点 + weeksAhead 周
        val lastX = points.last().first
        val predictX = lastX + weeksAhead * 7f
        val predicted = (slope * predictX + intercept).coerceIn(0.0, 100.0)

        // 置信度：数据量因子(>=5 满分) × 拟合优度
        val dataFactor = (n / 5.0).coerceAtMost(1.0)
        val confidence = (dataFactor * r2).toFloat()

        return Forecast(
            projectName = projectName,
            history = history,
            predictedScore = predicted,
            slope = slope,
            confidence = confidence,
            weeksAhead = weeksAhead
        )
    }

    /**
     * 2. 横向分位对比：计算学员在某项目上的同群体排名。
     *
     * @param studentName 目标学员
     * @param projectName 项目名
     * @param allLessonsByStudent 所有学员的课时数据（姓名 → 课时列表）
     * @param gradeFilter 年级过滤（同年级才对比），null=不过滤
     * @param genderFilter 性别过滤，null=不过滤
     */
    fun computePercentile(
        studentName: String,
        projectName: String,
        allLessonsByStudent: Map<String, List<Lesson>>,
        allStudents: List<Student>,
        gradeFilter: String? = null,
        genderFilter: String? = null
    ): Percentile? {
        // 筛选符合条件的学员（含目标学员）
        val filteredNames = allStudents.filter { s ->
            (gradeFilter == null || s.grade == gradeFilter) &&
            (genderFilter == null || s.gender == genderFilter)
        }.map { it.name }.toSet()

        // 取每个学员该项目最近一次得分
        val scoreByName = mutableMapOf<String, Double>()
        for ((name, lessons) in allLessonsByStudent) {
            if (name !in filteredNames) continue
            val scores = AbilityAnalyzer.extractScores(lessons)
            val trend = AbilityAnalyzer.getTrend(projectName, scores)
            if (trend.isNotEmpty()) {
                scoreByName[name] = trend.last().score
            }
        }

        val targetScore = scoreByName[studentName] ?: return null
        if (scoreByName.isEmpty()) return null

        // 按分数降序排名
        val sorted = scoreByName.entries.sortedByDescending { it.value }
        val rank = sorted.indexOfFirst { it.key == studentName } + 1
        val total = sorted.size
        val percentile = ((total - rank).toFloat() / total * 100f)
        val average = scoreByName.values.average()
        val top = scoreByName.values.maxOrNull() ?: 0.0

        return Percentile(
            studentName = studentName,
            projectName = projectName,
            studentScore = targetScore,
            rank = rank,
            total = total,
            percentile = percentile,
            averageScore = average,
            topScore = top
        )
    }

    /**
     * 3. 班级弱项聚合：将多个学员的五维雷达平均，找出班级整体短板。
     *
     * @param studentRadars 学员姓名 → 雷达数据
     */
    fun aggregateClassRadar(studentRadars: Map<String, AbilityAnalyzer.AbilityRadar>): ClassRadar {
        if (studentRadars.isEmpty()) {
            return ClassRadar(
                dimensionAverages = AbilityAnalyzer.DIMENSIONS.associateWith { 0f },
                weakestDimension = "—",
                strongestDimension = "—",
                studentCount = 0,
                dimensionDistribution = emptyMap()
            )
        }

        val dims = AbilityAnalyzer.DIMENSIONS
        val averages = mutableMapOf<String, Float>()
        val distribution = mutableMapOf<String, MutableList<Float>>()

        for (dim in dims) {
            val values = studentRadars.values.map { radarScore(it, dim) }.filter { it > 0f }
            distribution[dim] = values.toMutableList()
            averages[dim] = if (values.isNotEmpty()) values.average().toFloat() else 0f
        }

        // 找最弱/最强（仅在有数据的维度中比较）
        val validAverages = averages.filter { it.value > 0f }
        val weakest = validAverages.minByOrNull { it.value }?.key ?: "—"
        val strongest = validAverages.maxByOrNull { it.value }?.key ?: "—"

        return ClassRadar(
            dimensionAverages = averages,
            weakestDimension = weakest,
            strongestDimension = strongest,
            studentCount = studentRadars.size,
            dimensionDistribution = distribution
        )
    }

    /**
     * 4. 训练效果评估：对比训练前后某维度的得分变化。
     *
     * @param dimension 维度名
     * @param beforeScore 训练前得分
     * @param afterScore 训练后得分
     */
    fun evaluateTrainingEffect(
        dimension: String,
        beforeScore: Float,
        afterScore: Float
    ): TrainingEffect {
        val improvement = afterScore - beforeScore
        val improvementPercent = if (beforeScore > 0) (improvement / beforeScore * 100f) else 0f
        val verdict = when {
            improvement >= 15 -> "显著进步"
            improvement >= 5 -> "稳步提升"
            improvement >= -2 -> "基本持平"
            improvement >= -10 -> "略有下滑"
            else -> "明显退步"
        }
        return TrainingEffect(
            dimension = dimension,
            beforeScore = beforeScore,
            afterScore = afterScore,
            improvement = improvement,
            improvementPercent = improvementPercent,
            verdict = verdict
        )
    }

    /**
     * 批量评估所有维度的训练效果。
     *
     * @param before 训练前雷达
     * @param after 训练后雷达
     */
    fun evaluateAllDimensions(
        before: AbilityAnalyzer.AbilityRadar,
        after: AbilityAnalyzer.AbilityRadar
    ): List<TrainingEffect> {
        return AbilityAnalyzer.DIMENSIONS.map { dim ->
            evaluateTrainingEffect(dim, radarScore(before, dim), radarScore(after, dim))
        }
    }

    /** 从 AbilityRadar 取出指定维度的得分 */
    private fun radarScore(radar: AbilityAnalyzer.AbilityRadar, dim: String): Float = when (dim) {
        "速度" -> radar.speed
        "力量" -> radar.strength
        "耐力" -> radar.endurance
        "柔韧" -> radar.flexibility
        "灵敏" -> radar.agility
        else -> 0f
    }
}
