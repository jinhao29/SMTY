package com.shangmentiyu.sportscoach.core

import java.time.LocalDate

/**
 * 按用户勾选上课日 + 总节数计算排课日期列表（处理器层）。
 *
 * 职责：
 * - 给定开始日期、总节数、勾选的上课日集合，逐日后移累计勾选日，
 *   直到累计数等于总节数，得到完整排课日期列表。
 * - 纯内存计算，无数据库访问，无副作用，便于单元测试。
 *
 * 算法步骤：
 * 1. 初始化 lessonDates 为空列表，current = startDate，count = 0
 * 2. 当 count < totalLessons 时循环：
 *    - 如果 current 的星期几在 selectedDays 中，加入 lessonDates，count++
 *    - current 向后移动一天
 * 3. 循环结束后，lessonDates 的最后一个元素即为预计结束日期
 *
 * 边界处理：
 * - startDate 当天若属于勾选日则算第 1 节，否则顺延到下一个勾选日
 * - 支持任意勾选日组合（1~7 天）
 * - 全选七天等同于每天上课
 *
 * @param startDate 开始排课日（当天若属于勾选日则算第 1 节）
 * @param totalLessons 本次总节数（必须 > 0）
 * @param selectedDays 用户勾选的所有星期（1=周一 ... 7=周日，不可为空）
 * @return 排课日期列表，按时间升序排列
 */
object LessonDateCalculator {

    /**
     * 计算完整的排课日期列表。
     *
     * @param startDate 开始排课日
     * @param totalLessons 本次总节数
     * @param selectedDays 勾选的上课日集合（1=周一 ... 7=周日）
     * @return 按时间升序排列的排课日期列表
     * @throws IllegalArgumentException totalLessons <= 0 或 selectedDays 为空
     */
    fun calculateLessonDates(
        startDate: LocalDate,
        totalLessons: Int,
        selectedDays: Set<Int>
    ): List<LocalDate> {
        require(totalLessons > 0) { "totalLessons must be > 0" }
        require(selectedDays.isNotEmpty()) { "selectedDays must not be empty" }

        val result = mutableListOf<LocalDate>()
        var current = startDate
        var count = 0

        while (count < totalLessons) {
            if (current.dayOfWeek.value in selectedDays) {
                result.add(current)
                count++
            }
            current = current.plusDays(1)
        }

        return result
    }

    /**
     * 计算预计结束日期（最后一天上课日期）。
     *
     * @param startDate 开始排课日
     * @param totalLessons 本次总节数
     * @param selectedDays 勾选的上课日集合
     * @return 预计结束日期
     */
    fun calculateEndDate(
        startDate: LocalDate,
        totalLessons: Int,
        selectedDays: Set<Int>
    ): LocalDate {
        return calculateLessonDates(startDate, totalLessons, selectedDays).last()
    }
}
