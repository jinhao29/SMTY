package com.shangmentiyu.sportscoach.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * === v32：签到记录实体（排课与签到分离） ===
 *
 * 每次教练手动签到/签退各写一条记录，与 lessons 表的排课占位解耦：
 * - 排课仅创建 Lesson(status="待签到") 占位，不产生本表记录
 * - 签到：翻转占位（或新建课时）+ 写入 type="签到" 记录
 * - 签退：consumeLessonForCheckOut 事务内写入 type="签退" 记录
 *
 * (studentName, lessonId, type) 唯一索引是重复签到/签退的数据库级防线：
 * 同一学员同一课时同一操作类型只允许一条记录，冲突插入直接被拒。
 */
@Entity(
    tableName = "sign_in_records",
    indices = [
        Index(
            value = ["studentName", "lessonId", "type"],
            unique = true,
            name = "idx_sign_in_records_student_lesson_type"
        )
    ]
)
data class SignInRecord(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString().take(12),
    val studentName: String,              // 学员姓名（软关联，保留用于显示）
    val studentId: String? = null,        // 学员唯一ID（可空，旧数据兜底按姓名）
    val lessonId: String,                 // 关联课时 ID（软关联 lessons.id）
    val type: String,                     // 签到 / 签退
    val operator: String = "",            // 操作人（教练名，可空）
    val createdAt: Long = System.currentTimeMillis() // 操作时间戳
)
