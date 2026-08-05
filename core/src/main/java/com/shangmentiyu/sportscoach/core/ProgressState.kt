package com.shangmentiyu.sportscoach.core

/**
 * 统一进度状态（处理器层）。
 *
 * v24 优化3 引入：为 Excel 导出 / 数据备份 / 数据恢复等长耗时操作提供
 * 统一的进度反馈模型，避免各 ViewModel 各自定义 sealed class 导致 UI 重复实现。
 *
 * 设计要点：
 * - [currentStep] 人类可读的当前步骤文案（如 "正在导出学员数据：第 25/100 条..."）
 * - [progress] 0f~1f 的进度比例（current/total），未知总大小时为 -1f 表示不确定进度
 * - 配合 [com.shangmentiyu.sportscoach.ui.theme.ProgressDialog] 在 UI 层统一展示
 *
 * 使用示例：
 * ```
 * val state = ProgressState.working("正在备份…", 0f)
 * val done = ProgressState.done("备份完成")
 * ```
 */
data class ProgressState(
    val isActive: Boolean,
    val currentStep: String,
    val progress: Float
) {
    companion object {
        /** 空闲状态：无进行中的操作 */
        val Idle = ProgressState(
            isActive = false,
            currentStep = "",
            progress = 0f
        )

        /**
         * 构造进行中状态。
         *
         * @param currentStep 当前步骤文案
         * @param progress 0f~1f 进度比例；传 -1f 表示不确定进度（无总量信息）
         */
        fun working(currentStep: String, progress: Float = -1f): ProgressState =
            ProgressState(isActive = true, currentStep = currentStep, progress = progress)

        /**
         * 从 current/total 计算进度比例并构造进行中状态。
         *
         * @param current 当前已处理条目数（从 0 开始）
         * @param total 总条目数（<=0 时进度为不确定）
         * @param messagePrefix 文案前缀（如 "正在导出学员数据"）
         */
        fun workingCounted(current: Int, total: Int, messagePrefix: String): ProgressState {
            val step = if (total > 0) {
                "$messagePrefix：第 ${current + 1}/$total 条…"
            } else {
                "$messagePrefix…"
            }
            val ratio = if (total > 0) (current + 1).toFloat() / total else -1f
            return working(step, ratio)
        }

        /** 构造完成状态（isActive=false） */
        fun done(message: String): ProgressState =
            ProgressState(isActive = false, currentStep = message, progress = 1f)
    }
}
