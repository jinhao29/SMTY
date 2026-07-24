package com.shangmentiyu.sportscoach.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 学员实体 */
@Entity(tableName = "students")
data class Student(
    @PrimaryKey val name: String,        // 姓名（主键）
    val gender: String = "男",            // 性别
    val grade: String = "1",              // 年级编码(1-12, 13=中考)
    val school: String = "",              // 学校
    val phone: String = "",               // 电话
    val age: Int = 0,                     // 年龄（岁，0=未填）
    val heightCm: Int = 0,                // 身高（厘米，0=未填）
    val weightKg: Float = 0f,             // 体重（千克，0=未填）
    val bmi: Float = 0f,                  // BMI（自动计算，0=未计算）
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** 课堂记录实体 */
@Entity(tableName = "lessons")
data class Lesson(
    @PrimaryKey val id: String,           // UUID前8位
    val date: String,                     // YYYY-MM-DD
    val time: String,                     // HH:mm（签到时间）
    val studentName: String,              // 学员姓名(外键)
    val content: String = "[]",           // 训练内容JSON
    val scores: String = "{}",            // 成绩JSON
    val summary: String = "",             // 课后小结
    val duration: Int = 60,               // 课时时长(分钟)
    val coach: String = "",               // 教练
    val location: String = "",            // 地点
    val lessonType: String = "训练课",     // 训练/体测/技术/恢复
    val attendance: String = "准时",       // 准时/迟到/请假/旷课
    val attitude: String = "认真",         // 训练态度（可自由输入）
    val performance: Int = 7,             // 1-10
    val nextGoal: String = "",            // 下次课目标
    val coachComment: String = "",        // 教练寄语（自由编辑给家长的寄语）
    val packageId: String = "",           // 消耗的课时包ID（空=未扣减课时）
    val photoPath: String = "",           // 签到照片路径（空=未拍照）
    val signOutTime: String = "",         // 签退时间 HH:mm（空=未签退）
    val signOutPhotoPath: String = "",    // 签退照片路径（空=未拍照）
    val contentImages: String = "[]",     // 课后反馈训练内容图片路径 JSON（字符串列表，便于反馈给家长）
    val createdAt: Long = System.currentTimeMillis()
)

/** 训练内容项（用于JSON序列化） */
data class ExerciseItem(
    val name: String = "",
    val sets: Int = 3,
    val reps: String = "",
    val intensity: String = "中",          // 低/中/高/极限
    val done: Boolean = false,
    val note: String = ""
)

/** 成绩项（用于JSON序列化） */
data class ScoreItem(
    val value: String = "",
    val score: Double = 0.0,
    val grade: String = ""                 // 优秀/良好/及格/不及格
)
