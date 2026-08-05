package com.shangmentiyu.sportscoach.domain.scheduling

import com.shangmentiyu.sportscoach.data.repo.OperationRepository

/**
 * 排课三段绝对硬决策（架构层二，v46）。
 *
 * 将原先散落在 ViewModel 的排课校验逻辑抽离为独立领域用例，返回 [SchedulingDecision]：
 * 1. 余额检查：有效剩余课时 <= 0 → [SchedulingDecision.NoPackage]
 * 2. 额度封顶：从 [todayStr] 起已占用的未来未消课课时 >= 总余额 → [SchedulingDecision.QuotaExhausted]
 * 3. 购买日前置：排课日期早于学员最早购买日期 → [SchedulingDecision.BeforePurchase]
 * 全部通过 → [SchedulingDecision.Allowed]
 *
 * 所有查询均走 studentId 双通道（studentId 优先、studentName 回退）。
 *
 * @param operationRepository 运营数据仓库
 */
class CanScheduleLessonsUseCase(
    private val operationRepository: OperationRepository
) {

    /**
     * @param studentName 学员姓名
     * @param dateStr 待排课日期 YYYY-MM-DD
     * @param todayStr 今天 YYYY-MM-DD（额度封顶统计的起点）
     * @return 排课决策
     */
    suspend operator fun invoke(
        studentName: String,
        dateStr: String,
        todayStr: String
    ): SchedulingDecision {
        // 1. 总剩余课时（当天有效课时包）
        val totalRemaining = EffectiveRemainingCalculator.calculate(
            packages = operationRepository.getActivePackagesByStudent(studentName),
            dateStr = dateStr
        )
        if (totalRemaining <= 0) return SchedulingDecision.NoPackage

        // 2. 从今天起未来已占用的排课数量（实时查询，已生成占位天然计入）
        val pendingCount = operationRepository.countUnconsumedLessonsFrom(studentName, todayStr)
        if (pendingCount >= totalRemaining) {
            return SchedulingDecision.QuotaExhausted(totalRemaining)
        }

        // 3. 购买日期前置硬校验
        val purchaseDate = operationRepository.earliestPurchaseDateOf(studentName)
        if (purchaseDate != null && dateStr < purchaseDate) {
            return SchedulingDecision.BeforePurchase
        }

        return SchedulingDecision.Allowed
    }
}
