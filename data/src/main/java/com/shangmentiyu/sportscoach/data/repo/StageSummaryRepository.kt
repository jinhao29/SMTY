package com.shangmentiyu.sportscoach.data.repo

import com.shangmentiyu.sportscoach.data.model.Lesson
import org.json.JSONArray
import org.json.JSONObject

/**
 * 阶段性总结 Repository（管理层）。
 *
 * 从原 [OperationRepository] 拆分而来，承载阶段汇总的聚合计算。
 *
 * 职责：
 * - 聚合指定学员在指定时间范围内的课时记录，计算阶段总结（[computeStageSummary]）
 *
 * 设计说明：
 * - 数据类 [OperationRepository.StageSummary] / [OperationRepository.ScoreProgressItem]
 *   保留在协调器 [OperationRepository] 内（保持旧的外部类型引用兼容），本类只做纯计算。
 * - 不直接访问 DAO：课时数据由调用方一次性取回后传入，保持本类无状态、可单测。
 */
class StageSummaryRepository {

    /**
     * 计算学员的阶段总结。
     * @param studentName 学员姓名
     * @param startDate 起始日期 YYYY-MM-DD（含）
     * @param endDate 结束日期 YYYY-MM-DD（含）
     */
    suspend fun computeStageSummary(
        studentName: String,
        startDate: String,
        endDate: String,
        allLessons: List<Lesson>
    ): OperationRepository.StageSummary {
        val inRange = allLessons.filter { l ->
            l.studentName == studentName && l.date in startDate..endDate
        }.sortedBy { it.date }

        val total = inRange.size
        val attended = inRange.count { it.attendance !in listOf("请假", "旷课") }
        val attendanceRate = if (total > 0) attended.toFloat() / total else 0f
        val avgPerf = if (total > 0) inRange.map { it.performance }.average().toFloat() else 0f
        val avgDur = if (total > 0) inRange.map { it.duration }.average().toInt() else 0
        val attitudeDist = inRange.groupingBy { it.attitude }.eachCount()
        val allExercises = inRange.flatMap { parseExercisesForStats(it.content) }
        val doneEx = allExercises.count { it.first }
        val totalEx = allExercises.size
        val completionRate = if (totalEx > 0) doneEx.toFloat() / totalEx else 0f

        // 成绩进步对比（首末对比）
        val scoreProgress = computeScoreProgress(inRange)

        val firstDate = inRange.firstOrNull()?.date ?: ""
        val lastDate = inRange.lastOrNull()?.date ?: ""
        val summaryText = buildSummaryText(
            studentName, startDate, endDate, total, attended, attendanceRate,
            avgPerf, avgDur, completionRate, scoreProgress
        )

        return OperationRepository.StageSummary(
            studentName = studentName,
            startDate = startDate,
            endDate = endDate,
            totalLessons = total,
            attendedLessons = attended,
            attendanceRate = attendanceRate,
            avgPerformance = avgPerf,
            avgDuration = avgDur,
            attitudeDistribution = attitudeDist,
            completedExerciseRate = completionRate,
            scoreProgress = scoreProgress,
            firstLessonDate = firstDate,
            lastLessonDate = lastDate,
            summaryText = summaryText
        )
    }

    /** 解析课时训练内容为 (done, name) 列表 */
    private fun parseExercisesForStats(json: String): List<Pair<Boolean, String>> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                obj.optBoolean("done", false) to obj.optString("name")
            }
        } catch (_: Exception) { emptyList() }
    }

    /** 计算各项成绩的首末对比 */
    private fun computeScoreProgress(lessons: List<Lesson>): List<OperationRepository.ScoreProgressItem> {
        val scoreMap = mutableMapOf<String, MutableList<Float>>()
        for (l in lessons) {
            if (l.scores.isBlank() || l.scores == "{}") continue
            try {
                val obj = JSONObject(l.scores)
                obj.keys().forEach { key ->
                    val score = obj.optJSONObject(key)?.optDouble("score", 0.0) ?: 0.0
                    scoreMap.getOrPut(key) { mutableListOf() }.add(score.toFloat())
                }
            } catch (_: Exception) { }
        }
        return scoreMap.map { (name, scores) ->
            val first = scores.firstOrNull() ?: 0f
            val last = scores.lastOrNull() ?: 0f
            OperationRepository.ScoreProgressItem(name, first, last, last - first, scores.size)
        }.sortedByDescending { it.samples }
    }

    /** 生成阶段性总结文字 */
    private fun buildSummaryText(
        studentName: String, startDate: String, endDate: String,
        total: Int, attended: Int, attendanceRate: Float,
        avgPerf: Float, avgDur: Int, completionRate: Float,
        scoreProgress: List<OperationRepository.ScoreProgressItem>
    ): String {
        val sb = StringBuilder()
        sb.append("【$studentName 阶段总结 $startDate ~ $endDate】\n\n")
        sb.append("本阶段共安排 $total 节课，实到 $attended 节，")
        sb.append("出勤率 ${"%.0f".format(attendanceRate * 100)}%。\n")
        sb.append("平均课时时长 ${avgDur} 分钟，整体表现评分 ${"%.1f".format(avgPerf)}/10，")
        sb.append("训练动作完成率 ${"%.0f".format(completionRate * 100)}%。\n\n")
        if (scoreProgress.isNotEmpty()) {
            sb.append("成绩进步：\n")
            for (p in scoreProgress) {
                val arrow = if (p.delta > 0) "↑" else if (p.delta < 0) "↓" else "→"
                sb.append("· ${p.name}: ${"%.1f".format(p.firstScore)} → ${"%.1f".format(p.lastScore)} $arrow ${"%.1f".format(Math.abs(p.delta))}\n")
            }
        }
        return sb.toString()
    }
}
