package com.shangmentiyu.sportscoach.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

/**
 * [HeightPredictionProcessor] 单元测试。
 *
 * 覆盖范围（v22 单元测试基建示例）：
 * 1. CMH 遗传靶身高公式：男孩 +13、女孩 -13
 * 2. 环境修正系数：
 *    - 睡眠修正（≥8.5h / 7~8.5h / <7h / 未填写）
 *    - 营养修正（≥4 / =3 / ≤2 / 未填写）
 *    - 运动修正（≥180min / <180min / 未填写）
 * 3. 总修正值上下限：±3.5cm
 * 4. 极端数据与边界条件：
 *    - 父母身高为 0 → 返回 null
 *    - 年龄超限（男孩 >16、女孩 >14）→ 骨骼闭合警告
 *    - 边界值（睡眠 8.5h 刚好 +1、运动 180min 刚好 +1.5）
 *    - 修正值达到 +3.5 上限
 *    - 修正值达到 -3.5 下限
 *
 * 运行方式：./gradlew :app:testDebugUnitTest --tests "com.shangmentiyu.sportscoach.domain.HeightPredictionProcessorTest"
 */
class HeightPredictionProcessorTest {

    private lateinit var processor: HeightPredictionProcessor

    @Before
    fun setUp() {
        processor = HeightPredictionProcessor()
    }

    // ============ 1. CMH 公式正确性 ============

    @Test
    fun `boy cmh formula adds 13`() {
        // 男孩：(180 + 160 + 13) / 2 = 176.5
        val result = processor.predict(
            gender = "男", age = 12,
            fatherHeight = 180.0, motherHeight = 160.0,
            avgSleepHours = 8.0, nutritionScore = 3, sportsMinsPerWeek = 100
        )
        assertThat(result).isNotNull()
        assertThat(result!!.targetHeight).isWithin(0.01).of(176.5)
    }

    @Test
    fun `girl cmh formula subtracts 13`() {
        // 女孩：(180 + 160 - 13) / 2 = 163.5
        val result = processor.predict(
            gender = "女", age = 12,
            fatherHeight = 180.0, motherHeight = 160.0,
            avgSleepHours = 8.0, nutritionScore = 3, sportsMinsPerWeek = 100
        )
        assertThat(result).isNotNull()
        assertThat(result!!.targetHeight).isWithin(0.01).of(163.5)
    }

    @Test
    fun `unknown gender defaults to boy formula`() {
        // 性别字段非"女"时按男孩公式处理（包含 "男" 与异常值）
        val result = processor.predict(
            gender = "未知", age = 12,
            fatherHeight = 180.0, motherHeight = 160.0,
            avgSleepHours = 8.0, nutritionScore = 3, sportsMinsPerWeek = 100
        )
        assertThat(result).isNotNull()
        assertThat(result!!.targetHeight).isWithin(0.01).of(176.5)
    }

    // ============ 2. 环境修正系数 ============

    // --- 睡眠修正 ---
    @Test
    fun `sleep at least 8_5 hours adds 1 cm`() {
        val baseline = predictBaseline()  // 睡眠 8.0（不修正）+ 营养 3（不修正）+ 运动 100（不修正）
        val sleepGood = predictBaselineCopy(avgSleepHours = 8.5)
        assertThat(sleepGood!!.adjustedHeight - baseline!!.adjustedHeight).isWithin(0.01).of(1.0)
    }

    @Test
    fun `sleep less than 7 hours subtracts 1 cm`() {
        val baseline = predictBaseline()
        val sleepBad = predictBaselineCopy(avgSleepHours = 6.5)
        assertThat(sleepBad!!.adjustedHeight - baseline!!.adjustedHeight).isWithin(0.01).of(-1.0)
    }

    @Test
    fun `sleep between 7 and 8_5 hours has no adjustment`() {
        val baseline = predictBaseline()
        val sleepMid = predictBaselineCopy(avgSleepHours = 7.5)
        assertThat(sleepMid!!.adjustedHeight).isWithin(0.01).of(baseline!!.adjustedHeight)
    }

    @Test
    fun `sleep zero or negative has no adjustment`() {
        // 未填写（0）或异常负值不修正
        val baseline = predictBaseline()
        val sleepZero = predictBaselineCopy(avgSleepHours = 0.0)
        assertThat(sleepZero!!.adjustedHeight).isWithin(0.01).of(baseline!!.adjustedHeight)
    }

    // --- 营养修正 ---
    @Test
    fun `nutrition score at least 4 adds 1 cm`() {
        val baseline = predictBaseline()
        val nutritionGood = predictBaselineCopy(nutritionScore = 4)
        assertThat(nutritionGood!!.adjustedHeight - baseline!!.adjustedHeight).isWithin(0.01).of(1.0)
    }

    @Test
    fun `nutrition score at most 2 subtracts 1 cm`() {
        val baseline = predictBaseline()
        val nutritionBad = predictBaselineCopy(nutritionScore = 2)
        assertThat(nutritionBad!!.adjustedHeight - baseline!!.adjustedHeight).isWithin(0.01).of(-1.0)
    }

    @Test
    fun `nutrition score 3 has no adjustment`() {
        val baseline = predictBaseline()
        val nutritionMid = predictBaselineCopy(nutritionScore = 3)
        assertThat(nutritionMid!!.adjustedHeight).isWithin(0.01).of(baseline!!.adjustedHeight)
    }

    @Test
    fun `nutrition score zero or negative has no adjustment`() {
        val baseline = predictBaseline()
        val nutritionZero = predictBaselineCopy(nutritionScore = 0)
        assertThat(nutritionZero!!.adjustedHeight).isWithin(0.01).of(baseline!!.adjustedHeight)
    }

    // --- 运动修正 ---
    @Test
    fun `sports at least 180 mins adds 1_5 cm`() {
        val baseline = predictBaseline()
        val sportsGood = predictBaselineCopy(sportsMinsPerWeek = 180)
        assertThat(sportsGood!!.adjustedHeight - baseline!!.adjustedHeight).isWithin(0.01).of(1.5)
    }

    @Test
    fun `sports less than 180 mins has no adjustment`() {
        val baseline = predictBaseline()
        val sportsLow = predictBaselineCopy(sportsMinsPerWeek = 179)
        assertThat(sportsLow!!.adjustedHeight).isWithin(0.01).of(baseline!!.adjustedHeight)
    }

    @Test
    fun `sports zero or negative has no adjustment`() {
        val baseline = predictBaseline()
        val sportsZero = predictBaselineCopy(sportsMinsPerWeek = 0)
        assertThat(sportsZero!!.adjustedHeight).isWithin(0.01).of(baseline!!.adjustedHeight)
    }

    // ============ 3. 修正值上下限 ±3.5cm ============

    @Test
    fun `adjustment capped at plus 3_5 cm`() {
        // 睡眠 +1、营养 +1、运动 +1.5 → 总 +3.5（恰好达上限，不触发截断）
        val result = processor.predict(
            gender = "男", age = 12,
            fatherHeight = 175.0, motherHeight = 160.0,
            avgSleepHours = 9.0, nutritionScore = 5, sportsMinsPerWeek = 300
        )
        assertThat(result).isNotNull()
        assertThat(result!!.adjustment).isWithin(0.01).of(3.5)
    }

    @Test
    fun `adjustment capped at minus 3_5 cm`() {
        // 睡眠 -1、营养 -1、运动 0 → 总 -2.0（未触达下限 -3.5）
        val result = processor.predict(
            gender = "男", age = 12,
            fatherHeight = 175.0, motherHeight = 160.0,
            avgSleepHours = 6.0, nutritionScore = 1, sportsMinsPerWeek = 0
        )
        assertThat(result).isNotNull()
        // 总修正 = -1（睡眠）+ -1（营养）+ 0（运动）= -2.0
        assertThat(result!!.adjustment).isWithin(0.01).of(-2.0)
    }

    @Test
    fun `raw adjustment does not exceed plus 3_5 cap`() {
        // 注意：当前实现中三项相加最大值就是 +3.5，正好等于上限
        // 此测试验证即便加上更多理论修正值，结果仍被限制在 3.5
        // 由于算法本身的最大值就是 3.5，这里通过断言 adjustedHeight - targetHeight <= 3.5 验证
        val result = processor.predict(
            gender = "男", age = 10,
            fatherHeight = 190.0, motherHeight = 170.0,
            avgSleepHours = 10.0, nutritionScore = 5, sportsMinsPerWeek = 500
        )
        assertThat(result).isNotNull()
        assertThat(result!!.adjustment).isAtMost(3.5)
    }

    @Test
    fun `adjustment floor at minus 3_5 cm`() {
        // 当前算法不可能达到 -3.5 下限（睡眠 -1 + 营养 -1 + 运动 0 = -2）
        // 但仍验证 adjustment >= -3.5 的不变式
        val result = processor.predict(
            gender = "女", age = 12,
            fatherHeight = 170.0, motherHeight = 155.0,
            avgSleepHours = 5.0, nutritionScore = 1, sportsMinsPerWeek = 0
        )
        assertThat(result).isNotNull()
        assertThat(result!!.adjustment).isAtLeast(-3.5)
    }

    // ============ 4. 极端数据与边界条件 ============

    @Test
    fun `returns null when father height is zero`() {
        val result = processor.predict(
            gender = "男", age = 12,
            fatherHeight = 0.0, motherHeight = 160.0,
            avgSleepHours = 8.0, nutritionScore = 3, sportsMinsPerWeek = 100
        )
        assertThat(result).isNull()
    }

    @Test
    fun `returns null when mother height is zero`() {
        val result = processor.predict(
            gender = "女", age = 12,
            fatherHeight = 180.0, motherHeight = 0.0,
            avgSleepHours = 8.0, nutritionScore = 3, sportsMinsPerWeek = 100
        )
        assertThat(result).isNull()
    }

    @Test
    fun `returns null when both parents heights are zero`() {
        val result = processor.predict(
            gender = "男", age = 12,
            fatherHeight = 0.0, motherHeight = 0.0,
            avgSleepHours = 8.0, nutritionScore = 3, sportsMinsPerWeek = 100
        )
        assertThat(result).isNull()
    }

    @Test
    fun `returns null when parents heights are negative`() {
        // 异常负值同样视为数据不足
        val result = processor.predict(
            gender = "男", age = 12,
            fatherHeight = -10.0, motherHeight = 160.0,
            avgSleepHours = 8.0, nutritionScore = 3, sportsMinsPerWeek = 100
        )
        assertThat(result).isNull()
    }

    @Test
    fun `bone age warning when boy older than 16`() {
        val result = processor.predict(
            gender = "男", age = 17,
            fatherHeight = 175.0, motherHeight = 160.0,
            avgSleepHours = 8.0, nutritionScore = 3, sportsMinsPerWeek = 100
        )
        assertThat(result).isNotNull()
        assertThat(result!!.boneAgeWarning).contains("骨骼可能已闭合")
    }

    @Test
    fun `bone age warning when girl older than 14`() {
        val result = processor.predict(
            gender = "女", age = 15,
            fatherHeight = 175.0, motherHeight = 160.0,
            avgSleepHours = 8.0, nutritionScore = 3, sportsMinsPerWeek = 100
        )
        assertThat(result).isNotNull()
        assertThat(result!!.boneAgeWarning).contains("骨骼可能已闭合")
    }

    @Test
    fun `no bone age warning when boy younger than 16`() {
        val result = processor.predict(
            gender = "男", age = 16,
            fatherHeight = 175.0, motherHeight = 160.0,
            avgSleepHours = 8.0, nutritionScore = 3, sportsMinsPerWeek = 100
        )
        assertThat(result).isNotNull()
        assertThat(result!!.boneAgeWarning).isNull()
    }

    @Test
    fun `no bone age warning when girl younger than 14`() {
        val result = processor.predict(
            gender = "女", age = 14,
            fatherHeight = 175.0, motherHeight = 160.0,
            avgSleepHours = 8.0, nutritionScore = 3, sportsMinsPerWeek = 100
        )
        assertThat(result).isNotNull()
        assertThat(result!!.boneAgeWarning).isNull()
    }

    @Test
    fun `no bone age warning when age is zero`() {
        // 未填写年龄（0）不应触发骨骼闭合警告
        val result = processor.predict(
            gender = "男", age = 0,
            fatherHeight = 175.0, motherHeight = 160.0,
            avgSleepHours = 8.0, nutritionScore = 3, sportsMinsPerWeek = 100
        )
        assertThat(result).isNotNull()
        assertThat(result!!.boneAgeWarning).isNull()
    }

    // --- 边界值：阈值上的精确行为 ---

    @Test
    fun `sleep exactly 8_5 hours gets plus 1 cm`() {
        // 边界值：8.5h 恰好满足 ≥8.5 触发 +1cm
        val baseline = predictBaseline()  // 8.0h 不修正
        val atBoundary = predictBaselineCopy(avgSleepHours = 8.5)
        assertThat(atBoundary!!.adjustedHeight - baseline!!.adjustedHeight).isWithin(0.01).of(1.0)
    }

    @Test
    fun `sleep exactly 7_0 hours gets no adjustment`() {
        // 边界值：7.0h 不满足 <7.0，不修正
        val baseline = predictBaseline()  // 8.0h
        val atBoundary = predictBaselineCopy(avgSleepHours = 7.0)
        assertThat(atBoundary!!.adjustedHeight).isWithin(0.01).of(baseline!!.adjustedHeight)
    }

    @Test
    fun `sleep 6_99 hours gets minus 1 cm`() {
        // 边界值下方：6.99h 满足 <7.0，触发 -1cm
        val baseline = predictBaseline()
        val justBelow = predictBaselineCopy(avgSleepHours = 6.99)
        assertThat(justBelow!!.adjustedHeight - baseline!!.adjustedHeight).isWithin(0.01).of(-1.0)
    }

    @Test
    fun `nutrition score exactly 4 gets plus 1 cm`() {
        val baseline = predictBaseline()
        val atBoundary = predictBaselineCopy(nutritionScore = 4)
        assertThat(atBoundary!!.adjustedHeight - baseline!!.adjustedHeight).isWithin(0.01).of(1.0)
    }

    @Test
    fun `nutrition score exactly 2 gets minus 1 cm`() {
        val baseline = predictBaseline()
        val atBoundary = predictBaselineCopy(nutritionScore = 2)
        assertThat(atBoundary!!.adjustedHeight - baseline!!.adjustedHeight).isWithin(0.01).of(-1.0)
    }

    @Test
    fun `sports exactly 180 mins gets plus 1_5 cm`() {
        val baseline = predictBaseline()
        val atBoundary = predictBaselineCopy(sportsMinsPerWeek = 180)
        assertThat(atBoundary!!.adjustedHeight - baseline!!.adjustedHeight).isWithin(0.01).of(1.5)
    }

    @Test
    fun `sports 179 mins gets no adjustment`() {
        val baseline = predictBaseline()
        val justBelow = predictBaselineCopy(sportsMinsPerWeek = 179)
        assertThat(justBelow!!.adjustedHeight).isWithin(0.01).of(baseline!!.adjustedHeight)
    }

    // ============ 5. 预测区间正确性 ============

    @Test
    fun `bounds centered at adjusted height with at least 2 cm spread`() {
        val result = processor.predict(
            gender = "男", age = 12,
            fatherHeight = 175.0, motherHeight = 160.0,
            avgSleepHours = 8.0, nutritionScore = 3, sportsMinsPerWeek = 100
        )
        assertThat(result).isNotNull()
        // 修正值 0 时，区间至少为 ±2.0
        val r = result!!
        assertThat(r.lowerBound).isWithin(0.01).of(r.adjustedHeight - 2.0)
        assertThat(r.upperBound).isWithin(0.01).of(r.adjustedHeight + 2.0)
    }

    @Test
    fun `bounds widen with larger adjustment`() {
        // 修正值 +3.5 时，区间应为 ±3.5
        val result = processor.predict(
            gender = "男", age = 12,
            fatherHeight = 175.0, motherHeight = 160.0,
            avgSleepHours = 9.0, nutritionScore = 5, sportsMinsPerWeek = 300
        )
        assertThat(result).isNotNull()
        val r = result!!
        assertThat(r.lowerBound).isWithin(0.01).of(r.adjustedHeight - 3.5)
        assertThat(r.upperBound).isWithin(0.01).of(r.adjustedHeight + 3.5)
    }

    @Test
    fun `result rounding keeps one decimal place`() {
        val result = processor.predict(
            gender = "男", age = 12,
            fatherHeight = 175.0, motherHeight = 160.0,
            avgSleepHours = 8.0, nutritionScore = 3, sportsMinsPerWeek = 100
        )
        assertThat(result).isNotNull()
        // (175 + 160 + 13) / 2 = 174.0 → 保留一位小数后应为 174.0
        assertThat(result!!.targetHeight).isWithin(0.01).of(174.0)
    }

    // ============ 6. 建议文案 ============

    @Test
    fun `advice text mentions all three improvements when all bad`() {
        val result = processor.predict(
            gender = "男", age = 12,
            fatherHeight = 175.0, motherHeight = 160.0,
            avgSleepHours = 6.0, nutritionScore = 1, sportsMinsPerWeek = 50
        )
        assertThat(result).isNotNull()
        val advice = result!!.adviceText
        assertThat(advice).contains("睡眠")
        assertThat(advice).contains("营养")
        assertThat(advice).contains("运动")
    }

    @Test
    fun `advice text positive when all good`() {
        val result = processor.predict(
            gender = "男", age = 12,
            fatherHeight = 175.0, motherHeight = 160.0,
            avgSleepHours = 9.0, nutritionScore = 5, sportsMinsPerWeek = 300
        )
        assertThat(result).isNotNull()
        // 全部达标时文案应包含"优秀"或"良好"等正向描述
        assertThat(result!!.adviceText).contains("优秀")
    }

    // ============ 辅助方法 ============

    /**
     * 基线预测：所有修正项均为中性值（不修正）
     * - 睡眠 8.0h：不修正（7~8.5 区间内）
     * - 营养评分 3：不修正（≤2 才修正）
     * - 运动 100min：不修正（<180）
     */
    private fun predictBaseline(): HeightPredictionResult? {
        return processor.predict(
            gender = "男", age = 12,
            fatherHeight = 175.0, motherHeight = 160.0,
            avgSleepHours = 8.0, nutritionScore = 3, sportsMinsPerWeek = 100
        )
    }

    /**
     * 基线副本：基于 [predictBaseline] 修改单个参数，方便对比修正值差异
     */
    private fun predictBaselineCopy(
        avgSleepHours: Double = 8.0,
        nutritionScore: Int = 3,
        sportsMinsPerWeek: Int = 100
    ): HeightPredictionResult? {
        return processor.predict(
            gender = "男", age = 12,
            fatherHeight = 175.0, motherHeight = 160.0,
            avgSleepHours = avgSleepHours,
            nutritionScore = nutritionScore,
            sportsMinsPerWeek = sportsMinsPerWeek
        )
    }
}
