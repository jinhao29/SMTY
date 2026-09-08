package com.shangmentiyu.sportscoach.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangmentiyu.sportscoach.data.model.Schedule
import com.shangmentiyu.sportscoach.ui.operation.OperationViewModel
import com.shangmentiyu.sportscoach.ui.theme.FloatingSnackbarHost
import com.shangmentiyu.sportscoach.ui.theme.IOSCard
import com.shangmentiyu.sportscoach.ui.theme.ScheduleListSkeleton
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appGroupedBackground
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate

/**
 * 课表页面（Keep 风格周视图）。
 *
 * 设计要点（参考 Keep 直播课表）：
 * - 顶部：返回 + 标题 + 清空全部（v53 拆分至 [ScheduleTopBar]）
 * - 日期选择条：横向滚动的 7 天（今天 / 周一~周日 + 日期数字），选中高亮（[ScheduleWeekHeader]）
 * - 主体：按开始时间升序排列的垂直课程卡片列表
 * - 课程卡片：左侧大字号时间（开始 + 结束），右侧白色卡片（学员、时长/地点/类型、状态）
 * - 点击课程卡片：进入编辑（复用 ScheduleEditDialog）
 * - 长按课程卡片：进入多选模式（批量删除）
 *
 * v53 拆分说明（原 1223 行 God 类）：
 * - 纯日期/过滤逻辑 → [ScheduleWeekLogic]
 * - 周头部（周切换 + 日期选择条）→ [ScheduleWeekComponents]
 * - 概览卡 + 余额警告横幅 → [ScheduleOverviewComponents]
 * - 顶栏 + 多选操作栏 → [ScheduleTopBars]
 * 本文件仅保留：状态编排、数据派生接线、Scaffold 骨架与对话框触发。
 *
 * 数据流与业务逻辑保持不变：ScheduleScreen → OperationViewModel → ScheduleRepository → Room
 *
 * @param onBack 返回回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onBack: () -> Unit,
    /** false = 作为底部 Tab 使用（俱乐部模式），隐藏返回箭头 */
    showBack: Boolean = true
) {
    val vm: OperationViewModel = koinViewModel()

    val schedules by vm.schedules.collectAsStateWithLifecycle()
    val weekStart by vm.weekStart.collectAsStateWithLifecycle()
    val editing by vm.editingSchedule.collectAsStateWithLifecycle()
    // v24 优化2：余额不足警告（顶部 Alert Banner 显示）
    val noBalanceWarnings by vm.noBalanceWarnings.collectAsStateWithLifecycle()
    // === v48 终极打磨：排课列表首帧加载标记（骨架屏） ===
    val schedulesLoaded by vm.schedulesLoaded.collectAsStateWithLifecycle()

    var showEditDialog by remember { mutableStateOf(false) }
    var isCreate by remember { mutableStateOf(false) }
    var prefillDay by remember { mutableStateOf<Int?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    // === 按学员删除排课对话框状态 ===
    var showDeleteByStudentDialog by remember { mutableStateOf(false) }
    // === 按课时包自动排课对话框状态 ===
    var showAutoScheduleDialog by remember { mutableStateOf(false) }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var pendingDeleteSchedule by remember { mutableStateOf<Schedule?>(null) }

    // === 多选模式状态（批量删除使用）===
    // isMultiSelectMode：是否处于多选模式，进入后卡片点击切换选中态而非进入编辑
    // selectedScheduleIds：已选中的排课 ID 集合，跨日期切换时保持，退出多选模式时清空
    // showBatchDeleteDialog：批量删除二次确认弹窗
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedScheduleIds = remember { mutableStateMapOf<String, Boolean>() }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    // 已选数量：用 derivedStateOf 隔离重组，避免 selectedScheduleIds 变动引发全屏重组
    val selectedCount by remember { derivedStateOf { selectedScheduleIds.count { it.value } } }

    // === 修复：收集 vm.toast 并通过 SnackbarHost 显示 ===
    // 历史问题：ScheduleScreen 没有 snackbarHost，导致 saveSchedule 内部的校验 toast
    //（如"无法排课：尚未拥有有效课时包"、"排课生效日期不能早于今天"等）
    // 无法显示给用户，用户点击保存按钮后看不到任何反馈，误以为"点击无反应"。
    // 修复：在 Scaffold 添加 snackbarHost，监听 vm.toast 变化并显示 Snackbar。
    val snackbarHost = remember { SnackbarHostState() }
    val toast by vm.toast.collectAsStateWithLifecycle()
    LaunchedEffect(toast) {
        val msg = toast
        if (!msg.isNullOrBlank()) {
            snackbarHost.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            vm.clearToast()
        }
    }

    // === v32：移除进入排课页自动清理历史占位排课 ===
    // 清理无效课表改为仅在设置页手动触发，避免每次进入周课表都触发清理，
    // 且旧清理规则会误删未来/未签退课时。生成本周长期排课的补调保留。
    LaunchedEffect(Unit) { vm.ensureLongTermLessonsForWeek() }

    // 本周 7 天条目（1=周一 ... 7=周日）
    val weekDays: List<DayItem> = remember(weekStart) { buildWeekDays(weekStart) }

    // 默认选中今天对应的 dayOfWeek（仅在首次进入页面时计算），切换周时保持
    // 当前选中的星期几不变，这样用户能明确看到日期选择条随周切换而移动。
    var selectedDayOfWeek by remember { mutableIntStateOf(1) }
    var hasSelectedToday by remember { mutableStateOf(false) }

    LaunchedEffect(weekDays) {
        if (!hasSelectedToday && weekDays.isNotEmpty()) {
            try {
                selectedDayOfWeek = todayDayOfWeekInWeek(weekDays)
                hasSelectedToday = true
            } catch (e: Exception) {
                android.util.Log.e("CalendarCrash", "初始化选中日期失败", e)
                selectedDayOfWeek = 1
                hasSelectedToday = true
            }
        }
    }

    // === Bug 修复3：当前选中日期是否为过去日期（用于 UI 置灰 + "已过去"角标）===
    val todayLocal = remember { LocalDate.now() }
    val selectedDateLocal = remember(weekStart, selectedDayOfWeek) {
        selectedDateFromWeek(weekStart, selectedDayOfWeek)
    }
    val isSelectedDatePast = selectedDateLocal.isBefore(todayLocal)

    // 当前选中日期的课程列表（按开始时间升序，已暂停置底）
    val daySchedules = remember(schedules, selectedDayOfWeek, selectedDateLocal) {
        daySchedulesFor(schedules, selectedDayOfWeek, selectedDateLocal)
    }

    // 当天是否全选（用于"全选当天"按钮文案切换）
    val isAllDaySelected by remember(daySchedules, selectedScheduleIds.size) {
        derivedStateOf {
            daySchedules.isNotEmpty() && daySchedules.all { selectedScheduleIds[it.id] == true }
        }
    }

    // === 123.txt UI 优化：日历随列表滚动 ===
    // 月历和概览卡作为 LazyColumn 的第一个 item，随列表一起滚动
    val selectedDateStr = remember(weekStart, selectedDayOfWeek) {
        selectedDateStrFromWeek(weekStart, selectedDayOfWeek)
    }
    val scheduledDates = remember(schedules, selectedDateStr) {
        scheduledDatesFor(schedules, selectedDateStr)
    }

    // === 性能优化：把日历点击 lambda 提取到 remember ===
    // 避免 LazyColumn 重组时每次创建新 lambda 实例，
    // 导致 ScheduleCalendar 因参数引用变化而被迫重组
    val onCalendarDateSelected: (String) -> Unit = remember(vm, weekStart) {
        { dateStr: String ->
            weekShiftForCalendarClick(weekStart, dateStr)?.let { (shift, dow) ->
                if (shift != 0) {
                    vm.shiftWeek(shift)
                }
                selectedDayOfWeek = dow
            }
        }
    }

    // 最外层 Box：FAB 覆盖于 Scaffold 之上，置于外层 z 层（高于 SnackbarHost）
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            containerColor = appGroupedBackground(),
            snackbarHost = { FloatingSnackbarHost(snackbarHost) },
            topBar = {
                if (isMultiSelectMode) {
                    MultiSelectTopBar(
                        selectedCount = selectedCount,
                        hasDaySchedules = daySchedules.isNotEmpty(),
                        isAllDaySelected = isAllDaySelected,
                        onClose = {
                            // 退出多选模式并清空选中
                            isMultiSelectMode = false
                            selectedScheduleIds.clear()
                        },
                        onToggleAllDay = {
                            if (isAllDaySelected) {
                                // 取消全选当天
                                daySchedules.forEach { selectedScheduleIds.remove(it.id) }
                            } else {
                                // 全选当天
                                daySchedules.forEach { selectedScheduleIds[it.id] = true }
                            }
                        }
                    )
                } else {
                    ScheduleTopBar(
                        onBack = onBack,
                        showBack = showBack,
                        hasSchedules = schedules.isNotEmpty(),
                        onAutoSchedule = { showAutoScheduleDialog = true },
                        onDeleteByStudent = { showDeleteByStudentDialog = true },
                        onEnterMultiSelect = { isMultiSelectMode = true },
                        onClearAll = { showClearAllDialog = true }
                    )
                }
            },
            bottomBar = {
                // === 多选模式底部操作栏：悬浮白色胶囊 + 删除选中按钮 ===
                if (isMultiSelectMode) {
                    MultiSelectBottomBar(
                        selectedCount = selectedCount,
                        onDelete = { showBatchDeleteDialog = true }
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(appGroupedBackground())
                    .padding(padding)
            ) {
                // === v34 布局优化3：顶部瘦身 ===
                // 取消白色 IOSCard 包裹，直接平铺在浅灰底色上，缩小整体垂直高度
                ScheduleWeekHeader(
                    weekStart = weekStart,
                    weekDays = weekDays,
                    selectedDayOfWeek = selectedDayOfWeek,
                    onDaySelected = { dow ->
                        if (dow in 1..7) {
                            selectedDayOfWeek = dow
                        }
                    },
                    onShiftWeek = { vm.shiftWeek(it) },
                    onToday = { vm.resetToThisWeek() }
                )

                // === v24 优化2：余额不足警告 Alert Banner（浅橙色背景提示条） ===
                if (noBalanceWarnings.isNotEmpty()) {
                    NoBalanceWarningBanner(
                        warnings = noBalanceWarnings,
                        onDismiss = { vm.clearNoBalanceWarnings() }
                    )
                }

                // === v48 终极打磨：首帧骨架屏（替代转圈/闪空态） ===
                if (!schedulesLoaded) {
                    ScheduleListSkeleton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.screenH, vertical = Spacing.md)
                    )
                } else if (daySchedules.isEmpty()) {
                    // 空状态：日历 + 概览卡 + 空提示
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = Spacing.screenH,
                            vertical = Spacing.sm
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        item(key = "calendar") {
                            ScheduleCalendar(
                                selectedDate = selectedDateStr,
                                scheduledDates = scheduledDates,
                                onDateSelected = onCalendarDateSelected
                            )
                        }
                        item(key = "overview") {
                            OverviewCard(
                                totalToday = daySchedules.size,
                                signedOut = 0,
                                remaining = daySchedules.size
                            )
                        }
                        item(key = "empty_state") {
                            IOSCard(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = Spacing.xl
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "今日无排课",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = appOnSurface()
                                        )
                                        Spacer(Modifier.height(Spacing.sm))
                                        Text(
                                            "点击右下角 + 添加课程",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = appOnSurfaceVariant()
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = Spacing.screenH,
                            vertical = Spacing.sm
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        // === 日历作为第一个 item，随列表滚动 ===
                        item(key = "calendar") {
                            ScheduleCalendar(
                                selectedDate = selectedDateStr,
                                scheduledDates = scheduledDates,
                                onDateSelected = onCalendarDateSelected
                            )
                        }
                        // === 概览卡作为第二个 item ===
                        item(key = "overview") {
                            OverviewCard(
                                totalToday = daySchedules.size,
                                signedOut = 0,
                                remaining = daySchedules.size
                            )
                        }
                        items(daySchedules, key = { it.id }) { s ->
                            val isSelected = selectedScheduleIds[s.id] == true
                            KeepScheduleCard(
                                schedule = s,
                                isPastDate = isSelectedDatePast,
                                selectionMode = isMultiSelectMode,
                                isSelected = isSelected,
                                onClick = {
                                    if (isMultiSelectMode) {
                                        // 多选模式：点击切换选中态（过去日期也允许选中删除）
                                        if (isSelected) {
                                            selectedScheduleIds.remove(s.id)
                                        } else {
                                            selectedScheduleIds[s.id] = true
                                        }
                                    } else {
                                        // 允许编辑过去日期的排课（支持补录/恢复历史排课）
                                        isCreate = false
                                        prefillDay = null
                                        vm.startEdit(s.id)
                                        showEditDialog = true
                                    }
                                },
                                onLongClick = {
                                    if (isMultiSelectMode) {
                                        // 多选模式下长按也切换选中（与点击一致）
                                        if (isSelected) {
                                            selectedScheduleIds.remove(s.id)
                                        } else {
                                            selectedScheduleIds[s.id] = true
                                        }
                                    } else {
                                        // 非多选模式下长按进入多选模式并选中当前
                                        // （允许长按过去日期的排课进入多选，支持补录/恢复历史排课）
                                        isMultiSelectMode = true
                                        selectedScheduleIds[s.id] = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // 删除课程二次确认对话框
    if (showDeleteConfirmDialog && pendingDeleteSchedule != null) {
        val toDelete = pendingDeleteSchedule!!
        DeleteScheduleConfirmDialog(
            schedule = toDelete,
            onConfirm = {
                vm.deleteSchedule(toDelete.id)
                showDeleteConfirmDialog = false
                pendingDeleteSchedule = null
            },
            onDismiss = {
                showDeleteConfirmDialog = false
                pendingDeleteSchedule = null
            }
        )
    }

    // 编辑/新建对话框
    val readyToShow = if (isCreate) showEditDialog else (showEditDialog && editing != null)
    if (readyToShow) {
        ScheduleEditDialog(
            vm = vm,
            isCreate = isCreate,
            prefillDayOfWeek = prefillDay,
            onDismiss = {
                vm.cancelEdit()
                showEditDialog = false
            },
            onSaved = {
                vm.cancelEdit()
                showEditDialog = false
            }
        )
    }

    // === 批量删除二次确认对话框（多选模式）===
    if (showBatchDeleteDialog) {
        BatchDeleteSchedulesDialog(
            selectedCount = selectedCount,
            onConfirm = {
                val ids = selectedScheduleIds.filter { it.value }.keys.toList()
                vm.deleteSchedules(ids)
                showBatchDeleteDialog = false
                isMultiSelectMode = false
                selectedScheduleIds.clear()
            },
            onDismiss = { showBatchDeleteDialog = false }
        )
    }

    // === 按课时包自动排课对话框 ===
    if (showAutoScheduleDialog) {
        AutoScheduleFromPackageDialog(
            vm = vm,
            preselectedPackageId = "",
            onDismiss = { showAutoScheduleDialog = false }
        )
    }

    // 清空全部课表确认对话框
    if (showClearAllDialog) {
        ClearAllSchedulesDialog(
            scheduleCount = schedules.size,
            onConfirm = {
                vm.deleteAllSchedules()
                showClearAllDialog = false
            },
            onDismiss = { showClearAllDialog = false }
        )
    }

    // === 按学员删除排课对话框（内部含搜索 + 确认两步）===
    if (showDeleteByStudentDialog) {
        val students by vm.students.collectAsStateWithLifecycle()
        DeleteByStudentDialog(
            students = students.map { it.name },
            onDelete = { name ->
                vm.deleteAllSchedulesByStudent(name)
                showDeleteByStudentDialog = false
            },
            onDismiss = { showDeleteByStudentDialog = false }
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7FA)
@Composable
private fun ScheduleScreenPreview() {
    ScheduleScreen(onBack = {})
}
