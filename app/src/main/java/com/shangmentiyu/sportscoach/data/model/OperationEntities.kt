package com.shangmentiyu.sportscoach.data.model

import androidx.compose.runtime.Stable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 课程包实体：记录学员购买的课时包（用于课时消耗追踪与续费提醒）。
 *
 * 一个学员可拥有多个课程包（如：10 次卡 / 30 次卡 / 月卡），
 * 每次签到会消耗对应课程包的剩余课时。
 */
@Entity(tableName = "lesson_packages")
// v26 优化2：@Stable 让 LazyColumn 课时包列表按字段对比，避免无效重组
@Stable
data class LessonPackage(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString().take(8),
    val studentName: String,              // 学员姓名（软关联，保留用于显示）
    val studentId: String? = null,        // 学员唯一ID（软关联外键，v20 引入，旧数据 NULL）
    val name: String,                     // 套餐名称（如"10次卡"、"30次卡"）
    val totalLessons: Int,                // 总课时数
    val usedLessons: Int = 0,             // 已用课时数
    val price: Double = 0.0,              // 价格（元）
    val purchaseDate: String,             // 购买日期 YYYY-MM-DD
    val expireDate: String = "",          // 过期日期 YYYY-MM-DD（空=永不过期）
    val status: String = "活跃",          // 活跃 / 已用完 / 已过期 / 已退费
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    /** 剩余课时 */
    val remainingLessons: Int get() = (totalLessons - usedLessons).coerceAtLeast(0)

    /** 使用进度（0-1） */
    val progress: Float get() = if (totalLessons > 0) usedLessons.toFloat() / totalLessons else 0f

    /** 是否即将用完（剩余 ≤ 3） */
    val isLowBalance: Boolean get() = remainingLessons in 1..3

    /** 是否已用完 */
    val isExhausted: Boolean get() = remainingLessons == 0

    /** 是否已过期 */
    val isExpired: Boolean get() = status == "已过期" ||
        (expireDate.isNotBlank() && expireDate < todayString())

    /** 是否需要续费提醒（剩余≤3 或 30 天内过期） */
    val needsRenewal: Boolean get() = isLowBalance || isNearExpiry()

    /** 是否接近过期（30 天内） */
    fun isNearExpiry(): Boolean {
        if (expireDate.isBlank()) return false
        val days = daysToExpiry()
        return days in 0..30
    }

    /** 距过期天数（负数=已过期） */
    fun daysToExpiry(): Int {
        if (expireDate.isBlank()) return Int.MAX_VALUE
        return try {
            // 线程安全：使用 [LocalDate] + [ChronoUnit.DAYS.between]，替代 [java.text.SimpleDateFormat]
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd", java.util.Locale.getDefault())
            val expiry = java.time.LocalDate.parse(expireDate, formatter)
            val today = java.time.LocalDate.now()
            java.time.temporal.ChronoUnit.DAYS.between(today, expiry).toInt()
        } catch (_: Exception) { Int.MAX_VALUE }
    }

    private fun todayString(): String =
        // 线程安全：[LocalDate.now] + [DateTimeFormatter] 不可变，无 Calendar 状态污染
        java.time.LocalDate.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd", java.util.Locale.getDefault()))
}

/**
 * 教练实体：多教练协作管理。
 *
 * 记录教练基础信息与绩效统计（统计在业务层计算）。
 */
@Entity(tableName = "coaches")
// v26 优化2：@Stable 让 LazyColumn 教练列表按字段对比，避免无效重组
@Stable
data class Coach(
    @PrimaryKey val name: String,         // 教练姓名（主键）
    val phone: String = "",               // 联系电话
    val specialty: String = "",           // 专长（如"田径"、"球类"）
    val hireDate: String = "",            // 入职日期
    val status: String = "在职",          // 在职 / 离职
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 排课实体：固定时段的周期性课程安排。
 *
 * 与单次 lesson（已签到的课时记录）不同，
 * schedule 表示"每周一 10:00 给张三上课"这样的周期性安排。
 *
 * 课表视图（参考 Wake Up 课表）使用 color 字段为卡片着色，
 * content 字段存储本节课的训练内容 JSON（ExerciseItem 列表）。
 */
@Entity(
    tableName = "schedules",
    indices = [
        // v37 任务2：为常用查询字段添加索引
        Index(value = ["studentId"], name = "idx_schedules_student_id"),
        Index(value = ["studentName"], name = "idx_schedules_student_name"),
        Index(value = ["startDate"], name = "idx_schedules_start_date"),
        Index(value = ["dayOfWeek"], name = "idx_schedules_day_of_week"),
        Index(value = ["studentId", "dayOfWeek"], name = "idx_schedules_student_day")
    ]
)
// v26 优化2：@Stable 让 LazyColumn 排课列表按字段对比，避免无效重组
@Stable
data class Schedule(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString().take(8),
    val studentName: String,              // 学员姓名（软关联，保留用于显示）
    val studentId: String? = null,        // 学员唯一ID（软关联外键，v20 引入，旧数据 NULL）
    val coachName: String = "",           // 教练姓名
    val dayOfWeek: Int,                   // 周几（1=周一 ... 7=周日）
    val startTime: String,                // HH:mm
    val durationMinutes: Int = 60,        // 单次时长
    val location: String = "",            // 地点
    val lessonType: String = "训练课",     // 类型（自定义：训练课/体测课/技术课/恢复课...）
    val startDate: String,                // 生效日期 YYYY-MM-DD
    val endDate: String = "",             // 结束日期（空=长期有效）
    val isLongTerm: Boolean = false,      // 是否长期排课（勾选后每周自动生成对应时间的课表）
    val isActive: Boolean = true,         // 是否启用
    val note: String = "",
    val content: String = "[]",           // 训练内容 JSON（ExerciseItem 列表）
    val contentImages: String = "[]",    // 训练内容图片路径 JSON（字符串列表，用户从电脑截图导入）
    val preClassTask: String = "[]",      // 课前任务 JSON（已废弃，保留字段避免迁移）
    val color: String = "blue",           // 卡片颜色标识：blue/green/orange/purple/pink/teal
    val equipment: String = "[]",         // 上课器材 JSON（字符串列表，如 ["绳梯","小栏架"]）
    val createdAt: Long = System.currentTimeMillis()
) {
    /** 结束时间（HH:mm） */
    fun endTime(): String {
        return try {
            val (h, m) = startTime.split(":").let { it[0].toInt() to it[1].toInt() }
            val total = h * 60 + m + durationMinutes
            val eh = (total / 60) % 24
            val em = total % 60
            String.format("%02d:%02d", eh, em)
        } catch (_: Exception) { startTime }
    }
}

/**
 * 排课记忆实体：记录教练历史上用过的上课时间/地点，供排课时下拉选择。
 *
 * 以 coachName + field + value 作为联合主键，避免重复。
 * 每次排课保存时更新 updatedAt，下拉列表按 updatedAt 降序展示。
 */
@Entity(tableName = "schedule_memory", primaryKeys = ["coachName", "field", "value"])
// v26 优化2：@Stable 让 LazyColumn 记忆列表按字段对比，避免无效重组
@Stable
data class ScheduleMemory(
    val coachName: String,               // 教练姓名
    val field: String,                   // 字段名："time" 或 "location"
    val value: String,                   // 值：HH:mm 或 地点文本
    val updatedAt: Long = System.currentTimeMillis()
)
