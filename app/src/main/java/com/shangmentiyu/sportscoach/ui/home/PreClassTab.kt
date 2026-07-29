package com.shangmentiyu.sportscoach.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.model.PlanImage
import com.shangmentiyu.sportscoach.data.model.Schedule
import com.shangmentiyu.sportscoach.ui.AppViewModelFactory
import com.shangmentiyu.sportscoach.ui.dailyplan.DailyPlanViewModel
import com.shangmentiyu.sportscoach.ui.operation.OperationViewModel
import com.shangmentiyu.sportscoach.ui.schedule.ScheduleEditDialog
import com.shangmentiyu.sportscoach.ui.theme.PrimaryButton
import com.shangmentiyu.sportscoach.ui.theme.SecondaryButton
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appBackground
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appPrimary

/**
 * 课前准备 Tab：展示选定日期的排课时间线。
 *
 * 每条排课卡片显示：
 * - 上课时间（开始-结束）
 * - 学员姓名 + 课时类型
 * - 上课地点
 * - 上课内容任务（来自排课 content 字段，解析为动作列表展示）
 * - 训练内容图片（来自排课 contentImages 字段，教练从电脑截图导入的训练计划）
 * - 上课器材（来自排课 equipment 字段）
 * - 快速签到入口（跳转 LESSON_CHECKIN）
 *
 * 新增：顶部"添加排课"按钮，复用 ScheduleEditDialog 快速为当前周几创建排课。
 */
@Composable
fun PreClassTab(
    vm: HomeViewModel,
    onLessonCheckIn: () -> Unit,
    onSchedule: () -> Unit = {}
) {
    val context = LocalContext.current
    val dailyVm: DailyPlanViewModel = viewModel(
        factory = AppViewModelFactory(context.applicationContext as android.app.Application)
    )
    val opVm: OperationViewModel = viewModel(
        factory = AppViewModelFactory(context.applicationContext as android.app.Application)
    )

    val selectedDate by dailyVm.selectedDate.collectAsState()
    val schedules by dailyVm.schedules.collectAsState()
    val lessons by dailyVm.lessons.collectAsState()
    val dayOfWeek by dailyVm.dayOfWeek.collectAsState()

    var editingScheduleId by remember { mutableStateOf<String?>(null) }
    // 收集编辑中的排课数据：仅当数据加载完成（editing != null）时才渲染编辑对话框，
    // 避免异步加载未完成时 Dialog 一直卡在"加载中"。
    val editingSchedule by opVm.editingSchedule.collectAsState()

    // ============================================================
    // === v5 新增：精彩瞬间照片上传到 PC 端 ===
    // ============================================================
    // 缓存当前要上传的学员姓名（点击按钮时写入，选择图片后读取）
    var pendingMomentStudentName by remember { mutableStateOf<String?>(null) }
    // 缓存最近一次选中的 URI（在 launcher 回调中写入，由 LaunchedEffect 消费）
    var pendingMomentUri by remember { mutableStateOf<android.net.Uri?>(null) }
    // 启动系统图库选择器（PickVisualMedia），选择完成后写入 pendingMomentUri
    val momentLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val studentName = pendingMomentStudentName
        pendingMomentStudentName = null  // 用完立即清理，避免下次误用
        if (uri == null || studentName.isNullOrBlank()) return@rememberLauncherForActivityResult
        // 写入状态，由下方的 LaunchedEffect 在 Composable 上下文中消费
        pendingMomentUri = uri
        // 同步把 studentName 写回，LaunchedEffect 用 key 校验
        pendingMomentStudentName = studentName
    }
    // 在 Composable 上下文中执行上传（避免在 launcher 回调里调用 @Composable）
    androidx.compose.runtime.LaunchedEffect(pendingMomentUri, pendingMomentStudentName) {
        val uri = pendingMomentUri ?: return@LaunchedEffect
        val studentName = pendingMomentStudentName ?: return@LaunchedEffect
        // 消费后立即清理，避免重复触发
        pendingMomentUri = null
        pendingMomentStudentName = null
        val msg = vm.uploadMoment(uri, studentName)
        vm.showToast(msg)
    }

    // === v26 优化3：智能引导推荐 ===
    // 收集排课记忆（时间/地点）和未来一周排课，用于"今日无排课"空状态的引导推荐
    val timeMemories by opVm.timeMemories.collectAsState()
    val locationMemories by opVm.locationMemories.collectAsState()
    val allSchedules by opVm.schedules.collectAsState()
    // "添加典型排课"对话框：预填教练最近使用过的时间和地点
    var showTypicalSchedule by remember { mutableStateOf(false) }
    // "查看近期排课"对话框：展示未来一周的排课概览
    var showRecentSchedules by remember { mutableStateOf(false) }

    // === v28 优化1：历史归档列表状态 ===
    // 点击"查看全部历史归档"按钮后弹出全屏 Dialog，懒加载 archived_lessons 表
    var showArchivedList by remember { mutableStateOf(false) }
    var archivedLessons by remember { mutableStateOf<List<com.shangmentiyu.sportscoach.data.model.ArchivedLesson>>(emptyList()) }
    var archivedLoading by remember { mutableStateOf(false) }

    val dayNames = mapOf(
        1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四",
        5 to "周五", 6 to "周六", 7 to "周日"
    )

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenH, vertical = Spacing.screenV),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // 日期切换器
        IosCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { dailyVm.previousDay() }) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "前一天")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        selectedDate,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = appPrimary()
                    )
                    Text(
                        dayNames[dayOfWeek] ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = appOnSurfaceVariant()
                    )
                }
                IconButton(onClick = { dailyVm.nextDay() }) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "后一天")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                TextButton(
                    onClick = { dailyVm.goToday() },
                    modifier = Modifier.weight(1f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text("回到今天", color = appPrimary())
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                TextButton(
                    onClick = onSchedule,
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(16.dp), tint = appPrimary())
                    Spacer(Modifier.width(4.dp))
                    Text("查看周课表", color = appPrimary())
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }

        // 今日统计
        IosCard {
            Column(modifier = Modifier.padding(Spacing.md)) {
                Text("今日概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(label = "排课", value = "${schedules.size}")
                    StatItem(label = "已签到", value = "${lessons.size}")
                    StatItem(label = "待签到", value = "${(schedules.size - lessons.size).coerceAtLeast(0)}")
                }
            }
        }

        IosSectionHeader("课前准备清单")

        // 排课时间线
        if (schedules.isEmpty()) {
            // === v26 优化3：今日无排课时的智能引导推荐 ===
            // 不再只显示一句话，而是给出两个推荐按钮，引导教练快速行动
            IosCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = appOnSurfaceVariant(),
                        modifier = Modifier.size(32.dp)
                    )
                    Text("今日无排课",
                        color = appOnSurface(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text("空闲时间也能高效利用，试试下面的快捷操作",
                        color = appOnSurfaceVariant(),
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Spacing.xs))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        // 按钮1：查看未来一周排课概览（次要操作）
                        SecondaryButton(
                            text = "查看排课",
                            onClick = { showRecentSchedules = true },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.Schedule
                        )
                        // 按钮2：从近期记忆一键填充典型排课表单（主要操作）
                        PrimaryButton(
                            text = "添加排课",
                            onClick = { showTypicalSchedule = true },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.Add,
                            enabled = timeMemories.isNotEmpty() || locationMemories.isNotEmpty()
                        )
                    }
                    // 当没有排课记忆时的友好提示
                    if (timeMemories.isEmpty() && locationMemories.isEmpty()) {
                        Text(
                            "「添加排课」需要先排过一次课，下次就能一键填充时间和地点了",
                            color = appOnSurfaceVariant(),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        } else {
            schedules.sortedBy { it.startTime }.forEach { schedule ->
                val signedLesson = dailyVm.findSignedLesson(schedule, selectedDate)
                PreClassScheduleCard(
                    schedule = schedule,
                    lessonStatus = signedLesson?.status,
                    contentItems = opVm.parseContent(schedule.content),
                    contentImages = opVm.parseImages(schedule.contentImages),
                    equipmentList = opVm.parseEquipment(schedule.equipment),
                    onSign = { onLessonCheckIn() },
                    onEdit = {
                        opVm.startEdit(schedule.id)
                        editingScheduleId = schedule.id
                    },
                    // === v5 修复：点击"上传精彩瞬间"按钮时缓存当前学员名并启动图库选择器 ===
                    // 原代码漏传 onUploadMoment 参数，按钮点击 = no-op（默认空实现 {}）
                    // 这里补上：先缓存 schedule.studentName 到 pendingMomentStudentName，
                    // 再调用 momentLauncher.launch 启动系统图库
                    // 图库回调中读取 pendingMomentStudentName，触发 LaunchedEffect 执行上传
                    onUploadMoment = {
                        pendingMomentStudentName = schedule.studentName
                        momentLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    }
                )
            }
        }

        // === v28 优化1：查看全部历史归档入口 ===
        // 默认所有列表只查 lessons 表（热数据），仅当教练主动点击时加载归档表（冷数据）
        IosCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "查看全部历史归档",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "一年以上的旧课时已自动归档，点击查看完整记录",
                        style = MaterialTheme.typography.labelSmall,
                        color = appOnSurfaceVariant()
                    )
                }
                IconButton(onClick = { showArchivedList = true }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = "查看归档",
                        tint = appPrimary()
                    )
                }
            }
        }
    }

    // 编辑排课对话框：点击课前准备清单中的课程卡片编辑按钮触发
    // 仅当编辑数据加载完成（editingSchedule != null）时才渲染，与 OperationScreen/ScheduleScreen
    // 保持一致，避免异步加载未完成时 Dialog 一直卡在"加载中"。
    if (editingScheduleId != null && editingSchedule != null) {
        ScheduleEditDialog(
            vm = opVm,
            isCreate = false,
            onDismiss = {
                opVm.cancelEdit()
                editingScheduleId = null
            },
            onSaved = {
                opVm.cancelEdit()
                editingScheduleId = null
            }
        )
    }

    // === v26 优化3：典型排课对话框 ===
    // 从 ScheduleMemory 调出教练最近使用过的"上课时间"和"上课地点"，预填到表单
    // 教练只需选择学员即可一键完成排课，减少重复输入的疲劳感
    if (showTypicalSchedule) {
        val prefillTime = timeMemories.firstOrNull()?.value
        val prefillLoc = locationMemories.firstOrNull()?.value
        ScheduleEditDialog(
            vm = opVm,
            isCreate = true,
            prefillDayOfWeek = dayOfWeek,
            prefillStartTime = prefillTime,
            prefillLocation = prefillLoc,
            onDismiss = { showTypicalSchedule = false },
            onSaved = { showTypicalSchedule = false }
        )
    }

    // === v26 优化3：近期排课概览对话框 ===
    if (showRecentSchedules) {
        RecentSchedulesDialog(
            schedules = allSchedules,
            onDismiss = { showRecentSchedules = false }
        )
    }

    // === v28 优化1：历史归档列表对话框 ===
    // 点击"查看全部历史归档"按钮后懒加载 archived_lessons 表数据
    if (showArchivedList) {
        // 进入对话框时异步加载归档数据，加载中显示 Loading 指示
        androidx.compose.runtime.LaunchedEffect(Unit) {
            if (archivedLessons.isEmpty() && !archivedLoading) {
                archivedLoading = true
                vm.loadAllArchivedLessons { list ->
                    archivedLessons = list
                    archivedLoading = false
                }
            }
        }
        ArchivedLessonsDialog(
            lessons = archivedLessons,
            loading = archivedLoading,
            onDismiss = {
                showArchivedList = false
                // 关闭后清空缓存，下次打开重新加载，避免长期持有大量归档数据
                archivedLessons = emptyList()
            }
        )
    }
}

/**
 * 近期排课概览对话框（v26 优化3）：展示未来一周的排课概览。
 *
 * 用于"今日无排课"空状态下的智能引导，让教练快速看到未来一周的排课安排，
 * 无需跳转到周课表页面即可了解整体排课情况。
 *
 * @param schedules 全部活跃排课列表
 * @param onDismiss 关闭回调
 */
@Composable
private fun RecentSchedulesDialog(
    schedules: List<Schedule>,
    onDismiss: () -> Unit
) {
    // 按周几分组并按开始时间排序
    val dayNames = mapOf(
        1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四",
        5 to "周五", 6 to "周六", 7 to "周日"
    )
    val grouped = schedules.sortedBy { it.dayOfWeek }.groupBy { it.dayOfWeek }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(appBackground())
        ) {
            // 顶部栏
            Row(
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "未来一周排课概览",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭")
                }
            }

            if (schedules.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无任何排课记录", color = appOnSurfaceVariant())
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    items(grouped.entries.toList()) { (dayOfWeek, daySchedules) ->
                        IosCard {
                            Column(modifier = Modifier.padding(Spacing.md)) {
                                Text(
                                    dayNames[dayOfWeek] ?: "周$dayOfWeek",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = appPrimary()
                                )
                                Spacer(Modifier.height(Spacing.xs))
                                daySchedules.sortedBy { it.startTime }.forEach { s ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            s.startTime,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = appPrimary(),
                                            modifier = Modifier.width(60.dp)
                                        )
                                        Text(
                                            s.studentName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (s.location.isNotBlank()) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Outlined.LocationOn,
                                                    contentDescription = null,
                                                    tint = appOnSurfaceVariant(),
                                                    modifier = Modifier.size(12.dp))
                                                Text(
                                                    s.location,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = appOnSurfaceVariant()
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreClassScheduleCard(
    schedule: Schedule,
    lessonStatus: String?,
    contentItems: List<com.shangmentiyu.sportscoach.data.model.ExerciseItem>,
    contentImages: List<String>,
    equipmentList: List<String>,
    onSign: () -> Unit,
    onEdit: () -> Unit,
    /**
     * === v5 新增：上传精彩瞬间回调 ===
     * 点击"上传精彩瞬间"按钮时触发，由调用方启动图库选择器。
     * 卡片本身不感知 Uri，只负责把学员姓名传给调用方。
     */
    onUploadMoment: () -> Unit = {}
) {
    // === v25 新增：订阅该学员的电脑端训练计划图片 ===
    // 通过 produceState 异步订阅 PlanImageRepository.getByStudent 返回的 Flow
    // 学员姓名变更或新截图同步进来时，画廊自动响应更新
    val context = LocalContext.current
    val planImages by produceState(
        initialValue = emptyList<PlanImage>(),
        schedule.studentName
    ) {
        // 使用 AppDatabase 单例获取 planImageDao，避免修改 HomeViewModel 注入链
        val dao = AppDatabase.getDatabase(context.applicationContext as android.app.Application)
            .planImageDao()
        dao.getByStudent(schedule.studentName).collect { value = it }
    }

    // 三态：null=未签到 / "已签到"=上课中 / "已签退"=已完成
    val isSignedIn = lessonStatus != null
    val isSignedOut = lessonStatus == "已签退"

    IosCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = appPrimary(),
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    "${schedule.startTime} - ${schedule.endTime()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = appPrimary()
                )
                Spacer(Modifier.weight(1f))
                // 状态徽章：已签退=绿色已完成，已签到=橙色上课中
                if (isSignedOut) {
                    StatusBadge(
                        text = "已完成",
                        icon = Icons.Outlined.CheckCircle,
                        color = com.shangmentiyu.sportscoach.ui.theme.ScoreExcellent
                    )
                } else if (isSignedIn) {
                    StatusBadge(
                        text = "上课中",
                        icon = Icons.Outlined.PlayArrow,
                        color = appPrimary()
                    )
                }
                // 编辑按钮：点击后打开 ScheduleEditDialog 修改课程内容
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "编辑课程",
                        tint = appPrimary(),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                schedule.studentName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            // 上课地点
            if (schedule.location.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocationOn, contentDescription = null,
                        tint = appOnSurfaceVariant(),
                        modifier = Modifier.size(14.dp).padding(end = 4.dp))
                    Text("地点：${schedule.location}",
                        style = MaterialTheme.typography.bodySmall,
                        color = appOnSurfaceVariant())
                }
            }

            // 课时类型 + 教练
            val info = buildString {
                append(schedule.lessonType)
                if (schedule.coachName.isNotBlank()) append(" · ${schedule.coachName}")
            }
            Spacer(Modifier.height(4.dp))
            Text(info, style = MaterialTheme.typography.bodySmall,
                color = appOnSurfaceVariant())

            // 上课内容任务（解析后的动作列表）
            if (contentItems.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                Text("上课内容任务", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold, color = appPrimary())
                contentItems.forEach { item ->
                    val detail = buildString {
                        append("• ${item.name.ifBlank { "（未命名）" }}")
                        if (item.sets > 0) append(" · ${item.sets}组")
                        if (item.reps.isNotBlank()) append(" × ${item.reps}")
                        if (item.intensity.isNotBlank()) append(" · ${item.intensity}")
                    }
                    Text(detail, style = MaterialTheme.typography.bodySmall)
                }
            }

            // 训练内容图片（教练从电脑截图导入的训练计划）
            if (contentImages.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                Text("训练计划图片", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = appPrimary())
                // 横向滚动展示图片缩略图
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    contentImages.forEach { path ->
                        ScheduleImageThumb(path = path)
                    }
                }
            }

            // === v25 新增：来自电脑端的训练计划截图画廊（LazyRow 水平卡片）===
            // 数据来源：电脑端 PySide6 截图 → 局域网 HTTP 下载 → LanImageReceiver 解析姓名
            // → PlanImageRepository 写入 student_plan_images 表
            // 显示：每张图片以 96dp 宽卡片展示，点击可放大查看（复用 ZoomableImageDialog）
            if (planImages.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "来自电脑端的训练计划",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = appPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${planImages.size} 张",
                        style = MaterialTheme.typography.labelSmall,
                        color = appOnSurfaceVariant()
                    )
                }
                Spacer(Modifier.height(4.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(planImages) { planImage ->
                        LanPlanImageCard(planImage = planImage)
                    }
                }
            }

            // 上课器材
            if (equipmentList.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                Text("上课器材", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = appPrimary())
                Text(equipmentList.joinToString("、"), style = MaterialTheme.typography.bodySmall)
            }

            // 签到按钮区：三态显示
            Spacer(Modifier.height(Spacing.sm))
            when {
                // 已签退：不可再签到，避免误扣课时（按钮禁用，onClick 为空）
                isSignedOut -> {
                    TextButton(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                        enabled = false
                    ) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = com.shangmentiyu.sportscoach.ui.theme.ScoreExcellent)
                        Spacer(Modifier.width(6.dp))
                        Text("已完成（不可重复签到）",
                            color = com.shangmentiyu.sportscoach.ui.theme.ScoreExcellent,
                            fontWeight = FontWeight.Medium)
                    }
                }
                // 已签到未签退：可进入课堂查看
                isSignedIn -> {
                    SecondaryButton(
                        text = "进入课堂",
                        onClick = onSign,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Outlined.PlayArrow
                    )
                }
                // 未签到：显示主签到按钮
                else -> {
                    PrimaryButton(
                        text = "课前签到",
                        onClick = onSign,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Outlined.PlayArrow
                    )
                }
            }
            // === v5 新增：上传精彩瞬间到 PC 端 ===
            // 与签到按钮同行下方，使用 TextButton 次要样式（不抢主按钮视觉）
            // 仅当教练已配置 PC 端 IP 时按钮才有意义，但此处不阻塞：
            // 未配置时点击会返回失败提示，引导教练去设置
            TextButton(
                onClick = onUploadMoment,
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = appOnSurfaceVariant())
                Spacer(Modifier.width(4.dp))
                Text("上传精彩瞬间",
                    color = appOnSurfaceVariant(),
                    style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * 状态徽章：圆角胶囊 + 图标 + 文字。
 *
 * 用于课前准备卡片头部右侧，显示课时当前状态：
 * - 已签退（绿色已完成）：表示课时已结束，不可再签到
 * - 已签到（橙色上课中）：表示学员已签到但未签退
 *
 * @param text 徽章文字
 * @param icon 前缀图标
 * @param color 徽章主色（图标+文字+淡背景）
 */
@Composable
private fun StatusBadge(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 排课训练计划图片缩略图：从内部存储路径加载显示。
 * 点击可放大查看（全屏 Dialog，支持双指缩放、拖动、双击重置）。
 */
@Composable
private fun ScheduleImageThumb(path: String) {
    var fullscreen by remember { mutableStateOf(false) }
    val bitmap = remember(path) {
        try {
            val file = java.io.File(path)
            if (file.exists()) {
                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            } else null
        } catch (_: Exception) { null }
    }

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(appOnSurface().copy(alpha = 0.05f))
            .clickable { fullscreen = true }
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "训练计划图片",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = appOnSurfaceVariant(),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    // 全屏缩放查看
    if (fullscreen && bitmap != null) {
        ZoomableImageDialog(bitmap = bitmap, onDismiss = { fullscreen = false })
    }
}

/**
 * v25 新增：电脑端训练计划图片画廊卡片。
 *
 * 数据来源：电脑端 PySide6 截图 → 局域网 HTTP 下载 → filesDir/ImportedPlans/
 * 显示规格：
 * - 卡片宽度 96dp，高度 110dp（含日期标签）
 * - 圆角 8dp，纯白背景
 * - 图片以 ContentScale.Crop 填充 96×96 区域
 * - 底部叠加日期标签（白色半透明背景）
 * - 点击放大查看（复用 [ZoomableImageDialog]）
 *
 * @param planImage 训练计划图片记录（含 imagePath / createdAt / originalFilename）
 */
@Composable
private fun LanPlanImageCard(planImage: PlanImage) {
    var fullscreen by remember { mutableStateOf(false) }
    val bitmap = remember(planImage.id, planImage.imagePath) {
        try {
            val file = java.io.File(planImage.imagePath)
            if (file.exists()) {
                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            } else null
        } catch (_: Exception) { null }
    }

    // 日期格式化：createdAt 毫秒 → yyyy/MM/dd
    val dateText = remember(planImage.createdAt) {
        try {
            val sdf = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.CHINA)
            sdf.format(java.util.Date(planImage.createdAt))
        } catch (_: Exception) {
            ""
        }
    }

    Column(
        modifier = Modifier
            .width(96.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(appOnSurface().copy(alpha = 0.05f))
            .clickable { if (bitmap != null) fullscreen = true }
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(appOnSurface().copy(alpha = 0.05f))
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "电脑端训练计划",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // 图片文件丢失时显示占位图标
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = appOnSurfaceVariant(),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        // 底部日期标签
        if (dateText.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(appOnSurface().copy(alpha = 0.08f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    dateText,
                    style = MaterialTheme.typography.labelSmall,
                    color = appOnSurface().copy(alpha = 0.7f)
                )
            }
        }
    }

    // 全屏缩放查看（复用现有组件）
    if (fullscreen && bitmap != null) {
        ZoomableImageDialog(bitmap = bitmap, onDismiss = { fullscreen = false })
    }
}

/**
 * 全屏可缩放图片查看器。
 *
 * 支持手势：
 * - 双指捏合缩放（1x ~ 5x）
 * - 放大后单指拖动平移
 * - 双击切换 1x / 2.5x
 * - 缩放回到 1x 时自动归位
 *
 * @param bitmap 待显示的 Bitmap
 * @param onDismiss 关闭回调（点击关闭按钮或返回键）
 */
@Composable
internal fun ZoomableImageDialog(
    bitmap: android.graphics.Bitmap,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f)),
            contentAlignment = Alignment.Center
        ) {
            // 图片：应用缩放与平移变换
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "训练计划图片（可缩放）",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        // 双指缩放 + 单指拖动（仅放大时生效）
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = newScale
                            if (newScale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                // 缩回 1x 时归位
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        // 双击切换 1x / 2.5x
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            )

            // 右上角关闭按钮
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 底部缩放比例提示
            Text(
                "${scale.toInt()}x  ·  双击切换  ·  双指缩放",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * === v28 优化1：历史归档课时列表对话框 ===
 *
 * 全屏展示全部归档课时（archived_lessons 表），按日期降序、时间降序。
 * 与热数据 lessons 表查询分离，仅在用户主动打开时加载，不影响日常列表性能。
 *
 * 设计原则：
 * - 数据量大时仅显示前 500 条 + 顶部统计（避免一次性渲染数千条导致 OOM）
 * - 卡片样式与 [PreClassScheduleCard] 保持一致，确保视觉统一
 *
 * @param lessons 归档课时列表
 * @param loading 是否正在加载
 * @param onDismiss 关闭回调
 */
@Composable
private fun ArchivedLessonsDialog(
    lessons: List<com.shangmentiyu.sportscoach.data.model.ArchivedLesson>,
    loading: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(appBackground())
        ) {
            // 顶部栏：标题 + 关闭按钮 + 统计
            Row(
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "全部历史归档",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "共 ${lessons.size} 条已归档课时（一年前）",
                        style = MaterialTheme.typography.labelSmall,
                        color = appOnSurfaceVariant()
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭")
                }
            }

            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = appPrimary()
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        Text("正在加载归档数据...", color = appOnSurfaceVariant())
                    }
                }
            } else if (lessons.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = appOnSurfaceVariant(),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        Text("暂无归档课时", color = appOnSurfaceVariant())
                        Text(
                            "数据量超过 2000 条且存在一年以上旧记录时会自动归档",
                            style = MaterialTheme.typography.labelSmall,
                            color = appOnSurfaceVariant()
                        )
                    }
                }
            } else {
                // 仅显示前 500 条避免一次性渲染过多导致 OOM
                val displayList = if (lessons.size > 500) lessons.take(500) else lessons
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    items(displayList) { lesson ->
                        ArchivedLessonCard(lesson = lesson)
                    }
                    if (lessons.size > 500) {
                        item {
                            IosCard {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "仅显示前 500 条，共 ${lessons.size} 条归档记录",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = appOnSurfaceVariant()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单条归档课时卡片：展示学员、日期、时间、内容、教练寄语等关键信息。
 */
@Composable
private fun ArchivedLessonCard(lesson: com.shangmentiyu.sportscoach.data.model.ArchivedLesson) {
    IosCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = appPrimary(),
                    modifier = Modifier.padding(end = 8.dp).size(16.dp)
                )
                Text(
                    "${lesson.date} ${lesson.time}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = appPrimary()
                )
                Spacer(Modifier.weight(1f))
                // 归档时间标签
                Text(
                    "已归档",
                    style = MaterialTheme.typography.labelSmall,
                    color = appOnSurfaceVariant(),
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .background(appOnSurfaceVariant().copy(alpha = 0.1f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                lesson.studentName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (lesson.content.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "内容：${lesson.content.take(80)}${if (lesson.content.length > 80) "..." else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = appOnSurfaceVariant()
                )
            }
            if (lesson.coachComment.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "寄语：${lesson.coachComment.take(80)}${if (lesson.coachComment.length > 80) "..." else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = appOnSurfaceVariant()
                )
            }
        }
    }
}
