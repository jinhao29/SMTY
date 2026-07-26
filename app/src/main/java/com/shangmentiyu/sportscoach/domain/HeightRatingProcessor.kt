package com.shangmentiyu.sportscoach.domain

/**
 * 当前身高评级处理器（纯逻辑单元，无状态，便于单元测试）。
 *
 * 依据 [GrowthStandardTable] 中国儿童身高标准表，
 * 对学员当前身高进行 P3 / P50 / P97 三档评级。
 *
 * 评级规则：
 * - 当前身高 < P3     → [HeightRating.HEIGHT_SHORT]  偏矮
 * - P3 ≤ 当前身高 ≤ P97 → [HeightRating.HEIGHT_AVERAGE] 正常
 * - 当前身高 > P97    → [HeightRating.HEIGHT_TALL]   优秀
 *
 * 当评级为偏矮时，结合后天环境数据（睡眠 / 营养 / 运动）
 * 生成 1-2 句具体的干预建议。
 */
class HeightRatingProcessor {

    /**
     * 评级结果。
     *
     * @param rating          评级状态
     * @param standard        命中的标准条目（含 P3/P50/P97）
     * @param advice          偏矮时的干预建议；其他评级为 null
     */
    data class RatingOutcome(
        val rating: HeightRating,
        val standard: GrowthStandard,
        val advice: String?
    )

    /**
     * 执行评级计算。
     *
     * @param gender       性别（"男" 或 "女"）
     * @param age          年龄（周岁）
     * @param currentHeight 当前身高（cm）
     * @param avgSleepHours 日常平均睡眠小时（用于偏矮干预建议）
     * @param nutritionScore 营养评分 1-5（用于偏矮干预建议）
     * @param sportsMinsPerWeek 每周运动分钟（用于偏矮干预建议）
     * @return 评级结果；年龄超出 3-18 范围或身高为 0 时返回 null
     */
    fun evaluate(
        gender: String,
        age: Int,
        currentHeight: Double,
        avgSleepHours: Double = 0.0,
        nutritionScore: Int = 0,
        sportsMinsPerWeek: Int = 0
    ): RatingOutcome? {
        if (currentHeight <= 0.0) return null
        val standard = GrowthStandardTable.lookup(gender, age) ?: return null

        val rating = when {
            currentHeight < standard.p3  -> HeightRating.HEIGHT_SHORT
            currentHeight > standard.p97 -> HeightRating.HEIGHT_TALL
            else                         -> HeightRating.HEIGHT_AVERAGE
        }

        val advice = if (rating == HeightRating.HEIGHT_SHORT) {
            buildInterventionAdvice(avgSleepHours, nutritionScore, sportsMinsPerWeek)
        } else null

        return RatingOutcome(rating, standard, advice)
    }

    /**
     * 偏矮干预建议生成：结合睡眠 / 营养 / 运动给出 1-2 句具体话术。
     */
    private fun buildInterventionAdvice(
        sleep: Double,
        nutrition: Int,
        sports: Int
    ): String {
        val parts = mutableListOf<String>()
        parts.add("当前身高低于同龄标准")

        // 优先运动建议（教练场景核心）
        if (sports <= 0 || sports < 180) {
            parts.add("建议增加跳绳 / 摸高 / 篮球等纵向拉伸运动，每周至少 3 次每次 1 小时")
        }
        // 睡眠建议
        if (sleep <= 0 || sleep < 9.0) {
            parts.add("保证每日 9 小时以上睡眠，生长激素分泌高峰在深睡期")
        }
        // 营养建议
        if (nutrition <= 0 || nutrition <= 3) {
            parts.add("补充优质蛋白与钙质，每日牛奶 + 鸡蛋 + 鱼虾")
        }

        // 最多取前 2 条，避免文案过长
        return parts.take(3).joinToString("；")
    }
}
