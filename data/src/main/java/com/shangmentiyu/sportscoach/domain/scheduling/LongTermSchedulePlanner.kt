package com.shangmentiyu.sportscoach.domain.scheduling

import com.shangmentiyu.sportscoach.data.model.Schedule
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 计划生成的一条长期课时占位：使用 [schedule] 模板在 [date] 当天生成 Lesson */
data class PlannedLongTermLesson(
    val schedule: Schedule,
    val date: String
)

/**
 * 长期排课生成策略纯逻辑（无 IO / 无状态，可独立单元测试）。
 *
 * === v49 彻底重构：根治「额度用完仍排课」 ===
 *
 * 生成策略（严格遵循）：
 * 1. 每个学员独立循环（调用方按学员传入 [studentSchedules]）
 * 2. 遍历未来日期（从 [weekStart] 所在周开始，共 [windowDays] 天）
 * 3. 检查每一天是否已存在排课（[alreadyBookedDates] 命中则跳过）
 * 4. 若当天未排：
 *    - 判断剩余额度 [availableQuota] 是否 > 0：
 *      - 是 → 生成一条排课（按当天 dayOfWeek 命中的模板），并将额度减 1
 *      - 否 → 立即停止该学员后续所有排课生成（break）
 * 5. 严格遵循学员排课偏好：仅在该学员有长期模板的周几（dayOfWeek）生成，
 *    周几无偏好（无模板）直接跳过
 * 6. 模板的 startDate / endDate 生效边界逐日校验，超出范围跳过该天
 *
 * 模板（schedules 表长期记录）由手动排课时写入；本规划器只产出
 * 待生成的 (模板, 日期) 计划，由调用方落库为 lessons 占位记录。
 */
object LongTermSchedulePlanner {

    /** 默认未来生成窗口：28 天（约 4 周），受额度封顶，额度用尽即提前停止 */
    const val DEFAULT_WINDOW_DAYS = 28

    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    /**
     * 生成未来窗口内的长期课时占位计划。
     *
     * === v49 体验课：长期排课中排除体验课（isTrial=true 不参与自动生成） ===
     *
     * @param studentSchedules 该学员全部长期排课模板（isLongTerm 且活跃，调用方过滤；体验课会被忽略）
     * @param weekStart 当前周起始日期（周一）YYYY-MM-DD，遍历从该周开始
     * @param today 今天 YYYY-MM-DD，早于今天的日期一律不生成
     * @param availableQuota 当前剩余可排课时（总-已消耗-待消耗），逐节扣减
     * @param alreadyBookedDates 该学员已有课时记录的日期集合（YYYY-MM-DD），当天已排则跳过
     * @param windowDays 未来生成窗口天数
     * @return 待生成的 (模板, 日期) 计划列表；额度用尽时提前终止
     */
    fun plan(
        studentSchedules: List<Schedule>,
        weekStart: String,
        today: String,
        availableQuota: Int,
        alreadyBookedDates: Set<String>,
        windowDays: Int = DEFAULT_WINDOW_DAYS
    ): List<PlannedLongTermLesson> {
        // 体验课不参与长期自动生成（双重防御：调用方过滤 + 此处兜底）
        val regularSchedules = studentSchedules.filter { !it.isTrial }
        if (regularSchedules.isEmpty() || availableQuota <= 0) return emptyList()

        val start = try {
            LocalDate.parse(weekStart, formatter)
        } catch (_: Exception) {
            return emptyList()
        }

        var remaining = availableQuota
        val result = mutableListOf<PlannedLongTermLesson>()

        for (offset in 0 until windowDays) {
            val date = start.plusDays(offset.toLong())
            val dateStr = date.format(formatter)
            // 过去日期不补排（历史数据不回溯）
            if (dateStr < today) continue
            // 学员排课偏好：该天（周几）无长期模板则跳过
            val dayOfWeek = date.dayOfWeek.value // 1=周一 ... 7=周日
            val candidates = regularSchedules.filter { it.dayOfWeek == dayOfWeek }
            if (candidates.isEmpty()) continue
            // 当天已存在排课（占位/签到）则跳过
            if (dateStr in alreadyBookedDates) continue
            // 额度用尽：立即停止该学员后续所有排课生成
            if (remaining <= 0) break

            // 取该周几的模板（同一周几多条时按开始时间升序取第一条，每天至多生成一节）
            val sched = candidates.minByOrNull { it.startTime } ?: continue
            // 模板生效边界
            if (sched.startDate.isNotBlank() && dateStr < sched.startDate) continue
            if (sched.endDate.isNotBlank() && dateStr > sched.endDate) continue

            result += PlannedLongTermLesson(schedule = sched, date = dateStr)
            remaining--
        }
        return result
    }
}
