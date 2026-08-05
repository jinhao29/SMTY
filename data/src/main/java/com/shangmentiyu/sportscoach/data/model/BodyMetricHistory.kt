package com.shangmentiyu.sportscoach.data.model

import androidx.compose.runtime.Stable
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 学员身体形态历史记录实体：用于绘制体型变化曲线。
 *
 * 每次学员测量身高/体重后追加一条记录，保留全部历史。
 * 自动计算 BMI = weightKg / (heightM * heightM)。
 *
 * 性能：v26 优化2添加 [Stable] 注解，作为 Compose State 使用时避免无效重组。
 */
@Stable
@Entity(tableName = "body_metric_history")
data class BodyMetricHistory(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString().take(8),
    val studentName: String,              // 学员姓名（软关联，保留用于显示）
    val studentId: String? = null,        // 学员唯一ID（软关联外键，v20 引入，旧数据 NULL）
    val date: String,                     // 测量日期 YYYY-MM-DD
    val heightCm: Int = 0,                // 身高 cm
    val weightKg: Float = 0f,             // 体重 kg
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    /** 计算的 BMI 值 */
    val bmi: Float get() {
        if (heightCm <= 0 || weightKg <= 0f) return 0f
        val h = heightCm / 100.0
        return (weightKg / (h * h)).toFloat()
    }
}
