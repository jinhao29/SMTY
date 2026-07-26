package com.shangmentiyu.sportscoach.domain

import kotlin.math.abs

/**
 * 身高遗传潜力与后天预测处理器。
 *
 * 纯逻辑单元，无状态，便于单元测试。
 *
 * 算法规则：
 * 1. 遗传靶身高（CMH 公式）：
 *    - 男孩 = (父高 + 母高 + 13) / 2
 *    - 女孩 = (父高 + 母高 - 13) / 2
 * 2. 后天环境修正（在靶身高基础上做加减法）：
 *    - 睡眠 ≥ 8.5h → +1cm；< 7h → -1cm
 *    - 营养评分 ≥ 4 → +1cm；≤ 2 → -1cm
 *    - 每周运动 ≥ 180min → +1.5cm
 * 3. 总修正值限制在 ±3.5cm 以内
 * 4. 年龄超限提示：男孩 > 16 岁 / 女孩 > 14 岁 → 骨骼可能已闭合
 */
class HeightPredictionProcessor {

    /** 修正值上限（cm） */
    private val maxAdjustment = 3.5

    /**
     * 执行身高预测计算。
     *
     * @param gender           性别（"男" 或 "女"）
     * @param age              年龄（岁）
     * @param fatherHeight     父亲身高（cm）
     * @param motherHeight     母亲身高（cm）
     * @param avgSleepHours    日常平均睡眠小时
     * @param nutritionScore   营养均衡评分（1-5）
     * @param sportsMinsPerWeek 每周运动总时长（分钟）
     * @return 预测结果，若父母身高为 0 则返回 null 表示数据不足
     */
    fun predict(
        gender: String,
        age: Int,
        fatherHeight: Double,
        motherHeight: Double,
        avgSleepHours: Double,
        nutritionScore: Int,
        sportsMinsPerWeek: Int
    ): HeightPredictionResult? {
        // 数据有效性校验：父母身高必须大于 0
        if (fatherHeight <= 0.0 || motherHeight <= 0.0) return null

        // 1. 计算遗传靶身高（CMH 公式）
        val targetHeight = calculateTargetHeight(gender, fatherHeight, motherHeight)

        // 2. 计算后天环境修正值
        val sleepAdj = calculateSleepAdjustment(avgSleepHours)
        val nutritionAdj = calculateNutritionAdjustment(nutritionScore)
        val sportsAdj = calculateSportsAdjustment(sportsMinsPerWeek)
        val rawAdjustment = sleepAdj + nutritionAdj + sportsAdj

        // 3. 限制总修正值在 ±3.5cm 以内
        val adjustment = rawAdjustment.coerceIn(-maxAdjustment, maxAdjustment)

        // 4. 计算预测身高与浮动区间
        val adjustedHeight = targetHeight + adjustment
        val lowerBound = adjustedHeight - abs(adjustment).coerceAtLeast(2.0)
        val upperBound = adjustedHeight + abs(adjustment).coerceAtLeast(2.0)

        // 5. 生成建议文案
        val adviceText = buildAdviceText(sleepAdj, nutritionAdj, sportsAdj, adjustment)

        // 6. 骨骼闭合警告
        val boneAgeWarning = checkBoneAgeWarning(gender, age)

        return HeightPredictionResult(
            targetHeight = roundOneDecimal(targetHeight),
            adjustedHeight = roundOneDecimal(adjustedHeight),
            lowerBound = roundOneDecimal(lowerBound),
            upperBound = roundOneDecimal(upperBound),
            adjustment = roundOneDecimal(adjustment),
            adviceText = adviceText,
            boneAgeWarning = boneAgeWarning
        )
    }

    /** CMH 公式：男孩 +13，女孩 -13 */
    private fun calculateTargetHeight(gender: String, father: Double, mother: Double): Double {
        return if (gender == "女") {
            (father + mother - 13) / 2.0
        } else {
            (father + mother + 13) / 2.0
        }
    }

    /** 睡眠修正：≥ 8.5h 加 1cm，< 7h 减 1cm */
    private fun calculateSleepAdjustment(hours: Double): Double {
        return when {
            hours <= 0.0 -> 0.0  // 未填写不修正
            hours >= 8.5 -> 1.0
            hours < 7.0 -> -1.0
            else -> 0.0
        }
    }

    /** 营养修正：评分 ≥ 4 加 1cm，≤ 2 减 1cm */
    private fun calculateNutritionAdjustment(score: Int): Double {
        return when {
            score <= 0 -> 0.0  // 未填写不修正
            score >= 4 -> 1.0
            score <= 2 -> -1.0
            else -> 0.0
        }
    }

    /** 运动修正：每周 ≥ 180min 加 1.5cm */
    private fun calculateSportsAdjustment(mins: Int): Double {
        return if (mins > 0 && mins >= 180) 1.5 else 0.0
    }

    /** 检查骨骼闭合年龄限制 */
    private fun checkBoneAgeWarning(gender: String, age: Int): String? {
        if (age <= 0) return null
        val limit = if (gender == "女") 14 else 16
        return if (age > limit) "骨骼可能已闭合，请以医院骨龄检测为准" else null
    }

    /** 根据各项修正值生成综合建议文案 */
    private fun buildAdviceText(
        sleepAdj: Double, nutritionAdj: Double, sportsAdj: Double, totalAdj: Double
    ): String {
        if (totalAdj <= 0.0 && totalAdj > -0.01) {
            return "后天环境维持良好，保持当前生活习惯即可"
        }
        val parts = mutableListOf<String>()
        if (sleepAdj < 0) parts.add("建议保证每日 8.5 小时以上睡眠")
        if (nutritionAdj < 0) parts.add("建议改善饮食营养均衡度")
        if (sportsAdj <= 0) parts.add("建议每周运动 3 次以上，每次 1 小时")
        if (parts.isEmpty()) {
            return "睡眠、营养、运动均达标，后天环境优秀"
        }
        return parts.joinToString("；")
    }

    /** 保留一位小数 */
    private fun roundOneDecimal(value: Double): Double {
        return kotlin.math.round(value * 10.0) / 10.0
    }
}
