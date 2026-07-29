package com.shangmentiyu.sportscoach.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shangmentiyu.sportscoach.data.model.Schedule
import com.shangmentiyu.sportscoach.ui.AppViewModelFactory
import com.shangmentiyu.sportscoach.ui.operation.OperationViewModel
import com.shangmentiyu.sportscoach.ui.theme.LightSecondary
import com.shangmentiyu.sportscoach.ui.theme.LightTertiary
import com.shangmentiyu.sportscoach.ui.theme.LightPrimary
import com.shangmentiyu.sportscoach.ui.theme.LightPrimaryContainer
import com.shangmentiyu.sportscoach.ui.theme.LightPrimary
import com.shangmentiyu.sportscoach.ui.theme.LightOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.IOSCard
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appGroupedBackground
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appOutline
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appSurface
import com.shangmentiyu.sportscoach.ui.theme.glassTopAppBarColors
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 日期条目：Keep 风格日期选择条使用。
 *
 * @param dayOfWeek ISO 周几（1=周一 ... 7=周日）
 * @param dayName 周几文本（如"周一"）
 * @param date 对应日期
 * @param dateLabel 日期文本（如"03-04"）
 */
private data class DayItem(
    val dayOfWeek: Int,
    val dayName: String,
    val date: Date,
    val dateLabel: String
)

/**
 * 课表页面（Keep 风格周视图）。
 *
 * 设计要点（参考 Keep 直播课表）：
 * - 顶部：返回 + 标题 + 清空全部
 * - 日期选择条：横向滚动的 7 天（今天 / 周一~周日 + 日期数字），选中高亮
 * - 主体：按开始时间升序排列的垂直课程卡片列表
 * - 课程卡片：左侧大字号时间（开始 + 结束），右侧白色卡片（学员、时长/地点/类型、状态）
 * - 点击课程卡片：进入编辑（复用 ScheduleEditDialog）
 * - 长按课程卡片：弹出修改/删除操作菜单
 * - 右下角 FAB：为当前选中的星期几快速新增课程
 *
 * 数据流与业务逻辑保持不变：ScheduleScreen → OperationViewModel → ScheduleRepository → Room
 *
 * @param onBack 返回回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val vm: OperationViewModel = viewModel(
        factory = AppViewModelFactory(context.applicationContext as android.app.Application)
    )

    val schedules by vm.schedules.collectAsState()
    val weekStart by vm.weekStart.collectAsState()
    val editing by vm.editingSchedule.collectAsState()
    // v24 优化2：余额不足警告（顶部 Alert Banner 显示）
    val noBalanceWarnings by vm.noBalanceWarnings.collectAsState()

    var showEditDialog by remember { mutableStateOf(false) }
    var isCreate by remember { mutableStateOf(true) }
    var prefillDay by remember { mutableStateOf<Int?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    // 长按课程卡片弹出的操作菜单状态
    var actionTargetSchedule by remember { mutableStateOf<Schedule?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var pendingDeleteSchedule by remember { mutableStateOf<Schedule?>(null) }

    val dateFmt = remember { java.time.format.DateTimeFormatter.ofPattern("MM-dd", Locale.getDefault()) }
    val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    // === 修复：收集 vm.toast 并通过 SnackbarHost 显示 ===
    // 历史问题：ScheduleScreen 没有 snackbarHost，导致 saveSchedule 内部的校验 toast
    //（如"无法排课：尚未拥有有效课时包"、"排课生效日期不能早于今天"等）
    // 无法显示给用户，用户点击保存按钮后看不到任何反馈，误以为"点击无反应"。
    // 修复：在 Scaffold 添加 snackbarHost，监听 vm.toast 变化并显示 Snackbar。
    val snackbarHost = remember { androidx.compose.material3.SnackbarHostState() }
    val toast by vm.toast.collectAsState()
    androidx.compose.runtime.LaunchedEffect(toast) {
        val msg = toast
        if (!msg.isNullOrBlank()) {
            snackbarHost.showSnackbar(msg)
            vm.clearToast()
        }
    }

    // === Bug 修复2：进入排课页时自动清理历史废弃占位排课 ===
    // 静默模式：仅在确有清理时通过 toast 反馈，避免每次进入页面都弹"无清理"提示
    LaunchedEffect(Unit) { vm.cleanupOnEnter() }

    // 计算本周 7 天对应的 Date 与 dayOfWeek（1=周一 ... 7=周日）
    // 日期格式化线程安全：Date→LocalDate 转换后用 [DateTimeFormatter] 格式化
    val weekDays: List<DayItem> = remember(weekStart) {
        val cal = Calendar.getInstance().apply { time = weekStart }
        val zone = java.time.ZoneId.systemDefault()
        (1..7).map { dayOfWeek ->
            val date = cal.time
            val localDate = date.toInstant().atZone(zone).toLocalDate()
            val item = DayItem(
                dayOfWeek = dayOfWeek,
                dayName = dayNames[dayOfWeek - 1],
                date = date,
                dateLabel = dateFmt.format(localDate)
            )
            cal.add(Calendar.DATE, 1)
            item
        }
    }

    // 默认选中今天对应的 dayOfWeek（仅在首次进入页面时计算），切换周时保持
    // 当前选中的星期几不变，这样用户能明确看到日期选择条随周切换而移动。
    var selectedDayOfWeek by remember { mutableIntStateOf(1) }
    var hasSelectedToday by remember { mutableStateOf(false) }

    LaunchedEffect(weekDays) {
        if (!hasSelectedToday && weekDays.isNotEmpty()) {
            val todayCal = Calendar.getInstance()
            val todayIdx = weekDays.indexOfFirst { day ->
                val d = Calendar.getInstance().apply { time = day.date }
                d.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                    d.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
            }
            selectedDayOfWeek = if (todayIdx >= 0) weekDays[todayIdx].dayOfWeek else 1
            hasSelectedToday = true
        }
    }

    // === Bug 修复3：当前选中日期是否为过去日期（用于 UI 置灰 + "已过去"角标）===
    // 必须放在 selectedDayOfWeek 声明之后，否则 Kotlin 编译器报 Unresolved reference
    // weekStart 是周一，selectedDayOfWeek 1=周一 ... 7=周日
    val todayLocal = remember { LocalDate.now() }
    val selectedDateLocal = remember(weekStart, selectedDayOfWeek) {
        val zone = ZoneId.systemDefault()
        val weekStartLocal = weekStart.toInstant().atZone(zone).toLocalDate()
        weekStartLocal.plusDays((selectedDayOfWeek - 1).toLong())
    }
    val isSelectedDatePast = selectedDateLocal.isBefore(todayLocal)

    // 当前选中日期的课程列表（按开始时间升序，已暂停置底）
    val daySchedules = remember(schedules, selectedDayOfWeek) {
        schedules
            .filter { it.dayOfWeek == selectedDayOfWeek }
            .sortedWith(compareBy({ if (it.isActive) 0 else 1 }, { it.startTime }))
    }

    Scaffold(
        containerColor = appGroupedBackground(),
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("课表", fontWeight = FontWeight.Bold) },
                colors = glassTopAppBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // === Bug 修复2：手动触发"清理过去无效排课"按钮 ===
                    // 即使启动时已自动清理，仍保留手动按钮供用户主动触发
                    // （如数据库被外部同步污染后可一键再次清理）
                    IconButton(onClick = { vm.cleanupPastLessonsManually() }) {
                        Icon(
                            Icons.Outlined.CleaningServices,
                            contentDescription = "清理过去无效排课",
                            tint = appPrimary()
                        )
                    }
                    if (schedules.isNotEmpty()) {
                        IconButton(onClick = { showClearAllDialog = true }) {
                            Icon(
                                Icons.Outlined.DeleteSweep,
                                contentDescription = "清空全部",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    isCreate = true
                    prefillDay = selectedDayOfWeek
                    vm.startCreate()
                    showEditDialog = true
                },
                shape = CircleShape,
                containerColor = appPrimary()
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "新增课程",
                    tint = Color.White
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenH)
                    .padding(top = Spacing.sm, bottom = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // 第一行：周次范围标题 + 周切换按钮组（上一周 / 今天 / 下一周）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = weekRangeText(weekStart),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = appOnSurface(),
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        // 上一周按钮（图标 + 文字 + 圆角浅主色背景）
                        WeekShiftButton(
                            text = "上一周",
                            icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                            onClick = { vm.shiftWeek(-7) }
                        )
                        // 今天按钮（实心主色，突出快捷回到本周）
                        TodayButton(onClick = { vm.resetToThisWeek() })
                        // 下一周按钮
                        WeekShiftButton(
                            text = "下一周",
                            icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            onClick = { vm.shiftWeek(7) }
                        )
                    }
                }

                // 第二行：胶囊日期选择条（直接平铺在浅灰底色上，无白色背景）
                DaySelector(
                    days = weekDays,
                    selectedDayOfWeek = selectedDayOfWeek,
                    onDaySelected = { selectedDayOfWeek = it }
                )

                // === v40 新增：中央概览卡片（今日总课时 / 已签退 / 剩余排课）===
                // 参照图2中间屏幕的仪表盘/概览卡片，纯白背景 + 柔和阴影 + 圆角 16dp
                // 三个核心数据并排展示，数字大加粗居中，使用珊瑚橙或深黑色
                OverviewCard(
                    totalToday = daySchedules.size,
                    signedOut = 0,  // TODO: 从 VM 获取已签退数（当前无直接数据流，暂用 0）
                    remaining = daySchedules.size  // 剩余排课 = 今日总课时（未区分签退状态时）
                )
            }

            // === v24 优化2：余额不足警告 Alert Banner（浅橙色背景提示条） ===
            if (noBalanceWarnings.isNotEmpty()) {
                NoBalanceWarningBanner(
                    warnings = noBalanceWarnings,
                    onDismiss = { vm.clearNoBalanceWarnings() }
                )
            }

            // === 课程列表（IOSCard 白色卡片包裹，与添加排课页面风格一致）===
            if (daySchedules.isEmpty()) {
                IOSCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screenH, vertical = Spacing.sm),
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
                                color = Color(0xFF1A1A1A)
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            Text(
                                "点击右下角 + 添加课程",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6B6B6B)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = Spacing.screenH,
                        vertical = Spacing.sm
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    items(daySchedules, key = { it.id }) { s ->
                        KeepScheduleCard(
                            schedule = s,
                            isPastDate = isSelectedDatePast,
                            onClick = {
                                // === Bug 修复3：过去日期的排课不可操作（避免误编辑历史记录）===
                                if (isSelectedDatePast) return@KeepScheduleCard
                                isCreate = false
                                prefillDay = null
                                vm.startEdit(s.id)
                                showEditDialog = true
                            },
                            onLongClick = {
                                // === Bug 修复3：过去日期的排课不可操作 ===
                                if (isSelectedDatePast) return@KeepScheduleCard
                                actionTargetSchedule = s
                            }
                        )
                    }
                }
            }
        }
    }

    // 长按课程卡片弹出的操作菜单（修改 / 删除该节课）
    actionTargetSchedule?.let { target ->
        AlertDialog(
            onDismissRequest = { actionTargetSchedule = null },
            title = { Text("课程操作", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "学员：${target.studentName}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "时间：${target.startTime}" +
                            if (target.lessonType.isNotBlank()) " · ${target.lessonType}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (target.location.isNotBlank()) {
                        Text(
                            "地点：${target.location}",
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
                TextButton(
                    onClick = {
                        val s = target
                        actionTargetSchedule = null
                        isCreate = false
                        prefillDay = null
                        vm.startEdit(s.id)
                        showEditDialog = true
                    }
                ) {
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
                    TextButton(
                        onClick = {
                            pendingDeleteSchedule = target
                            actionTargetSchedule = null
                            showDeleteConfirmDialog = true
                        }
                    ) {
                        Icon(
                            Icons.Outlined.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { actionTargetSchedule = null }) {
                        Text("取消")
                    }
                }
            }
        )
    }

    // 删除课程二次确认对话框
    if (showDeleteConfirmDialog && pendingDeleteSchedule != null) {
        val toDelete = pendingDeleteSchedule!!
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                pendingDeleteSchedule = null
            },
            title = { Text("删除课程", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    buildString {
                        append("确认删除以下课程吗？\n\n")
                        append("学员：${toDelete.studentName}\n")
                        append("时间：${toDelete.startTime}")
                        if (toDelete.lessonType.isNotBlank()) {
                            append(" · ${toDelete.lessonType}")
                        }
                        append("\n\n此操作不可撤销。")
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteSchedule(toDelete.id)
                        showDeleteConfirmDialog = false
                        pendingDeleteSchedule = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirmDialog = false
                    pendingDeleteSchedule = null
                }) {
                    Text("取消")
                }
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

    // 清空全部课表确认对话框
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("清空全部课表", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "确认清空所有排课记录吗？\n\n" +
                    "此操作将删除全部 ${schedules.size} 条排课，" +
                    "但不会影响已签到的课时记录。\n此操作不可撤销。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteAllSchedules()
                        showClearAllDialog = false
                    }
                ) {
                    Text("清空全部", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * v24 优化2：余额不足警告 Alert Banner。
 *
 * 浅橙色背景圆角卡片，显示本周排课时检测到的余额不足学员列表，
 * 提示教练及时为学员续费。点击关闭按钮可手动清除警告。
 *
 * 设计要点：
 * - 浅橙色背景（#FFF3E0）+ 深橙色文字（#E65100），符合 Material 警告色规范
 * - 圆角 12dp，与 IOSCard 风格一致
 * - 警告图标 + 标题 + 学员列表 + 关闭按钮
 * - 不改动现有 UI 布局，仅在周次信息卡片后新增可关闭的提示条
 *
 * @param warnings 余额不足警告文案列表（每条形如 "陈书楠 周五 余额不足"）
 * @param onDismiss 关闭回调
 */
@Composable
private fun NoBalanceWarningBanner(
    warnings: List<String>,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenH, vertical = Spacing.xs)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFFF3E0))   // 浅橙色背景
            .padding(Spacing.md)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = Color(0xFFE65100),   // 深橙色
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = "余额不足提醒",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE65100)
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "关闭",
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            warnings.forEach { warning ->
                Text(
                    text = "• $warning",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B4E00),   // 深棕色文字
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * 中央概览卡片（v40 新增）。
 *
 * 参照图2中间屏幕的仪表盘/概览卡片：
 * - 纯白背景 + 柔和阴影 + 圆角 16dp，无边框
 * - 三个核心数据并排展示：今日总课时 / 已签退 / 剩余排课
 * - 数字大号加粗居中，使用珊瑚橙 #FF6B47（突出主数据）或深黑 #1A1A1A
 * - 标签小号灰色 #6B6B6B
 *
 * @param totalToday 今日总课时
 * @param signedOut 已签退数
 * @param remaining 剩余排课数
 */
@Composable
private fun OverviewCard(
    totalToday: Int,
    signedOut: Int,
    remaining: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.08f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 今日总课时（珊瑚橙突出）
            OverviewStatItem(
                value = totalToday.toString(),
                label = "今日总课时",
                valueColor = appPrimary()
            )
            // 分隔线
            Box(
                modifier = Modifier
                    .size(width = 1.dp, height = 32.dp)
                    .background(appOutline().copy(alpha = 0.2f))
            )
            // 已签退（深黑色）
            OverviewStatItem(
                value = signedOut.toString(),
                label = "已签退",
                valueColor = appOnSurface()
            )
            // 分隔线
            Box(
                modifier = Modifier
                    .size(width = 1.dp, height = 32.dp)
                    .background(appOutline().copy(alpha = 0.2f))
            )
            // 剩余排课（深黑色）
            OverviewStatItem(
                value = remaining.toString(),
                label = "剩余排课",
                valueColor = appOnSurface()
            )
        }
    }
}

/**
 * 概览卡片单个统计项：大号数字 + 小号标签，居中竖排。
 */
@Composable
private fun OverviewStatItem(
    value: String,
    label: String,
    valueColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = appOnSurfaceVariant(),
            fontWeight = FontWeight.Normal
        )
    }
}

/**
 * 横向滚动日历视图（v40 重构版）。
 *
 * === v40 布局重构（参照图2中间屏幕的日历视图）===
 * - 横向滚动的 7 天日历，每项为全圆角胶囊
 * - 未选中：浅灰背景（#F0F0F0）+ 深灰文字
 * - 选中：珊瑚橙背景（#FF6B47）+ 白色文字
 * - 自动滚动：选中项变化时，自动滚动到 LazyRow 可见区域中间
 *
 * @param days 本周 7 天数据
 * @param selectedDayOfWeek 当前选中星期几（1=周一 ... 7=周日）
 * @param onDaySelected 选中回调
 */
@Composable
private fun DaySelector(
    days: List<DayItem>,
    selectedDayOfWeek: Int,
    onDaySelected: (Int) -> Unit
) {
    val todayCal = Calendar.getInstance()
    val capsuleShape = RoundedCornerShape(50)
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // 选中项变化时，自动滚动到 LazyRow 可见区域中间
    LaunchedEffect(selectedDayOfWeek, days) {
        if (days.isNotEmpty()) {
            val idx = days.indexOfFirst { it.dayOfWeek == selectedDayOfWeek }
            if (idx >= 0) {
                // 滚动到选中项，偏移量让它大致居中（-2 表示往前 2 项，让选中项在中间）
                val target = (idx - 2).coerceAtLeast(0)
                listState.animateScrollToItem(target)
            }
        }
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.screenH),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        items(days, key = { it.dayOfWeek }) { day ->
            val isSelected = day.dayOfWeek == selectedDayOfWeek
            val isToday = remember(day.date) {
                val d = Calendar.getInstance().apply { time = day.date }
                d.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                    d.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
            }
            val displayDayName = if (isToday) "今天" else day.dayName

            // 选中 = 珊瑚橙 #FF6B47 背景 + 白色文字；未选中 = 浅灰 #F0F0F0 背景 + 深灰文字
            val selectedBg = appPrimary()
            val unselectedBg = Color(0xFFF0F0F0)
            val selectedText = Color.White
            val unselectedText = appOnSurface().copy(alpha = 0.7f)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(capsuleShape)
                    .clickable { onDaySelected(day.dayOfWeek) }
                    .background(
                        if (isSelected) selectedBg else unselectedBg,
                        capsuleShape
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = displayDayName,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) selectedText else unselectedText
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = day.dateLabel,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) selectedText else appOnSurface().copy(alpha = 0.9f)
                )
            }
        }
    }
}

/**
 * 课程详情列表卡片（v40 重构版）。
 *
 * === v40 布局重构（参照图1左侧屏幕的任务卡片）===
 * - 左侧：圆角矩形学员头像占位图（蓝青渐变底色 + 学员姓名首字母白色）
 * - 中间：堆叠排列信息
 *   第一行 = 学员姓名（纯黑加粗）
 *   第二行 = 课程时间 · 地点 · 教练姓名（灰色 #6B6B6B 小字号，点号分隔）
 * - 右侧：珊瑚橙"训练课"胶囊标签（课时类型）
 * - 卡片圆角 16dp，纯白背景，柔和阴影，无边框
 *
 * 交互：
 * - 点击：进入编辑（过去日期禁用）
 * - 长按：弹出操作菜单（修改 / 删除）（过去日期禁用）
 *
 * === Bug 修复3：过去日期视觉区分 ===
 * - [isPastDate]=true 时，整张卡片降低透明度（0.4f）并叠加"已过去"角标
 * - 与 [schedule.isActive]=false（已暂停）的 0.5f 透明度叠加
 *
 * @param schedule 排课数据
 * @param isPastDate 当前选中日期是否为过去日期（用于置灰 + "已过去"角标）
 * @param onClick 点击回调
 * @param onLongClick 长按回调
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun KeepScheduleCard(
    schedule: Schedule,
    isPastDate: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // === Bug 修复3：过去日期叠加 0.4f 透明度，与 isActive=false 的 0.5f 叠加 ===
    val baseAlpha = if (schedule.isActive) 1f else 0.5f
    val pastAlpha = if (isPastDate) 0.4f else 1f
    val inactiveAlpha = baseAlpha * pastAlpha

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.08f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(appSurface())
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(Spacing.md)
            .graphicsLayerAlpha(inactiveAlpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // === 左侧：圆角矩形学员头像（蓝青渐变底色 + 首字母白色）===
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF4A90E2),  // 蓝色
                            Color(0xFF50C9CE)   // 青色
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = schedule.studentName.firstOrNull()?.toString() ?: "?",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.width(Spacing.md))

        // === 中间：学员名 + 详情（weight=1 撑满剩余空间）===
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 第一行：学员名（纯黑加粗）+ 过去角标
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = schedule.studentName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = appOnSurface(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isPastDate) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(appOnSurfaceVariant().copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "已过去",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = appOnSurfaceVariant()
                        )
                    }
                }
                if (!schedule.isActive) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(appOnSurfaceVariant().copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "已暂停",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = appOnSurfaceVariant()
                        )
                    }
                }
            }
            // 第二行：课程时间 · 地点 · 教练姓名（灰色 #6B6B6B 小字号，点号分隔）
            val metaText = buildString {
                append("${schedule.startTime}-${schedule.endTime()}")
                if (schedule.location.isNotBlank()) {
                    append(" · ")
                    append(schedule.location)
                }
                if (schedule.coachName.isNotBlank()) {
                    append(" · ")
                    append("教练：${schedule.coachName}")
                }
            }
            Text(
                text = metaText,
                fontSize = 12.sp,
                color = appOnSurfaceVariant(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // === 最右侧：珊瑚橙"训练课"胶囊标签（课时类型）===
        if (schedule.lessonType.isNotBlank()) {
            Spacer(Modifier.width(Spacing.sm))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(appPrimary().copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = schedule.lessonType,
                    fontSize = 11.sp,
                    color = appPrimary(),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 应用透明度到整个组件（通过 graphicsLayer）。
 * 用于过去日期/已暂停卡片的视觉降级。
 */
@Composable
private fun Modifier.graphicsLayerAlpha(alpha: Float): Modifier =
    this.then(
        Modifier.graphicsLayer { this.alpha = alpha }
    )

/**
 * 计算本周日期范围文本（yyyy年MM月dd日 ~ yyyy年MM月dd日）。
 * 线程安全：基于 [java.time.LocalDate] + [DateTimeFormatter]，替代 [SimpleDateFormat]。
 */
private fun weekRangeText(weekStart: Date): String {
    // 修复：原格式"yyyy年MM月dd日 ~ yyyy年MM月dd日"过长导致"2026年0..."被截断。
    // 改为简洁的"MM.dd - MM.dd"格式，年份信息不在此处显示，避免溢出。
    val fmt = java.time.format.DateTimeFormatter.ofPattern("MM.dd", Locale.getDefault())
    val zone = java.time.ZoneId.systemDefault()
    val start = weekStart.toInstant().atZone(zone).toLocalDate()
    val end = start.plusDays(6)
    return "${start.format(fmt)} - ${end.format(fmt)}"
}

/**
 * 将 Schedule.color 字符串映射为 Color。
 */
private fun scheduleColor(colorKey: String): Color = when (colorKey) {
    "blue" -> LightSecondary
    "green" -> LightTertiary
    "orange" -> LightPrimary
    "purple" -> LightPrimary
    "pink" -> LightPrimaryContainer
    "teal" -> LightOnSurfaceVariant
    else -> LightSecondary
}

/**
 * 周切换按钮：图标 + 文字 + 圆角浅色背景。
 *
 * 设计要点：
 * - 圆角胶囊背景（主色 10% 透明度），主色文字与图标
 * - 充足的水平 padding（12dp）保证点击区可点击
 * - 文字尺寸 13sp，配合 16dp 图标
 * - 用于"上一周"/"下一周"切换，间距由外部 Row 控制（建议 Spacing.sm）
 *
 * @param text 按钮文字（如"上一周"）
 * @param icon 方向图标
 * @param onClick 点击回调
 */
@Composable
private fun WeekShiftButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(appPrimary().copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = appPrimary(),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = appPrimary(),
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * "今天"按钮：实心主色背景 + 白色文字，突出快捷回到本周的主操作。
 *
 * 设计要点：
 * - 与 [WeekShiftButton] 的浅主色背景形成视觉对比，突出"回到今天"这一常用快捷操作
 * - 圆角胶囊形，文字居中
 * - 充足的水平 padding 保证点击区可点击
 *
 * @param onClick 点击回调，调用 [OperationViewModel.resetToThisWeek]
 */
@Composable
private fun TodayButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(appPrimary())
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "今天",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}
