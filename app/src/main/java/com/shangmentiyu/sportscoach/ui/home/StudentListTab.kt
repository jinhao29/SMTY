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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.ui.components.BaseDarkCard
import com.shangmentiyu.sportscoach.ui.home.HomeViewModel.GradeFilter
import com.shangmentiyu.sportscoach.ui.home.HomeViewModel.StudentSortBy
import com.shangmentiyu.sportscoach.ui.theme.CapsuleSelectedBg
import com.shangmentiyu.sportscoach.ui.theme.CapsuleUnselectedText
import com.shangmentiyu.sportscoach.ui.theme.GradientEnd
import com.shangmentiyu.sportscoach.ui.theme.GradientStart
import com.shangmentiyu.sportscoach.ui.theme.SearchFieldBg
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.StudentCardButtonBg
import com.shangmentiyu.sportscoach.ui.theme.StudentCardButtonIcon
import com.shangmentiyu.sportscoach.ui.theme.StudentCardOnDark
import com.shangmentiyu.sportscoach.ui.theme.StudentCardSubOnDark

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
                    color = Color.White)  // v38：深色背景白字
                Spacer(Modifier.height(8.dp))
                Text("点击顶部筛选栏右侧的 + 添加学员",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFA6A8AB))  // v38：浅灰副标题
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
                // === v38 视觉微调：融入深色主题，去除刺眼白底 ===
                // - 未选中：底色 #2C2C2E 深灰 + 浅灰文字 #A6A8AB（与筛选胶囊统一）
                // - 选中：底色 #6C5CE7 紫色 + 白色文字（主题色，不再用白底）
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
                            color = Color.White  // v38：深色背景白字
                        )
                        FilterChip(
                            selected = voiceMode,
                            onClick = { vm.setVoiceMode(!voiceMode) },
                            label = { Text(if (voiceMode) "已开启" else "已关闭") },
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                containerColor = Color(0xFF2C2C2E),
                                labelColor = Color(0xFFA6A8AB),
                                selectedContainerColor = Color(0xFF6C5CE7),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
                itemsIndexed(filteredStudents, key = { _, s -> s.name }) { idx, student ->
                    val remaining = remainingMap[student.name] ?: -1
                    val nextLesson = nextLessons[student.name]
                    // v37 任务4：触觉反馈，提升按钮点击物理感
                    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                    StudentListItem(
                        student = student,
                        remaining = remaining,
                        nextLesson = nextLesson,
                        showTopDivider = idx > 0,
                        onSign = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            vm.sign(student.name) { result ->
                                if (result.lessonId.isNotBlank()) {
                                    onSign(result.lessonId)
                                }
                            }
                        },
                        onGrowth = { onGrowth(student.name) },
                        onEdit = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onEditStudent(student)
                        },
                        onDelete = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            deleteTarget = student
                        },
                        onEditNextLesson = { editLessonTarget = nextLesson },
                        onHeightPrediction = { onHeightPrediction(student.name) },
                        onDietManage = { onDietManage(student.name) }
                    )
                }
                // v37 修复：列表末尾追加底部安全间距
                // 防止最后一张学员卡片被底部导航栏遮挡，确保末项完整可见
                item {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Spacing.screenV + 80.dp)
                    )
                }
            }
        }
        // v32 优化4：移除底部 FloatingActionButton，避免遮挡末张学员卡片
        // 添加学员入口已迁移至顶部筛选栏搜索框右侧的紫色圆形 + 按钮
    }

    // 删除学员确认对话框
    deleteTarget?.let { student ->
        GlassAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = "删除学员",
            content = { Text("确认删除学员「${student.name}」及其所有课时记录？此操作不可撤销。", color = Color.White) },  // v38：深色背景白字
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteStudent(student.name)
                        deleteTarget = null
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消", color = Color(0xFFA6A8AB)) }  // v38：浅灰副标题
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
                    color = Color.White  // v38：深色背景白字
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
            ) { Text("保存", color = Color.White) }  // v38：紫色按钮白字
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = Color(0xFFA6A8AB)) }  // v38：浅灰副标题
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
    // v35 视觉重构：深色卡 #1C1C1E + 渐变头像 + 青色按钮，移除橙色分割线
    // v37 性能优化：缓存 Brush 与计算结果，避免滑动时每次重组都重新创建
    val avatarBrush = remember(GradientStart, GradientEnd) {
        Brush.linearGradient(colors = listOf(GradientStart, GradientEnd))
    }
    // 缓存基本信息字符串，仅当 student 变化时重新计算
    val basicInfo = remember(student.age, student.gender, student.grade) {
        val gradeLabel = com.shangmentiyu.sportscoach.core.Standards.gradeLabel(student.grade)
        buildString {
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
    }
    // 缓存 BMI 计算，避免每次重组都执行浮点运算
    val bmi = remember(student.heightCm, student.weightKg, student.bmi) {
        if (student.bmi > 0f) student.bmi
        else if (student.heightCm > 0 && student.weightKg > 0f)
            student.weightKg / ((student.heightCm / 100f) * (student.heightCm / 100f))
        else 0f
    }
    BaseDarkCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onGrowth)
        ) {
            // 第一行：渐变头像 + 姓名 + 课时余额
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // v35：头像背景改为青色渐变 #00D2FF → #3A7BD5
                // v37：使用缓存的 avatarBrush，避免每次重组都创建 Brush 对象
                // v38 视觉微调：首字母字号从 titleMedium 升级为 titleLarge，
                // 让每个学员头像中央的首字母（如"陈"、"秦"）更醒目，提升列表辨识度。
                // 保留蓝色渐变底色不变，仅文字白色加粗放大。
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(brush = avatarBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        student.name.firstOrNull()?.toString() ?: "?",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.size(12.dp))

                // v35：主标题白色 #FFFFFF
                Text(student.name, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = StudentCardOnDark)
                Spacer(Modifier.weight(1f))
                RemainingBadge(remaining)
            }

            // 第二行：基本信息（年龄、性别、年级）—— v35：副标题灰 #8E8E93
            // v37：basicInfo 已在顶部用 remember 缓存，此处直接引用
            if (basicInfo.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(basicInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = StudentCardSubOnDark)
            }
            // 学校单独一行
            if (student.school.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(student.school,
                    style = MaterialTheme.typography.bodySmall,
                    color = StudentCardSubOnDark,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }

            // 第三行：下一节课信息 —— v35：青色 #00D2FF 强调
            if (nextLesson != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null,
                        tint = StudentCardButtonIcon,
                        modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("下一节：${nextLesson.date} ${nextLesson.time}",
                        style = MaterialTheme.typography.labelMedium,
                        color = StudentCardButtonIcon)
                    if (!nextLesson.location.isNullOrBlank()) {
                        Spacer(Modifier.size(8.dp))
                        Icon(Icons.Outlined.LocationOn, contentDescription = null,
                            tint = StudentCardButtonIcon,
                            modifier = Modifier.size(14.dp))
                        Spacer(Modifier.size(2.dp))
                        Text(nextLesson.location,
                            style = MaterialTheme.typography.labelMedium,
                            color = StudentCardButtonIcon,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false))
                    }
                    Spacer(Modifier.weight(1f))
                    // v35：修改下一节课入口（暗色背景 + 青色图标）
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(StudentCardButtonBg)
                            .clickable(onClick = onEditNextLesson),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = "修改下一节课",
                            tint = StudentCardButtonIcon,
                            modifier = Modifier.size(16.dp))
                    }
                }
            }

            // 第四行：身高体重BMI chips —— v35：青色文字
            if (student.heightCm > 0 || student.weightKg > 0f || student.bmi > 0f) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (student.heightCm > 0) {
                        MetricChip("${student.heightCm}cm", StudentCardButtonIcon)
                    }
                    if (student.weightKg > 0f) {
                        MetricChip("${student.weightKg}kg", StudentCardButtonIcon)
                    }
                    // v37：使用顶部缓存的 bmi，避免每次重组都执行浮点运算
                    if (bmi > 0f) {
                        MetricChip("BMI ${"%.1f".format(bmi)}", StudentCardButtonIcon)
                    }
                }
            }

            // 第五行：操作按钮组 —— v35：暗色背景 + 青色文字
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextActionButton(
                    text = "编辑",
                    onClick = onEdit,
                    type = TextActionType.NEUTRAL
                )
                Spacer(Modifier.size(4.dp))
                TextActionButton(
                    text = "删除",
                    onClick = onDelete,
                    type = TextActionType.DANGER
                )
                Spacer(Modifier.size(4.dp))
                // 签到按钮：青色渐变背景 + 白色文字
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
 * - [PRIMARY]：浅灰文字 + 深灰背景（用于次要主操作：身高预测、饮食）
 * - [NEUTRAL]：浅灰文字 + 深灰背景（用于中性操作：编辑）
 * - [DANGER]：浅红文字 + 深灰背景（用于危险操作：删除，仅文字区分语义）
 * - [SOLID]：浅灰文字 + 深灰背景（用于主操作：签到，不再用亮蓝渐变）
 *
 * === v38 视觉微调：所有类型统一深灰底 #2C2C2E ===
 * 原设计中 SOLID 类型使用青色渐变实心背景，在深色卡片上过于吵闹。
 * 现统一为深灰底，仅 DANGER 文字保留浅红 #FF453A，其余均为浅灰 #A6A8AB。
 * 去除突兀的亮蓝色实心按钮，让卡片底部按钮组视觉一致、克制。
 */
enum class TextActionType { PRIMARY, NEUTRAL, DANGER, SOLID }

/**
 * v35 视觉重构 → v38 视觉微调：文字按键（深色卡专用）。
 *
 * 设计要点（v38 定稿）：
 * - 圆角胶囊形背景，文字居中
 * - 所有类型统一深灰背景 #2C2C2E
 * - NEUTRAL/PRIMARY/SOLID 文字浅灰 #A6A8AB
 * - DANGER 文字浅红 #FF453A（语义区分，底色不变）
 * - 无 border，无渐变，仅依赖文字颜色区分操作语义
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
    // v38：所有类型统一深灰底，仅文字颜色区分
    val backgroundColor = StudentCardButtonBg  // #2C2C2E
    val textColor = when (type) {
        TextActionType.PRIMARY -> Color(0xFFA6A8AB)
        TextActionType.NEUTRAL -> Color(0xFFA6A8AB)
        TextActionType.DANGER -> Color(0xFFFF453A)  // 保留语义浅红
        TextActionType.SOLID -> Color(0xFFA6A8AB)   // 签到不再用渐变，改浅灰
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
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

    // v36 全局 UI 统一：筛选栏容器去除深色 surface 背景，融入全局 #F5F7FA 底色
    // 内部下拉框与搜索框统一使用 #F0F2F5 浅灰胶囊背景 + 无边框
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.sm)
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
                color = Color.White  // v38：深色背景白字
            )
            Spacer(Modifier.width(Spacing.sm))
            // 计数徽标（筛选后/总数）
            Text(
                text = "$filteredCount/$totalCount",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFA6A8AB)  // v38：浅灰统计数据
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
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFA6A8AB)  // v38：浅灰副标题
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
            // v38 视觉微调：去掉外部 label，胶囊内只显示当前选中值 + 图标 + 箭头
            // 文字与图标统一浅灰 #A6A8AB，底色 #2C2C2E（SearchFieldBg）
            ExposedDropdownMenuBox(
                expanded = sortExpanded,
                onExpandedChange = { sortExpanded = !sortExpanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = sortByLabel(sortBy),
                    onValueChange = {},
                    readOnly = true,
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Sort,
                            contentDescription = null,
                            tint = Color(0xFFA6A8AB),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        Icon(
                            Icons.Outlined.ArrowDropDown,
                            contentDescription = null,
                            tint = Color(0xFFA6A8AB),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SearchFieldBg,
                        unfocusedContainerColor = SearchFieldBg,
                        disabledContainerColor = SearchFieldBg,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        focusedTextColor = Color(0xFFA6A8AB),
                        unfocusedTextColor = Color(0xFFA6A8AB),
                        disabledTextColor = Color(0xFFA6A8AB),
                        focusedLeadingIconColor = Color(0xFFA6A8AB),
                        unfocusedLeadingIconColor = Color(0xFFA6A8AB),
                        focusedTrailingIconColor = Color(0xFFA6A8AB),
                        unfocusedTrailingIconColor = Color(0xFFA6A8AB),
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
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
            // v38 视觉微调：同排序下拉，去掉 label，统一浅灰文字 + #2C2C2E 底色
            ExposedDropdownMenuBox(
                expanded = gradeExpanded,
                onExpandedChange = { gradeExpanded = !gradeExpanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = gradeFilterLabel(gradeFilter),
                    onValueChange = {},
                    readOnly = true,
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.FilterList,
                            contentDescription = null,
                            tint = Color(0xFFA6A8AB),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        Icon(
                            Icons.Outlined.ArrowDropDown,
                            contentDescription = null,
                            tint = Color(0xFFA6A8AB),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SearchFieldBg,
                        unfocusedContainerColor = SearchFieldBg,
                        disabledContainerColor = SearchFieldBg,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        focusedTextColor = Color(0xFFA6A8AB),
                        unfocusedTextColor = Color(0xFFA6A8AB),
                        disabledTextColor = Color(0xFFA6A8AB),
                        focusedLeadingIconColor = Color(0xFFA6A8AB),
                        unfocusedLeadingIconColor = Color(0xFFA6A8AB),
                        focusedTrailingIconColor = Color(0xFFA6A8AB),
                        unfocusedTrailingIconColor = Color(0xFFA6A8AB),
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
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
                label = { Text("搜索姓名", color = Color(0xFFA6A8AB)) },  // v38：浅灰副标题
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        tint = Color(0xFFA6A8AB),  // v38：浅灰图标
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
                                tint = Color(0xFFA6A8AB),  // v38：浅灰图标
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SearchFieldBg,
                    unfocusedContainerColor = SearchFieldBg,
                    disabledContainerColor = SearchFieldBg,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,  // v38：深色背景白字
                    unfocusedTextColor = Color.White,  // v38：深色背景白字
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = Color(0xFFA6A8AB),  // v38：浅灰副标题
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search
                ),
                modifier = Modifier.weight(1f)
            )
            // v36 全局 UI 统一：+ 按钮改为纯紫色圆形图标
            // - 圆形 36dp，纯紫底 #6C5CE7，白色 + 图标
            // - 与深色卡片风格对齐，去除半透明底色
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
                    modifier = Modifier.size(20.dp)
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
