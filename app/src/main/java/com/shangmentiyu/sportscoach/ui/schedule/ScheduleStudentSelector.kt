package com.shangmentiyu.sportscoach.ui.schedule

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.ui.theme.AppTextField
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.StyledDropdown
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant

/**
 * 学员选择器：根据排课模式渲染不同的选择 UI。
 *
 * - 体验课模式（[isTrial]）：自由输入临时学员姓名
 * - 小班课模式（[isGroupClass]）：多选学员 Chip
 * - 普通模式：单学员下拉选择（含加载占位与空态）
 *
 * 状态与回调全部由父级（ScheduleEditDialog）提升传入，本组件只负责渲染。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleStudentSelector(
    isTrial: Boolean,
    isGroupClass: Boolean,
    studentName: String,
    onStudentNameChange: (String) -> Unit,
    students: List<Student>,
    studentsLoaded: Boolean,
    groupSelectedIds: Set<String>,
    groupSelectedNames: List<String>,
    onGroupToggle: (Student) -> Unit,
    onStudentSelected: (Student) -> Unit
) {
    if (isTrial) {
        // 体验课不关联注册学员：切换为自由输入姓名
        AppTextField(
            value = studentName,
            onValueChange = onStudentNameChange,
            label = { Text("体验课学员姓名") },
            leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    } else if (isGroupClass) {
        // 小班课：多选学员 Chip
        if (!studentsLoaded) {
            AppTextField(
                value = "",
                onValueChange = {},
                label = { Text("正在加载学员…") },
                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                singleLine = true,
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (students.isEmpty()) {
            AppTextField(
                value = "",
                onValueChange = {},
                label = { Text("暂无学员，请先到学员管理添加") },
                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                singleLine = true,
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                "已选 ${groupSelectedNames.size} 名学员",
                style = MaterialTheme.typography.bodySmall,
                color = appOnSurfaceVariant()
            )
            Spacer(Modifier.height(Spacing.sm))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                students.forEach { s ->
                    val isSelected = s.studentId != null && s.studentId in groupSelectedIds
                    FilterChip(
                        selected = isSelected,
                        onClick = { onGroupToggle(s) },
                        label = { Text(s.name) },
                        leadingIcon = {
                            if (isSelected) {
                                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            } else {
                                Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    )
                }
            }
        }
    } else if (!studentsLoaded) {
        // 学员列表首帧未到达前的加载占位
        AppTextField(
            value = "",
            onValueChange = {},
            label = { Text("正在加载学员…") },
            leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
            singleLine = true,
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        )
    } else if (students.isEmpty()) {
        // 确实无注册学员时的显式空态
        AppTextField(
            value = "",
            onValueChange = {},
            label = { Text("暂无学员，请先到学员管理添加") },
            leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
            singleLine = true,
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        Log.d("StudentPicker", "下拉框渲染学员数量: ${students.size}")
        StyledDropdown(
            selected = students.firstOrNull { it.name == studentName }
                ?.let { "${it.name} (${it.gender})" },
            options = students.map { "${it.name} (${it.gender})" },
            optionLabel = { it },
            optionIcon = { Icons.Outlined.Person },
            onSelected = { label ->
                students.firstOrNull { "${it.name} (${it.gender})" == label }
                    ?.let(onStudentSelected)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7FA)
@Composable
private fun ScheduleStudentSelectorPreview() {
    ScheduleStudentSelector(
        isTrial = false,
        isGroupClass = false,
        studentName = "",
        onStudentNameChange = {},
        students = listOf(
            Student(name = "张三", studentId = "1"),
            Student(name = "李四", studentId = "2")
        ),
        studentsLoaded = true,
        groupSelectedIds = emptySet(),
        groupSelectedNames = emptyList(),
        onGroupToggle = {},
        onStudentSelected = {}
    )
}
