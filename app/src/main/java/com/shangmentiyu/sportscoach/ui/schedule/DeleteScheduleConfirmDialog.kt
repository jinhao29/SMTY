package com.shangmentiyu.sportscoach.ui.schedule

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.shangmentiyu.sportscoach.data.model.Schedule

/**
 * 删除单条课程前的二次确认对话框。
 *
 * 独立 @Composable 文件（模块一任务3 抽离），避免内联 AlertDialog 造成重组卡顿。
 * 业务回调由调用方 [ScheduleScreen] 通过 lambda 注入。
 *
 * @param schedule 待删除的目标排课
 * @param onConfirm 确认删除回调（调用方负责执行 vm.deleteSchedule）
 * @param onDismiss 取消/关闭回调
 */
@Composable
fun DeleteScheduleConfirmDialog(
    schedule: Schedule,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除课程", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                buildString {
                    append("确认删除以下课程吗？\n\n")
                    append("学员：${schedule.studentName}\n")
                    append("时间：${schedule.startTime}")
                    if (schedule.lessonType.isNotBlank()) {
                        append(" · ${schedule.lessonType}")
                    }
                    append("\n\n此操作不可撤销。")
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
