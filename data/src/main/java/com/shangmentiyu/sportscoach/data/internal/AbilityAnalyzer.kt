package com.shangmentiyu.sportscoach.core

import com.shangmentiyu.sportscoach.data.model.Lesson
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 五维能力分析器：把体测成绩映射到速度/力量/耐力/柔韧/灵敏五个维度。
 *
 * 每个维度取该学员最近一次相关项目的得分（0-100），
 * 未参与的项目不计入该维度。
 *
 * 安全性：JSON 解析全部走 [JsonSafe] 兜底，脏数据不会导致页面崩溃。
 */
object AbilityAnalyzer {

    /** 五维名称（用于UI展示） */
    val DIMENSIONS = listOf("速度", "力量", "耐力", "柔韧", "灵敏")

    /** 项目到维度的映射表 */
    private val PROJECT_MAPPING: Map<String, String> = mapOf(
        // 速度
        "50米跑" to "速度",
        "100米跑" to "速度",
        "50米×8往返跑" to "速度",
        // 力量
        "立定跳远" to "力量",
        "引体向上(男)/仰卧起坐(女)" to "力量",
        "引体向上" to "力量",
        "仰卧起坐" to "力量",
        "投掷实心球(2kg)" to "力量",
        "二级蛙跳" to "力量",
        // 耐力
        "1000米跑" to "耐力",
        "800米跑" to "耐力",
        "1000米跑(男)/800米跑(女)" to "耐力",
        "4分钟跳绳" to "耐力",
        "50米×8往返跑" to "耐力",
        // 柔韧
        "坐位体前屈" to "柔韧",
        // 灵敏
        "10米×4折返跑" to "灵敏",
        "足球" to "灵敏",
        "篮球" to "灵敏",
        "排球" to "灵敏",
        "乒乓球" to "灵敏",
        "羽毛球" to "灵敏",
        "网球" to "灵敏"
    )

    /** 单次成绩项 */
    data class ScoreEntry(
        val projectName: String,
        val value: String,
        val score: Double,
        val grade: String,
        val date: String
    )

    /** 五维能力值（0-100） */
    data class AbilityRadar(
        val speed: Float = 0f,
        val strength: Float = 0f,
        val endurance: Float = 0f,
        val flexibility: Float = 0f,
        val agility: Float = 0f
    ) {
        /** 转为有序列表，与 DIMENSIONS 对应 */
        fun toList(): List<Float> = listOf(speed, strength, endurance, flexibility, agility)
    }

    /** 单个项目的历史趋势 */
    data class TrendPoint(
        val date: String,
        val value: String,
        val score: Double
    )

    /**
     * 从所有课时记录中提取成绩列表（按时间升序）。
     * @param lessons 学员的全部课时（任意顺序）
     * @return 成绩条目列表（按日期升序）
     */
    fun extractScores(lessons: List<Lesson>): List<ScoreEntry> {
        val result = mutableListOf<ScoreEntry>()
        for (lesson in lessons) {
            if (lesson.scores.isBlank() || lesson.scores == "{}") continue
            // 使用 JsonSafe 兜底：脏数据不会导致整批成绩解析失败
            val obj = JsonSafe.parseObject(lesson.scores) ?: continue
            for (name in obj.keys()) {
                // 单条项目解析失败时跳过，不影响其他有效项
                val item = obj.optJSONObject(name) ?: continue
                result.add(
                    ScoreEntry(
                        projectName = name,
                        value = item.optString("value", ""),
                        score = item.optDouble("score", 0.0),
                        grade = item.optString("grade", ""),
                        date = lesson.date
                    )
                )
            }
        }
        // 按日期升序
        return result.sortedBy { it.date }
    }

    /**
     * 计算五维能力雷达：每个维度取最近一次相关项目的得分。
     * 若某维度无数据，默认 0。
     */
    fun computeRadar(scores: List<ScoreEntry>): AbilityRadar {
        val byDimension = mutableMapOf<String, ScoreEntry>()
        for (entry in scores) {
            val dim = PROJECT_MAPPING[entry.projectName] ?: continue
            // 取最近一次（scores 已升序，后者覆盖前者）
            byDimension[dim] = entry
        }
        return AbilityRadar(
            speed = (byDimension["速度"]?.score ?: 0.0).toFloat(),
            strength = (byDimension["力量"]?.score ?: 0.0).toFloat(),
            endurance = (byDimension["耐力"]?.score ?: 0.0).toFloat(),
            flexibility = (byDimension["柔韧"]?.score ?: 0.0).toFloat(),
            agility = (byDimension["灵敏"]?.score ?: 0.0).toFloat()
        )
    }

    /**
     * 获取指定项目的历史趋势（按日期升序）。
     * @param projectName 项目名
     * @param scores 全部成绩
     */
    fun getTrend(projectName: String, scores: List<ScoreEntry>): List<TrendPoint> {
        return scores.filter { it.projectName == projectName }
            .map { TrendPoint(it.date, it.value, it.score) }
    }

    /**
     * 获取该学员参与过的所有项目名（去重，按频次降序）。
     */
    fun getAllProjectNames(scores: List<ScoreEntry>): List<String> {
        return scores.groupingBy { it.projectName }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { it.key }
    }

    /**
     * === v28 优化3：根据体测项目名查询所属维度 ===
     *
     * 用于 [TrainingContentRecommender] 将"弱项项目"映射为"训练维度"，
     * 从而匹配对应的训练模板。
     *
     * @param projectName 体测项目名（如 "50米跑"）
     * @return 所属维度（如 "速度"）；若项目未配置映射，返回 null
     */
    fun getDimensionByProject(projectName: String): String? =
        PROJECT_MAPPING[projectName]

    /**
     * 周期对比：计算最近N天与上N天的平均分对比。
     * @return Pair(近期平均分, 上期平均分)
     *
     * 线程安全：基于 [LocalDate] 与 [ChronoUnit] 计算，无 [java.text.SimpleDateFormat] 的 Calendar 状态污染。
     */
    fun periodCompare(scores: List<ScoreEntry>, recentDays: Int = 30): Pair<Double, Double> {
        if (scores.isEmpty()) return 0.0 to 0.0
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
        val dates = scores.mapNotNull { runCatching { LocalDate.parse(it.date, formatter) }.getOrNull() }
        if (dates.isEmpty()) return 0.0 to 0.0
        val lastDate = dates.max()
        // 周期阈值（以最近一次成绩为基准前推 N 天）
        val recentStart = lastDate.minusDays(recentDays.toLong())
        val priorStart = recentStart.minusDays(recentDays.toLong())

        var recentSum = 0.0
        var recentCount = 0
        var priorSum = 0.0
        var priorCount = 0
        for (entry in scores) {
            val date = try { LocalDate.parse(entry.date, formatter) } catch (_: Exception) { null } ?: continue
            when {
                date >= recentStart -> { recentSum += entry.score; recentCount++ }
                date >= priorStart -> { priorSum += entry.score; priorCount++ }
            }
        }
        val recentAvg = if (recentCount > 0) recentSum / recentCount else 0.0
        val priorAvg = if (priorCount > 0) priorSum / priorCount else 0.0
        return recentAvg to priorAvg
    }
}
