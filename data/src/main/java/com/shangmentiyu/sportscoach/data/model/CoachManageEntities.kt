package com.shangmentiyu.sportscoach.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 教练管理模块实体（v33）：可上课时段 / 薪资结算 / 提现申请。
 *
 * 说明：
 * - 主讲/助教分配直接复用 schedules.coachName / assistantCoach 软关联字段，
 *   不建独立关联表，与既有数据流（排课 → 生成课时记录）保持一致。
 * - 教练可上课时段为周期性（每周重复），无 valid_from/valid_to。
 */
@Entity(
    tableName = "coach_schedules",
    indices = [Index(value = ["coachName"], name = "idx_coach_schedules_coach")]
)
data class CoachSchedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val coachName: String,               // 所属教练姓名（软关联 coaches.name）
    val dayOfWeek: Int,                  // 周几（1=周一 ... 7=周日）
    val startTime: String,               // 开始时间 HH:mm
    val endTime: String,                 // 结束时间 HH:mm
    val note: String = ""
)

@Entity(
    tableName = "coach_payouts",
    indices = [Index(
        value = ["coachName", "periodStart", "periodEnd"],
        name = "idx_coach_payouts_period",
        unique = true
    )]
)
data class CoachPayout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val coachName: String,               // 教练姓名
    val periodStart: String,             // 结算期起 yyyy-MM-dd
    val periodEnd: String,               // 结算期止 yyyy-MM-dd
    val lessonCount: Int,                // 实际消课数（已签退）
    val baseAmount: Double,              // 底薪部分
    val lessonFee: Double,               // 课时费部分
    val commission: Double,              // 分成部分（分红/团队提成）
    val totalAmount: Double,             // 合计应发
    val status: String = STATUS_PENDING, // 待发放 / 已发放
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_PENDING = "待发放"
        const val STATUS_PAID = "已发放"
    }
}

@Entity(tableName = "coach_payout_requests")
data class CoachPayoutRequest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val coachName: String,               // 申请教练
    val amount: Double,                  // 提现金额（元）
    val status: String = STATUS_PENDING, // 待审核 / 已通过 / 已驳回
    val appliedAt: Long,                 // 申请时间戳
    val processedAt: Long = 0,           // 审核时间戳（0=未审核）
    val note: String = ""                // 备注（可填收款方式）
) {
    companion object {
        const val STATUS_PENDING = "待审核"
        const val STATUS_APPROVED = "已通过"
        const val STATUS_REJECTED = "已驳回"
    }
}

/**
 * 教练-学员绑定实体（v34 教练绑定学员）。
 *
 * 软关联：coachName ↔ coaches.name，studentName ↔ students.name。
 * 绑定用于教练排课时的学员候选列表，不影响排课/课时数据本身。
 */
@Entity(
    tableName = "coach_student_bindings",
    primaryKeys = ["coachName", "studentName"],
    indices = [
        Index(value = ["coachName"], name = "idx_coach_student_coach"),
        Index(value = ["studentName"], name = "idx_coach_student_student")
    ]
)
data class CoachStudentBinding(
    val coachName: String,               // 教练姓名（软关联 coaches.name）
    val studentName: String,             // 学员姓名（软关联 students.name）
    val studentId: String? = null,       // 学员唯一 ID（软关联，旧数据可空）
    val boundAt: Long = System.currentTimeMillis()
)
