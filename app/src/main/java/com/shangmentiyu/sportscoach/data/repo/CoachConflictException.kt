package com.shangmentiyu.sportscoach.data.repo

/**
 * 教练时间冲突异常（v23 引入）。
 *
 * 由 [ScheduleRepository.checkCoachConflict] 在以下场景抛出：
 * - 用户新建排课时，目标教练在目标 dayOfWeek + startTime 已有启用中的排课
 * - 用户编辑排课时，将教练/时段改到已被另一条启用中的排课占用的时段
 *
 * 调用方（ViewModel）应捕获此异常并以 toast / 弹窗形式向用户展示 [userMessage]，
 * 不应将其视为程序错误进行日志告警。
 *
 * @param coachName 冲突的教练姓名（已规范化：空串会替换为"默认教练"）
 * @param dayOfWeek 冲突的 ISO 周几（1=周一 ... 7=周日）
 * @param startTime 冲突的开始时间字符串（HH:mm，已 trim）
 * @param existingStudentName 已占用该时段的学员姓名，用于在提示中告知用户"被谁占用"
 */
class CoachConflictException(
    val coachName: String,
    val dayOfWeek: Int,
    val startTime: String,
    val existingStudentName: String
) : Exception(buildUserMessage(coachName, dayOfWeek, startTime, existingStudentName)) {

    /** 面向用户的中文提示文案，可直接用于 toast / 弹窗 */
    val userMessage: String
        get() = message ?: "教练时间冲突"

    companion object {
        /** 周几序号 → 中文名（与 UI 日期选择条文案一致） */
        private val DAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

        /** 构造面向用户的中文提示文案 */
        private fun buildUserMessage(
            coachName: String,
            dayOfWeek: Int,
            startTime: String,
            existingStudentName: String
        ): String {
            val dayName = DAY_NAMES.getOrElse(dayOfWeek - 1) { "第${dayOfWeek}天" }
            return "$dayName $startTime 教练「$coachName」已排课（学员：$existingStudentName），请调整时间或教练"
        }
    }
}
