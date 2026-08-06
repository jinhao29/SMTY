package com.shangmentiyu.sportscoach.data.model

import androidx.compose.runtime.Stable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 学员训练计划图片实体（来自电脑端截图推送）。
 *
 * 数据来源：
 * - 桌面端 PySide6 截图训练计划页面 → 局域网 HTTP 服务 → LanImageReceiver 下载
 * - 文件名格式：{学员姓名}_{YYYYMMDD}_plan.png
 *
 * 关联策略（软关联，与 Lesson / LessonPackage 保持一致）：
 * - studentName：学员姓名（用于显示与查询，保留兼容旧数据）
 * - studentId：学员 ID（v48 新增，双通道软关联，与 Lesson/Schedule 一致）
 * - 无强外键约束，删除学员时通过 [PlanImageDao.deleteByStudentIdDual] 级联清理
 *
 * 存储位置：
 * - 图片文件：context.filesDir/ImportedPlans/{原文件名}
 * - 数据库仅记录路径，避免 blob 占用数据库体积
 *
 * === v25 新增：跨端训练计划截图同步 ===
 * === v48：studentId 双通道 ===
 */
@Stable
@Entity(
    tableName = "student_plan_images",
    indices = [
        Index(value = ["studentName"], name = "idx_plan_images_student"),
        Index(value = ["studentId"], name = "idx_plan_images_student_id"),
        Index(value = ["createdAt"], name = "idx_plan_images_created")
    ]
)
data class PlanImage(
    /** 主键：UUID */
    @PrimaryKey val id: String,
    /** 学员姓名（软关联） */
    val studentName: String,
    /** 学员 ID（v48 新增，双通道软关联，旧数据为 null） */
    val studentId: String? = null,
    /** 本地图片绝对路径（filesDir/ImportedPlans/xxx.png） */
    val imagePath: String,
    /** 来源 PC 的 IP（可选，溯源用） */
    val sourceHost: String = "",
    /** 原始文件名（来自 PC 端，含学员姓名与日期） */
    val originalFilename: String = "",
    /** 备注（教练可手动补充） */
    val note: String = "",
    /** 创建时间戳（毫秒） */
    val createdAt: Long = System.currentTimeMillis()
)
