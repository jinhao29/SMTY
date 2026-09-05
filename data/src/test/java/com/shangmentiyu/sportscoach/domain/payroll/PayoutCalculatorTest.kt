package com.shangmentiyu.sportscoach.domain.payroll

import com.google.common.truth.Truth.assertThat
import com.shangmentiyu.sportscoach.data.model.Coach
import com.shangmentiyu.sportscoach.data.model.CoachRole
import org.junit.Test

/**
 * [PayoutCalculator] 单元测试：四种角色薪资结构 + 边界（比例钳制 / 负值 / 保留两位小数）。
 *
 * 运行方式：./gradlew :data:testDebugUnitTest --tests "com.shangmentiyu.sportscoach.domain.payroll.PayoutCalculatorTest"
 */
class PayoutCalculatorTest {

    private fun coach(
        role: String,
        baseSalary: Double = 0.0,
        lessonRate: Double = 100.0,
        commissionRate: Double = 0.0
    ) = Coach(
        name = "测试教练",
        role = role,
        baseSalary = baseSalary,
        lessonRate = lessonRate,
        commissionRate = commissionRate
    )

    @Test
    fun `兼职只有课时费`() {
        val r = PayoutCalculator.compute(coach(CoachRole.PARTTIME), personalLessons = 10)
        assertThat(r.baseSalary).isEqualTo(0.0)
        assertThat(r.lessonFee).isEqualTo(1000.0)
        assertThat(r.commission).isEqualTo(0.0)
        assertThat(r.total).isEqualTo(1000.0)
    }

    @Test
    fun `全职等于底薪加课时费`() {
        val r = PayoutCalculator.compute(
            coach(CoachRole.FULLTIME, baseSalary = 5000.0),
            personalLessons = 20
        )
        assertThat(r.total).isEqualTo(5000.0 + 2000.0)
    }

    @Test
    fun `一级合伙人分红基数是机构净利润`() {
        val r = PayoutCalculator.compute(
            coach(CoachRole.PARTNER_L1, baseSalary = 3000.0, commissionRate = 10.0),
            personalLessons = 15,
            teamLessonFeeTotal = 999999.0, // L1 不看团队基数
            orgNetProfit = 20000.0
        )
        assertThat(r.commission).isEqualTo(2000.0)
        assertThat(r.total).isEqualTo(3000.0 + 1500.0 + 2000.0)
    }

    @Test
    fun `二级合伙人提成基数是团队课时费总额`() {
        val r = PayoutCalculator.compute(
            coach(CoachRole.PARTNER_L2, baseSalary = 2000.0, commissionRate = 5.0),
            personalLessons = 10,
            teamLessonFeeTotal = 8000.0,
            orgNetProfit = 999999.0   // L2 不看机构利润
        )
        assertThat(r.commission).isEqualTo(400.0)
        assertThat(r.total).isEqualTo(2000.0 + 1000.0 + 400.0)
    }

    @Test
    fun `分成比例钳制在0到100之间`() {
        val r = PayoutCalculator.compute(
            coach(CoachRole.PARTNER_L1, commissionRate = 150.0),
            personalLessons = 0,
            orgNetProfit = 1000.0
        )
        assertThat(r.commission).isEqualTo(1000.0) // 按 100% 计
    }

    @Test
    fun `金额保留两位小数`() {
        val r = PayoutCalculator.compute(
            coach(CoachRole.PARTNER_L1, baseSalary = 0.005, commissionRate = 10.0),
            personalLessons = 0,
            orgNetProfit = 1000.0
        )
        // 底薪 0.005 四舍五入到 0.01，验证 round2 生效
        assertThat(r.baseSalary).isEqualTo(0.01)
    }

    @Test
    fun `零消课与未设置角色按兼职处理`() {
        val r = PayoutCalculator.compute(coach(role = ""), personalLessons = 0)
        assertThat(r.total).isEqualTo(0.0)
    }
}
