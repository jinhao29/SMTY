package com.shangmentiyu.sportscoach.domain

/**
 * 活动水平枚举（TDEE 计算用）。
 *
 * 系数来源于 Mifflin-St Jeor 公式的标准活动因子：
 * - [SEDENTARY]    1.2    久坐无运动（如办公室职员）
 * - [LIGHT]        1.375  轻度运动（每周 1-3 次）
 * - [MODERATE]     1.55   中度运动（每周 3-5 次）
 * - [ACTIVE]       1.725  高度运动（每周 6-7 次）
 * - [VERY_ACTIVE]  1.9    极高强度（每日训练 / 体力劳动）
 *
 * 用枚举而非常数数字，便于 UI 下拉显示中文标签，且编译期防止非法值。
 */
enum class ActivityLevel(val factor: Double, val label: String) {
    SEDENTARY(1.2, "久坐无运动"),
    LIGHT(1.375, "轻度运动（每周1-3次）"),
    MODERATE(1.55, "中度运动（每周3-5次）"),
    ACTIVE(1.725, "高度运动（每周6-7次）"),
    VERY_ACTIVE(1.9, "极高强度（每日训练）");

    companion object {
        /** 默认值：教练未选择时使用中度运动（适合大多数学员） */
        val DEFAULT: ActivityLevel = MODERATE
    }
}

/**
 * TDEE 计算结果数据模型。
 *
 * 字段含义：
 * - [bmr]              基础代谢率（Basal Metabolic Rate），完全静止时维持生命所需热量
 * - [tdee]             每日总能量消耗（Total Daily Energy Expenditure），含活动系数
 * - [deficitAdvice]    减脂热量缺口建议（大卡/日），仅在成人（>16岁）时输出
 * - [warningText]      生长发育期警告，仅在 <16 岁时输出；此时 [deficitAdvice] 为 null
 * - [isAdult]          是否成人（>16岁），UI 据此决定是否展示缺口建议区
 *
 * 设计：[deficitAdvice] 与 [warningText] 互斥，二选一为非空，逻辑由 [TdeeProcessor] 强制保证。
 */
data class TdeeResult(
    val bmr: Double,
    val tdee: Double,
    val deficitAdvice: Int?,
    val warningText: String?,
    val isAdult: Boolean
)
