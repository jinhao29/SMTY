package com.shangmentiyu.sportscoach.domain

/**
 * 身高预测结果数据模型。
 *
 * @param targetHeight   遗传靶身高（CMH 公式计算，cm）
 * @param adjustedHeight 经过后天环境修正后的预测身高（cm）
 * @param lowerBound      预测区间下限（cm）
 * @param upperBound      预测区间上限（cm）
 * @param adjustment      后天修正总值（cm，正=加分，负=扣分）
 * @param adviceText      综合建议文案
 * @param boneAgeWarning  骨骼闭合警告提示（非空时表示超龄，需以医院骨龄检测为准）
 * @param rating          当前身高评级（基于中国儿童身高标准表，可空表示年龄超范围未评级）
 * @param ratingStandard  评级所用标准条目（可空，用于 UI 显示 P3/P50/P97 参考值）
 * @param ratingAdvice    评级专属干预建议（仅 HEIGHT_SHORT 时非空）
 */
data class HeightPredictionResult(
    val targetHeight: Double,
    val adjustedHeight: Double,
    val lowerBound: Double,
    val upperBound: Double,
    val adjustment: Double,
    val adviceText: String,
    val boneAgeWarning: String?,
    val rating: HeightRating? = null,
    val ratingStandard: GrowthStandard? = null,
    val ratingAdvice: String? = null
)
