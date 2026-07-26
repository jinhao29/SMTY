package com.shangmentiyu.sportscoach.domain

import kotlin.math.roundToInt

/**
 * TDEE 计算处理器（纯逻辑单元）。
 *
 * 算法依据：Mifflin-St Jeor 公式（1990 年发表，目前国际通用的 BMR 估算公式）。
 *
 * 公式：
 * - 男性：BMR = 10 × 体重(kg) + 6.25 × 身高(cm) − 5 × 年龄 + 5
 * - 女性：BMR = 10 × 体重(kg) + 6.25 × 身高(cm) − 5 × 年龄 − 161
 *
 * TDEE = BMR × 活动系数
 *
 * 减脂建议规则：
 * - 年龄 > 16 岁（成人）：建议每日热量缺口 300~500 大卡（取中值 400 大卡输出）
 * - 年龄 <= 16 岁（生长发育期）：强制不允许输出缺口，返回警告文案
 *
 * 无状态、纯函数，便于单元测试，符合项目分层架构的 Processor 层职责。
 */
class TdeeProcessor {

    /**
     * 计算 TDEE 结果。
     *
     * @param gender        性别："男" / "女"（其他值按"男"处理）
     * @param weightKg      体重（kg），<=0 视为无效输入
     * @param heightCm      身高（cm），<=0 视为无效输入
     * @param age           年龄（周岁），<=0 视为无效输入
     * @param activityLevel 活动水平枚举
     * @return 计算结果；任一参数无效时返回 null
     */
    fun calculate(
        gender: String,
        weightKg: Double,
        heightCm: Double,
        age: Int,
        activityLevel: ActivityLevel
    ): TdeeResult? {
        // 入参校验：体重 / 身高 / 年龄必须为正
        if (weightKg <= 0.0 || heightCm <= 0.0 || age <= 0) return null

        // 1. 计算 BMR（Mifflin-St Jeor）
        val bmr = if (gender == "女") {
            10.0 * weightKg + 6.25 * heightCm - 5.0 * age - 161.0
        } else {
            // "男" 或其他值统一按男性公式
            10.0 * weightKg + 6.25 * heightCm - 5.0 * age + 5.0
        }

        // 2. 计算 TDEE
        val tdee = bmr * activityLevel.factor

        // 3. 减脂建议：成人输出缺口；发育期强制警告，不允许缺口
        val isAdult = age > 16
        val deficitAdvice: Int?
        val warningText: String?
        if (isAdult) {
            // 取 300~500 中值 400，作为安全起始缺口
            deficitAdvice = 400
            warningText = null
        } else {
            deficitAdvice = null
            warningText = "处于生长发育期，不建议制造热量缺口，建议通过均衡饮食与运动管理体重。"
        }

        return TdeeResult(
            bmr = bmr.roundToInt().toDouble(),
            tdee = tdee.roundToInt().toDouble(),
            deficitAdvice = deficitAdvice,
            warningText = warningText,
            isAdult = isAdult
        )
    }
}
