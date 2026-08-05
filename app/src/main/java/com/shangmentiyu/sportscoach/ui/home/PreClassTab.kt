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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.model.PlanImage
import com.shangmentiyu.sportscoach.data.model.Schedule
import org.koin.androidx.compose.koinViewModel
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
        val dailyVm: DailyPlanViewModel = koinViewModel()
    val opVm: OperationViewModel = koinViewModel()

    val selectedDate by dailyVm.selectedDate.collectAsStateWithLifecycle()
    val schedules by dailyVm.schedules.collectAsStateWithLifecycle()
    val lessons by dailyVm.lessons.collectAsStateWithLifecycle()
    val dayOfWeek by dailyVm.dayOfWeek.collectAsStateWithLifecycle()

    var editingScheduleId by remember { mutableStateOf<String?>(null) }
    // 收集编辑中的排课数据：仅当数据加载完成（editing != null）时才渲染编辑对话框，
    // 避免异步加载未完成时 Dialog 一直卡在"加载中"。
    val editingSchedule by opVm.editingSchedule.collectAsStateWithLifecycle()

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
    // 收集排课记忆（时间/地点），用于"今日无排课"空状态的"添加典型排课"按钮预填
    // 仅这两项是 PreClassTab 主体必需的；allSchedules 移到 RecentSchedulesDialog 内部按需订阅
    val timeMemories by opVm.timeMemories.collectAsStateWithLifecycle()
    val locationMemories by opVm.locationMemories.collectAsStateWithLifecycle()
    // === 性能优化 M1：移除顶层 allSchedules 订阅 ===
    // 原 collectAsState 在 PreClassTab 顶层订阅全部活跃排课，
    // 任何排课表变化（教练改排课）都会触发 PreClassTab 全量重组，包括上方日期切换器和今日统计。
    // 现在把订阅下沉到 RecentSchedulesDialog 内部，仅在弹窗打开时订阅。
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

    // === 性能优化 H4+M5：改用 LazyColumn ===
    // 原 Column + verticalScroll + forEach 一次性把所有 PreClassScheduleCard 组合进树，
    // 排课数据多时主线程组合开销大。LazyColumn 仅组合屏幕可见卡片。
    // 排序结果用 remember(schedules) 缓存，仅在 schedules 变化时重排。
    val sortedSchedules = remember(schedules) { schedules.sortedBy { it.startTime } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screenH, vertical = Spacing.screenV),
        // 悬浮底栏避让
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // 日期切换器
        item(key = "date_switcher") {
            IosCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    // === 日期行：左箭头 + 日期胶囊 + 右箭头 ===
                    // 日期包裹在浅珊瑚橙圆角矩形中，与白色卡片背景形成层次感
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { dailyVm.previousDay() }) {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "前一天")
                        }
                        // 日期胶囊：浅珊瑚橙背景 #FFEBE6 + 珊瑚橙文字 #FF6B47
                        Box(
                            modifier = Modifier
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                .background(Color(0xFFFFEBE6))
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
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
                                    color = appPrimary()
                                )
                            }
                        }
                        IconButton(onClick = { dailyVm.nextDay() }) {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "后一天")
                        }
                    }
                    // === 两个胶囊按钮：水平居中排列 ===
                    // 浅珊瑚橙背景 + 珊瑚橙文字 + 无边框，与日期胶囊风格统一
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 回到今天 胶囊
                        Box(
                            modifier = Modifier
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                                .background(Color(0xFFFFEBE6))
                                .clickable { dailyVm.goToday() }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "回到今天",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = appPrimary()
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        // 查看周课表 胶囊（左图标 + 右文字）
                        Box(
                            modifier = Modifier
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                                .background(Color(0xFFFFEBE6))
                                .clickable { onSchedule() }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = appPrimary()
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "查看周课表",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = appPrimary()
                                )
                            }
                        }
                    }
                }
            }
        }

        // === 123.txt UI 重构：珊瑚橙渐变头部 + 3 个独立统计卡片 + 本周进度点 ===
        item(key = "today_overview") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // 1. 珊瑚橙渐变头部卡片（展示"今日排课 X 节"）
                TodayOverviewHeader(scheduleCount = schedules.size)

                // 2. 三个独立白色大圆角统计卡片
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    FloatingStatCard(
                        label = "排课数",
                        value = "${schedules.size}",
                        modifier = Modifier.weight(1f)
                    )
                    FloatingStatCard(
                        label = "已签到",
                        value = "${lessons.size}",
                        modifier = Modifier.weight(1f)
                    )
                    FloatingStatCard(
                        label = "待签到",
                        value = "${(schedules.size - lessons.size).coerceAtLeast(0)}",
                        modifier = Modifier.weight(1f)
                    )
                }

                // 3. 本周进度点卡片（周一到周日）
                WeeklyProgressDots(
                    selectedDate = selectedDate,
                    schedules = schedules,
                    lessons = lessons
                )
            }
        }

        item(key = "preclass_header") {
            IosSectionHeader("课前准备清单")
        }

        // 排课时间线
        if (schedules.isEmpty()) {
            // === v26 优化3：今日无排课时的智能引导推荐 ===
            // 不再只显示一句话，而是给出两个推荐按钮，引导教练快速行动
            item(key = "empty_state") {
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
            }
        } else {
            items(
                items = sortedSchedules,
                key = { schedule -> schedule.id }
            ) { schedule ->
                // L3 优化：parseXxx 结果用 remember 缓存，避免每次重组都重新解析
                val signedLesson = remember(schedule.id, selectedDate, lessons) {
                    dailyVm.findSignedLesson(schedule, selectedDate)
                }
                val contentItems = remember(schedule.content) { opVm.parseContent(schedule.content) }
                val contentImages = remember(schedule.contentImages) { opVm.parseImages(schedule.contentImages) }
                val equipmentList = remember(schedule.equipment) { opVm.parseEquipment(schedule.equipment) }
                PreClassScheduleCard(
                    schedule = schedule,
                    signedLesson = signedLesson,
                    contentItems = contentItems,
                    contentImages = contentImages,
                    equipmentList = equipmentList,
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
        item(key = "archive_entry") {
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
    // 性能优化 M1：对话框内部独立订阅 opVm.schedules，
    // 这样排课变化只会触发对话框内部重组，不再波及 PreClassTab 主体。
    if (showRecentSchedules) {
        RecentSchedulesDialog(
            opVm = opVm,
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
 * === 性能优化 M1 ===：内部独立订阅 [OperationViewModel.schedules]，
 * 这样排课表变化只会触发对话框内部重组，不再波及 PreClassTab 主体。
 *
 * @param opVm OperationViewModel，用于查询全部活跃排课
 * @param onDismiss 关闭回调
 */
@Composable
private fun RecentSchedulesDialog(
    opVm: OperationViewModel,
    onDismiss: () -> Unit
) {
    // 对话框内部按需订阅，仅在对话框打开时收集
    val schedules by opVm.schedules.collectAsStateWithLifecycle()
    RecentSchedulesDialogContent(schedules = schedules, onDismiss = onDismiss)
}

/**
 * 实际渲染近期排课概览对话框内容。
 */
@Composable
private fun RecentSchedulesDialogContent(
    schedules: List<Schedule>,
    onDismiss: () -> Unit
) {
    // 按周几分组并按开始时间排序
    val dayNames = mapOf(
        1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四",
        5 to "周五", 6 to "周六", 7 to "周日"
    )
    // 排序结果缓存，仅在 schedules 变化时重排
    val grouped = remember(schedules) {
        schedules.sortedBy { it.dayOfWeek }.groupBy { it.dayOfWeek }
    }
    // 预计算分组列表（避免 LazyColumn 每次重组都 toList 分配新 List）
    val groupedEntries = remember(grouped) { grouped.entries.toList() }

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
                        items(groupedEntries, key = { it.key }) { (dayOfWeek, daySchedules) ->
                        IosCard {
                            Column(modifier = Modifier.padding(Spacing.md)) {
                                Text(
                                    dayNames[dayOfWeek] ?: "周$dayOfWeek",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = appPrimary()
                                )
                                Spacer(Modifier.height(Spacing.xs))
                                val sortedList = remember(daySchedules) { daySchedules.sortedBy { it.startTime } }
                                sortedList.forEach { s ->
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

// === 性能优化4：以下卡片/弹窗组件已提取到独立文件 PreClassScheduleCard.kt ===
// - PreClassScheduleCard（课前准备卡片）
// - StatusBadge（状态徽章）
// - ScheduleImageThumb（训练计划缩略图）
// - LanPlanImageCard（电脑端训练计划卡片）
// - ZoomableImageDialog（全屏可缩放图片查看器）
// 拆分目的：切断重绘传播，主文件重组时卡片可被 Compose 编译器跳过

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
                val displayList = remember(lessons) { if (lessons.size > 500) lessons.take(500) else lessons }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    items(displayList, key = { it.id }) { lesson ->
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
