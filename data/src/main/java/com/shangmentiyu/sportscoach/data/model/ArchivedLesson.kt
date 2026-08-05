package com.shangmentiyu.sportscoach.data.model

import androidx.compose.runtime.Stable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 归档课堂记录实体（数据层）。
 *
 * 用于冷热数据归档策略：将一年前的 [Lesson] 记录迁移到本表，
 * 主表 [Lesson] 仅保留近一年数据，避免历史数据累积导致查询性能下降。
 *
 * 字段与 [Lesson] 完全一致，仅表名不同：
 * - 主表 lessons：热数据，频繁查询（首页/学员详情/统计）
 * - 归档表 archived_lessons：冷数据，仅历史报表与归档查询时访问
 *
 * 迁移策略（v22 引入）：
 * - 调用 [com.shangmentiyu.sportscoach.data.repo.OperationRepository.archiveLessonsBefore]
 *   将指定日期前的记录从 lessons 表移动到本表
 * - 归档操作在数据库事务内完成，保证原子性
 * - 归档后主表行数减少，索引体积下降，首页与学员详情查询显著加速
 *
 * 索引设计（与 [Lesson] 保持一致，便于归档后按学员/日期查询）：
 * - idx_archived_lessons_student_date：按学员+日期归档查询
 * - idx_archived_lessons_date：按日期归档查询
 *
 * 性能：v26 优化2添加 [Stable] 注解，作为 Compose State 使用时避免无效重组。
 */
@Stable
@Entity(
    tableName = "archived_lessons",
    indices = [
        Index(value = ["studentName", "date"], name = "idx_archived_lessons_student_date"),
        Index(value = ["date"], name = "idx_archived_lessons_date")
    ]
)
data class ArchivedLesson(
    @PrimaryKey val id: String,           // 与原 Lesson.id 一致（迁移时保留）
    val date: String,                     // YYYY-MM-DD
    val time: String,                     // HH:mm（签到时间）
    val studentName: String,              // 学员姓名（软关联，保留用于显示）
    val studentId: String? = null,        // 学员唯一ID（软关联外键，可能为 NULL）
    val content: String = "[]",           // 训练内容JSON
    val scores: String = "{}",            // 成绩JSON
    val summary: String = "",             // 课后小结
    val duration: Int = 60,               // 课时时长(分钟)
    val coach: String = "",               // 教练
    val location: String = "",            // 地点
    val lessonType: String = "训练课",     // 训练/体测/技术/恢复
    val attendance: String = "准时",       // 准时/迟到/请假/旷课
    val attitude: String = "认真",         // 训练态度
    val performance: Int = 7,              // 1-10
    val nextGoal: String = "",            // 下次课目标
    val coachComment: String = "",        // 教练寄语
    val packageId: String = "",           // 消耗的课时包ID
    val photoPath: String = "",           // 签到照片路径
    val signOutTime: String = "",         // 签退时间
    val signOutPhotoPath: String = "",    // 签退照片路径
    val contentImages: String = "[]",     // 课后反馈图片路径 JSON
    val status: String = "已签到",         // v27：课时状态：已签到 / 已签退（与 Lesson 对齐）
    val archivedAt: Long = System.currentTimeMillis(),  // 归档时间戳
    val createdAt: Long = System.currentTimeMillis()    // 原始创建时间
)
