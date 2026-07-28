package com.shangmentiyu.sportscoach.ui.schedule

import com.shangmentiyu.sportscoach.data.model.ExerciseItem

/**
 * 编辑表单：UI 层与 ViewModel 之间传递的排课数据载体。
 * 与 [com.shangmentiyu.sportscoach.data.model.Schedule] 实体的区别：
 * content 直接用 List<ExerciseItem> 而非 JSON 字符串。
 *
 * 注意：原 ScheduleViewModel 已合并到 OperationViewModel，
 * 排课功能的唯一入口为 OperationViewModel。
 *
 * 多选周几支持：
 * - 新建模式：[daysOfWeek] 非空时，按所选的多个周几循环创建多条 Schedule，避免重复添加
 * - 编辑模式：仅编辑单条记录的 [dayOfWeek]，[daysOfWeek] 为空
 */
data class ScheduleForm(
    val studentName: String = "",
    val coachName: String = "李",    // 教练默认为"李"
    val dayOfWeek: Int = 1,          // 1=周一 ... 7=周日（编辑模式使用，或新建模式回退值）
    val daysOfWeek: Set<Int> = emptySet(),  // 新建模式多选周几（1=周一 ... 7=周日），空=使用 dayOfWeek
    val startTime: String = "09:00",
    val durationMinutes: Int = 60,
    val location: String = "",
    val lessonType: String = "训练课",
    val isLongTerm: Boolean = false, // 是否长期排课（勾选后每周自动生成对应时间的课表）
    val content: List<ExerciseItem> = emptyList(),
    val contentImages: List<String> = emptyList(),  // 训练内容图片路径（从电脑截图导入）
    val color: String = "blue",
    val note: String = "",
    val equipment: List<String> = emptyList()
)
