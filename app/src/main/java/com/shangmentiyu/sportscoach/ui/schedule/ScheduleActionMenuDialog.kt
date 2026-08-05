package com.shangmentiyu.sportscoach.ui.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shangmentiyu.sportscoach.data.model.Schedule
import com.shangmentiyu.sportscoach.ui.theme.Spacing

/**
 * 长按课程卡片弹出的操作菜单（修改 / 删除该节课）。
 *
 * 独立 @Composable 文件（模块一任务3 抽离），避免内联 AlertDialog 造成重组卡顿。
 * 业务回调由调用方 [ScheduleScreen] 通过 lambda 注入。
 *
 * @param schedule 目标排课
 * @param onEdit 点击"修改"回调
 * @param onDelete 点击"删除"回调（调用方负责弹出二次确认）
 * @param onDismiss 关闭菜单
 */
@Composable
fun ScheduleActionMenuDialog(
    schedule: Schedule,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("课程操作", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "学员：${schedule.studentName}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "时间：${schedule.startTime}" +
                        if (schedule.lessonType.isNotBlank()) " · ${schedule.lessonType}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                if (schedule.location.isNotBlank()) {
                    Text(
                        "地点：${schedule.location}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "请选择操作",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onEdit) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(Spacing.xs))
                Text("修改")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}
