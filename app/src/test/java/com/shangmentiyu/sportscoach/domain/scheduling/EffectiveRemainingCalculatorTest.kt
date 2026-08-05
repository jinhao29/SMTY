package com.shangmentiyu.sportscoach.domain.scheduling

import com.google.common.truth.Truth.assertThat
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import org.junit.Test

/**
 * [EffectiveRemainingCalculator] 单元测试（架构层二，v46）。
 *
 * 覆盖范围：
 * 1. 购买日期边界：购买日前不生效、购买日当天生效
 * 2. 过期日期边界：过期后不生效、永不过期生效
 * 3. 多课时包求和
 * 4. 已用课时扣减（remainingLessons = total - used）
 * 5. 空列表返回 0
 *
 * 运行方式：./gradlew :app:testDebugUnitTest --tests "com.shangmentiyu.sportscoach.domain.scheduling.EffectiveRemainingCalculatorTest"
 */
class EffectiveRemainingCalculatorTest {

    private fun pkg(
        total: Int,
        purchaseDate: String,
        expireDate: String = "",
        used: Int = 0
    ) = LessonPackage(
        studentName = "张三",
        name = "测试课包",
        totalLessons = total,
        usedLessons = used,
        purchaseDate = purchaseDate,
        expireDate = expireDate
    )

    @Test
    fun `购买日期之前不生效`() {
        val packages = listOf(pkg(total = 10, purchaseDate = "2025-07-24"))
        assertThat(EffectiveRemainingCalculator.calculate(packages, "2025-07-20")).isEqualTo(0)
        assertThat(EffectiveRemainingCalculator.calculate(packages, "2025-07-23")).isEqualTo(0)
    }

    @Test
    fun `购买日期当天生效`() {
        val packages = listOf(pkg(total = 10, purchaseDate = "2025-07-24"))
        assertThat(EffectiveRemainingCalculator.calculate(packages, "2025-07-24")).isEqualTo(10)
        assertThat(EffectiveRemainingCalculator.calculate(packages, "2025-07-25")).isEqualTo(10)
    }

    @Test
    fun `过期日期之后不生效_永不过期生效`() {
        val expired = pkg(total = 10, purchaseDate = "2025-01-01", expireDate = "2025-06-30")
        val forever = pkg(total = 5, purchaseDate = "2025-01-01", expireDate = "")
        // 过期包在过期后不计入，永不过期包始终计入
        assertThat(EffectiveRemainingCalculator.calculate(listOf(expired, forever), "2025-07-01"))
            .isEqualTo(5)
        // 过期前两者都计入
        assertThat(EffectiveRemainingCalculator.calculate(listOf(expired, forever), "2025-06-30"))
            .isEqualTo(15)
    }

    @Test
    fun `多课时包按日期过滤后求和`() {
        val early = pkg(total = 8, purchaseDate = "2025-07-01")
        val late = pkg(total = 12, purchaseDate = "2025-07-24")
        // 7.20 时 late 尚未生效
        assertThat(EffectiveRemainingCalculator.calculate(listOf(early, late), "2025-07-20"))
            .isEqualTo(8)
        // 7.24 后两者都生效
        assertThat(EffectiveRemainingCalculator.calculate(listOf(early, late), "2025-07-25"))
            .isEqualTo(20)
    }

    @Test
    fun `已用课时从总量中扣减`() {
        val packages = listOf(pkg(total = 10, purchaseDate = "2025-07-01", used = 4))
        assertThat(EffectiveRemainingCalculator.calculate(packages, "2025-07-20")).isEqualTo(6)
    }

    @Test
    fun `空列表返回零`() {
        assertThat(EffectiveRemainingCalculator.calculate(emptyList(), "2025-07-20")).isEqualTo(0)
    }
}
