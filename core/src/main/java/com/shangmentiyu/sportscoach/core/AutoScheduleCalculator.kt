package com.shangmentiyu.sportscoach.core

import java.time.LocalDate

/**
 * 按课时包自动排课的纯逻辑计算器（处理器层）。
 *
 * 职责：
 * - 给定排课起始日期、剩余课时、选中的周几集合，计算每个周几的最后一次上课日期
 * - 纯算法无副作用，便于单元测试
 *
 * 算法原理：
 * - 排课从 startDate 之后（含）第一个选中周几开始
 * - 每周按选中周几顺序循环消耗课时（每周消耗 [Set.size] 节）
 * - 最后一周可能不满（剩余课时 % 每周课时数 != 0）
 * - 每个周几的 endDate = 该周几最后一次出现的日期
 *
 * 边界处理：
 * - startDate 当天的周几不在选中集合中时，自动顺延到下一个选中周几
 * - 某个周几因课时不足无法在最后一周排到时，其 endDate 取倒数第二周的对应日期
 *
 * 示例：
 * - startDate=2024-08-05（周一），remaining=30，daysOfWeek=[1,3,5]
 * - 每周 3 节，10 周正好排完
 * - 周一 endDate = 2024-10-07（第 10 周周一）
 * - 周三 endDate = 2024-10-09（第 10 周周三）
 * - 周五 endDate = 2024-10-11（第 10 周周五）
 *
 * - startDate=2024-08-05（周一），remaining=31，daysOfWeek=[1,3,5]
 * - 10 周排 30 节，第 11 周只排周一 1 节
 * - 周一 endDate = 2024-10-14（第 11 周周一）
 * - 周三 endDate = 2024-10-09（第 10 周周三，因第 11 周只排周一）
 * - 周五 endDate = 2024-10-11（第 10 周周五）
 */
object AutoScheduleCalculator {

    /**
     * 计算每个选中周几的最后一次上课日期。
     *
     * @param startDate 排课起始日期（取 max(今天, 课包购买日)）
     * @param remaining 剩余课时数
     * @param daysOfWeek 选中的周几集合（1=周一 ... 7=周日），不可为空
     * @return Map<周几, 最后一次上课日期>，key 为 [Set] 中的每个元素
     */
    fun calculateEndDates(
        startDate: LocalDate,
        remaining: Int,
        daysOfWeek: Set<Int>
    ): Map<Int, LocalDate> {
        if (remaining <= 0 || daysOfWeek.isEmpty()) return emptyMap()
        val sortedDays = daysOfWeek.sorted()
        val weeklyCount = sortedDays.size

        // 找到 startDate 之后（含）第一个选中周几对应的日期，作为第一次排课日期
        val firstLessonDate = findFirstLessonDate(startDate, sortedDays)
        val firstDow = firstLessonDate.dayOfWeek.value
        val startIdx = sortedDays.indexOf(firstDow)
        // 第一次排课所在周的周一
        val firstLessonMon = firstLessonDate.minusDays((firstDow - 1).toLong())

        val result = mutableMapOf<Int, LocalDate>()
        for ((dayIdxInWeek, dow) in sortedDays.withIndex()) {
            // 该 dow 在排课序列中的相对偏移（相对于 startIdx）
            // r = (dayIdxInWeek - startIdx + weeklyCount) % weeklyCount
            val r = (dayIdxInWeek - startIdx + weeklyCount) % weeklyCount
            // 若 r > remaining - 1，说明该 dow 永远排不到（课时不够撑到它）
            if (r > remaining - 1) continue

            // 最后一次排课位置（0-based）：
            // iMax = r + weeklyCount * floor((remaining - 1 - r) / weeklyCount)
            val iMax = r + weeklyCount * ((remaining - 1 - r) / weeklyCount)
            // 全局位置 = startIdx + iMax
            val globalPos = startIdx + iMax
            // 周索引（从第一次排课所在周开始，0-based）
            val weekIdx = globalPos / weeklyCount
            // 该 dow 的最后一次上课日期 = 第一次排课所在周的周一 + weekIdx 周 + (dow - 1) 天
            val endDate = firstLessonMon.plusWeeks(weekIdx.toLong())
                .plusDays((dow - 1).toLong())
            result[dow] = endDate
        }
        return result
    }

    /**
     * 找到 startDate 之后（含）第一个选中周几对应的日期。
     *
     * - 若 startDate 的周几已在 [sortedDays] 中，直接返回 startDate
     * - 否则按周内顺序找下一个选中周几（可能跨周到下一周）
     *
     * @param startDate 起始日期
     * @param sortedDays 已排序的周几列表（1=周一 ... 7=周日）
     * @return 第一次排课日期
     */
    private fun findFirstLessonDate(startDate: LocalDate, sortedDays: List<Int>): LocalDate {
        val startDow = startDate.dayOfWeek.value
        return if (startDow in sortedDays) {
            startDate
        } else {
            // 找同周内大于 startDow 的下一个选中周几；若没有则取下一周的第一个选中周几
            val nextDow = sortedDays.firstOrNull { it > startDow } ?: sortedDays.first()
            val daysToAdd = if (nextDow > startDow) {
                (nextDow - startDow).toLong()
            } else {
                (7 - startDow + nextDow).toLong()
            }
            startDate.plusDays(daysToAdd)
        }
    }
}
