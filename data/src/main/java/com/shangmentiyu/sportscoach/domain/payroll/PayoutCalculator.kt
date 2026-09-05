package com.shangmentiyu.sportscoach.domain.payroll

import com.shangmentiyu.sportscoach.data.model.Coach
import com.shangmentiyu.sportscoach.data.model.CoachRole

/**
 * 薪资结算结果（纯数据，UI 与入库共用）。
 */
data class PayoutBreakdown(
    val baseSalary: Double,       // 底薪部分
    val lessonFee: Double,        // 课时费 = lessonRate × 个人消课数
    val commission: Double,       // 分成部分（L1 净利润分红 / L2 团队课时费提成）
    val total: Double             // 合计应发
)

/**
 * 薪资计算器（处理层，纯函数无状态，便于单测）。
 *
 * 角色薪资结构（与角色定义一一对应，不存 salaryMode 避免双源冲突）：
 * - 兼职教练：课时费 × 个人消课数
 * - 全职教练：底薪 + 课时费 × 个人消课数
 * - 一级合伙人：底薪 + 课时费 × 个人消课数 + 分成比例 × 机构当月净利润（人工录入）
 * - 二级合伙人：底薪 + 课时费 × 个人消课数 + 分成比例 × 所辖团队课时费总额
 *
 * @param coach 教练档案（取 role / baseSalary / lessonRate / commissionRate）
 * @param personalLessons 本结算期个人实际消课数（已签退）
 * @param teamLessonFeeTotal 所辖团队（含自己）课时费总额，L2 提成基数
 * @param orgNetProfit 机构当月净利润，L1 分红基数（人工录入，默认 0）
 */
object PayoutCalculator {

    fun compute(
        coach: Coach,
        personalLessons: Int,
        teamLessonFeeTotal: Double = 0.0,
        orgNetProfit: Double = 0.0
    ): PayoutBreakdown {
        val lessonFee = coach.lessonRate * personalLessons
        val base = when (coach.role) {
            CoachRole.PARTTIME -> 0.0
            else -> coach.baseSalary   // 全职 / L1 / L2 均含底薪
        }
        val rate = (coach.commissionRate.coerceIn(0.0, 100.0)) / 100.0
        val commission = when (coach.role) {
            CoachRole.PARTNER_L1 -> rate * orgNetProfit
            CoachRole.PARTNER_L2 -> rate * teamLessonFeeTotal
            else -> 0.0
        }
        return PayoutBreakdown(
            baseSalary = round2(base),
            lessonFee = round2(lessonFee),
            commission = round2(commission),
            total = round2(base + lessonFee + commission)
        )
    }

    /** 金额保留 2 位小数（分），避免浮点误差入库 */
    private fun round2(value: Double): Double = Math.round(value * 100.0) / 100.0
}
