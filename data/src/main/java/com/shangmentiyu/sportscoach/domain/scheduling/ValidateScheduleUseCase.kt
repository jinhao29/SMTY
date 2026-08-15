package com.shangmentiyu.sportscoach.domain.scheduling

import android.util.Log
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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

    /** 待消耗占位课时数（长期自动生成 + 未签退 + date >= fromDate），供长期排课生成器做「历史占位先扣除」抵消 */
    suspend fun countPendingPlaceholderLessons(studentName: String, fromDate: String): Int

    /** 待消耗占位课时数（已排但未签退，signOutTime 为空、非体验课，仅统计今天及未来），额度计算主口径 */
    suspend fun countUncheckedOutLessons(studentName: String, today: String): Int

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
    suspend fun validateStartDateOrThrow(studentName: String, startDate: String, isTrial: Boolean = false, skipBoundaryCheck: Boolean = false) {
        if (skipBoundaryCheck) return
        if (isTrial) return
        if (startDate.isBlank()) return
        val purchaseDate = resolveEarliestPurchaseDate(studentName) ?: return
        if (startDate < purchaseDate) {
            throw IllegalArgumentException(
                "无法排课：所选日期($startDate)早于该学员首次购买课时包的日期($purchaseDate)"
            )
        }
    }

    /** 布尔版日期校验：true=日期合法（>= 首次购买日期或无课时包记录）；体验课恒为 true */
    suspend fun isDateValid(studentName: String, dateStr: String, isTrial: Boolean = false): Boolean {
        if (isTrial) return true
        val purchaseDate = resolveEarliestPurchaseDate(studentName) ?: return true
        return dateStr >= purchaseDate
    }

    /**
     * 统一解析学员最早购买日期（双重回退）。
     *
     * 1. 优先调用 [ScheduleValidationSource.earliestPurchaseDateOf]（查全量课时包，含已过期/已耗尽）。
     * 2. 若返回 null，回退查 [ScheduleValidationSource.getActivePackagesByStudent]（仅活跃包），
     *    取其中最早的 purchaseDate。
     * 3. 仍为 null（无任何课时包或所有 purchaseDate 均空）→ 返回 null，调用方跳过日期校验。
     */
    private suspend fun resolveEarliestPurchaseDate(studentName: String): String? {
        source.earliestPurchaseDateOf(studentName)?.let { return it }
        return source.getActivePackagesByStudent(studentName)
            .map { it.purchaseDate }
            .filter { it.isNotBlank() }
            .minOrNull()
    }

    /**
     * 核心校验 2：精确剩余可排课时。
     *
     * 剩余课时 = 所有活跃课时包剩余课时之和（remainingLessons = totalLessons - usedLessons，已扣除消耗）
     * 待消耗 = 已排但未签退的占位课时数（signOutTime 为空且非体验课）
     * 剩余可排 = 剩余课时 - 待消耗
     *
     * === v49 体验课：isTrial=true 返回 Int.MAX_VALUE（视为额度无限，跳过余额校验） ===
     *
     * @return 剩余可排课时，下限为 0；体验课返回 Int.MAX_VALUE
     */
    suspend fun availableQuota(studentName: String, fromDate: String, isTrial: Boolean = false): Int =
        if (isTrial) Int.MAX_VALUE else quotaBreakdown(studentName, fromDate).available

    /** 额度明细：剩余课时 / 已签退（仅展示） / 待消耗 / 剩余可排；额度用尽时打 Logcat（tag=ScheduleQuota） */
    suspend fun quotaBreakdown(studentName: String, fromDate: String, isTrial: Boolean = false): QuotaBreakdown {
        if (isTrial) return QuotaBreakdown(Int.MAX_VALUE, 0, 0, Int.MAX_VALUE)
        val totalRemaining = source.getActivePackagesByStudent(studentName)
            .sumOf { it.remainingLessons }
        val consumed = source.countCheckedOutLessons(studentName)
        // 待消耗只统计今天及未来（过去的未签退课时是历史遗留，不应继续占用额度）
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()))
        val pending = source.countUncheckedOutLessons(studentName, today)
        // 剩余课时已扣除消耗（remainingLessons = totalLessons - usedLessons），
        // 无需再减「已签退」，否则会重复扣减，导致「明明还有课却显示无课可排」。
        val available = (totalRemaining - pending).coerceAtLeast(0)
        Log.d("ScheduleQuota", "剩余课时: $totalRemaining, 已签退: $consumed, 待消耗: $pending, 实际可用: $available")
        if (available <= 0) {
            Log.w("ScheduleQuota",
                "额度用尽拦截 student=$studentName fromDate=$fromDate | " +
                    "剩余课时=$totalRemaining 已签退=$consumed 待消耗=$pending 实际剩余=$available")
        }
        return QuotaBreakdown(totalRemaining, consumed, pending, available)
    }

    /** 是否仍有剩余可排课时（> 0 才允许新增排课）；体验课恒为 true */
    suspend fun hasRemainingCapacity(studentName: String, fromDate: String, isTrial: Boolean = false): Boolean =
        if (isTrial) true else quotaBreakdown(studentName, fromDate).available > 0

    /** 未来可用额度 = 剩余可排课时（供长期排课生成器逐节扣减）；体验课不参与长期生成 */
    suspend fun futureAvailableLessons(studentName: String, fromDate: String, isTrial: Boolean = false): Int =
        if (isTrial) Int.MAX_VALUE else quotaBreakdown(studentName, fromDate).available
}

/**
 * 额度明细：剩余课时（活跃课时包剩余之和，已扣除消耗）/ 已签退（仅展示，不参与额度计算）/
 * 待消耗（已排未签退占位）/ 剩余可排。
 * UI 在「额度用尽」提示中展示，便于区分「真的没课了」还是「占位扣多了」。
 */
data class QuotaBreakdown(
    val totalRemaining: Int,
    val consumed: Int,
    val pending: Int,
    val available: Int
)

/**
 * 额度已满业务异常：手动排课保存时剩余可排课时为 0 抛出。
 *
 * 与 [IllegalArgumentException]（日期早于购买）不同，本异常为额度语义，
 * 由 Repository 保存路径上抛，UI 层捕获后直接展示 [message]。
 */
class ScheduleQuotaExceededException(message: String) : IllegalStateException(message)
