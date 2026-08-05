package com.shangmentiyu.sportscoach.core

import kotlin.math.pow

/**
 * BMI 处理器：纯逻辑单元，负责 BMI 计算与体型分类。
 *
 * 设计原则：
 * - 无状态、无 Android 依赖，便于单元测试
 * - 使用中国成人 BMI 标准（GB/T 26343-2010 学生体质健康标准亦参考此分类）
 *
 * 分类标准（中国成人 BMI，单位 kg/m²）：
 * - 偏瘦：< 18.5
 * - 正常：18.5 ≤ BMI < 24
 * - 超重：24 ≤ BMI < 28
 * - 肥胖：≥ 28
 *
 * 注：青少年体型评估严格意义上应使用年龄性别百分位表，
 * 此处提供成人标准作为快速参考，UI 层可标注"参考值"。
 */
object BmiProcessor {

    /** BMI 体型分类 */
    enum class BmiCategory {
        THIN,       // 偏瘦
        NORMAL,     // 正常
        OVERWEIGHT, // 超重
        OBESE;      // 肥胖

        /** 中文标签 */
        val label: String get() = when (this) {
            THIN -> "偏瘦"
            NORMAL -> "正常"
            OVERWEIGHT -> "超重"
            OBESE -> "肥胖"
        }
    }

    /** BMI 计算结果 */
    data class BmiResult(
        val bmi: Float,
        val category: BmiCategory,
        val valid: Boolean,
        val message: String
    ) {
        companion object {
            val INVALID = BmiResult(0f, BmiCategory.THIN, false, "请填写身高体重")
        }
    }

    /**
     * 计算 BMI 并判定体型分类。
     *
     * @param heightCm 身高（厘米），需 > 0
     * @param weightKg 体重（千克），需 > 0
     * @return BmiResult，参数无效时返回 INVALID
     */
    fun compute(heightCm: Int, weightKg: Float): BmiResult {
        if (heightCm <= 0 || weightKg <= 0f) return BmiResult.INVALID

        val heightM = heightCm / 100.0
        val bmi = (weightKg.toDouble() / heightM.pow(2.0)).toFloat()
        val category = classify(bmi)
        return BmiResult(
            bmi = bmi,
            category = category,
            valid = true,
            message = "BMI ${String.format("%.1f", bmi)} · ${category.label}"
        )
    }

    /** BMI 数值映射体型分类 */
    fun classify(bmi: Float): BmiCategory = when {
        bmi < 18.5f -> BmiCategory.THIN
        bmi < 24f -> BmiCategory.NORMAL
        bmi < 28f -> BmiCategory.OVERWEIGHT
        else -> BmiCategory.OBESE
    }

    /** 一位小数格式化 */
    fun format(bmi: Float): String = String.format("%.1f", bmi)
}
