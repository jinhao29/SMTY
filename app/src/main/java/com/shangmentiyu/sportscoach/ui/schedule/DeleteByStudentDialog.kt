package com.shangmentiyu.sportscoach.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shangmentiyu.sportscoach.ui.theme.AppTextFieldShape
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appOutline
import com.shangmentiyu.sportscoach.ui.theme.appTextFieldColors

/**
 * 按学员删除排课对话框。
 *
 * 独立 @Composable 文件（模块一任务3 抽离）：
 * - 外部负责展示/隐藏（`showDeleteByStudentDialog`）
 * - 内部自持搜索状态 [search] 与确认步骤 [deletingStudent]，调用方无感知
 * - 选中学员后内部弹出二次确认，确认时回调 [onDelete] 后整体关闭
 *
 * @param students 学员姓名列表（由调用方从 vm.students 收集后传入）
 * @param onDelete 确认删除某学员全部排课回调（调用方负责 vm.deleteAllSchedulesByStudent + 关闭对话框）
 * @param onDismiss 取消/关闭回调
 */
@Composable
fun DeleteByStudentDialog(
    students: List<String>,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var deletingStudent by remember { mutableStateOf<String?>(null) }

    val filteredStudents = remember(students, search) {
        if (search.isBlank()) students
        else students.filter { it.contains(search, ignoreCase = true) }
    }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "按学员删除排课",
        confirmButton = { },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                "选择学员删除其所有排课记录（不影响课时包数据）",
                style = MaterialTheme.typography.bodySmall,
                color = appOnSurfaceVariant()
            )
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("搜索学员") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = AppTextFieldShape,
                colors = appTextFieldColors()
            )
            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items = filteredStudents, key = { it }) { name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { deletingStudent = name }
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(name, modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = appOutline(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    // 内部确认步骤：选中学员后二次确认
    deletingStudent?.let { name ->
        GlassAlertDialog(
            onDismissRequest = { deletingStudent = null },
            title = "确认删除排课",
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(name)
                        deletingStudent = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { deletingStudent = null }) { Text("取消") }
            }
        ) {
            Text("确认删除学员「${name}」的所有排课记录？\n\n此操作仅删除排课记录，不影响课时包数据。")
        }
    }
}
