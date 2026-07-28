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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.ui.home.HomeViewModel.GradeFilter
import com.shangmentiyu.sportscoach.ui.home.HomeViewModel.StudentSortBy
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
    onEditStudent: (Student) -> Unit,
    onHeightPrediction: (String) -> Unit = {},
    onDietManage: (String) -> Unit = {}
) {
    val students by vm.students.collectAsState()
    // === v24 优化5：使用筛选+排序后的学员列表 ===
    val filteredStudents by vm.filteredStudents.collectAsState()
    val remainingMap by vm.remainingMap.collectAsState()
    val nextLessons by vm.nextLessons.collectAsState()
    val sortBy by vm.sortBy.collectAsState()
    val gradeFilter by vm.gradeFilter.collectAsState()
    val nameQuery by vm.nameQuery.collectAsState()
    // === v31 优化3：语音播报模式开关状态 ===
    val voiceMode by vm.voiceModeEnabled.collectAsState()

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
                Text("点击顶部筛选栏右侧的 + 添加学员",
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
                // === v24 优化5：筛选与排序条（极简下拉选择器） ===
                // v32 优化4：搜索框右侧紧贴 + 按钮，移除底部 FAB 避免遮挡卡片
                item { StudentFilterBar(
                    totalCount = students.size,
                    filteredCount = filteredStudents.size,
                    sortBy = sortBy,
                    gradeFilter = gradeFilter,
                    nameQuery = nameQuery,
                    onSortByChanged = vm::setSortBy,
                    onGradeFilterChanged = vm::setGradeFilter,
                    onNameQueryChanged = vm::setNameQuery,
                    onReset = vm::resetFilters,
                    onAddStudent = onAddStudent
                ) }
                item {
                    IosSectionHeader("学员列表（${filteredStudents.size}/${students.size}）")
                }
                // === v31 优化3：语音播报模式开关（户外签到语音播报） ===
                // - 默认关闭，教练在学员列表顶部手动开启
                // - 开启后 sign() 签到时通过 VoiceAnnouncer 播报"学员 X 已签到，剩余 Y 节课"
                // - 选中态使用主题色（珊瑚橙），未选中态浅灰，符合 iOS 风格
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.screenH, vertical = Spacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "语音模式",
                            style = MaterialTheme.typography.bodyMedium,
                            color = com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant()
                        )
                        FilterChip(
                            selected = voiceMode,
                            onClick = { vm.setVoiceMode(!voiceMode) },
                            label = { Text(if (voiceMode) "已开启" else "已关闭") }
                        )
                    }
                }
                itemsIndexed(filteredStudents, key = { _, s -> s.name }) { idx, student ->
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
                        onEditNextLesson = { editLessonTarget = nextLesson },
                        onHeightPrediction = { onHeightPrediction(student.name) },
                        onDietManage = { onDietManage(student.name) }
                    )
                }
            }
        }
        // v32 优化4：移除底部 FloatingActionButton，避免遮挡末张学员卡片
        // 添加学员入口已迁移至顶部筛选栏搜索框右侧的极简 + 按钮
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
    onEditNextLesson: () -> Unit,
    onHeightPrediction: () -> Unit = {},
    onDietManage: () -> Unit = {}
) {
    IosGroupedListCard {
        if (showTopDivider) {
            Box(
                modifier = Modifier
                    .padding(start = 76.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(com.shangmentiyu.sportscoach.ui.theme.appDividerColor())
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onGrowth)
                .padding(Spacing.md)
        ) {
            // 第一行：头像 + 姓名 + 课时余额 + 操作按钮组
            Row(
                modifier = Modifier.fillMaxWidth(),
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

                // 姓名行
                Text(student.name, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = com.shangmentiyu.sportscoach.ui.theme.appOnSurface())
                Spacer(Modifier.weight(1f))
                RemainingBadge(remaining)
            }

            // 第二行：基本信息（年龄、性别、年级、学校）分行显示避免拥挤
            // 年级显示规则：学龄前不显示年级；其他显示中文（一年级/初一/高一）
            val gradeLabel = com.shangmentiyu.sportscoach.core.Standards.gradeLabel(student.grade)
            val basicInfo = buildString {
                if (student.age > 0) append("${student.age}岁")
                if (student.gender.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(student.gender)
                }
                if (gradeLabel.isNotEmpty()) {
                    if (isNotEmpty()) append(" · ")
                    append(gradeLabel)
                }
            }
            if (basicInfo.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(basicInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant())
            }
            // 学校单独一行（如已填写），避免与基本信息挤在一行
            if (student.school.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(student.school,
                    style = MaterialTheme.typography.bodySmall,
                    color = com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant(),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }

            // 第三行：下一节课信息（日期时间 + 地点 + 修改入口）单独成行
            if (nextLesson != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("下一节：${nextLesson.date} ${nextLesson.time}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    if (!nextLesson.location.isNullOrBlank()) {
                        Spacer(Modifier.size(8.dp))
                        Icon(Icons.Outlined.LocationOn, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp))
                        Spacer(Modifier.size(2.dp))
                        Text(nextLesson.location,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false))
                    }
                    Spacer(Modifier.weight(1f))
                    // 修改下一节课时间入口（加大点击区到 28dp）
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .clickable(onClick = onEditNextLesson),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = "修改下一节课",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp))
                    }
                }
            }

            // 第四行：身高体重BMI chips
            if (student.heightCm > 0 || student.weightKg > 0f || student.bmi > 0f) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (student.heightCm > 0) {
                        MetricChip("${student.heightCm}cm",
                            com.shangmentiyu.sportscoach.ui.theme.FeatureIconPurple)
                    }
                    if (student.weightKg > 0f) {
                        MetricChip("${student.weightKg}kg",
                            com.shangmentiyu.sportscoach.ui.theme.FeatureIconPurple)
                    }
                    val bmi = if (student.bmi > 0f) student.bmi
                              else if (student.heightCm > 0 && student.weightKg > 0f)
                                  student.weightKg / ((student.heightCm / 100f) * (student.heightCm / 100f))
                              else 0f
                    if (bmi > 0f) {
                        MetricChip("BMI ${"%.1f".format(bmi)}",
                            com.shangmentiyu.sportscoach.ui.theme.FeatureIconPurple)
                    }
                }
            }

            // 第五行：操作按钮组（v32 优化4：精简为 签到 / 编辑 / 删除 三个核心按钮）
            // - 身高预测、饮食等信息类操作请点击卡片进入"成长档案"详情页查看
            // - 按钮间距收紧至 4dp，让卡片底部留出更多呼吸感
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 编辑
                TextActionButton(
                    text = "编辑",
                    onClick = onEdit,
                    type = TextActionType.NEUTRAL
                )
                Spacer(Modifier.size(4.dp))
                // 删除
                TextActionButton(
                    text = "删除",
                    onClick = onDelete,
                    type = TextActionType.DANGER
                )
                Spacer(Modifier.size(4.dp))
                // 签到按钮（突出主操作）
                TextActionButton(
                    text = "签到",
                    onClick = onSign,
                    type = TextActionType.SOLID
                )
            }
        }
    }
}

/**
 * 文字按键样式类型。
 * - [PRIMARY]：主色文字 + 浅主色背景（用于次要主操作：身高预测、饮食）
 * - [NEUTRAL]：次要文字色 + 浅灰背景（用于中性操作：编辑）
 * - [DANGER]：错误色文字 + 浅错误色背景（用于危险操作：删除）
 * - [SOLID]：主色实心背景 + 白色文字（用于主操作：签到）
 */
enum class TextActionType { PRIMARY, NEUTRAL, DANGER, SOLID }

/**
 * 文字按键：使用文字标签代替纯图标，提升可读性。
 *
 * 设计要点：
 * - 圆角胶囊形背景，文字居中
 * - 通过 [type] 区分视觉层级，保持操作意图清晰
 * - 充足的水平 padding 保证点击区可点击
 * - 文字尺寸 12sp，保证在学员卡片底部多按钮不拥挤
 *
 * @param text 按钮文字
 * @param onClick 点击回调
 * @param type 按钮视觉类型
 */
@Composable
private fun TextActionButton(
    text: String,
    onClick: () -> Unit,
    type: TextActionType
) {
    val backgroundColor = when (type) {
        TextActionType.PRIMARY -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        TextActionType.NEUTRAL -> com.shangmentiyu.sportscoach.ui.theme.appSurfaceVariant()
        TextActionType.DANGER -> MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
        TextActionType.SOLID -> MaterialTheme.colorScheme.primary
    }
    val textColor = when (type) {
        TextActionType.PRIMARY -> MaterialTheme.colorScheme.primary
        TextActionType.NEUTRAL -> com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant()
        TextActionType.DANGER -> MaterialTheme.colorScheme.error
        TextActionType.SOLID -> Color.White
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            fontWeight = FontWeight.Medium
        )
    }
}

// ==================== v24 优化5：学员列表筛选与排序条 ====================

/**
 * 学员筛选与排序条：极简下拉选择器 + 姓名搜索框 + 重置按钮 + 添加学员入口。
 *
 * 设计要点：
 * - 单卡片容器，圆角 10dp，与学员卡片风格统一
 * - 顶部一行展示"总数/筛选数" + 重置按钮
 * - 第二行：排序下拉 + 年级下拉（各占 1/2 宽度）
 * - 第三行：姓名搜索框（带搜索图标和清空按钮）+ 右侧极简 + 按钮
 *
 * 业务约定：
 * - 排序方式与年级筛选互不干扰，可叠加使用
 * - 姓名搜索支持汉字与拼音模糊匹配（不区分大小写）
 * - 重置按钮一键清空所有筛选条件
 *
 * v32 优化4：搜索框右侧紧贴 + 按钮替代原底部 FAB
 * - 极简细线框圆形，主题色 #FF6B47
 * - 点击触发 onAddStudent，与原 FAB 行为完全一致
 *
 * @param totalCount 学员总数（未筛选前）
 * @param filteredCount 当前筛选后显示的学员数
 * @param sortBy 当前排序方式
 * @param gradeFilter 当前年级筛选
 * @param nameQuery 当前姓名查询关键字
 * @param onSortByChanged 排序方式变更回调
 * @param onGradeFilterChanged 年级筛选变更回调
 * @param onNameQueryChanged 姓名查询变更回调
 * @param onReset 重置所有筛选条件回调
 * @param onAddStudent 点击 + 按钮触发添加学员（替代原 FAB）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudentFilterBar(
    totalCount: Int,
    filteredCount: Int,
    sortBy: StudentSortBy,
    gradeFilter: GradeFilter,
    nameQuery: String,
    onSortByChanged: (StudentSortBy) -> Unit,
    onGradeFilterChanged: (GradeFilter) -> Unit,
    onNameQueryChanged: (String) -> Unit,
    onReset: () -> Unit,
    onAddStudent: () -> Unit = {}
) {
    // 是否有任意筛选条件激活（用于决定重置按钮是否可见）
    val hasActiveFilter = sortBy != StudentSortBy.Default ||
            gradeFilter != GradeFilter.All ||
            nameQuery.isNotBlank()

    // 排序下拉菜单展开状态
    var sortExpanded by remember { mutableStateOf(false) }
    // 年级下拉菜单展开状态
    var gradeExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(Spacing.md)
    ) {
        // 第一行：筛选标题 + 计数 + 重置按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.FilterList,
                contentDescription = null,
                tint = com.shangmentiyu.sportscoach.ui.theme.appPrimary(),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = "筛选与排序",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = com.shangmentiyu.sportscoach.ui.theme.appOnSurface()
            )
            Spacer(Modifier.width(Spacing.sm))
            // 计数徽标（筛选后/总数）
            Text(
                text = "$filteredCount/$totalCount",
                style = MaterialTheme.typography.labelMedium,
                color = com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant()
            )
            Spacer(Modifier.weight(1f))
            // 重置按钮（仅在有激活筛选时显示）
            if (hasActiveFilter) {
                TextButton(
                    onClick = onReset,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 8.dp,
                        vertical = 0.dp
                    )
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        "重置",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.sm))

        // 第二行：排序下拉 + 年级下拉（各占 1/2 宽度）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // 排序下拉
            ExposedDropdownMenuBox(
                expanded = sortExpanded,
                onExpandedChange = { sortExpanded = !sortExpanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = sortByLabel(sortBy),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("排序") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Sort,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sortExpanded) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                )
                DropdownMenu(
                    expanded = sortExpanded,
                    onDismissRequest = { sortExpanded = false }
                ) {
                    StudentSortBy.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sortByLabel(sort)) },
                            onClick = {
                                onSortByChanged(sort)
                                sortExpanded = false
                            }
                        )
                    }
                }
            }

            // 年级下拉
            ExposedDropdownMenuBox(
                expanded = gradeExpanded,
                onExpandedChange = { gradeExpanded = !gradeExpanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = gradeFilterLabel(gradeFilter),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("年级") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.FilterList,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(gradeExpanded) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                )
                DropdownMenu(
                    expanded = gradeExpanded,
                    onDismissRequest = { gradeExpanded = false }
                ) {
                    GradeFilter.entries.forEach { grade ->
                        DropdownMenuItem(
                            text = { Text(gradeFilterLabel(grade)) },
                            onClick = {
                                onGradeFilterChanged(grade)
                                gradeExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.sm))

        // 第三行：姓名搜索框（带搜索图标 + 清空按钮）+ 右侧极简 + 按钮
        // v32 优化4：原底部 FAB 迁移至此，避免遮挡末张学员卡片
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            OutlinedTextField(
                value = nameQuery,
                onValueChange = onNameQueryChanged,
                label = { Text("搜索姓名") },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (nameQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onNameQueryChanged("") },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "清空",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search
                ),
                modifier = Modifier.weight(1f)
            )
            // 醒目实心圆形 + 按钮：主题色 #FF6B47 填充背景 + 白色图标
            // - 圆形 40dp，与搜索框等高，实心背景确保在浅色主题下清晰可见
            // - 图标 22dp，白色，视觉重量足够，用户一眼就能找到添加入口
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onAddStudent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = "添加学员",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * 排序方式的可读标签。
 *
 * @param sortBy 排序方式枚举
 * @return 中文可读标签，如 "默认排序"、"剩余课时 ↑"、"今日/明日有课优先"、"拼音 A→Z"
 */
private fun sortByLabel(sortBy: StudentSortBy): String = when (sortBy) {
    StudentSortBy.Default -> "默认排序"
    StudentSortBy.RemainingAsc -> "剩余课时 ↑"
    StudentSortBy.UpcomingFirst -> "今日/明日有课优先"
    StudentSortBy.NamePinyin -> "拼音 A→Z"
}

/**
 * 年级筛选的可读标签。
 *
 * @param gradeFilter 年级筛选枚举
 * @return 中文可读标签，如 "全部年级"、"小学"、"初中"、"高中"、"中考"
 */
private fun gradeFilterLabel(gradeFilter: GradeFilter): String = when (gradeFilter) {
    GradeFilter.All -> "全部年级"
    GradeFilter.Primary -> "小学"
    GradeFilter.Junior -> "初中"
    GradeFilter.Senior -> "高中"
    GradeFilter.ZhongKao -> "中考"
}
