package com.shangmentiyu.sportscoach.data.repo

import com.shangmentiyu.sportscoach.data.db.CoachDao
import com.shangmentiyu.sportscoach.data.db.CoachPayoutDao
import com.shangmentiyu.sportscoach.data.db.CoachPayoutRequestDao
import com.shangmentiyu.sportscoach.data.db.CoachWorkloadDao
import com.shangmentiyu.sportscoach.data.model.Coach
import com.shangmentiyu.sportscoach.data.model.CoachPayout
import com.shangmentiyu.sportscoach.data.model.CoachPayoutRequest
import com.shangmentiyu.sportscoach.data.model.CoachRole
import com.shangmentiyu.sportscoach.domain.payroll.PayoutCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

/**
 * 教练薪资与提现 Repository（管理层）。
 *
 * 职责：
 * - 按月结算：拉取结算期消课数 → [PayoutCalculator] 计算各教练应发 → 批量入库（同周期 REPLACE）
 * - 二级合伙人提成基数 = 所辖团队（直接+间接下属，含自己）课时费总额
 * - 一级合伙人分红基数 = 机构当月净利润（人工录入，本系统不核算损益）
 * - 提现申请 / 审核
 * - 生成月度薪资报表文本（供系统分享导出）
 */
class CoachPayrollRepository(
    private val coachDao: CoachDao,
    private val payoutDao: CoachPayoutDao,
    private val requestDao: CoachPayoutRequestDao,
    private val workloadDao: CoachWorkloadDao
) {

    fun getAllPayouts(): Flow<List<CoachPayout>> = payoutDao.getAll()

    fun getRequests(): Flow<List<CoachPayoutRequest>> = requestDao.getAll()

    /**
     * 生成分结算期薪资：在职与休假教练参与（休假仍属雇佣关系，当月已消课照常结算），仅排除离职；
     * 同教练同周期 REPLACE 覆盖旧结算。
     *
     * @param periodStart 结算期起 yyyy-MM-dd
     * @param periodEnd   结算期止 yyyy-MM-dd
     * @param orgNetProfit 机构当月净利润（L1 分红基数，人工录入）
     * @return 生成的结算条数
     */
    suspend fun settlePeriod(periodStart: String, periodEnd: String, orgNetProfit: Double): Int {
        val coaches = coachDao.getAll().firstOrNullList().filter { it.status != "离职" }
        if (coaches.isEmpty()) return 0

        val consumed = workloadDao.consumedCountByCoach(periodStart, periodEnd)
            .associate { it.name to it.count }
        // L2 提成基数：团队（含自己）课时费总额 = Σ(成员 lessonRate × 消课数)
        val teamFeeByL2 = buildTeamFeeByL2(coaches, consumed)

        coaches.forEach { coach ->
            val lessons = consumed[coach.name] ?: 0
            val breakdown = PayoutCalculator.compute(
                coach = coach,
                personalLessons = lessons,
                teamLessonFeeTotal = teamFeeByL2[coach.name] ?: 0.0,
                orgNetProfit = orgNetProfit
            )
            payoutDao.upsert(
                CoachPayout(
                    coachName = coach.name,
                    periodStart = periodStart,
                    periodEnd = periodEnd,
                    lessonCount = lessons,
                    baseAmount = breakdown.baseSalary,
                    lessonFee = breakdown.lessonFee,
                    commission = breakdown.commission,
                    totalAmount = breakdown.total
                )
            )
        }
        return coaches.size
    }

    /** 标记结算单已发放 / 待发放 */
    suspend fun updatePayoutStatus(id: Long, status: String) = payoutDao.updateStatus(id, status)

    /** 发起提现申请 */
    suspend fun submitRequest(coachName: String, amount: Double, note: String) {
        require(amount > 0) { "提现金额必须大于 0" }
        requestDao.insert(
            CoachPayoutRequest(
                coachName = coachName,
                amount = amount,
                appliedAt = System.currentTimeMillis(),
                note = note
            )
        )
    }

    /** 审核提现（通过/驳回） */
    suspend fun reviewRequest(id: Long, approved: Boolean) {
        requestDao.updateStatus(
            id = id,
            status = if (approved) CoachPayoutRequest.STATUS_APPROVED else CoachPayoutRequest.STATUS_REJECTED,
            processedAt = System.currentTimeMillis()
        )
    }

    /**
     * 生成月度薪资报表文本（纯文本表格，供分享/复制）。
     *
     * 格式：教练 / 角色 / 消课数 / 底薪 / 课时费 / 分成 / 合计 / 状态
     */
    suspend fun buildReportText(periodStart: String, periodEnd: String): String {
        val payouts = payoutDao.getByPeriod(periodStart, periodEnd).firstOrNullList()
        if (payouts.isEmpty()) return "该结算期暂无薪资记录"
        val coaches = coachDao.getAll().firstOrNullList().associateBy { it.name }
        val header = "薪资报表 $periodStart ~ $periodEnd\n" +
            "教练\t角色\t消课\t底薪\t课时费\t分成\t合计\t状态"
        val lines = payouts.joinToString("\n") { p ->
            val role = coaches[p.coachName]?.let { CoachRole.label(it.role) } ?: ""
            listOf(
                p.coachName, role, "${p.lessonCount}",
                fmt(p.baseAmount), fmt(p.lessonFee), fmt(p.commission),
                fmt(p.totalAmount), p.status
            ).joinToString("\t")
        }
        val total = payouts.sumOf { it.totalAmount }
        return "$header\n$lines\n合计应发：${fmt(total)} 元"
    }

    // ---------- 内部工具 ----------

    /** 每个 L2 合伙人的团队课时费总额（团队=全部后代+自己） */
    private fun buildTeamFeeByL2(
        coaches: List<Coach>,
        consumed: Map<String, Int>
    ): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        val l2s = coaches.filter { it.role == CoachRole.PARTNER_L2 }
        if (l2s.isEmpty()) return result
        val childrenOf = coaches.groupBy { it.superiorId }
        val rateOf = coaches.associate { it.name to it.lessonRate }

        fun descendantsOf(name: String): Set<String> {
            val seen = mutableSetOf<String>()
            val queue = ArrayDeque(childrenOf[name]?.map { it.name } ?: emptyList())
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                if (current in seen) continue
                seen.add(current)
                childrenOf[current]?.forEach { queue.add(it.name) }
            }
            return seen
        }

        l2s.forEach { l2 ->
            val team = descendantsOf(l2.name) + l2.name
            result[l2.name] = team.sumOf { member ->
                rateOf[member]!! * (consumed[member] ?: 0)
            }
        }
        return result
    }

    private suspend fun <T> Flow<List<T>>.firstOrNullList(): List<T> =
        this.firstOrNull() ?: emptyList()

    private fun fmt(v: Double): String = if (v == v.toLong().toDouble()) {
        v.toLong().toString()
    } else {
        String.format("%.2f", v)
    }
}
