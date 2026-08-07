package com.shangmentiyu.sportscoach.domain.scheduling

import com.google.common.truth.Truth.assertThat
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * [ValidateScheduleUseCase] 单元测试（v49 彻底重构：三要素公式 + 业务异常上抛）。
 *
 * 覆盖范围：
 * 1. 场景1：购买前日期排课应当被拒绝（isDateValid = false / validateStartDateOrThrow 抛异常）
 * 2. 场景3：手动排课时额度不足，保存操作应失败（hasRemainingCapacity = false / availableQuota = 0）
 * 3. 三要素公式：剩余可排课时 = 总课时(活跃包剩余之和) - 已消耗(已签退) - 待消耗(占位)
 * 4. 无课时包时的兼容行为
 *
 * 运行方式：./gradlew :app:testDebugUnitTest --tests "com.shangmentiyu.sportscoach.domain.scheduling.ValidateScheduleUseCaseTest"
 */
class ValidateScheduleUseCaseTest {

    private class FakeSource(
        var packages: List<LessonPackage> = emptyList(),
        var checkedOut: Int = 0,
        var pendingPlaceholders: Int = 0,
        var earliestPurchase: String? = null
    ) : ScheduleValidationSource {
        override suspend fun getActivePackagesByStudent(studentName: String) = packages
        override suspend fun countCheckedOutLessons(studentName: String) = checkedOut
        override suspend fun countPendingPlaceholderLessons(studentName: String, fromDate: String) = pendingPlaceholders
        override suspend fun earliestPurchaseDateOf(studentName: String) = earliestPurchase
    }

    private fun pkg(total: Int, purchaseDate: String, used: Int = 0) = LessonPackage(
        studentName = "张三",
        name = "测试课包",
        totalLessons = total,
        usedLessons = used,
        purchaseDate = purchaseDate
    )

    // === 场景1：购买前日期排课应当被拒绝 ===

    @Test
    fun `购买日期之前不通过_购买日当天及之后通过`() = runTest {
        val source = FakeSource(earliestPurchase = "2025-07-24")
        val useCase = ValidateScheduleUseCase(source)
        assertThat(useCase.isDateValid("张三", "2025-07-23")).isFalse()
        assertThat(useCase.isDateValid("张三", "2025-07-24")).isTrue()
        assertThat(useCase.isDateValid("张三", "2026-01-01")).isTrue()
    }

    @Test
    fun `场景1_购买前日期排课抛出明确异常`() = runTest {
        val source = FakeSource(earliestPurchase = "2025-07-24")
        val useCase = ValidateScheduleUseCase(source)
        val result = runCatching { useCase.validateStartDateOrThrow("张三", "2025-07-23") }
        val e = result.exceptionOrNull()
        assertThat(e).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(e?.message).contains("早于")
        assertThat(e?.message).contains("2025-07-24")
    }

    @Test
    fun `场景1_购买日当天及之后的日期不抛异常`() = runTest {
        val source = FakeSource(earliestPurchase = "2025-07-24")
        val useCase = ValidateScheduleUseCase(source)
        useCase.validateStartDateOrThrow("张三", "2025-07-24")
        useCase.validateStartDateOrThrow("张三", "2026-01-01")
    }

    @Test
    fun `无课时包时不拦截日期`() = runTest {
        val useCase = ValidateScheduleUseCase(FakeSource(earliestPurchase = null))
        assertThat(useCase.isDateValid("张三", "2020-01-01")).isTrue()
        useCase.validateStartDateOrThrow("张三", "2020-01-01")
    }

    // === v49 体验课：跳过购买日期校验与余额校验 ===

    @Test
    fun `体验课_跳过购买日期校验_早于购买日的日期不抛异常`() = runTest {
        val source = FakeSource(earliestPurchase = "2025-07-24")
        val useCase = ValidateScheduleUseCase(source)
        // isTrial=true 时即使日期早于首次购买日也不拦截
        assertThat(useCase.isDateValid("张三", "2025-07-20", isTrial = true)).isTrue()
        useCase.validateStartDateOrThrow("张三", "2025-07-20", isTrial = true)
    }

    @Test
    fun `体验课_跳过余额校验_无任何课时包也可排`() = runTest {
        val source = FakeSource(packages = emptyList(), earliestPurchase = null)
        val useCase = ValidateScheduleUseCase(source)
        // 无课时包 + 体验课 → 恒有额度
        assertThat(useCase.hasRemainingCapacity("张三", "2025-07-20", isTrial = true)).isTrue()
    }

    @Test
    fun `体验课_即使额度已满也允许排课`() = runTest {
        val source = FakeSource(
            packages = listOf(pkg(total = 2, purchaseDate = "2025-07-01", used = 2)),
            checkedOut = 0,
            pendingPlaceholders = 0
        )
        val useCase = ValidateScheduleUseCase(source)
        // 常规排课额度已满（剩余 0）
        assertThat(useCase.hasRemainingCapacity("张三", "2025-07-20")).isFalse()
        // 体验课不受额度限制
        assertThat(useCase.hasRemainingCapacity("张三", "2025-07-20", isTrial = true)).isTrue()
    }

    // === 场景3：手动排课时，若额度不足，保存操作应失败 ===

    @Test
    fun `场景3_额度不足时剩余可排课时为零`() = runTest {
        // 10 节包全部已用（remaining=0）→ 总课时=0 → 不可排
        val source = FakeSource(packages = listOf(pkg(total = 10, purchaseDate = "2025-07-01", used = 10)))
        val useCase = ValidateScheduleUseCase(source)
        assertThat(useCase.availableQuota("张三", "2025-07-20")).isEqualTo(0)
        assertThat(useCase.hasRemainingCapacity("张三", "2025-07-20")).isFalse()
    }

    @Test
    fun `场景3_总课时被已签退与占位全部占用时不可排`() = runTest {
        // 总课时=10，已签退=6，占位=4 → 剩余 0 → 不可排
        val source = FakeSource(
            packages = listOf(pkg(total = 10, purchaseDate = "2025-07-01")),
            checkedOut = 6,
            pendingPlaceholders = 4
        )
        val useCase = ValidateScheduleUseCase(source)
        assertThat(useCase.availableQuota("张三", "2025-07-20")).isEqualTo(0)
        assertThat(useCase.hasRemainingCapacity("张三", "2025-07-20")).isFalse()
    }

    @Test
    fun `场景3_仍有剩余可排课时允许排课`() = runTest {
        val source = FakeSource(
            packages = listOf(pkg(total = 10, purchaseDate = "2025-07-01")),
            checkedOut = 6,
            pendingPlaceholders = 3
        )
        val useCase = ValidateScheduleUseCase(source)
        assertThat(useCase.availableQuota("张三", "2025-07-20")).isEqualTo(1)
        assertThat(useCase.hasRemainingCapacity("张三", "2025-07-20")).isTrue()
    }

    // === 三要素公式 ===

    @Test
    fun `额度已满不通过_有空余通过`() = runTest {
        val source = FakeSource(packages = listOf(pkg(total = 10, purchaseDate = "2025-07-01")))
        val useCase = ValidateScheduleUseCase(source)
        source.pendingPlaceholders = 10
        assertThat(useCase.hasRemainingCapacity("张三", "2025-07-20")).isFalse()
        source.pendingPlaceholders = 9
        assertThat(useCase.hasRemainingCapacity("张三", "2025-07-20")).isTrue()
    }

    @Test
    fun `无有效课时包时额度不通过`() = runTest {
        val source = FakeSource(packages = emptyList())
        assertThat(ValidateScheduleUseCase(source).hasRemainingCapacity("张三", "2025-07-20")).isFalse()
    }

    @Test
    fun `未来可用额度等于总课时减去已占用_下限为零`() = runTest {
        // 总课时=8（10节包已用2），已占用=3 → 可用 5
        val source = FakeSource(
            packages = listOf(pkg(total = 10, purchaseDate = "2025-07-01", used = 2)),
            checkedOut = 1,
            pendingPlaceholders = 2
        )
        val useCase = ValidateScheduleUseCase(source)
        assertThat(useCase.futureAvailableLessons("张三", "2025-07-20")).isEqualTo(5)
        source.checkedOut = 100
        assertThat(useCase.futureAvailableLessons("张三", "2025-07-20")).isEqualTo(0)
    }

    @Test
    fun `三要素公式_剩余等于总课时减已签退减占位`() = runTest {
        val source = FakeSource(
            packages = listOf(pkg(total = 15, purchaseDate = "2025-07-01"), pkg(total = 5, purchaseDate = "2025-07-01")),
            checkedOut = 3,
            pendingPlaceholders = 7
        )
        val useCase = ValidateScheduleUseCase(source)
        // 总课时 = 15 + 5 = 20；剩余 = 20 - 3 - 7 = 10
        assertThat(useCase.availableQuota("张三", "2025-07-20")).isEqualTo(10)
    }
}
