package com.shangmentiyu.sportscoach.core

import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.Student
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 家长报告生成器：纯逻辑处理器，从课时记录中提炼周报/月报内容。
 *
 * 输出 JSON 结构：
 * {
 *   "studentName": "...",
 *   "reportType": "周报"/"月报",
 *   "period": "YYYY-MM-DD ~ YYYY-MM-DD",
 *   "stats": { "lessonCount": N, "totalMinutes": N, "avgPerformance": N, "attendance": {...} },
 *   "scoreChanges": [ {project, before, after, delta, trend} ],
 *   "milestones": [ {type, title, desc, date} ],
 *   "radar": {speed, strength, endurance, flexibility, agility},
 *   "coachComment": "...",
 *   "suggestion": "..."
 * }
 *
 * 安全性：成绩 JSON 解析全部走 [JsonSafe] 兜底，
 * 单条脏数据跳过，不影响整体报告生成。
 */
object ReportGenerator {

    /** 报告类型 */
    const val TYPE_WEEKLY = "周报"
    const val TYPE_MONTHLY = "月报"

    /** 里程碑类型 */
    const val MILESTONE_FIRST_TEST = "首测"           // 首次体测
    const val MILESTONE_BREAKTHROUGH = "突破"         // 项目突破 90 分
    const val MILESTONE_STREAK = "连续进步"           // 同项目连续 N 次提升
    const val MILESTONE_FULL_ATTEND = "全勤"          // 周期内无缺勤
    const val MILESTONE_HIGH_PERFORMANCE = "高表现"   // 平均表现 ≥ 9

    /**
     * 日期格式化工具：[SimpleDateFormat] 非线程安全，禁止作为成员变量持有，
     * 每次调用新建实例，避免多协程并发解析时 Calendar 状态污染。
     */
    private fun newDateFormat(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * 计算报告周期起止日期。
     * @param type TYPE_WEEKLY 或 TYPE_MONTHLY
     * @param refDate 参考日期（默认今天）
     * @return Pair<startDate, endDate>，endDate 为 refDate，startDate 为前推 N 天
     */
    fun computePeriod(type: String, refDate: java.util.Date = java.util.Date()): Pair<String, String> {
        val endCal = Calendar.getInstance().apply { time = refDate }
        val days = if (type == TYPE_WEEKLY) 7 else 30
        val startCal = Calendar.getInstance().apply {
            time = refDate
            add(Calendar.DAY_OF_YEAR, -(days - 1))
        }
        val sdf = newDateFormat()
        return sdf.format(startCal.time) to sdf.format(endCal.time)
    }

    /**
     * 生成完整报告 JSON。
     * @param student 学员信息
     * @param lessons 全部课时（任意顺序，函数内部会按日期筛选+排序）
     * @param type 报告类型
     * @param refDate 参考日期
     * @return 报告 JSON 字符串
     */
    fun generate(
        student: Student,
        lessons: List<Lesson>,
        type: String,
        refDate: java.util.Date = java.util.Date()
    ): String {
        val (startDate, endDate) = computePeriod(type, refDate)
        val startMillis = parseMillis(startDate)
        val endMillis = parseMillis(endDate) + 24 * 3600 * 1000L - 1

        // 筛选周期内的课时
        val periodLessons = lessons.filter {
            val t = parseMillis(it.date)
            t in startMillis..endMillis
        }.sortedBy { it.date }

        val stats = buildStats(periodLessons)
        val scoreChanges = buildScoreChanges(lessons, startMillis, endMillis)
        val milestones = buildMilestones(lessons, periodLessons, scoreChanges)
        val radar = AbilityAnalyzer.computeRadar(
            AbilityAnalyzer.extractScores(lessons.filter { parseMillis(it.date) <= endMillis })
        )
        val coachComment = pickCoachComment(periodLessons)
        val suggestion = buildSuggestion(stats, scoreChanges, radar)

        val json = JSONObject()
        json.put("studentName", student.name)
        json.put("reportType", type)
        json.put("period", "$startDate ~ $endDate")
        json.put("startDate", startDate)
        json.put("endDate", endDate)
        json.put("stats", stats)
        json.put("scoreChanges", scoreChanges)
        json.put("milestones", milestones)
        json.put("radar", JSONObject().apply {
            put("speed", radar.speed)
            put("strength", radar.strength)
            put("endurance", radar.endurance)
            put("flexibility", radar.flexibility)
            put("agility", radar.agility)
        })
        json.put("coachComment", coachComment)
        json.put("suggestion", suggestion)
        return json.toString()
    }

    /** 构建周期内训练统计 */
    private fun buildStats(lessons: List<Lesson>): JSONObject {
        val count = lessons.size
        val totalMinutes = lessons.sumOf { it.duration }
        val avgPerf = if (count > 0) lessons.map { it.performance }.average() else 0.0
        // 出勤统计
        val attendance = JSONObject()
        val attMap = lessons.groupingBy { it.attendance }.eachCount()
        attMap.forEach { (k, v) -> attendance.put(k, v) }
        // 态度统计
        val attitude = JSONObject()
        lessons.groupingBy { it.attitude }.eachCount().forEach { (k, v) -> attitude.put(k, v) }

        return JSONObject().apply {
            put("lessonCount", count)
            put("totalMinutes", totalMinutes)
            put("avgPerformance", String.format("%.1f", avgPerf))
            put("attendance", attendance)
            put("attitude", attitude)
        }
    }

    /**
     * 构建成绩变化：对比周期前最近一次与周期内最近一次。
     */
    private fun buildScoreChanges(
        allLessons: List<Lesson>,
        startMillis: Long,
        endMillis: Long
    ): JSONArray {
        val arr = JSONArray()
        // 提取周期前最近一次成绩
        val beforeScores = mutableMapOf<String, AbilityAnalyzer.ScoreEntry>()
        for (lesson in allLessons.sortedBy { it.date }) {
            val t = parseMillis(lesson.date)
            if (t >= startMillis) break
            if (lesson.scores.isBlank() || lesson.scores == "{}") continue
            // 使用 JsonSafe 兜底：脏数据跳过，不影响后续对比
            val obj = JsonSafe.parseObject(lesson.scores) ?: continue
            for (name in obj.keys()) {
                // 单条项目解析失败时跳过
                val item = obj.optJSONObject(name) ?: continue
                beforeScores[name] = AbilityAnalyzer.ScoreEntry(
                    projectName = name,
                    value = item.optString("value", ""),
                    score = item.optDouble("score", 0.0),
                    grade = item.optString("grade", ""),
                    date = lesson.date
                )
            }
        }
        // 提取周期内最近一次成绩
        val afterScores = mutableMapOf<String, AbilityAnalyzer.ScoreEntry>()
        for (lesson in allLessons.sortedByDescending { it.date }) {
            val t = parseMillis(lesson.date)
            if (t > endMillis) continue
            if (t < startMillis) break
            if (lesson.scores.isBlank() || lesson.scores == "{}") continue
            // 使用 JsonSafe 兜底：脏数据跳过，不影响后续对比
            val obj = JsonSafe.parseObject(lesson.scores) ?: continue
            for (name in obj.keys()) {
                if (afterScores.containsKey(name)) continue
                // 单条项目解析失败时跳过
                val item = obj.optJSONObject(name) ?: continue
                afterScores[name] = AbilityAnalyzer.ScoreEntry(
                    projectName = name,
                    value = item.optString("value", ""),
                    score = item.optDouble("score", 0.0),
                    grade = item.optString("grade", ""),
                    date = lesson.date
                )
            }
        }
        // 对比
        for ((name, after) in afterScores) {
            val before = beforeScores[name]
            val delta = if (before != null) after.score - before.score else 0.0
            val trend = when {
                before == null -> "新增"
                delta > 0.5 -> "↑"
                delta < -0.5 -> "↓"
                else -> "持平"
            }
            arr.put(JSONObject().apply {
                put("project", name)
                put("before", before?.score ?: 0.0)
                put("after", after.score)
                put("delta", String.format("%.1f", delta))
                put("trend", trend)
                put("afterGrade", after.grade)
                put("date", after.date)
            })
        }
        return arr
    }

    /**
     * 里程碑检测：从全部历史 + 周期内数据中识别值得纪念的节点。
     */
    private fun buildMilestones(
        allLessons: List<Lesson>,
        periodLessons: List<Lesson>,
        scoreChanges: JSONArray
    ): JSONArray {
        val arr = JSONArray()
        val seenTypes = mutableSetOf<String>()

        fun add(type: String, title: String, desc: String, date: String) {
            // 同类型里程碑在周期内只保留一条最新
            if (type in seenTypes && type != MILESTONE_BREAKTHROUGH && type != MILESTONE_STREAK) return
            arr.put(JSONObject().apply {
                put("type", type)
                put("title", title)
                put("desc", desc)
                put("date", date)
            })
            seenTypes.add(type)
        }

        // 1. 首测：周期内是否包含该学员首次有成绩记录的课
        val firstScoreLesson = allLessons
            .filter { it.scores.isNotBlank() && it.scores != "{}" }
            .minByOrNull { it.date }
        if (firstScoreLesson != null && firstScoreLesson.date in periodLessons.map { it.date }) {
            add(MILESTONE_FIRST_TEST, "完成首次体测",
                "学员首次完成体测项目，建立了能力基线。", firstScoreLesson.date)
        }

        // 2. 突破：周期内项目首次突破 90 分
        for (i in 0 until scoreChanges.length()) {
            // 单条变化解析失败时跳过
            val change = scoreChanges.optJSONObject(i) ?: continue
            if (change.optDouble("after", 0.0) >= 90.0 && change.optDouble("before", 0.0) < 90.0) {
                add(MILESTONE_BREAKTHROUGH, "项目突破 90 分",
                    "${change.optString("project")} 达到 ${change.optDouble("after", 0.0).toInt()} 分（${change.optString("afterGrade")}）",
                    change.optString("date"))
            }
        }

        // 3. 连续进步：同项目最近 3 次成绩持续上升
        val allScores = AbilityAnalyzer.extractScores(allLessons)
        val projectNames = allScores.groupingBy { it.projectName }.eachCount()
            .filter { it.value >= 3 }.keys
        for (project in projectNames) {
            val trend = AbilityAnalyzer.getTrend(project, allScores)
            if (trend.size >= 3) {
                val last3 = trend.takeLast(3)
                if (last3[0].score < last3[1].score && last3[1].score < last3[2].score) {
                    val lastDate = last3[2].date
                    if (lastDate in periodLessons.map { it.date }) {
                        add(MILESTONE_STREAK, "连续 3 次进步",
                            "$project 连续 3 次测试成绩稳步提升（${last3[0].score.toInt()}→${last3[2].score.toInt()}）",
                            lastDate)
                    }
                }
            }
        }

        // 4. 全勤：周期内课时数 ≥ 2 且无缺勤
        if (periodLessons.size >= 2 && periodLessons.none {
                it.attendance == "请假" || it.attendance == "旷课"
            }) {
            add(MILESTONE_FULL_ATTEND, "周期全勤",
                "本周期内 ${periodLessons.size} 节课全部准时出勤。",
                periodLessons.last().date)
        }

        // 5. 高表现：周期内平均表现 ≥ 9
        val avgPerf = if (periodLessons.isNotEmpty())
            periodLessons.map { it.performance }.average() else 0.0
        if (avgPerf >= 9.0) {
            add(MILESTONE_HIGH_PERFORMANCE, "高表现周期",
                "本周期平均训练表现 ${String.format("%.1f", avgPerf)} 分，状态出色。",
                periodLessons.last().date)
        }

        return arr
    }

    /** 选取周期内最近一次的教练寄语 */
    private fun pickCoachComment(periodLessons: List<Lesson>): String {
        return periodLessons.lastOrNull { it.coachComment.isNotBlank() }?.coachComment
            ?: "继续保持训练节奏，下个周期争取更大进步！"
    }

    /** 根据统计+变化+雷达生成训练建议 */
    private fun buildSuggestion(
        stats: JSONObject,
        scoreChanges: JSONArray,
        radar: AbilityAnalyzer.AbilityRadar
    ): String {
        val sb = StringBuilder()
        // 找最弱维度
        val dims = listOf(
            "速度" to radar.speed,
            "力量" to radar.strength,
            "耐力" to radar.endurance,
            "柔韧" to radar.flexibility,
            "灵敏" to radar.agility
        ).filter { it.second > 0f }
        if (dims.isNotEmpty()) {
            val weakest = dims.minByOrNull { it.second }!!
            sb.append("建议下阶段重点强化【${weakest.first}】维度（当前 ${weakest.second.toInt()} 分）。")
        }
        // 找退步最大的项目
        var maxDecline: Pair<String, Double>? = null
        for (i in 0 until scoreChanges.length()) {
            // 单条变化解析失败时跳过
            val c = scoreChanges.optJSONObject(i) ?: continue
            val delta = c.optDouble("delta", 0.0)
            if (delta < -0.5) {
                if (maxDecline == null || delta < maxDecline.second) {
                    maxDecline = c.optString("project") to delta
                }
            }
        }
        if (maxDecline != null) {
            sb.append("注意【${maxDecline.first}】有所退步（${maxDecline.second}），可安排专项复习。")
        }
        // 出勤提示
        val lessonCount = stats.optInt("lessonCount", 0)
        if (lessonCount < 2) {
            sb.append("训练频次偏低，建议每周保持 2-3 次以巩固效果。")
        }
        return if (sb.isEmpty()) "整体表现稳定，按当前节奏继续推进即可。" else sb.toString()
    }

    private fun parseMillis(dateStr: String): Long {
        return try {
            newDateFormat().parse(dateStr)?.time ?: 0L
        } catch (_: Exception) { 0L }
    }
}
