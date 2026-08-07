package com.shangmentiyu.sportscoach.data.model

import androidx.compose.runtime.Stable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 学员实体
 *
 * === v26 优化2：@Stable 注解 ===
 *
 * Compose 编译器在 StateFlow<List<Student>> 变化时会触发 LazyColumn 重组。
 * 默认情况下 Compose 无法识别 data class 的"是否相等"，每次 emit 都会全量重组所有 item。
 *
 * 加上 @Stable 后，Compose 编译器会按字段对比实例，
 * 仅当字段值变化时才触发重组，列表滑动更丝滑、省电。
 *
 * 适用场景：所有在 Compose UI 中作为 State 暴露的数据类。
 */
@Stable
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
    // === 身高遗传潜力与后天预测字段（v17 引入，默认 0 兼容老数据） ===
    val fatherHeight: Double = 0.0,       // 父亲身高（厘米）
    val motherHeight: Double = 0.0,       // 母亲身高（厘米）
    val avgSleepHours: Double = 0.0,      // 日常平均睡眠小时
    val nutritionScore: Int = 0,          // 日常营养均衡评分（1-5，0=未填）
    val sportsMinsPerWeek: Int = 0,       // 每周运动总时长（分钟）
    // === v20 引入：软删除标志 + 软关联外键 ===
    // isActive=false 表示已逻辑删除（学员行保留，不出现在日常列表，但历史课时/排课数据保留用于报表）
    // studentId 为可选软关联字段：用于更精准的内部查询和改名级联查找（旧数据初始为 NULL，由业务层后续回填）
    // === v46 加固：DB 层默认值 = 1（双保险，即使 SQL 直插绕过 Kotlin 默认值也是活跃）===
    @ColumnInfo(defaultValue = "1")
    val isActive: Boolean = true,         // 是否活跃（软删除标志，false=已删除）
    val studentId: String? = null,       // 学员唯一ID（软关联外键，NULL=旧数据未生成）
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 课堂记录实体
 *
 * 索引设计（v16 引入，覆盖所有高频查询路径）：
 * - idx_lessons_student_date_time：getByStudent（学员详情历史列表）
 * - idx_lessons_date：getByDate / countByDate（首页今日课时、统计）
 * - idx_lessons_date_time_asc：getFrom（学员列表"下一节课"查询）
 * - idx_lessons_student_date_time_unique：countByStudentDateTime（长期排课查重，唯一索引）
 * - idx_lessons_student_date_pkg：countUnconsumedFrom（长期排课课时包余额计算）
 */
@Entity(
    tableName = "lessons",
    indices = [
        Index(value = ["studentName", "date", "time"], name = "idx_lessons_student_date_time"),
        Index(value = ["date"], name = "idx_lessons_date"),
        Index(value = ["date", "time"], name = "idx_lessons_date_time_asc"),
        Index(value = ["studentName", "date", "time"], name = "idx_lessons_student_date_time_unique", unique = true),
        Index(value = ["studentName", "date", "packageId"], name = "idx_lessons_student_date_pkg"),
        // v37 任务2：为 studentId 字段添加索引，加速按学员ID查询课时
        Index(value = ["studentId"], name = "idx_lessons_student_id"),
        Index(value = ["studentId", "date"], name = "idx_lessons_student_id_date")
    ]
)
// v26 优化2：@Stable 让 LazyColumn 课时列表按字段对比，避免无效重组
@Stable
data class Lesson(
    @PrimaryKey val id: String,           // UUID前8位
    val date: String,                     // YYYY-MM-DD
    val time: String,                     // HH:mm（签到时间）
    val studentName: String,              // 学员姓名（软关联，保留用于显示）
    val studentId: String? = null,        // 学员唯一ID（软关联外键，v20 引入，旧数据 NULL）
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
    // === v27：签到/签退状态字段 ===
    // 取值："已签到"（默认，签到时写入但未扣减课时包）/ "已签退"（签退时扣减课时包并标记）
    // 配合"签退后消耗课时"重构：签到时仅创建 Lesson(status="已签到", packageId="")
    // 签退时事务内：consumeLesson 扣减课时包 + 更新 Lesson(status="已签退", packageId, signOutTime)
    val status: String = "已签到",         // 课时状态：已签到 / 已签退
    // === v49 体验课：未注册学员临时体验课占位/签到记录，签退不扣减课时包 ===
    val isTrial: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/** 训练内容项（用于JSON序列化） */
// v26 优化2：@Stable 让训练内容 LazyColumn 列表项按字段对比，避免无效重组
@Stable
data class ExerciseItem(
    val name: String = "",
    val sets: Int = 3,
    val reps: String = "",
    val intensity: String = "中",          // 低/中/高/极限
    val done: Boolean = false,
    val note: String = ""
)

/** 成绩项（用于JSON序列化） */
// v26 优化2：@Stable 让成绩 LazyColumn 列表项按字段对比，避免无效重组
@Stable
data class ScoreItem(
    val value: String = "",
    val score: Double = 0.0,
    val grade: String = ""                 // 优秀/良好/及格/不及格
)
