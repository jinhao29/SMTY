package com.shangmentiyu.sportscoach.data.repo

import com.shangmentiyu.sportscoach.data.db.CoachDao
import com.shangmentiyu.sportscoach.data.db.LessonDao
import com.shangmentiyu.sportscoach.data.db.LessonPackageDao
import com.shangmentiyu.sportscoach.data.db.ScheduleDao
import com.shangmentiyu.sportscoach.data.db.TrainingCycleDao
import com.shangmentiyu.sportscoach.data.model.Coach
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import com.shangmentiyu.sportscoach.data.model.Schedule
import com.shangmentiyu.sportscoach.data.model.TrainingCycle
import com.shangmentiyu.sportscoach.data.model.WeeklyPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 运营管理 Repository（管理层）。
 *
 * 统一封装 LessonPackage / Coach / Schedule / TrainingCycle 四类实体的数据访问，
 * 对上层提供业务语义清晰的方法，并包含阶段性总结的聚合计算。
 */
class OperationRepository(
    private val pkgDao: LessonPackageDao,
    private val coachDao: CoachDao,
    private val scheduleDao: ScheduleDao,
    private val cycleDao: TrainingCycleDao,
    private val lessonDao: LessonDao
) {

    /**
     * 消课结果：携带扣减的课时包信息，供上层记录到 Lesson 表与 UI 反馈。
     */
    data class ConsumeResult(
        val success: Boolean,
        val packageId: String = "",
        val packageName: String = "",
        val remainingAfter: Int = 0,
        val message: String = ""
    )

    /**
     * 续费提醒：聚合单个学员单个课时包的提醒信息。
     */
    data class RenewalAlert(
        val studentName: String,
        val packageName: String,
        val remaining: Int,
        val daysToExpiry: Int,
        val reason: String            // "剩余不足" / "即将过期" / "已用完"
    )

    /**
     * 学员剩余课时汇总。
     */
    data class RemainingSummary(
        val studentName: String,
        val totalRemaining: Int,
        val activePackageName: String  // 最早购买的活跃包名（用于卡片显示）
    )

    /**
     * 消课并发保护锁：确保读 + 写在同一临界区内完成，
     * 避免并发签到时多协程读到相同余额并各自扣减，导致同一课时被扣多次。
     */
    private val consumeMutex = Mutex()

    // === 课程包 ===
    fun getAllPackages(): Flow<List<LessonPackage>> = pkgDao.getAll()
    fun getPackagesByStudent(name: String): Flow<List<LessonPackage>> = pkgDao.getByStudent(name)
    fun getActivePackages(): Flow<List<LessonPackage>> = pkgDao.getActive()
    fun countActivePackages(): Flow<Int> = pkgDao.countActive()
    suspend fun getPkgById(id: String): LessonPackage? = pkgDao.getById(id)
    suspend fun addPackage(pkg: LessonPackage) = pkgDao.insert(pkg)
    suspend fun updatePackage(pkg: LessonPackage) = pkgDao.update(pkg)
    suspend fun deletePackage(id: String) = pkgDao.deleteById(id)

    /**
     * 消耗一次课时：找到该学员最早购买且仍有余额的活跃课程包，usedLessons + 1。
     *
     * 使用 [consumeMutex] 互斥锁保护读 + 写临界区，防止并发签到时
     * 多个协程同时读到相同余额并各自扣减，导致同一课时被扣多次或扣错包。
     *
     * 写库后通过 [pkgDao.update] 返回的受影响行数 + 重新查询双重校验，
     * 确保扣减真正落库；任一校验失败均返回 success=false，避免"签到成功但课时未减"。
     *
     * @return ConsumeResult.success=true 表示成功扣减；false 表示无可用课时包或扣减失败
     */
    suspend fun consumeLesson(studentName: String): ConsumeResult = consumeMutex.withLock {
        val packages = pkgDao.getByStudent(studentName).first()
        android.util.Log.d("ConsumeLesson",
            "学员=$studentName 查询到课时包${packages.size}个: ${packages.map { "${it.name}(status=${it.status},used=${it.usedLessons}/${it.totalLessons},expire=${it.expireDate})" }}")
        val active = packages.filter { it.status == "活跃" && !it.isExhausted && !it.isExpired }
        android.util.Log.d("ConsumeLesson", "过滤后活跃包${active.size}个")
        val target = active.minByOrNull { it.purchaseDate }
            ?: return@withLock ConsumeResult(success = false, message = "无可用课时包")

        val newUsed = (target.usedLessons + 1).coerceAtMost(target.totalLessons)
        val updated = if (newUsed >= target.totalLessons) {
            target.copy(usedLessons = target.totalLessons, status = "已用完")
        } else {
            target.copy(usedLessons = newUsed)
        }
        val affected = pkgDao.update(updated)

        // 校验1：受影响行数必须为1，否则 update 未生效
        if (affected != 1) {
            android.util.Log.e("ConsumeLesson",
                "update 受影响行数=$affected（预期1），扣减未落库！target.id=${target.id}")
            return@withLock ConsumeResult(
                success = false,
                message = "课时扣减失败（更新未生效）"
            )
        }

        // 校验2：重新查询验证 usedLessons 已更新
        val recheck = pkgDao.getById(target.id)
        if (recheck == null || recheck.usedLessons != updated.usedLessons) {
            android.util.Log.e("ConsumeLesson",
                "re-query 校验失败：期望used=${updated.usedLessons}，实际used=${recheck?.usedLessons}")
            return@withLock ConsumeResult(
                success = false,
                message = "课时扣减失败（校验不一致）"
            )
        }

        android.util.Log.d("ConsumeLesson",
            "扣减成功：${target.name} used ${target.usedLessons}->${updated.usedLessons} 剩余${updated.remainingLessons}")
        ConsumeResult(
            success = true,
            packageId = target.id,
            packageName = target.name,
            remainingAfter = updated.remainingLessons,
            message = "已扣减课时（${target.name}）"
        )
    }

    /**
     * 获取学员剩余课时汇总（按所有活跃包累加）。
     */
    suspend fun getRemainingSummary(studentName: String): RemainingSummary {
        val packages = pkgDao.getByStudent(studentName).first()
        val active = packages.filter { it.status == "活跃" && !it.isExhausted && !it.isExpired }
        val total = active.sumOf { it.remainingLessons }
        val activeName = active.minByOrNull { it.purchaseDate }?.name ?: ""
        return RemainingSummary(studentName, total, activeName)
    }

    /**
     * 续费提醒流：观察所有课时包，过滤出需要续费的项。
     * 触发条件：剩余≤3 / 30天内过期 / 已用完但仍标记活跃。
     */
    fun getRenewalAlerts(): Flow<List<RenewalAlert>> {
        return pkgDao.getAll().map { list ->
            list.filter { pkg ->
                pkg.status == "活跃" && (
                    pkg.isLowBalance ||
                    pkg.isNearExpiry() ||
                    pkg.isExhausted ||
                    pkg.isExpired
                )
            }.map { pkg ->
                val reason = when {
                    pkg.isExhausted -> "已用完"
                    pkg.isExpired -> "已过期"
                    pkg.isLowBalance -> "剩余不足"
                    pkg.isNearExpiry() -> "即将过期"
                    else -> "需关注"
                }
                RenewalAlert(
                    studentName = pkg.studentName,
                    packageName = pkg.name,
                    remaining = pkg.remainingLessons,
                    daysToExpiry = pkg.daysToExpiry(),
                    reason = reason
                )
            }
        }
    }

    // === 教练 ===
    fun getActiveCoaches(): Flow<List<Coach>> = coachDao.getActive()
    fun getAllCoaches(): Flow<List<Coach>> = coachDao.getAll()
    suspend fun getCoachByName(name: String): Coach? = coachDao.getByName(name)
    suspend fun upsertCoach(coach: Coach) = coachDao.upsert(coach)
    suspend fun deleteCoach(name: String) = coachDao.deleteByName(name)

    // === 排课 ===
    fun getActiveSchedules(): Flow<List<Schedule>> = scheduleDao.getActive()
    fun getAllSchedules(): Flow<List<Schedule>> = scheduleDao.getAll()
    fun getSchedulesByStudent(name: String): Flow<List<Schedule>> = scheduleDao.getByStudent(name)
    fun getSchedulesByCoach(name: String): Flow<List<Schedule>> = scheduleDao.getByCoach(name)
    fun getSchedulesByDay(dayOfWeek: Int): Flow<List<Schedule>> = scheduleDao.getByDay(dayOfWeek)
    suspend fun getScheduleById(id: String): Schedule? = scheduleDao.getById(id)
    suspend fun addSchedule(schedule: Schedule) = scheduleDao.insert(schedule)
    suspend fun updateSchedule(schedule: Schedule) = scheduleDao.update(schedule)
    suspend fun deleteSchedule(id: String) = scheduleDao.deleteById(id)

    /** 清空所有排课记录（课表管理"清空全部"功能） */
    suspend fun deleteAllSchedules() = scheduleDao.deleteAll()

    // === 长期排课：自动生成本周课时记录 ===

    /**
     * 查重：指定学员+日期+时间是否已有课时记录。
     * 长期排课自动生成时调用，避免重复插入。
     */
    suspend fun hasLessonForScheduleOnDate(studentName: String, date: String, time: String): Boolean {
        return lessonDao.countByStudentDateTime(studentName, date, time) > 0
    }

    /**
     * 检查学员是否还能排课：剩余课时包余额 > 未来未消课课时数时才允许继续排课。
     *
     * 长期排课生成时调用，确保排课精确到最后一节课，避免超额排课。
     *
     * @param studentName 学员姓名
     * @param fromDate 起始日期 YYYY-MM-DD（含）
     * @return true=仍有余额可排课；false=余额已用完
     */
    suspend fun canScheduleMoreLessons(studentName: String, fromDate: String): Boolean {
        val summary = getRemainingSummary(studentName)
        val pendingCount = lessonDao.countUnconsumedFrom(studentName, fromDate)
        return summary.totalRemaining > pendingCount
    }

    /**
     * 统计学员从指定日期起未消课的课时数量。
     * 用于长期排课生成时计算可用额度 = 课时包余额 - 未消课课时数。
     *
     * @param studentName 学员姓名
     * @param fromDate 起始日期 YYYY-MM-DD（含）
     * @return 未消课的课时数量
     */
    suspend fun countUnconsumedLessonsFrom(studentName: String, fromDate: String): Int {
        return lessonDao.countUnconsumedFrom(studentName, fromDate)
    }

    /**
     * 根据长期排课 Schedule 生成一条课时记录（Lesson）。
     *
     * 约定：
     * - 自动生成的 Lesson 用 lessonType 标记为 "长期自动"
     * - attendance = "准时"，但 packageId = "" 表示未扣减课时
     * - content / contentImages 直接从 Schedule 复制，便于上课时引用
     * - 学员可在课后反馈中编辑此 Lesson，结算时再扣减课时
     *
     * @param sched 长期排课实体
     * @param dateStr 本周对应日期 YYYY-MM-DD
     */
    suspend fun generateLongTermLesson(sched: Schedule, dateStr: String) {
        val lesson = Lesson(
            id = java.util.UUID.randomUUID().toString().take(8),
            date = dateStr,
            time = sched.startTime,
            studentName = sched.studentName,
            content = sched.content,
            duration = sched.durationMinutes,
            coach = sched.coachName,
            location = sched.location,
            lessonType = "${sched.lessonType}(长期自动)",
            attendance = "准时",
            packageId = ""
        )
        lessonDao.insert(lesson)
    }



    // === 训练周期 ===
    fun getAllCycles(): Flow<List<TrainingCycle>> = cycleDao.getAll()
    fun getActiveCycles(): Flow<List<TrainingCycle>> = cycleDao.getActive()
    fun getCyclesByStudent(name: String): Flow<List<TrainingCycle>> = cycleDao.getByStudent(name)
    suspend fun getCycleById(id: String): TrainingCycle? = cycleDao.getById(id)
    suspend fun addCycle(cycle: TrainingCycle) = cycleDao.insert(cycle)
    suspend fun updateCycle(cycle: TrainingCycle) = cycleDao.update(cycle)
    suspend fun deleteCycle(id: String) = cycleDao.deleteById(id)

    /**
     * 创建周期并自动生成空的周计划列表。
     */
    suspend fun createCycle(
        studentName: String,
        name: String,
        goal: String,
        totalWeeks: Int,
        startDate: String
    ): String {
        val cycle = TrainingCycle(
            studentName = studentName,
            name = name,
            goal = goal,
            totalWeeks = totalWeeks,
            startDate = startDate,
            endDate = calcEndDate(startDate, totalWeeks)
        ).withWeeklyPlans(emptyWeeklyPlans(totalWeeks))
        cycleDao.insert(cycle)
        return cycle.id
    }

    /** 生成空的周计划列表 */
    private fun emptyWeeklyPlans(totalWeeks: Int): List<WeeklyPlan> =
        (1..totalWeeks).map { i ->
            WeeklyPlan(
                weekIndex = i,
                title = "第$i 周",
                goal = "",
                focus = "",
                exercisesJson = "[]"
            )
        }

    /** 计算周期结束日期 */
    private fun calcEndDate(startDate: String, weeks: Int): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdf.parse(startDate) ?: return ""
            val cal = java.util.Calendar.getInstance().apply {
                time = date
                add(java.util.Calendar.WEEK_OF_YEAR, weeks)
                add(java.util.Calendar.DAY_OF_MONTH, -1)
            }
            sdf.format(cal.time)
        } catch (_: Exception) { "" }
    }

    // === 阶段性总结 ===

    /**
     * 阶段总结数据：聚合指定学员在指定时间范围内的所有课时记录。
     */
    data class StageSummary(
        val studentName: String,
        val startDate: String,
        val endDate: String,
        val totalLessons: Int,
        val attendedLessons: Int,          // 实到（非请假非旷课）
        val attendanceRate: Float,         // 出勤率 0-1
        val avgPerformance: Float,         // 平均表现评分 1-10
        val avgDuration: Int,              // 平均课时时长
        val attitudeDistribution: Map<String, Int>,  // 态度分布
        val completedExerciseRate: Float,  // 训练动作完成率 0-1
        val scoreProgress: List<ScoreProgressItem>,  // 各项成绩的进步对比
        val firstLessonDate: String,
        val lastLessonDate: String,
        val summaryText: String            // 自动生成的总结文字
    )

    data class ScoreProgressItem(
        val name: String,
        val firstScore: Float,
        val lastScore: Float,
        val delta: Float,
        val samples: Int
    )

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
    ): StageSummary {
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

        return StageSummary(
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
    private fun computeScoreProgress(lessons: List<Lesson>): List<ScoreProgressItem> {
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
            ScoreProgressItem(name, first, last, last - first, scores.size)
        }.sortedByDescending { it.samples }
    }

    /** 生成阶段性总结文字 */
    private fun buildSummaryText(
        studentName: String, startDate: String, endDate: String,
        total: Int, attended: Int, attendanceRate: Float,
        avgPerf: Float, avgDur: Int, completionRate: Float,
        scoreProgress: List<ScoreProgressItem>
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
