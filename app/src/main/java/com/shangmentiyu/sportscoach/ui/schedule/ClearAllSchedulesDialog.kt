package com.shangmentiyu.sportscoach.ui.schedule

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

/**
 * 清空全部课表的二次确认对话框。
 *
 * 独立 @Composable 文件（模块一任务3 抽离），避免内联 AlertDialog 造成重组卡顿。
 *
 * @param scheduleCount 当前排课总数（文案中展示）
 * @param onConfirm 确认清空回调（调用方负责 vm.deleteAllSchedules）
 * @param onDismiss 取消/关闭回调
 */
@Composable
fun ClearAllSchedulesDialog(
    scheduleCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清空全部课表", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "确认清空所有排课记录吗？\n\n" +
                    "此操作将删除全部 $scheduleCount 条排课，" +
                    "但不会影响已签到的课时记录。\n此操作不可撤销。"
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("清空全部", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
