package com.shangmentiyu.sportscoach.domain.scheduling

import com.shangmentiyu.sportscoach.data.model.LessonPackage

/**
 * 有效剩余课时纯计算器（无 IO / 无状态，可独立单元测试）。
 *
 * === 架构层二（v46）：从 Repository 提取的纯业务逻辑 ===
 * 有效判定（同时满足）：
 * - 课时包在 [dateStr] 当天已生效：purchaseDate <= dateStr
 * - 未过期：expireDate 为空 或 expireDate >= dateStr
 *
 * 使用方：
 * - [CalculateRemainingLessonsUseCase]（协调层）
 * - [com.shangmentiyu.sportscoach.data.repo.OperationRepository.getEffectiveRemainingLessons]
 * - [com.shangmentiyu.sportscoach.data.repo.LessonPackageRepository.getEffectiveRemainingLessons]
 *
 * 集中唯一实现，保证"排课弹窗 / 主页 / 长期排课"拿到绝对一致的余额计算结果。
 */
object EffectiveRemainingCalculator {

    /**
     * 计算 [packages] 中在 [dateStr] 当天有效的课时包剩余总课时。
     *
     * @param packages 学员的全部活跃课时包（调用方负责取数）
     * @param dateStr 待排课日期 YYYY-MM-DD
     * @return 该日期有效课时包的剩余总课时
     */
    fun calculate(packages: List<LessonPackage>, dateStr: String): Int {
        return packages
            .filter { pkg ->
                pkg.purchaseDate <= dateStr &&
                    (pkg.expireDate.isBlank() || pkg.expireDate >= dateStr)
            }
            .sumOf { it.remainingLessons }
    }
}
