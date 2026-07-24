package com.shangmentiyu.sportscoach.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import com.shangmentiyu.sportscoach.ui.theme.OutlinedDatePickerField
import com.shangmentiyu.sportscoach.ui.theme.OutlinedTimePickerField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.ui.theme.Spacing

/**
 * 学员列表 Tab：展示所有学员，每项显示课时余额、下一节课信息、身高体重BMI。
 *
 * 增强点：
 * - "下一节课"显示真正的下一节未上课（含今日及未来），数据来自 [HomeViewModel.nextLessons]
 * - 点击下一节课的"编辑"图标可修改该课时的日期与时间
 * - 点击学员跳转成长档案
 */
@Composable
fun StudentListTab(
    vm: HomeViewModel,
    onSign: (String) -> Unit,
    onAddStudent: () -> Unit,
    onGrowth: (String) -> Unit,
    onEditStudent: (Student) -> Unit
) {
    val students by vm.students.collectAsState()
    val remainingMap by vm.remainingMap.collectAsState()
    val nextLessons by vm.nextLessons.collectAsState()

    var deleteTarget by remember { mutableStateOf<Student?>(null) }
    // 当前正在编辑的"下一节课"，null 表示未打开修改对话框
    var editLessonTarget by remember { mutableStateOf<Lesson?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (students.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("还没有学员", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(8.dp))
                Text("点击右下角 + 添加学员",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = Spacing.screenH,
                    vertical = Spacing.screenV
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                item {
                    IosSectionHeader("学员列表（${students.size}）")
                }
                itemsIndexed(students, key = { _, s -> s.name }) { idx, student ->
                    val remaining = remainingMap[student.name] ?: -1
                    val nextLesson = nextLessons[student.name]
                    StudentListItem(
                        student = student,
                        remaining = remaining,
                        nextLesson = nextLesson,
                        showTopDivider = idx > 0,
                        onSign = {
                            vm.sign(student.name) { result ->
                                if (result.lessonId.isNotBlank()) {
                                    onSign(result.lessonId)
                                }
                            }
                        },
                        onGrowth = { onGrowth(student.name) },
                        onEdit = { onEditStudent(student) },
                        onDelete = { deleteTarget = student },
                        onEditNextLesson = { editLessonTarget = nextLesson }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onAddStudent,
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.lg),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        ) {
            Icon(Icons.Filled.Add, contentDescription = "添加学员")
        }
    }

    // 删除学员确认对话框
    deleteTarget?.let { student ->
        GlassAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = "删除学员",
            content = { Text("确认删除学员「${student.name}」及其所有课时记录？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteStudent(student.name)
                        deleteTarget = null
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }

    // 修改"下一节课"时间对话框
    editLessonTarget?.let { lesson ->
        EditNextLessonDialog(
            lesson = lesson,
            onDismiss = { editLessonTarget = null },
            onConfirm = { newDate, newTime ->
                vm.updateNextLessonTime(lesson.id, newDate, newTime)
                editLessonTarget = null
            }
        )
    }
}

/**
 * 修改"下一节课"日期与时间的对话框。
 *
 * 复用 [OutlinedDatePickerField] / [OutlinedTimePickerField] 保持 UI 一致性。
 * 确认后通过 [onConfirm] 回调返回新的日期和时间字符串。
 */
@Composable
private fun EditNextLessonDialog(
    lesson: Lesson,
    onDismiss: () -> Unit,
    onConfirm: (date: String, time: String) -> Unit
) {
    var dateInput by remember { mutableStateOf(lesson.date) }
    var timeInput by remember { mutableStateOf(lesson.time) }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "修改下一节课时间",
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    "学员：${lesson.studentName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                OutlinedDatePickerField(
                    value = dateInput,
                    onValueChange = { dateInput = it },
                    label = "上课日期"
                )
                OutlinedTimePickerField(
                    value = timeInput,
                    onValueChange = { timeInput = it },
                    label = "上课时间"
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(dateInput, timeInput) }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun StudentListItem(
    student: Student,
    remaining: Int,
    nextLesson: Lesson?,
    showTopDivider: Boolean,
    onSign: () -> Unit,
    onGrowth: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onEditNextLesson: () -> Unit
) {
    IosGroupedListCard {
        if (showTopDivider) {
            Box(
                modifier = Modifier
                    .padding(start = 76.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onGrowth)
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(avatarColorFor(student.name)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    student.name.firstOrNull()?.toString() ?: "?",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.size(12.dp))

            // 信息区
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(student.name, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    RemainingBadge(remaining)
                }
                Spacer(Modifier.height(4.dp))
                val subtitle = buildString {
                    if (student.age > 0) append("${student.age}岁")
                    if (student.gender.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(student.gender)
                    }
                    if (student.grade.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(student.grade)
                    }
                    if (student.school.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(student.school)
                    }
                }
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }

                // 下一节课信息（日期时间 + 地点 + 修改入口）
                if (nextLesson != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Schedule, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("下一节：${nextLesson.date} ${nextLesson.time}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                        if (!nextLesson.location.isNullOrBlank()) {
                            Spacer(Modifier.size(6.dp))
                            Icon(Icons.Filled.LocationOn, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp))
                            Spacer(Modifier.size(2.dp))
                            Text(nextLesson.location,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.weight(1f))
                        // 修改下一节课时间入口
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .clickable(onClick = onEditNextLesson),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "修改下一节课",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp))
                        }
                    }
                }

                // 身高体重BMI chips
                if (student.heightCm > 0 || student.weightKg > 0f || student.bmi > 0f) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (student.heightCm > 0) {
                            MetricChip("${student.heightCm}cm",
                                com.shangmentiyu.sportscoach.ui.theme.FeatureIconBlue)
                        }
                        if (student.weightKg > 0f) {
                            MetricChip("${student.weightKg}kg",
                                com.shangmentiyu.sportscoach.ui.theme.FeatureIconOrange)
                        }
                        val bmi = if (student.bmi > 0f) student.bmi
                                  else if (student.heightCm > 0 && student.weightKg > 0f)
                                      student.weightKg / ((student.heightCm / 100f) * (student.heightCm / 100f))
                                  else 0f
                        if (bmi > 0f) {
                            MetricChip("BMI ${"%.1f".format(bmi)}",
                                com.shangmentiyu.sportscoach.ui.theme.FeatureIconGreen)
                        }
                    }
                }
            }

            // 操作按钮
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "编辑",
                    modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp))
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onSign),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Add, contentDescription = "签到",
                    tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}
