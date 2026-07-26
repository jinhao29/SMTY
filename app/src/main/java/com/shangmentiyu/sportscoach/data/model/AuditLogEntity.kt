package com.shangmentiyu.sportscoach.data.model

import androidx.compose.runtime.Stable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * === v26 优化1：操作日志实体（AuditLog）===
 *
 * 用途：纯本地应用，教练有时会忘记何时修改了学员信息或课时包，
 *      本表作为"后悔药"和"备忘录"，记录关键增删改动作。
 *
 * 设计要点：
 * - 只记录"成功的修改动作"，查询失败/校验失败不入库
 * - 不阻塞主流程：Repository 中通过 @Transaction 同步写入，但日志写入失败不回滚业务事务
 * - 操作类型用字符串枚举，避免数据库存数字含义不清
 * - 变更前后核心数值用 JSON 文本存储，便于扩展不同业务场景
 *
 * 字段说明：
 * - [operator]：操作人，默认"教练"（多教练场景可填入教练姓名）
 * - [action]：操作类型（如 "新增学员" / "修改课时包" / "删除排课"）
 * - [targetStudent]：受影响学员名（无学员上下文时可空，如纯教练操作）
 * - [beforeJson]：变更前的核心数值 JSON（新增时为空）
 * - [afterJson]：变更后的核心数值 JSON（删除时为空）
 * - [summary]：人类可读的一句话摘要，便于在 UI 列表快速浏览
 *
 * 索引设计：
 * - idx_audit_log_created_at：按时间倒序查询（主查询路径）
 * - idx_audit_log_target_student：按学员过滤（"查看某学员的操作历史"）
 */
@Entity(
    tableName = "audit_logs",
    indices = [
        Index(value = ["createdAt"], name = "idx_audit_log_created_at"),
        Index(value = ["targetStudent"], name = "idx_audit_log_target_student")
    ]
)
// v26 优化2：@Stable 让 LazyColumn 操作日志列表按字段对比，避免无效重组
@Stable
data class AuditLogEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString().take(12),
    val operator: String = "教练",         // 操作人（默认教练，多教练场景填入教练名）
    val action: String,                   // 操作类型（如"新增学员"/"修改课时包"/"删除排课"）
    val targetStudent: String = "",       // 受影响学员名（无学员上下文时为空）
    val beforeJson: String = "",          // 变更前核心数值 JSON（新增时为空）
    val afterJson: String = "",           // 变更后核心数值 JSON（删除时为空）
    val summary: String = "",             // 一句话摘要，便于 UI 列表展示
    val createdAt: Long = System.currentTimeMillis()
)
