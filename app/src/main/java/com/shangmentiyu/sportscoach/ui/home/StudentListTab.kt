package com.shangmentiyu.sportscoach.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appSurface
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import com.shangmentiyu.sportscoach.ui.theme.OutlinedDatePickerField
import com.shangmentiyu.sportscoach.ui.theme.OutlinedTimePickerField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.ui.home.HomeViewModel.GradeFilter
import com.shangmentiyu.sportscoach.ui.home.HomeViewModel.StudentSortBy
import com.shangmentiyu.sportscoach.ui.theme.AppTextFieldShape
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.StudentListSkeleton
import com.shangmentiyu.sportscoach.ui.theme.appTextFieldColors

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
    val students by vm.students.collectAsStateWithLifecycle()
    // === v48 终极打磨：学员列表首帧加载标记（骨架屏） ===
    val studentsLoaded by vm.studentsLoaded.collectAsStateWithLifecycle()
    // === v24 优化5：使用筛选+排序后的学员列表 ===
    val filteredStudents by vm.filteredStudents.collectAsStateWithLifecycle()
    val remainingMap by vm.remainingMap.collectAsStateWithLifecycle()
    val nextLessons by vm.nextLessons.collectAsStateWithLifecycle()
    val sortBy by vm.sortBy.collectAsStateWithLifecycle()
    val gradeFilter by vm.gradeFilter.collectAsStateWithLifecycle()
    val nameQuery by vm.nameQuery.collectAsStateWithLifecycle()

    var deleteTarget by remember { mutableStateOf<Student?>(null) }
    // 当前正在编辑的"下一节课"，null 表示未打开修改对话框
    var editLessonTarget by remember { mutableStateOf<Lesson?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // === v41：筛选栏滚动隐藏（v42 优化：移除 nestedScroll 改用 snapshotFlow）===
            // 原 nestedScroll.onPreScroll 在每帧滚动时同步调用，导致严重卡顿
            // 改用 snapshotFlow 监听 listState 滚动位置，异步计算方向，不影响滚动性能
            val listState = rememberLazyListState()
            var filterBarVisible by remember { mutableStateOf(true) }

            LaunchedEffect(listState) {
                var prevIndex = 0
                var prevOffset = 0
                snapshotFlow {
                    listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                }.collect { (index, offset) ->
                    // 通过 layoutInfo 精确判断边界状态，避免底部回弹误判
                    val layoutInfo = listState.layoutInfo
                    val visibleItems = layoutInfo.visibleItemsInfo
                    val totalItems = layoutInfo.totalItemsCount
                    // 在顶部：第一个可见 item 是 index 0 且无向上偏移
                    val atTop = visibleItems.isNotEmpty() &&
                        visibleItems.first().index == 0 &&
                        visibleItems.first().offset >= 0
                    // 在底部：最后一个可见 item 是最后一项且完全显示在视口内
                    val atBottom = visibleItems.isNotEmpty() &&
                        visibleItems.last().index == totalItems - 1 &&
                        visibleItems.last().offset + visibleItems.last().size <=
                            layoutInfo.viewportEndOffset
                    // 顶部优先：一屏显示完时同时命中 atTop 和 atBottom，此时应显示
                    if (atTop) {
                        if (!filterBarVisible) filterBarVisible = true
                        prevIndex = index
                        prevOffset = offset
                        return@collect
                    }
                    // 关键修复：在底部时强制隐藏
                    // 原因：LazyColumn 到底部会有边界回弹，offset 变小会被误判为"向上滚动"
                    // 导致筛选栏错误弹出；到底部时直接强制隐藏，跳过方向判断
                    if (atBottom) {
                        if (filterBarVisible) filterBarVisible = false
                        prevIndex = index
                        prevOffset = offset
                        return@collect
                    }
                    // 中间区域：用方向判断
                    val isScrollingDown = index > prevIndex ||
                        (index == prevIndex && offset > prevOffset + 10)
                    val isScrollingUp = index < prevIndex ||
                        (index == prevIndex && offset < prevOffset - 10)
                    if (isScrollingDown) {
                        if (filterBarVisible) filterBarVisible = false
                    } else if (isScrollingUp) {
                        if (!filterBarVisible) filterBarVisible = true
                    }
                    prevIndex = index
                    prevOffset = offset
                }
            }

            // === 筛选栏：支持手动折叠/展开 + 滚动自动隐藏 ===
            // 折叠状态由 filterBarVisible 控制（滚动时自动隐藏，手动按钮可强制展开）
            // isCollapsed 控制完整筛选栏的折叠（折叠时只显示小标题行 + 展开按钮）
            var isCollapsed by remember { mutableStateOf(true) } // 默认折叠

            // 小标题行：始终显示（除非 filterBarVisible=false 滚动隐藏）
            // 右侧折叠/展开按钮：点击切换 isCollapsed
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenH, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.FilterList,
                        contentDescription = null,
                        tint = appPrimary(),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "筛选与排序",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = appOnSurface()
                    )
                    // 筛选激活时显示小圆点提示
                    if (nameQuery.isNotBlank() || gradeFilter != null || sortBy != StudentSortBy.Default) {
                        Spacer(Modifier.size(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(appPrimary())
                        )
                    }
                }
                // 折叠/展开切换按钮
                IconButton(
                    onClick = {
                        isCollapsed = !isCollapsed
                        // 手动展开时强制显示筛选栏（覆盖滚动隐藏状态）
                        if (!isCollapsed && !filterBarVisible) filterBarVisible = true
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Outlined.ArrowDropDown,
                        contentDescription = if (isCollapsed) "展开筛选栏" else "折叠筛选栏",
                        tint = appOnSurfaceVariant(),
                        modifier = Modifier
                            .size(20.dp)
                            // 展开时图标朝上，折叠时朝下
                            .graphicsLayer(rotationZ = if (isCollapsed) 0f else 180f)
                    )
                }
            }

            // 完整筛选栏：折叠时隐藏，展开时带动画显示
            AnimatedVisibility(
                visible = filterBarVisible && !isCollapsed,
                enter = slideInVertically() + expandVertically(),
                exit = slideOutVertically() + shrinkVertically()
            ) {
                StudentFilterBar(
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
                )
            }

            if (!studentsLoaded) {
                // === v48 终极打磨：骨架屏（替代转圈/闪空态） ===
                // Room 首帧到达前显示与学员卡片结构一致的脉冲占位块
                Column(modifier = Modifier.fillMaxSize()) {
                    IosSectionHeader("学员列表")
                    StudentListSkeleton()
                }
            } else if (students.isEmpty()) {
                // === 空状态：搜索栏下方居中显示提示文字 ===
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "还没有学员",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "点击上方搜索栏右侧的 + 添加学员",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                // === 痛点二 方案A：iOS 风格分组列表 ===
                // 学员卡片在纯白 Surface 分组卡片内
                Column(modifier = Modifier.fillMaxSize()) {
                    // 标题（在分组卡片外，iOS 风格珊瑚橙大写小标题）
                    IosSectionHeader("学员列表（${filteredStudents.size}/${students.size}）")
                    // === 学员卡片分组容器（纯白 + 16dp 圆角 + 极小阴影）===
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.screenH),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                        tonalElevation = 0.dp
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 0.dp,
                                end = 0.dp,
                                top = Spacing.sm,
                                bottom = 100.dp // 为悬浮胶囊导航留出空间，避免最后一张卡片被遮挡
                            ),
                            // 学员卡片之间无间距，由 0.5dp 细灰分割线分隔（iOS Grouped List 风格）
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
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
                            // v37 修复：列表末尾追加底部安全间距
                            item {
                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(Spacing.screenV + 80.dp)
                                )
                            }
                        }
                    }
                }
            }
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

// === 性能优化4：StudentListItem / CardActionButton / CardActionType 已提取到独立文件 StudentListItem.kt ===
// 拆分目的：切断重绘传播，主文件重组时学员卡片可被 Compose 编译器跳过（参数未变则不重组）

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
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(Spacing.md)
    ) {
        // === 筛选面板内容 ===
        // 原第一行（筛选与排序标题 + 计数 + 重置）已删除：
        // 标题已由外层折叠头统一显示，此处不再重复
        // 计数已由下方"学员列表（x/x）"显示
        // 重置按钮移至搜索栏行末（与 + 按钮并排）

        // 第一行：排序下拉 + 年级下拉（各占 1/2 宽度）
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
                    shape = AppTextFieldShape,
                    colors = appTextFieldColors(),
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
                    shape = AppTextFieldShape,
                    colors = appTextFieldColors(),
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
                placeholder = { Text("搜索姓名", color = appOnSurfaceVariant()) },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        tint = appOnSurfaceVariant(),
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
                                tint = appOnSurfaceVariant(),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = appSurface(),
                    unfocusedContainerColor = appSurface(),
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = appPrimary()
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search
                ),
                modifier = Modifier
                    .weight(1f)
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(50),
                        ambientColor = Color.Black.copy(alpha = 0.04f),
                        spotColor = Color.Black.copy(alpha = 0.06f)
                    )
            )
            // 重置按钮（仅在有激活筛选时显示）：移至此处与 + 按钮并排
            if (hasActiveFilter) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onReset),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "重置筛选",
                        tint = com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant(),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
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
