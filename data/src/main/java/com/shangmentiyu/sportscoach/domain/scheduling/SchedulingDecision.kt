package com.shangmentiyu.sportscoach.domain.scheduling

/**
 * 排课决策结果（架构层二，v46）。
 *
 * 由 [CanScheduleLessonsUseCase] 返回，供 UI/ViewModel 依据决策生成提示文案：
 * - [Allowed]：可以生成排课
 * - [NoPackage]：该日期无有效课时包（余额为 0）
 * - [QuotaExhausted]：未来已占用课时 >= 总余额，额度封顶
 * - [BeforePurchase]：排课日期早于学员最早购买日期
 */
sealed class SchedulingDecision {
    /** 允许生成 */
    data object Allowed : SchedulingDecision()

    /** 无有效课时包（余额为 0） */
    data object NoPackage : SchedulingDecision()

    /** 额度已用完（已占用 >= 总余额），携带剩余数供提示 */
    data class QuotaExhausted(val totalRemaining: Int) : SchedulingDecision()

    /** 排课日期早于最早购买日期，静默跳过 */
    data object BeforePurchase : SchedulingDecision()
}
