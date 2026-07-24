package com.shangmentiyu.sportscoach.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 家长报告实体：记录每次生成的周报/月报。
 * 报告内容（训练统计、成绩变化、里程碑、教练寄语）以 JSON 形式存于 content 字段。
 */
@Entity(tableName = "parent_reports")
data class ParentReport(
    @PrimaryKey val id: String,                  // UUID
    val studentName: String,                     // 学员姓名
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
