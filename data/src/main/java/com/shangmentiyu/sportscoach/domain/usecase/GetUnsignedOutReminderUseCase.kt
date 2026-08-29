package com.shangmentiyu.sportscoach.domain.usecase

import com.shangmentiyu.sportscoach.data.db.LessonDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 忘记签退提醒状态：按学员汇总过去日期未签退的课时。
 *
 * @param students 每个未签退学员的汇总（姓名 + 课时数 + 最近未签退日期），按日期降序
 * @param totalLessonCount 未签退课时总数
 * @param shouldShow 是否应显示提醒（记录非空时为 true，由 ViewModel 叠加会话级 dismiss 标记）
 */
data class UnsignedOutReminderState(
    val students: List<StudentUnsignedOut>,
    val totalLessonCount: Int,
    val shouldShow: Boolean
)

/** 单个学员的未签退汇总 */
data class StudentUnsignedOut(
    val studentName: String,
    val lessonCount: Int,
    val latestDate: String
)

/**
 * 忘记签退提醒用例（业务层）。
 *
 * 检测规则：查询 date < today 且 status = '已签到' 且未签退的课时，
 * 按学员分组汇总数量；无未签退记录时 [UnsignedOutReminderState.shouldShow] 为 false。
 *
 * 会话级关闭：shouldShow 仅依赖记录是否非空（不持久化）。
 * ViewModel 层叠加内存级 dismiss 标记实现"每次开 App 提示一次，关闭后本次会话不再提示"。
 *
 * Flow 形式：Room 表变更自动回流，教练签退处理后首页提醒卡片即时消失。
 *
 * @param lessonDao 课时 DAO（[LessonDao.getUnsignedOutLessonsBefore]）
 */
class GetUnsignedOutReminderUseCase(
    private val lessonDao: LessonDao
) {
    operator fun invoke(today: String): Flow<UnsignedOutReminderState> =
        lessonDao.getUnsignedOutLessonsBefore(today).map { lessons ->
            val byStudent = lessons
                .groupBy { it.studentName }
                .map { (name, list) ->
                    StudentUnsignedOut(name, list.size, list.maxOf { it.date })
                }
                .sortedByDescending { it.latestDate }
            UnsignedOutReminderState(
                students = byStudent,
                totalLessonCount = lessons.size,
                shouldShow = lessons.isNotEmpty()
            )
        }
}
