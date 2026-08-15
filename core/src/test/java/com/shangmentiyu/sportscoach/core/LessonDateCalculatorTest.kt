package com.shangmentiyu.sportscoach.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

/**
 * [LessonDateCalculator] 单元测试：验证按勾选上课日 + 总节数的日期计算逻辑。
 *
 * 测试基准日：2022-08-01 为周一。
 *
 * 运行方式：./gradlew :core:test --tests "com.shangmentiyu.sportscoach.core.LessonDateCalculatorTest"
 */
class LessonDateCalculatorTest {

    /** 2022-08-01 为周一 */
    private val monday = LocalDate.of(2022, 8, 1)

    // === DayOfWeek 常量（1=周一 ... 7=周日） ===
    private val MON = 1
    private val TUE = 2
    private val WED = 3
    private val THU = 4
    private val FRI = 5
    private val SAT = 6
    private val SUN = 7

    /**
     * 场景A：仅勾选周二、周四，开始日 8月1日（周一），排 4 节。
     *
     * 预期排课日：8/2(周二)、8/4(周四)、8/9(周二)、8/11(周四)。
     * 预计结束日：8/11。
     */
    @Test
    fun scenarioA_tueThu_4Lessons() {
        val selectedDays = setOf(TUE, THU)
        val dates = LessonDateCalculator.calculateLessonDates(monday, 4, selectedDays)

        assertEquals(4, dates.size)
        assertEquals(LocalDate.of(2022, 8, 2), dates[0])
        assertEquals(LocalDate.of(2022, 8, 4), dates[1])
        assertEquals(LocalDate.of(2022, 8, 9), dates[2])
        assertEquals(LocalDate.of(2022, 8, 11), dates[3])

        val endDate = LessonDateCalculator.calculateEndDate(monday, 4, selectedDays)
        assertEquals(LocalDate.of(2022, 8, 11), endDate)
    }

    /**
     * 场景B：勾选周一至周五，开始日 8月1日（周一），排 15 节。
     *
     * 每周 5 节，3 周排完。
     * 预计结束日：8/19（周五）。
     */
    @Test
    fun scenarioB_monToFri_15Lessons() {
        val selectedDays = setOf(MON, TUE, WED, THU, FRI)
        val dates = LessonDateCalculator.calculateLessonDates(monday, 15, selectedDays)

        assertEquals(15, dates.size)

        // 第1周：8/1~8/5
        assertEquals(LocalDate.of(2022, 8, 1), dates[0])
        assertEquals(LocalDate.of(2022, 8, 5), dates[4])
        // 第2周：8/8~8/12
        assertEquals(LocalDate.of(2022, 8, 8), dates[5])
        assertEquals(LocalDate.of(2022, 8, 12), dates[9])
        // 第3周：8/15~8/19
        assertEquals(LocalDate.of(2022, 8, 15), dates[10])
        assertEquals(LocalDate.of(2022, 8, 19), dates[14])

        val endDate = LessonDateCalculator.calculateEndDate(monday, 15, selectedDays)
        assertEquals(LocalDate.of(2022, 8, 19), endDate)
    }

    /**
     * 场景C：勾选周六、周日，开始日 8月1日（周一），排 4 节。
     *
     * 预期排课日：8/6(周六)、8/7(周日)、8/13(周六)、8/14(周日)。
     * 预计结束日：8/14。
     */
    @Test
    fun scenarioC_satSun_4Lessons() {
        val selectedDays = setOf(SAT, SUN)
        val dates = LessonDateCalculator.calculateLessonDates(monday, 4, selectedDays)

        assertEquals(4, dates.size)
        assertEquals(LocalDate.of(2022, 8, 6), dates[0])
        assertEquals(LocalDate.of(2022, 8, 7), dates[1])
        assertEquals(LocalDate.of(2022, 8, 13), dates[2])
        assertEquals(LocalDate.of(2022, 8, 14), dates[3])

        val endDate = LessonDateCalculator.calculateEndDate(monday, 4, selectedDays)
        assertEquals(LocalDate.of(2022, 8, 14), endDate)
    }

    /**
     * 场景D：勾选全周（周一至周日），开始日 8月1日（周一），排 7 节。
     *
     * 每天上课，连续 7 天。
     * 预计结束日：8/7（周日）。
     */
    @Test
    fun scenarioD_allDays_7Lessons() {
        val selectedDays = setOf(MON, TUE, WED, THU, FRI, SAT, SUN)
        val dates = LessonDateCalculator.calculateLessonDates(monday, 7, selectedDays)

        assertEquals(7, dates.size)
        assertEquals(LocalDate.of(2022, 8, 1), dates[0])
        assertEquals(LocalDate.of(2022, 8, 7), dates[6])

        val endDate = LessonDateCalculator.calculateEndDate(monday, 7, selectedDays)
        assertEquals(LocalDate.of(2022, 8, 7), endDate)
    }

    // === 边界用例 ===

    /**
     * 开始日当天属于勾选日时，当天算第 1 节。
     */
    @Test
    fun startDateMatchesSelectedDay_countsAsFirstLesson() {
        val dates = LessonDateCalculator.calculateLessonDates(monday, 1, setOf(MON))
        assertEquals(1, dates.size)
        assertEquals(monday, dates[0])
    }

    /**
     * 开始日当天不属于勾选日时，顺延到下一个勾选日。
     */
    @Test
    fun startDateNotInSelectedDays_skipsToNextSelectedDay() {
        // 周一开始，仅勾选周三，排 1 节 -> 第1节为周三(8/3)
        val dates = LessonDateCalculator.calculateLessonDates(monday, 1, setOf(WED))
        assertEquals(1, dates.size)
        assertEquals(LocalDate.of(2022, 8, 3), dates[0])
    }

    /**
     * totalLessons <= 0 时抛出 IllegalArgumentException。
     */
    @Test
    fun totalLessonsZero_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            LessonDateCalculator.calculateLessonDates(monday, 0, setOf(MON))
        }
    }

    /**
     * selectedDays 为空时抛出 IllegalArgumentException。
     */
    @Test
    fun emptySelectedDays_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            LessonDateCalculator.calculateLessonDates(monday, 5, emptySet())
        }
    }
}
