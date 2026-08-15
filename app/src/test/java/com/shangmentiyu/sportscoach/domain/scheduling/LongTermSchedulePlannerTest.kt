package com.shangmentiyu.sportscoach.domain.scheduling

import com.google.common.truth.Truth.assertThat
import com.shangmentiyu.sportscoach.data.model.Schedule
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * [LongTermSchedulePlanner] 单元测试（v49 彻底重构：独立学员循环 + 逐日生成 + 额度封顶）。
 *
 * 覆盖范围：
 * 1. 场景2：15 节课、每周 5 节（周一至周五），3 周后（15 节用完）第 4 周应停止生成
 * 2. 额度用尽立即停止该学员后续所有生成
 * 3. 周几无偏好（无模板）跳过
 * 4. 当天已存在排课（bookedDates）跳过
 * 5. 模板 startDate / endDate 边界
 *
 * 运行方式：./gradlew :app:testDebugUnitTest --tests "com.shangmentiyu.sportscoach.domain.scheduling.LongTermSchedulePlannerTest"
 */
class LongTermSchedulePlannerTest {

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** 场景2 基准：2026-08-03 为周一 */
    private val weekStart = "2026-08-03"
    private val today = "2026-08-03"

    private fun template(dayOfWeek: Int, startDate: String = weekStart, endDate: String = "") = Schedule(
        id = "sched_dow_$dayOfWeek",
        studentName = "张三",
        dayOfWeek = dayOfWeek,
        startTime = "10:00",
        startDate = startDate,
        endDate = endDate,
        isLongTerm = true
    )

    /** 周一~周五共 5 条模板（每周 5 节偏好） */
    private fun week5Templates() = (1..5).map { template(it) }

    @Test
    fun `场景2_15节课每周5节_3周后第4周停止生成`() {
        val plans = LongTermSchedulePlanner.plan(
            studentSchedules = week5Templates(),
            weekStart = weekStart,
            today = today,
            availableQuota = 15,
            alreadyBookedDates = emptySet(),
            windowDays = 28
        )
        // 15 节课：第 1/2/3 周各 5 节，第 4 周额度已用尽停止
        assertThat(plans.size).isEqualTo(15)
        // 第 4 周（weekStart + 21 天起）不再生成任何排课
        val week4Monday = LocalDate.parse(weekStart, formatter).plusDays(21).format(formatter)
        assertThat(plans.map { it.date }).doesNotContain(week4Monday)
        // 全部生成日期均在周一~周五（严格遵循学员偏好）
        plans.forEach { plan ->
            val dow = LocalDate.parse(plan.date, formatter).dayOfWeek.value
            assertThat(dow).isIn(listOf(1, 2, 3, 4, 5))
        }
        // 每天至多生成一节（日期互不相同）
        assertThat(plans.map { it.date }.distinct().size).isEqualTo(15)
        // 每节额度递减：生成日期按时间顺序排列
        assertThat(plans.map { it.date }).isInStrictOrder()
    }

    @Test
    fun `场景2_额度不足时第4周彻底停止_后续周不再生成`() {
        // 对比验证：窗口拉长到 56 天（8 周），仍只生成 15 节
        val plans = LongTermSchedulePlanner.plan(
            studentSchedules = week5Templates(),
            weekStart = weekStart,
            today = today,
            availableQuota = 15,
            alreadyBookedDates = emptySet(),
            windowDays = 56
        )
        assertThat(plans.size).isEqualTo(15)
    }

    @Test
    fun `额度用尽立即停止_剩余额度不足一周时只生成到额度为止`() {
        // 额度 12：第 1/2 周 10 节 + 第 3 周 2 节 = 12 节
        val plans = LongTermSchedulePlanner.plan(
            studentSchedules = week5Templates(),
            weekStart = weekStart,
            today = today,
            availableQuota = 12,
            alreadyBookedDates = emptySet(),
            windowDays = 28
        )
        assertThat(plans.size).isEqualTo(12)
    }

    @Test
    fun `额度为零不生成任何排课`() {
        val plans = LongTermSchedulePlanner.plan(
            studentSchedules = week5Templates(),
            weekStart = weekStart,
            today = today,
            availableQuota = 0,
            alreadyBookedDates = emptySet(),
            windowDays = 28
        )
        assertThat(plans).isEmpty()
    }

    @Test
    fun `周几无偏好跳过_仅周一至周三生成`() {
        val templates = (1..3).map { template(it) }
        val plans = LongTermSchedulePlanner.plan(
            studentSchedules = templates,
            weekStart = weekStart,
            today = today,
            availableQuota = 10,
            alreadyBookedDates = emptySet(),
            windowDays = 28
        )
        // 每周仅周一/二/三生成 → 4 周共 12 天候选，但额度 10 封顶
        assertThat(plans.size).isEqualTo(10)
        plans.forEach { plan ->
            val dow = LocalDate.parse(plan.date, formatter).dayOfWeek.value
            assertThat(dow).isIn(listOf(1, 2, 3))
        }
    }

    @Test
    fun `当天已存在排课则跳过该天`() {
        // 周一/周三/周五已有人约课 → 只生成周二/周四
        val plans = LongTermSchedulePlanner.plan(
            studentSchedules = week5Templates(),
            weekStart = weekStart,
            today = today,
            availableQuota = 10,
            alreadyBookedDates = setOf("2026-08-03", "2026-08-05", "2026-08-07"),
            windowDays = 7
        )
        // 本周剩余可排：周二(08-04)、周四(08-06) 共 2 节
        assertThat(plans.map { it.date }).containsExactly("2026-08-04", "2026-08-06")
    }

    @Test
    fun `模板生效日期边界_生效前不生成`() {
        // 模板 startDate = 下周一开始 → 本周不生成
        val lateTemplates = (1..5).map { template(it, startDate = "2026-08-10") }
        val plans = LongTermSchedulePlanner.plan(
            studentSchedules = lateTemplates,
            weekStart = weekStart,
            today = today,
            availableQuota = 10,
            alreadyBookedDates = emptySet(),
            windowDays = 14
        )
        assertThat(plans.map { it.date }.minOrNull()).isEqualTo("2026-08-10")
    }

    @Test
    fun `模板结束日期边界_结束后不生成`() {
        val templates = (1..5).map { template(it, endDate = "2026-08-07") }
        val plans = LongTermSchedulePlanner.plan(
            studentSchedules = templates,
            weekStart = weekStart,
            today = today,
            availableQuota = 10,
            alreadyBookedDates = emptySet(),
            windowDays = 14
        )
        // 本周五(08-07)为最后一天，共 5 节；下周不再生成
        assertThat(plans.size).isEqualTo(5)
        assertThat(plans.map { it.date }.maxOrNull()).isEqualTo("2026-08-07")
    }

    @Test
    fun `过去日期不补排`() {
        val plans = LongTermSchedulePlanner.plan(
            studentSchedules = week5Templates(),
            weekStart = "2026-07-27", // 上周一
            today = "2026-08-03",
            availableQuota = 10,
            alreadyBookedDates = emptySet(),
            windowDays = 14
        )
        // 早于今天的日期（07-27 ~ 07-31）不生成，从今天(08-03 周一)起生成
        assertThat(plans.map { it.date }.minOrNull()).isEqualTo("2026-08-03")
    }

    @Test
    fun `无模板不生成`() {
        val plans = LongTermSchedulePlanner.plan(
            studentSchedules = emptyList(),
            weekStart = weekStart,
            today = today,
            availableQuota = 10,
            alreadyBookedDates = emptySet()
        )
        assertThat(plans).isEmpty()
    }

    // === v49 体验课：绝不针对体验课自动生成（体验课必须教练手动单次添加） ===

    @Test
    fun `体验课模板不参与长期自动生成`() {
        // 周一~周五 5 条常规模板 + 周六/周日 2 条体验课模板（isTrial=true）
        val regular = (1..5).map { template(it) }
        val trial = (6..7).map { template(it).copy(isTrial = true) }
        val plans = LongTermSchedulePlanner.plan(
            studentSchedules = regular + trial,
            weekStart = weekStart,
            today = today,
            availableQuota = 20,
            alreadyBookedDates = emptySet(),
            windowDays = 28
        )
        // 体验课模板（周六/周日）绝不生成
        assertThat(plans.map { it.schedule.id }).doesNotContain("sched_dow_6")
        assertThat(plans.map { it.schedule.id }).doesNotContain("sched_dow_7")
        // 所有计划均来自常规模板（isTrial=false），额度 20 封顶仅生成常规模板周几
        plans.forEach { plan -> assertThat(plan.schedule.isTrial).isFalse() }
        plans.forEach { plan ->
            val dow = LocalDate.parse(plan.date, formatter).dayOfWeek.value
            assertThat(dow).isIn(listOf(1, 2, 3, 4, 5))
        }
    }

    @Test
    fun `仅有体验课模板时不生成任何计划`() {
        val trial = (1..3).map { template(it).copy(isTrial = true) }
        val plans = LongTermSchedulePlanner.plan(
            studentSchedules = trial,
            weekStart = weekStart,
            today = today,
            availableQuota = 10,
            alreadyBookedDates = emptySet(),
            windowDays = 28
        )
        assertThat(plans).isEmpty()
    }

    // === v50 终极逻辑阻断：首次购买日期 / 到期日期 / 历史待消耗三重拦截 ===

    @Test
    fun `拦截1_早于首次购买日期的天数跳过不生成`() {
        val plans = LongTermSchedulePlanner.plan(
            studentSchedules = week5Templates(),
            weekStart = weekStart,          // 2026-08-03 周一
            today = today,
            availableQuota = 10,
            alreadyBookedDates = emptySet(),
            windowDays = 14,
            firstPurchaseDate = "2026-08-10" // 首次购买：下周一
        )
        // 08-03 ~ 08-07 早于首次购买 → 全部跳过；最早生成日 = 08-10
        assertThat(plans.map { it.date }.minOrNull()).isEqualTo("2026-08-10")
        plans.forEach { plan -> assertThat(plan.date).isGreaterThan("2026-08-09") }
    }

    @Test
    fun `拦截2_超过课时包最晚到期日彻底停止不再生成`() {
        val plans = LongTermSchedulePlanner.plan(
            studentSchedules = week5Templates(),
            weekStart = weekStart,          // 2026-08-03 周一
            today = today,
            availableQuota = 10,
            alreadyBookedDates = emptySet(),
            windowDays = 14,
            expireDate = "2026-08-07"       // 课时包最晚到期：本周五
        )
        // 只生成到 08-07（5 节），之后日期全部 > 到期日 → break，1 节都不多排
        assertThat(plans.size).isEqualTo(5)
        assertThat(plans.map { it.date }.maxOrNull()).isEqualTo("2026-08-07")
    }

    @Test
    fun `拦截3与历史占位_已有待消耗先扣除_生成量加待消耗不超过总额度`() {
        val plans = LongTermSchedulePlanner.plan(
            studentSchedules = week5Templates(),
            weekStart = weekStart,
            today = today,
            availableQuota = 15,            // 总剩余额度
            alreadyBookedDates = emptySet(),
            windowDays = 28,
            pendingSlots = 5                // 已有待消耗占位 5 节 → 只剩 10 节可生成
        )
        // 15 - 5 = 10 节；生成量(10) + 待消耗(5) = 15 <= 总额度(15)
        assertThat(plans.size).isEqualTo(10)
    }

    // === 回退测试：firstPurchaseDate 为 null 时规划器行为（校验层负责拦截，规划器信任入参） ===

    @Test
    fun `回退_firstPurchaseDate为null时_规划器不对齐购买日期_从weekStart开始生成`() {
        // 模拟学员有多个课时包但 earliestPurchaseDateOf 返回 null 的场景：
        // 规划器收到 firstPurchaseDate=null，不会对齐购买日期，从 weekStart 开始生成。
        // 拦截责任由 ValidateScheduleUseCase.resolveEarliestPurchaseDate 回退逻辑承担。
        val plans = LongTermSchedulePlanner.plan(
            studentSchedules = week5Templates(),
            weekStart = weekStart,
            today = today,
            availableQuota = 10,
            alreadyBookedDates = emptySet(),
            windowDays = 14,
            firstPurchaseDate = null       // 模拟 earliestPurchaseDateOf 返回 null
        )
        // 无购买日期约束 → 从 weekStart(08-03 周一) 开始正常生成
        assertThat(plans.map { it.date }.minOrNull()).isEqualTo("2026-08-03")
        assertThat(plans.size).isEqualTo(10)
    }
}
