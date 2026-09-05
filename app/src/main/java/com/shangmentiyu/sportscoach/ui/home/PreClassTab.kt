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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.shangmentiyu.sportscoach.ui.theme.ScheduleListSkeleton
import com.shangmentiyu.sportscoach.ui.theme.SecondaryButton
import com.shangmentiyu.sportscoach.ui.theme.LightPrimary
import com.shangmentiyu.sportscoach.ui.theme.LightSecondary
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appBackground
import com.shangmentiyu.sportscoach.ui.theme.appDividerColor
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appPrimaryContainer
import com.shangmentiyu.sportscoach.ui.theme.appSurface
import com.shangmentiyu.sportscoach.ui.theme.appSurfaceVariant

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
    // === v48 终极打磨：排课列表首帧加载标记（骨架屏） ===
    val loaded by dailyVm.loaded.collectAsStateWithLifecycle()

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
        // === v48 终极打磨：首帧骨架屏（替代转圈/闪空态） ===
        if (!loaded) {
            item(key = "skeleton") {
                ScheduleListSkeleton()
            }
        } else {
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
                        // 日期胶囊：浅珊瑚橙背景 + 珊瑚橙文字
                        Box(
                            modifier = Modifier
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                .background(appPrimaryContainer())
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
                                .background(appPrimaryContainer())
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
                                .background(appPrimaryContainer())
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
                    lessons = lessons,
                    onSelectDate = { dailyVm.selectDate(it) }
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

// ============================================================
// === v55：全部历史归档对话框（视觉升级） ===
// 与主页 `TodayOverviewHeader` + `FloatingStatCard` 视觉语言一致：
// 珊瑚橙渐变头部 + 三个白色悬浮统计卡 + iOS Inset Grouped 卡片，
// 加入年份筛选 + 月份分组，让上千条归档数据仍可快速定位。
// ============================================================

/**
 * v55 月份段条目：(header key, 全部条数, 可见列表)。
 * 提取到顶层避免在 @Composable 函数内定义本地类带来的潜在稳定性问题。
 */
private data class MonthSection(
    val key: String,
    val totalCount: Int,
    val visible: List<com.shangmentiyu.sportscoach.data.model.ArchivedLesson>
)

/**
 * === v55：历史归档课时列表对话框（升级） ===
 *
 * 全屏展示全部归档课时（archived_lessons 表）。
 *
 * 视觉层级（与首页今日概览/设置页保持同一套设计语言）：
 * 1. iOS Large Title 顶部栏（小标题 + 大标题 + 关闭按钮）
 * 2. 珊瑚橙渐变统计头部（总条数 / 学员数 / 时间跨度）
 * 3. 年份筛选 chip 行（"全部 N" + 各年份）
 * 4. 按月份分组 LazyColumn（"YYYY年M月 · X 条"小标题 + 行式卡片）
 *
 * 性能：仍仅渲染前 500 条 + 底部提示，避免一次性绘制大量卡片 OOM。
 *
 * @param lessons 归档课时列表（已按日期降序）
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
            // 顶部栏：iOS Large Title（小标 + 大标 + 关闭）
            ArchivedDialogTopBar(
                totalCount = lessons.size,
                onDismiss = onDismiss
            )

            when {
                loading -> ArchivedLoadingState(modifier = Modifier.weight(1f))
                lessons.isEmpty() -> ArchivedEmptyState(modifier = Modifier.weight(1f))
                else -> ArchivedLessonsBody(
                    lessons = lessons,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * v55 顶部栏：左侧两层（small label + large title），右侧关闭按钮。
 * 视觉对标 iOS Settings 大标题：上方一行小灰色"历史归档"，下方一行 22sp Bold 大标题。
 */
@Composable
private fun ArchivedDialogTopBar(
    totalCount: Int,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Spacing.screenH,
                top = Spacing.md,
                bottom = Spacing.sm,
                end = Spacing.xs
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "历史归档",
                style = MaterialTheme.typography.labelMedium,
                color = appOnSurfaceVariant(),
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "全部归档课时",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = appOnSurface()
            )
            if (totalCount > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "一年前自动归档，共 $totalCount 条记录",
                    style = MaterialTheme.typography.labelSmall,
                    color = appOnSurfaceVariant()
                )
            }
        }
        // 关闭按钮：圆形浅灰背景，与首页胶囊按钮风格统一
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(appSurfaceVariant())
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "关闭",
                tint = appOnSurface(),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * v55 加载中状态：居中 spinner + 文案，垂直/水平双居中。
 */
@Composable
private fun ArchivedLoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = appPrimary(),
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                "正在加载归档数据…",
                style = MaterialTheme.typography.bodyMedium,
                color = appOnSurfaceVariant()
            )
        }
    }
}

/**
 * v55 空状态：渐变圆形图标 + 主标题 + 副标题 + 自动归档规则说明卡。
 * 比 v28 单时钟图标+两行字的版本更具「这个页面已完成」感。
 */
@Composable
private fun ArchivedEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 大圆形 + 珊瑚橙径向渐变背景 + Inventory2 仓储图标
        Box(
            modifier = Modifier
                .size(124.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            appPrimary().copy(alpha = 0.22f),
                            appPrimary().copy(alpha = 0.04f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Inventory2,
                contentDescription = null,
                tint = appPrimary(),
                modifier = Modifier.size(54.dp)
            )
        }

        Spacer(Modifier.height(Spacing.xl))

        Text(
            "暂无归档课时",
            color = appOnSurface(),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "一年前的旧课时会自动归档到这里",
            color = appOnSurfaceVariant(),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(Spacing.xl))

        // 自动归档规则小卡：iOS Inset Grouped 风格，与设置页一致
        IosCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(Spacing.md),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(appPrimaryContainer()),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = appPrimary(),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "自动归档规则",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = appOnSurface()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "当主表课时数超过 2000 条且存在 365 天前的旧记录时，" +
                            "App 会在启动时自动迁移到归档表，无需手动操作。",
                        style = MaterialTheme.typography.bodySmall,
                        color = appOnSurfaceVariant()
                    )
                }
            }
        }
    }
}

/**
 * v55 数据态主体：渐变统计头 + 年份筛选 + 按月分组的 LazyColumn。
 */
@Composable
private fun ArchivedLessonsBody(
    lessons: List<com.shangmentiyu.sportscoach.data.model.ArchivedLesson>,
    modifier: Modifier = Modifier
) {
    // 年份筛选（null = 全部）
    var selectedYear by remember { mutableStateOf<Int?>(null) }

    // 按年份聚合（用于 chip 角标）
    val yearCounts = remember(lessons) {
        lessons.groupBy { it.date.substring(0, 4) }
            .mapValues { it.value.size }
    }
    val availableYears = remember(yearCounts) {
        yearCounts.keys.map { it.toInt() }.sortedDescending()
    }

    // 应用筛选后的列表
    val filtered = remember(lessons, selectedYear) {
        if (selectedYear == null) lessons
        else lessons.filter { it.date.startsWith("${selectedYear}-") }
    }

    // 按月份分组（YYYY-MM 降序）
    val grouped = remember(filtered) {
        filtered.groupBy { it.date.substring(0, 7) }
            .toList()
            .sortedByDescending { it.first }
    }

    // 汇总统计：覆盖学员数、最早日期、最晚日期
    val studentCount = remember(filtered) { filtered.map { it.studentName }.distinct().size }
    val earliestDate = remember(filtered) { filtered.minOfOrNull { it.date } ?: "—" }
    val latestDate = remember(filtered) { filtered.maxOfOrNull { it.date } ?: "—" }

    // 月份段列表渲染用：仅显示前 500 条，OOM 防护。
    val displayCap = 500
    val monthSections = remember(grouped, displayCap) {
        val result = ArrayList<MonthSection>(grouped.size)
        var emitted = 0
        for ((monthKey, monthLessons) in grouped) {
            if (emitted >= displayCap) break
            val remaining = displayCap - emitted
            val visible =
                if (monthLessons.size > remaining) monthLessons.take(remaining) else monthLessons
            result.add(MonthSection(monthKey, monthLessons.size, visible))
            emitted += visible.size
        }
        result
    }
    val truncateNoticeVisible = filtered.size > displayCap

    Column(modifier = modifier.fillMaxSize()) {
        // 1. 珊瑚橙渐变统计头部
        ArchivedOverviewHeader(
            filteredCount = filtered.size,
            totalCount = lessons.size,
            studentCount = studentCount,
            earliestDate = earliestDate,
            latestDate = latestDate
        )

        Spacer(Modifier.height(Spacing.md))

        // 2. 年份 chip 筛选（仅在有多年份时显示）
        if (availableYears.isNotEmpty() && availableYears.size > 1) {
            YearFilterChips(
                totalCount = lessons.size,
                years = availableYears,
                yearCounts = yearCounts,
                selectedYear = selectedYear,
                onSelect = { selectedYear = it }
            )
            Spacer(Modifier.height(Spacing.sm))
        }

        // 3. 月度分组列表
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Spacing.screenH,
                end = Spacing.screenH,
                top = Spacing.xs,
                bottom = Spacing.xl
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            monthSections.forEach { section ->
                item(key = "month_header_${section.key}") {
                    MonthSectionHeader(monthKey = section.key, count = section.totalCount)
                }
                items(section.visible, key = { it.id }) { lesson ->
                    ArchivedLessonRow(lesson = lesson)
                }
            }
            if (truncateNoticeVisible) {
                item(key = "truncate_notice") {
                    ArchivedTruncateNotice(
                        shown = displayCap.coerceAtMost(filtered.size),
                        total = filtered.size
                    )
                }
            }
        }
    }
}

/**
 * v55 渐变统计头部：复用 `TodayOverviewHeader` 的珊瑚橙径向视觉，
 * 但展示归档专属 KPI（条数 / 覆盖学员数 / 时间跨度）。
 */
@Composable
private fun ArchivedOverviewHeader(
    filteredCount: Int,
    totalCount: Int,
    studentCount: Int,
    earliestDate: String,
    latestDate: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenH)
            .shadow(
                elevation = 8.dp,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                ambientColor = appPrimary().copy(alpha = 0.10f),
                spotColor = appPrimary().copy(alpha = 0.18f)
            )
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(LightPrimary, LightSecondary)
                )
            )
            .padding(horizontal = 22.dp, vertical = 22.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (filteredCount == totalCount) "已归档 "
                    else "当前显示 ",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "$filteredCount",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp,
                    lineHeight = 38.sp
                )
                Text(
                    text = " 节课时",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(10.dp))

            // 三栏 KPI 行：覆盖学员 / 时间跨度 / 归档规则
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                ArchivedHeaderKpi(
                    icon = Icons.Outlined.Person,
                    top = "$studentCount",
                    bottom = "位学员"
                )
                ArchivedHeaderKpi(
                    icon = Icons.Outlined.CalendarMonth,
                    top = earliestDate.takeLast(5) + " ~ " + latestDate.takeLast(5),
                    bottom = "起止日期"
                )
                ArchivedHeaderKpi(
                    icon = Icons.Outlined.History,
                    top = if (earliestDate != "—") earliestDate.substring(0, 4) else "—",
                    bottom = "最早一年"
                )
            }
        }
    }
}

/**
 * v55 渐变头中的单项 KPI：图标 + 大数字 + 小标签，纵向排布。
 * 文字纯白，与珊瑚橙背景形成强对比。
 */
@Composable
private fun ArchivedHeaderKpi(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    top: String,
    bottom: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                top,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                bottom,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}

/**
 * v55 年份 chip 行：横向滚动 chips，"全部 N" 永远在最左侧。
 *
 * 选中态：珊瑚橙填充 + 白字 + 阴影；
 * 未选中：纯白卡片底 + 次级文字。
 * 让上千条数据可按年快速切换上下文。
 */
@Composable
private fun YearFilterChips(
    totalCount: Int,
    years: List<Int>,
    yearCounts: Map<String, Int>,
    selectedYear: Int?,
    onSelect: (Int?) -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.screenH)
    ) {
        item(key = "chip_all") {
            YearChip(
                label = "全部",
                count = totalCount,
                selected = selectedYear == null,
                onClick = { onSelect(null) }
            )
        }
        items(years, key = { "chip_$it" }) { y ->
            YearChip(
                label = "$y",
                count = yearCounts["$y"] ?: 0,
                selected = selectedYear == y,
                onClick = { onSelect(y) }
            )
        }
    }
}

/**
 * 单个年份 chip：胶囊形，20dp 圆角。
 */
@Composable
private fun YearChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) appPrimary() else appSurface()
    val fg = if (selected) Color.White else appOnSurface()
    val secondaryFg = if (selected) Color.White.copy(alpha = 0.85f) else appOnSurfaceVariant()

    Box(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .then(
                if (selected) Modifier.shadow(
                    elevation = 4.dp,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    ambientColor = appPrimary().copy(alpha = 0.20f),
                    spotColor = appPrimary().copy(alpha = 0.30f)
                ) else Modifier
            )
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = fg,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "·",
                color = secondaryFg,
                fontSize = 13.sp
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (count >= 1000) String.format("%.1fk", count / 1000.0) else "$count",
                color = secondaryFg,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * v55 月度分组小标题：与设置页 IosSectionHeader 风格一致。
 * "2025年8月" 珊瑚橙 + "X 条归档" 灰色副标签。
 */
@Composable
private fun MonthSectionHeader(monthKey: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.md, bottom = Spacing.xs, start = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatMonthKey(monthKey),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = appPrimary()
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = "· ${formatCount(count)} 条归档",
            style = MaterialTheme.typography.labelSmall,
            color = appOnSurfaceVariant()
        )
        Spacer(Modifier.weight(1f))
        // 极细分隔线，营造 iOS Mail 风格分组感
        Box(
            modifier = Modifier
                .weight(1f)
                .height(0.5.dp)
                .background(appDividerColor())
        )
    }
}

/**
 * v55 月份键格式化："2024-08" → "2024年8月"。
 */
private fun formatMonthKey(key: String): String {
    val parts = key.split("-")
    return if (parts.size == 2) "${parts[0]}年${parts[1].toInt()}月" else key
}

/**
 * v55 数字格式化：1k+ 显示 k，否则原样。
 */
private fun formatCount(c: Int): String =
    if (c >= 1000) String.format("%.1fk", c / 1000.0) else "$c"

/**
 * v55 单条归档行：左侧圆形头像（首字符 + 哈希配色）+ 中间学员/日期 + 右侧时间 + 状态。
 * 视觉密度比 v28 IosCard 高，但仍保持克制。
 */
@Composable
private fun ArchivedLessonRow(lesson: com.shangmentiyu.sportscoach.data.model.ArchivedLesson) {
    IosCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(
                horizontal = Spacing.md,
                vertical = Spacing.md
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：头像（首字符 + 项目统一调色板 hash）
            AvatarCircle(name = lesson.studentName)

            Spacer(Modifier.width(Spacing.md))

            // 中间：姓名（粗体）+ 元数据行
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = lesson.studentName.ifBlank { "未知学员" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = appOnSurface(),
                        maxLines = 1
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    ArchivedStatusPill(status = lesson.status)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = appOnSurfaceVariant(),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = lesson.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = appOnSurfaceVariant()
                    )
                    if (lesson.lessonType.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(2.dp)
                                .clip(CircleShape)
                                .background(appDividerColor())
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = lesson.lessonType,
                            style = MaterialTheme.typography.bodySmall,
                            color = appOnSurfaceVariant(),
                            maxLines = 1
                        )
                    }
                }
                // 内容预览（仅在有内容时显示，最多 2 行）
                if (lesson.content.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = lesson.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = appOnSurfaceVariant(),
                        maxLines = 2
                    )
                }
            }

            Spacer(Modifier.width(Spacing.sm))

            // 右侧：时间块（大字 + AM/PM 小字）
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = lesson.time.takeIf { it.isNotBlank() } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = appPrimary()
                )
                if (lesson.duration > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${lesson.duration} 分钟",
                        style = MaterialTheme.typography.labelSmall,
                        color = appOnSurfaceVariant()
                    )
                }
            }
        }
    }
}

/**
 * v55 学员头像圆形：复用首页 `avatarColorFor` 的配色策略，按首字符 hash 分配。
 * 显示学员名字首字符（在字符串非空时），点击空字符串也不崩。
 */
@Composable
private fun AvatarCircle(name: String) {
    val bg = avatarColorFor(name)
    val initial = name.trim().firstOrNull()?.toString() ?: "·"

    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * v55 状态胶囊：复用首页 `ScoreExcellent/Pass/Fail` 风格。
 * "已签退" 浅绿、"已签到" 浅蓝、"待签到" 浅橙。
 */
@Composable
private fun ArchivedStatusPill(status: String) {
    val (bg, fg) = when (status) {
        "已签退" -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)
        "已签到" -> Color(0xFFE3F2FD) to Color(0xFF1565C0)
        "待签到" -> Color(0xFFFFF3E0) to Color(0xFFE65100)
        else -> appSurfaceVariant() to appOnSurfaceVariant()
    }
    Box(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            status,
            color = fg,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * v55 仅显示前 500 条的提示卡：放在 LazyColumn 底部，与 IosCard 同款。
 */
@Composable
private fun ArchivedTruncateNotice(shown: Int, total: Int) {
    IosCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Outlined.Tune,
                contentDescription = null,
                tint = appOnSurfaceVariant(),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                "仅显示前 $shown 条，共 $total 条，可按年份筛选缩小范围",
                style = MaterialTheme.typography.labelSmall,
                color = appOnSurfaceVariant()
            )
        }
    }
}
