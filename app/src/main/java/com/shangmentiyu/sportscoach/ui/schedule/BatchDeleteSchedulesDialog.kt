package com.shangmentiyu.sportscoach.ui.schedule

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

/**
 * 多选模式下的批量删除二次确认对话框。
 *
 * 独立 @Composable 文件（模块一任务3 抽离），避免内联 AlertDialog 造成重组卡顿。
 *
 * @param selectedCount 已选中的排课数量
 * @param onConfirm 确认批量删除回调（调用方负责 vm.deleteSchedules + 退出多选模式）
 * @param onDismiss 取消/关闭回调
 */
@Composable
fun BatchDeleteSchedulesDialog(
    selectedCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量删除排课", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "确认删除已选中的 $selectedCount 条排课吗？\n\n" +
                    "此操作仅删除排课记录，不会影响已签到的课时记录。\n此操作不可撤销。"
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除 $selectedCount 条", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
