package com.shangmentiyu.sportscoach.data.model

import androidx.compose.runtime.Stable
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 家长报告实体：记录每次生成的周报/月报。
 * 报告内容（训练统计、成绩变化、里程碑、教练寄语）以 JSON 形式存于 content 字段。
 *
 * 性能：v26 优化2添加 [Stable] 注解，作为 Compose State 使用时避免无效重组。
 */
@Stable
@Entity(tableName = "parent_reports")
data class ParentReport(
    @PrimaryKey val id: String,                  // UUID
    val studentName: String,                     // 学员姓名（软关联，保留用于显示）
    val studentId: String? = null,               // 学员唯一ID（软关联外键，v20 引入，旧数据 NULL）
    val reportType: String,                      // "周报" / "月报"
    val startDate: String,                       // YYYY-MM-DD
    val endDate: String,                         // YYYY-MM-DD
    val content: String,                         // JSON：完整报告内容
    val shared: Boolean = false,                 // 是否已分享给家长
    val createdAt: Long = System.currentTimeMillis()
) {
    /** 是否包含里程碑（用于 UI 高亮显示） */
    val hasMilestones: Boolean
        get() = content.contains("\"milestones\"") && !content.contains("\"milestones\":[]")
}
