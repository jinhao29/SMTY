package com.shangmentiyu.sportscoach.domain.scheduling

import com.google.common.truth.Truth.assertThat
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ValidateScheduleUseCaseTest {

    private class FakeSource(
        var packages: List<LessonPackage> = emptyList(),
        var unconsumed: Int = 0,
        var longTermPending: Int = 0,
        var earliestPurchase: String? = null
    ) : ScheduleValidationSource {
        override suspend fun getActivePackagesByStudent(studentName: String) = packages
        override suspend fun countUnconsumedLessonsFrom(studentName: String, fromDate: String) = unconsumed
        override suspend fun countLongTermPendingFrom(studentName: String, fromDate: String) = longTermPending
        override suspend fun earliestPurchaseDateOf(studentName: String) = earliestPurchase
    }

    private fun pkg(total: Int, purchaseDate: String, used: Int = 0) = LessonPackage(
        studentName = "张三",
        name = "测试课包",
        totalLessons = total,
        usedLessons = used,
        purchaseDate = purchaseDate
    )

    @Test
    fun `购买日期之前不通过_购买日当天及之后通过`() = runTest {
        val source = FakeSource(earliestPurchase = "2025-07-24")
        val useCase = ValidateScheduleUseCase(source)
        assertThat(useCase.isDateValid("张三", "2025-07-23")).isFalse()
        assertThat(useCase.isDateValid("张三", "2025-07-24")).isTrue()
        assertThat(useCase.isDateValid("张三", "2026-01-01")).isTrue()
    }

    @Test
    fun `无课时包时不拦截日期`() = runTest {
        val useCase = ValidateScheduleUseCase(FakeSource(earliestPurchase = null))
        assertThat(useCase.isDateValid("张三", "2020-01-01")).isTrue()
    }

    @Test
    fun `额度已满不通过_有空余通过`() = runTest {
        val source = FakeSource(packages = listOf(pkg(total = 10, purchaseDate = "2025-07-01")))
        val useCase = ValidateScheduleUseCase(source)
        source.unconsumed = 10
        assertThat(useCase.hasRemainingCapacity("张三", "2025-07-20")).isFalse()
        source.unconsumed = 9
        assertThat(useCase.hasRemainingCapacity("张三", "2025-07-20")).isTrue()
    }

    @Test
    fun `无有效课时包时额度不通过`() = runTest {
        val source = FakeSource(packages = emptyList())
        assertThat(ValidateScheduleUseCase(source).hasRemainingCapacity("张三", "2025-07-20")).isFalse()
    }

    @Test
    fun `未来可用额度等于剩余减去已占用_下限为零`() = runTest {
        val source = FakeSource(
            packages = listOf(pkg(total = 10, purchaseDate = "2025-07-01", used = 2)),
            longTermPending = 3
        )
        val useCase = ValidateScheduleUseCase(source)
        assertThat(useCase.futureAvailableLessons("张三", "2025-07-20")).isEqualTo(5)
        source.longTermPending = 100
        assertThat(useCase.futureAvailableLessons("张三", "2025-07-20")).isEqualTo(0)
    }
}
