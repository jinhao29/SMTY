package com.shangmentiyu.sportscoach.domain.scheduling

import com.shangmentiyu.sportscoach.data.model.LessonPackage

/**
 * 排课校验数据源（由 Repository 层实现，供 [ValidateScheduleUseCase] 只读取数）。
 *
 * === v49 彻底重构：额度统计口径统一为三要素公式 ===
 * 剩余可排课时 = 总课时 - 已消耗 - 待消耗
 * - 总课时（totalQuota）：所有活跃课时包剩余课时之和（不按日期生效过滤）
 * - 已消耗（consumed）：已签退的课时数（signOutTime 非空）
 * - 待消耗（pending）：已排但未签退的占位课时数（长期自动生成、未签退、date >= fromDate）
 * 只有在「总课时 - 已消耗 - 待消耗 > 0」时才允许新增排课。
 */
interface ScheduleValidationSource {
    suspend fun getActivePackagesByStudent(studentName: String): List<LessonPackage>

    /** 已签退课时数（signOutTime 非空），即三要素公式中的「已消耗」 */
    suspend fun countCheckedOutLessons(studentName: String): Int

    /** 待消耗占位课时数（长期自动生成 + 未签退 + date >= fromDate），即三要素公式中的「待消耗」 */
    suspend fun countPendingPlaceholderLessons(studentName: String, fromDate: String): Int

    /** 学员最早购买课时包的日期（历史事实，含已过期/已耗尽包），无则 null */
    suspend fun earliestPurchaseDateOf(studentName: String): String?
}

/**
 * 排课校验引擎（手动排课 / 长期排课生成 / 历史修正统一入口）。
 *
 * === v49 彻底重构 ===
 * 1. 日期校验：任何排课 startDate 必须 >= 学员首次购买日期；
 *    违反时通过 [validateStartDateOrThrow] 抛出带明确错误信息的异常，由 UI 层捕获并展示。
 * 2. 额度校验：剩余可排课时 = 总课时(活跃包剩余之和) - 已消耗(已签退) - 待消耗(占位)，
 *    严格 > 0 才允许新增排课，一旦为 0 不再生成任何未来排课。
 */
class ValidateScheduleUseCase(
    private val source: ScheduleValidationSource
) {

    /**
     * 核心校验 1：日期前置校验（手动 / 长期排课统一强制）。
     *
     * 若 [startDate] < 学员首次购买日期，抛出 [IllegalArgumentException]，
     * 错误信息包含具体日期，UI 层必须捕获并显示。
     *
     * === v49 体验课：isTrial=true 时跳过（体验课无购买日期约束） ===
     *
     * @throws IllegalArgumentException 排课日期早于首次购买日期
     */
    suspend fun validateStartDateOrThrow(studentName: String, startDate: String, isTrial: Boolean = false) {
        if (isTrial) return
        if (startDate.isBlank()) return
        val purchaseDate = source.earliestPurchaseDateOf(studentName) ?: return
        if (startDate < purchaseDate) {
            throw IllegalArgumentException(
                "无法排课：所选日期($startDate)早于该学员首次购买课时包的日期($purchaseDate)"
            )
        }
    }

    /** 布尔版日期校验：true=日期合法（>= 首次购买日期或无课时包记录）；体验课恒为 true */
    suspend fun isDateValid(studentName: String, dateStr: String, isTrial: Boolean = false): Boolean {
        if (isTrial) return true
        val purchaseDate = source.earliestPurchaseDateOf(studentName) ?: return true
        return dateStr >= purchaseDate
    }

    /**
     * 核心校验 2：精确剩余可排课时（三要素公式）。
     *
     * 总课时 = 所有活跃课时包剩余课时之和
     * 已消耗 = 已签退课时数（signOutTime 非空，体验课不计入）
     * 待消耗 = 已排未签退的占位课时数（date >= fromDate，体验课不计入）
     *
     * === v49 体验课：isTrial=true 返回 Int.MAX_VALUE（视为额度无限，跳过余额校验） ===
     *
     * @return 剩余可排课时，下限为 0；体验课返回 Int.MAX_VALUE
     */
    suspend fun availableQuota(studentName: String, fromDate: String, isTrial: Boolean = false): Int {
        if (isTrial) return Int.MAX_VALUE
        val totalQuota = source.getActivePackagesByStudent(studentName)
            .sumOf { it.remainingLessons }
        val consumed = source.countCheckedOutLessons(studentName)
        val pending = source.countPendingPlaceholderLessons(studentName, fromDate)
        return (totalQuota - consumed - pending).coerceAtLeast(0)
    }

    /** 是否仍有剩余可排课时（> 0 才允许新增排课）；体验课恒为 true */
    suspend fun hasRemainingCapacity(studentName: String, fromDate: String, isTrial: Boolean = false): Boolean =
        if (isTrial) true else availableQuota(studentName, fromDate) > 0

    /** 未来可用额度 = 剩余可排课时（供长期排课生成器逐节扣减）；体验课不参与长期生成 */
    suspend fun futureAvailableLessons(studentName: String, fromDate: String, isTrial: Boolean = false): Int =
        if (isTrial) Int.MAX_VALUE else availableQuota(studentName, fromDate)
}

/**
 * 额度已满业务异常：手动排课保存时剩余可排课时为 0 抛出。
 *
 * 与 [IllegalArgumentException]（日期早于购买）不同，本异常为额度语义，
 * 由 Repository 保存路径上抛，UI 层捕获后直接展示 [message]。
 */
class ScheduleQuotaExceededException(message: String) : IllegalStateException(message)
