package com.shangmentiyu.sportscoach.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangmentiyu.sportscoach.data.model.Schedule
import org.koin.androidx.compose.koinViewModel
import com.shangmentiyu.sportscoach.ui.operation.OperationViewModel
import com.shangmentiyu.sportscoach.ui.theme.IOSCard
import com.shangmentiyu.sportscoach.ui.theme.FloatingSnackbarHost
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.ScheduleListSkeleton
import com.shangmentiyu.sportscoach.ui.theme.appGroupedBackground
import com.shangmentiyu.sportscoach.ui.theme.appSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appOnWarningContainer
import com.shangmentiyu.sportscoach.ui.theme.appOutline
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appWarningContainer
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
 * @param dateLabel 日期文本（如"03-04"，用于展示）
 * @param dateStr 完整日期字符串（yyyy-MM-dd，用于 LazyRow 唯一 key，杜绝切周文字错乱）
 */
private data class DayItem(
    val dayOfWeek: Int,
    val dayName: String,
    val date: Date,
    val dateLabel: String,
    val dateStr: String
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
    val selectedScheduleIds = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    // 已选数量：用 derivedStateOf 隔离重组，避免 selectedScheduleIds 变动引发全屏重组
    val selectedCount by remember { derivedStateOf { selectedScheduleIds.count { it.value } } }

    val dateFmt = remember { java.time.format.DateTimeFormatter.ofPattern("MM-dd", Locale.getDefault()) }
    val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    // === 修复：收集 vm.toast 并通过 SnackbarHost 显示 ===
    // 历史问题：ScheduleScreen 没有 snackbarHost，导致 saveSchedule 内部的校验 toast
    //（如"无法排课：尚未拥有有效课时包"、"排课生效日期不能早于今天"等）
    // 无法显示给用户，用户点击保存按钮后看不到任何反馈，误以为"点击无反应"。
    // 修复：在 Scaffold 添加 snackbarHost，监听 vm.toast 变化并显示 Snackbar。
    val snackbarHost = remember { androidx.compose.material3.SnackbarHostState() }
    val toast by vm.toast.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(toast) {
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

    // 计算本周 7 天对应的 Date 与 dayOfWeek（1=周一 ... 7=周日）
    // 日期格式化线程安全：Date→LocalDate 转换后用 [DateTimeFormatter] 格式化
    val weekDays: List<DayItem> = remember(weekStart) {
        val cal = Calendar.getInstance().apply { time = weekStart }
        val zone = java.time.ZoneId.systemDefault()
        val fullDateFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
        (1..7).map { dayOfWeek ->
            val date = cal.time
            val localDate = date.toInstant().atZone(zone).toLocalDate()
            val item = DayItem(
                dayOfWeek = dayOfWeek,
                dayName = dayNames[dayOfWeek - 1],
                date = date,
                dateLabel = dateFmt.format(localDate),
                // 完整日期字符串作为 LazyRow key：跨周/跨年全局唯一，杜绝旧状态复用错乱
                dateStr = fullDateFmt.format(localDate)
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
            try {
                val todayCal = Calendar.getInstance()
                val todayIdx = weekDays.indexOfFirst { day ->
                    val d = Calendar.getInstance().apply { time = day.date }
                    d.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                        d.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
                }
                selectedDayOfWeek = if (todayIdx >= 0) weekDays[todayIdx].dayOfWeek else 1
                hasSelectedToday = true
            } catch (e: Exception) {
                android.util.Log.e("CalendarCrash", "初始化选中日期失败", e)
                selectedDayOfWeek = 1
                hasSelectedToday = true
            }
        }
    }

    // === Bug 修复3：当前选中日期是否为过去日期（用于 UI 置灰 + "已过去"角标）===
    // 必须放在 selectedDayOfWeek 声明之后，否则 Kotlin 编译器报 Unresolved reference
    // weekStart 是周一，selectedDayOfWeek 1=周一 ... 7=周日
    val todayLocal = remember { LocalDate.now() }
    val selectedDateLocal = remember(weekStart, selectedDayOfWeek) {
        try {
            val zone = ZoneId.systemDefault()
            val weekStartLocal = weekStart.toInstant().atZone(zone).toLocalDate()
            weekStartLocal.plusDays((selectedDayOfWeek - 1).toLong())
        } catch (e: Exception) {
            android.util.Log.e("CalendarCrash", "计算选中日期失败", e)
            LocalDate.now()
        }
    }
    val isSelectedDatePast = selectedDateLocal.isBefore(todayLocal)

    // 当前选中日期的课程列表（按开始时间升序，已暂停置底）
    // === 数据流对齐：与日历红点共享完全一致的过滤条件 ===
    // 活跃 + 非体验课(isTrial=0) + 周几命中 + 模板生效期(startDate~endDate)，
    // 与 scheduledDates 同一函数计算，杜绝"日历有红点但下方列表为空"
    val daySchedules = remember(schedules, selectedDayOfWeek, selectedDateLocal) {
        val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val selStr = selectedDateLocal.format(fmt)
        schedules
            .filter {
                it.dayOfWeek == selectedDayOfWeek &&
                    isScheduleEffective(it, selStr)
            }
            .sortedWith(compareBy({ if (it.isActive) 0 else 1 }, { it.startTime }))
    }

    // 当天是否全选（用于"全选当天"按钮文案切换）
    // 必须在 daySchedules 声明之后，否则编译报 Unresolved reference
    val isAllDaySelected by remember(daySchedules, selectedScheduleIds.size) {
        derivedStateOf {
            daySchedules.isNotEmpty() && daySchedules.all { selectedScheduleIds[it.id] == true }
        }
    }

    // === 123.txt UI 优化：日历随列表滚动 ===
    // 月历和概览卡作为 LazyColumn 的第一个 item，随列表一起滚动
    // 向下滑动时日历自然滚出视图顶部，避免遮挡学员排课详情
    // 回到顶部时日历自然滚回，完全无抖动（避免 AnimatedVisibility 反馈循环）
    // 根据 weekStart + selectedDayOfWeek 计算当前选中日期字符串
    val selectedDateStr = remember(weekStart, selectedDayOfWeek) {
        try {
            val zone = ZoneId.systemDefault()
            val weekStartLocal = weekStart.toInstant().atZone(zone).toLocalDate()
            weekStartLocal.plusDays((selectedDayOfWeek - 1).toLong())
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        } catch (e: Exception) {
            android.util.Log.e("CalendarCrash", "计算选中日期字符串失败", e)
            LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        }
    }
    // === 数据流对齐：红点改为"具体日期"集合 ===
    // 遍历当前显示月（42 格含上下邻月填充），仅对"生效期内活跃非体验课模板"命中的日期标红；
    // 与 daySchedules 共享同一过滤函数 isScheduleEffective，同一 Flow（OperationViewModel.schedules），
    // 彻底消除"日历有红点但下方列表为空"（体验课模板 / 已过生效期模板不再产生红点）
    val scheduledDates = remember(schedules, selectedDateStr) {
        val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val base = try {
            java.time.LocalDate.parse(selectedDateStr, fmt)
        } catch (e: Exception) {
            LocalDate.now()
        }
        val month = java.time.YearMonth.from(base)
        val first = month.atDay(1)
        val leading = first.dayOfWeek.value - 1
        val gridStart = first.minusDays(leading.toLong())
        (0 until 42).map { gridStart.plusDays(it.toLong()) }
            .filter { date -> schedules.any { s -> isScheduleEffective(s, date.format(fmt)) && s.dayOfWeek == date.dayOfWeek.value } }
            .map { it.format(fmt) }
            .toSet()
    }

    // === 性能优化：把日历点击 lambda 提取到 remember ===
    // 避免 LazyColumn 重组时每次创建新 lambda 实例，
    // 导致 ScheduleCalendar 因参数引用变化而被迫重组
    // 注：用 try-catch 而非 runCatching，确保 lambda 返回类型为 Unit
    val onCalendarDateSelected: (String) -> Unit = remember(vm, weekStart) {
        { dateStr: String ->
            try {
                val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                val clickedDate = java.time.LocalDate.parse(dateStr, formatter)
                val zone = ZoneId.systemDefault()
                val currentWeekStart = weekStart.toInstant().atZone(zone).toLocalDate()
                val daysDiff = java.time.temporal.ChronoUnit.DAYS.between(currentWeekStart, clickedDate)
                if (daysDiff < 0) {
                    vm.shiftWeek(-(7))
                } else if (daysDiff >= 7) {
                    vm.shiftWeek(7)
                }
                selectedDayOfWeek = clickedDate.dayOfWeek.value
            } catch (e: Exception) {
                android.util.Log.e("CalendarCrash", "切换日期失败", e)
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
                    // === 多选模式 TopAppBar：关闭按钮 + 已选数量 + 全选当天 ===
                    TopAppBar(
                        title = { Text("已选 $selectedCount 条", fontWeight = FontWeight.Bold) },
                        colors = glassTopAppBarColors(),
                        navigationIcon = {
                            IconButton(onClick = {
                                // 退出多选模式并清空选中
                                isMultiSelectMode = false
                                selectedScheduleIds.clear()
                            }) {
                                Icon(Icons.Outlined.Close, contentDescription = "退出多选")
                            }
                        },
                        actions = {
                            if (daySchedules.isNotEmpty()) {
                                TextButton(onClick = {
                                    if (isAllDaySelected) {
                                        // 取消全选当天
                                        daySchedules.forEach { selectedScheduleIds.remove(it.id) }
                                    } else {
                                        // 全选当天
                                        daySchedules.forEach { selectedScheduleIds[it.id] = true }
                                    }
                                }) {
                                    Text(
                                        if (isAllDaySelected) "取消全选" else "全选当天",
                                        color = appPrimary(),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        title = { Text("课表", fontWeight = FontWeight.Bold) },
                        colors = glassTopAppBarColors(),
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                            }
                        },
                        actions = {
                            // === 右上角功能按钮组：【图标 + 下方小字】垂直组合，替代纯图标按钮 ===
                            // 颜色规范：珊瑚橙 #FF6B47（appPrimary）/ 深灰 #6B6B6B（appOnSurfaceVariant）
                            ScheduleActionButton(
                                icon = Icons.Outlined.EventRepeat,
                                label = "排课",
                                tint = appPrimary(),
                                onClick = { showAutoScheduleDialog = true }
                            )
                            // === 按学员删除排课入口 ===
                            ScheduleActionButton(
                                icon = Icons.Outlined.PersonRemove,
                                label = "学员",
                                tint = appOnSurfaceVariant(),
                                onClick = { showDeleteByStudentDialog = true }
                            )
                            // 多选模式入口：仅在有排课时显示
                            if (schedules.isNotEmpty()) {
                                ScheduleActionButton(
                                    icon = Icons.Outlined.DeleteSweep,
                                    label = "多选",
                                    tint = appPrimary(),
                                    onClick = { isMultiSelectMode = true }
                                )
                            }
                            // 清空全部按钮（保留原有功能）
                            if (schedules.isNotEmpty()) {
                                ScheduleActionButton(
                                    icon = Icons.Outlined.CleaningServices,
                                    label = "清空",
                                    tint = appPrimary(),
                                    onClick = { showClearAllDialog = true }
                                )
                            }
                        }
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

                    // 第二行：胶囊日期选择条（始终保留在固定头部，作为周内快速切换）
                    DaySelector(
                        days = weekDays,
                        selectedDayOfWeek = selectedDayOfWeek,
                        onDaySelected = { dow ->
                            try {
                                if (dow in 1..7) {
                                    selectedDayOfWeek = dow
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("CalendarCrash", "切换日期失败", e)
                            }
                        }
                    )
                }

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
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
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
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
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
                                        // 允许长按过去日期的排课进入多选（支持补录/恢复历史排课）
                                        // 非多选模式下长按进入多选模式并选中当前
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
            .background(appWarningContainer())
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
                    tint = appOnWarningContainer(),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = "余额不足提醒",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = appOnWarningContainer()
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "关闭",
                        tint = appOnWarningContainer(),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            warnings.forEach { warning ->
                Text(
                    text = "• $warning",
                    style = MaterialTheme.typography.bodySmall,
                    color = appOnWarningContainer(),
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
            .background(appSurface())
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
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp,
            lineHeight = 34.sp,
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
        // key 使用完整日期字符串（dateStr = yyyy-MM-dd）而非 dayOfWeek：
        // 切周时新旧两天序列若用 dayOfWeek 作 key 则全部相同（1~7），LazyRow 会
        // 把 7 个项原地复用并套用旧状态，出现"数字消失/渲染空洞"；
        // 改用完整日期字符串后切周即整组重建，保证显示与数据一致。
        items(days, key = { it.dateStr }) { day ->
            val isSelected = day.dayOfWeek == selectedDayOfWeek
            val isToday = remember(day.date) {
                val d = Calendar.getInstance().apply { time = day.date }
                d.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                    d.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
            }
            val displayDayName = if (isToday) "今天" else day.dayName

            // 选中 = 珊瑚橙 #FF6B47 背景 + 白色文字；未选中 = 浅灰背景 + 深灰文字
            val selectedBg = appPrimary()
            val unselectedBg = appSurfaceVariant()
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
                    // v40 任务2b：未选中态日期数字用珊瑚橙 #FF6B47
                    color = if (isSelected) selectedText else appPrimary()
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
 * - 点击：进入编辑（过去日期禁用）；多选模式下切换选中
 * - 长按：弹出操作菜单（修改 / 删除）（过去日期禁用）；多选模式下切换选中
 *
 * === Bug 修复3：过去日期视觉区分 ===
 * - [isPastDate]=true 时，整张卡片降低透明度（0.4f）并叠加"已过去"角标
 * - 与 [schedule.isActive]=false（已暂停）的 0.5f 透明度叠加
 *
 * === 多选模式 ===
 * - [selectionMode]=true 时，卡片左侧显示圆形复选框
 * - 选中时复选框填充珊瑚橙 + 白色勾，头像区域保留
 * - 未选中时复选框为空心圆环
 *
 * @param schedule 排课数据
 * @param isPastDate 当前选中日期是否为过去日期（用于置灰 + "已过去"角标）
 * @param selectionMode 是否处于多选模式
 * @param isSelected 当前卡片是否被选中
 * @param onClick 点击回调
 * @param onLongClick 长按回调
 */

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

/**
 * 多选模式底部操作栏：悬浮白色大圆角胶囊 + 删除选中按钮。
 *
 * 设计要点（与底部悬浮导航栏风格一致）：
 * - 纯白底色大圆角胶囊（RoundedCornerShape(24.dp)）
 * - 悬浮投影（shadowElevation=8.dp），外层透明容器包裹
 * - windowInsetsPadding 避让系统导航栏
 * - 左侧显示已选数量，右侧珊瑚橙"删除选中"按钮
 * - 选中数量为 0 时按钮置灰，不可点击
 *
 * @param selectedCount 已选中的排课数量
 * @param onDelete 点击删除按钮的回调
 */
@Composable
private fun MultiSelectBottomBar(
    selectedCount: Int,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = Spacing.screenH, vertical = Spacing.sm)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = Color.Black.copy(alpha = 0.06f),
                    spotColor = Color.Black.copy(alpha = 0.10f)
                )
                .clip(RoundedCornerShape(24.dp))
                .background(appSurface())
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "已选 $selectedCount 条",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = appOnSurface()
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selectedCount > 0) MaterialTheme.colorScheme.error
                        else appOutline().copy(alpha = 0.3f)
                    )
                    .clickable(enabled = selectedCount > 0, onClick = onDelete)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteSweep,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = "删除选中",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * 课表页右上角功能按钮：【图标 + 下方小字】垂直组合。
 *
 * 颜色规范：珊瑚橙 #FF6B47（appPrimary）或 深灰 #6B6B6B（appOnSurfaceVariant），
 * 由调用方通过 tint 指定。onClick 与原纯图标按钮完全一致，不改变任何功能。
 */
@Composable
private fun ScheduleActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 9.sp,
            maxLines = 1
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7FA)
@Composable
private fun ScheduleScreenPreview() {
    ScheduleScreen(onBack = {})
}

/**
 * 排课模板在指定日期是否生效 —— 日历红点与下方列表共享的唯一过滤条件：
 * 活跃 + 非体验课(isTrial=0) + 日期落在模板生效期 startDate~endDate 内（空边界视为不限）。
 * 数据源均为 OperationViewModel.schedules（同一 Room Flow），保证红点与列表恒一致。
 */
private fun isScheduleEffective(s: Schedule, dateStr: String): Boolean {
    return s.isActive && !s.isTrial &&
        (s.startDate.isBlank() || dateStr >= s.startDate) &&
        (s.endDate.isBlank() || dateStr <= s.endDate)
}
