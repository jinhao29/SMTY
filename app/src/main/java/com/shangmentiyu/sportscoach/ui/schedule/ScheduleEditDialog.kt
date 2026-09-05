package com.shangmentiyu.sportscoach.ui.schedule

import android.util.Log

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.shangmentiyu.sportscoach.ui.theme.AppTopBar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shangmentiyu.sportscoach.data.model.ExerciseItem
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.data.repo.CoachConflictException
import com.shangmentiyu.sportscoach.ui.operation.OperationViewModel
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import com.shangmentiyu.sportscoach.ui.theme.IOSCard
import com.shangmentiyu.sportscoach.ui.theme.IOSColorPillSelector
import com.shangmentiyu.sportscoach.ui.theme.IOSSectionHeader
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appGroupedBackground
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appOutline
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appSurface
import com.shangmentiyu.sportscoach.ui.theme.glassTopAppBarColors
import com.shangmentiyu.sportscoach.ui.theme.StyledDropdown
import com.shangmentiyu.sportscoach.ui.theme.AppTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 添加/编辑课程对话框（iOS 分组风格 / Inset Grouped Form）。
 *
 * 视觉设计（方案 1：现代极简 / iOS 分组风）：
 * - 浅灰色分组大背景（iOS SystemGroupedBackground #F2F2F7）
 * - 白色圆角大卡片（12dp 圆角 + 柔和投影）
 * - 输入框带前缀图标（Prefix icon）
 * - 胶囊状彩色圆点（Pill-shaped color selector）
 * - 底部主蓝色填充按钮（iOS SystemBlue #007AFF）
 *
 * 表单字段（业务逻辑保持不变）：
 * - 学员（下拉选择，数据源来自 [OperationViewModel.students]）
 * - 教练（自由输入）
 * - 周几（下拉：周一~周日）
 * - 开始时间 + 时长（分钟）
 * - 地点（自由输入）
 * - 课时类型（下拉建议 + 自定义）
 * - 颜色（6 色胶囊选择器，参考 Wake Up 课表）
 * - 训练内容（ExerciseItem 列表，可增删）
 * - 课前任务（ExerciseItem 列表，上课前需完成的任务）
 * - 备注
 *
 * 交互：
 * - 编辑模式下底部有"删除课程"红色文字按钮
 * - 保存按钮调用 [OperationViewModel.saveSchedule]
 *
 * 适配说明：本组件统一接受 [OperationViewModel]，使运营管理成为唯一排课入口。
 *
 * 结构：本文件保留状态管理与组装逻辑，学员选择/周几/时间/长期排课/保存按钮
 * 分别拆分为独立 @Composable（ScheduleStudentSelector / ScheduleDatePicker /
 * ScheduleTimePicker / ScheduleRepeatSection / ScheduleSubmitButton）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditDialog(
    vm: OperationViewModel,
    isCreate: Boolean,
    prefillDayOfWeek: Int? = null,
    // === v26 优化3：支持从"添加典型排课"按钮预填时间/地点 ===
    prefillStartTime: String? = null,
    prefillLocation: String? = null,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val students by vm.students.collectAsStateWithLifecycle()
    // === v52 数据流加固：学员列表首帧加载标记 ===
    // Room Flow 首帧到达前 students 持有 emptyList()，此时渲染下拉框会显示
    // "请选择"且点开为空（用户感知为"学员下拉框消失"）。首帧到达前显示加载占位。
    val studentsLoaded by vm.studentsLoaded.collectAsStateWithLifecycle()
    // === v46 数据流诊断：Logcat 过滤 DataFlow 查看学员列表是否加载成功 ===
    Log.d("DataFlow", "下拉列表加载到的学员数量: ${students.size}, loaded=$studentsLoaded")
    val editing by vm.editingSchedule.collectAsStateWithLifecycle()
    // 排课记忆：时间/地点历史下拉
    val timeMemories by vm.timeMemories.collectAsStateWithLifecycle()
    val locationMemories by vm.locationMemories.collectAsStateWithLifecycle()
    // === v24 优化6：最近操作的上课周几记忆（新建模式默认选中） ===
    val dayOfWeekMemories by vm.dayOfWeekMemories.collectAsStateWithLifecycle()
    // === 修复：弹窗内独立消费 vm.toast 并显示 Snackbar ===
    // 历史问题：保存失败/校验提示通过 vm.toast 推送，但 ScheduleEditDialog 是全屏 Dialog，
    // 宿主页（ScheduleScreen/PreClassTab/OperationScreen）的 SnackbarHost 被遮挡在 Dialog 之下，
    // 导致"保存失败：数据库写入异常"等错误提示用户完全看不到，误以为点击无反应。
    val snackbarHostState = remember { SnackbarHostState() }
    val toast by vm.toast.collectAsStateWithLifecycle()
    LaunchedEffect(toast) {
        val msg = toast
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
            vm.clearToast()
        }
    }
    // === 焦点管理器：从下拉菜单选择项目后立即清除焦点，关闭软键盘 ===
    // 用户痛点：从历史记录选择学员/上课地点后，OutlinedTextField 仍保持焦点，
    // 导致系统自动弹出软键盘遮挡视线。选择后 clearFocus() 即可关闭键盘。
    // 若用户想手动输入新内容，单击输入框仍可正常唤起键盘（保留默认行为）。
    val focusManager = LocalFocusManager.current
    // === v28 优化3：协程作用域，用于学员选中后异步加载训练内容推荐 ===
    val scope = rememberCoroutineScope()
    // === 修复：弹窗打开时强制刷新学员课时包数据 ===
    // 确保展示与校验读到最新数据库状态（而非上一次缓存的旧状态）
    LaunchedEffect(Unit) {
        vm.loadStudentPackages(if (isCreate) null else editing?.studentId)
        vm.loadStudents()
    }
    // === v28 优化3：是否已为当前学员填充过推荐训练内容（避免重复覆盖用户编辑） ===
    var recommendedFor by remember { mutableStateOf<String?>(null) }

    // 编辑模式下等待原数据加载完成
    val loaded = if (isCreate) true else editing != null

    // === 表单状态 ===
    var studentName by remember { mutableStateOf("") }
    // v46：选中学员的唯一 ID（软关联，保存排课时传递；旧数据/手输为 null）
    var selectedStudentId by remember { mutableStateOf<String?>(null) }
    // 教练默认为"李"（用户要求）
    var coachName by remember { mutableStateOf("李") }
    // 新建模式下：优先使用调用方传入的 prefillDayOfWeek；
    // 若调用方未指定（null），则回退到上一次排课的周几记忆；都没有则用 1（周一）。
    var dayOfWeek by remember { mutableStateOf(prefillDayOfWeek ?: 1) }
    // 新建模式多选周几集合；编辑模式保持空（编辑单条记录使用 dayOfWeek）
    var selectedDays by remember { mutableStateOf<Set<Int>>(emptySet()) }
    // 是否已应用过"近期记忆"默认值（防止 LaunchedEffect 重复覆盖用户已修改的选中状态）
    var memoryApplied by remember { mutableStateOf(false) }
    var startTime by remember { mutableStateOf(prefillStartTime ?: "09:00") }
    var durationMinutes by remember { mutableStateOf("60") }
    var location by remember { mutableStateOf(prefillLocation ?: "") }
    var lessonType by remember { mutableStateOf("训练课") }
    var isLongTerm by remember { mutableStateOf(false) }
    // === v49 体验课：未注册学员临时体验课开关 ===
    var isTrial by remember { mutableStateOf(false) }
    // === 首次排课自动体验课：注册学员首次排课时第一天自动标记为体验课（不消耗课时）===
    var isFirstLessonAutoTrial by remember { mutableStateOf(true) }
    var isFirstLessonAutoTrialEnabled by remember { mutableStateOf(true) }
    // === 小班课：多选学员开关 + 选中集合 ===
    var isGroupClass by remember { mutableStateOf(false) }
    var groupSelectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var groupSelectedNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var color by remember { mutableStateOf("blue") }
    var note by remember { mutableStateOf("") }
    var content by remember { mutableStateOf<List<ExerciseItem>>(emptyList()) }
    var contentImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var equipment by remember { mutableStateOf<List<String>>(emptyList()) }

    // 加载已有数据到表单
    LaunchedEffect(loaded, editing) {
        if (!isCreate && editing != null) {
            val s = editing!!
            studentName = s.studentName
            // === v52 数据流加固：编辑模式同步软关联 ID ===
            // 此前 selectedStudentId 仅由"用户重新选择学员"时写入，编辑加载时保持 null。
            // 若用户在编辑过程中开关一次体验课（isTrial 清空 selectedStudentId 后切回），
            // 保存时 form.studentId=null，虽被 `form.studentId ?: editing.studentId` 兜底，
            // 但"复制上次训练内容/训练推荐"等依赖学员 ID 的路径会拿到错误的空关联。
            // 编辑加载时显式恢复，保证表单状态与数据库软关联一致。
            selectedStudentId = s.studentId
            coachName = s.coachName.ifBlank { "李" }
            dayOfWeek = s.dayOfWeek
            startTime = s.startTime
            durationMinutes = s.durationMinutes.toString()
            location = s.location
            lessonType = s.lessonType
            isLongTerm = s.isLongTerm
            isTrial = s.isTrial
            isGroupClass = s.groupScheduleId != null
            color = s.color
            content = vm.parseContent(s.content)
            contentImages = vm.parseImages(s.contentImages)
            equipment = vm.parseEquipment(s.equipment)
        } else if (isCreate && prefillDayOfWeek != null) {
            // 新建模式：从课表页 FAB 跳转时预填一个周几
            dayOfWeek = prefillDayOfWeek
            selectedDays = setOf(prefillDayOfWeek)
            memoryApplied = true  // 已显式预填，无需再应用记忆
        }
    }

    // === 首次自动体验课：选中学员后检测是否已有正式课记录，有则置灰取消勾选 ===
    LaunchedEffect(selectedStudentId, studentName, isTrial, isGroupClass) {
        if (!isCreate || isTrial || isGroupClass || studentName.isBlank()) {
            isFirstLessonAutoTrialEnabled = true
            isFirstLessonAutoTrial = true
            return@LaunchedEffect
        }
        val hasFormal = withContext(Dispatchers.IO) {
            vm.hasFormalLessons(selectedStudentId, studentName)
        }
        if (hasFormal) {
            isFirstLessonAutoTrialEnabled = false
            isFirstLessonAutoTrial = false
        } else {
            isFirstLessonAutoTrialEnabled = true
            isFirstLessonAutoTrial = true
        }
    }

    // === v25 优化5：监听保存成功事件，调用 onSaved() 关闭弹窗 ===
    // 替代原"按钮点击后立即 onSaved()"的同步关闭行为
    // 让"成功才关闭、冲突弹框、失败保持打开"三种分支能在 UI 层清晰区分
    LaunchedEffect(Unit) {
        vm.saveSuccessEvent.collect { onSaved() }
    }

    // === v25 优化5：教练时间冲突确认框状态 ===
    // 收到冲突事件时显示 GlassAlertDialog，用户确认后用 forceReplace=true 重新保存
    var pendingConflict by remember { mutableStateOf<CoachConflictException?>(null) }
    LaunchedEffect(Unit) {
        vm.coachConflictEvent.collect { e -> pendingConflict = e }
    }

    // 暂存当前表单快照，用于冲突确认后用 forceReplace=true 重新提交
    // （用户在确认框期间未修改表单，state 变量保持不变，直接复用即可）
    val buildForm: () -> ScheduleForm = {
        ScheduleForm(
            studentName = if (isGroupClass) "" else studentName,
            // v49 体验课：studentId 强制 null（未注册学员无软关联）
            studentId = if (isTrial) null else selectedStudentId,
            coachName = coachName,
            dayOfWeek = dayOfWeek,
            daysOfWeek = if (isCreate) selectedDays else emptySet(),
            startTime = startTime,
            durationMinutes = durationMinutes.toIntOrNull() ?: 60,
            location = location,
            lessonType = lessonType,
            isLongTerm = isLongTerm,
            isTrial = isTrial,
            isFirstLessonAutoTrial = isFirstLessonAutoTrial,
            content = content,
            contentImages = contentImages,
            color = color,
            note = note,
            equipment = equipment,
            isGroupClass = isGroupClass,
            groupStudentIds = groupSelectedIds,
            groupStudentNames = groupSelectedNames
        )
    }

    // === v24 优化6：新建模式 + 未显式预填 + 有近期周几记忆 → 应用最近一次操作的周几作为默认选中 ===
    // 独立 LaunchedEffect 监听 dayOfWeekMemories，确保数据库异步加载完成后才应用
    LaunchedEffect(isCreate, prefillDayOfWeek, dayOfWeekMemories) {
        if (isCreate && prefillDayOfWeek == null && !memoryApplied && dayOfWeekMemories.isNotEmpty()) {
            // 取最近一次操作的周几（dayOfWeekMemories 已按 updatedAt 降序）
            val recentDay = dayOfWeekMemories.first().value.toIntOrNull()
            if (recentDay != null && recentDay in 1..7) {
                dayOfWeek = recentDay
                selectedDays = setOf(recentDay)
            }
            memoryApplied = true
        }
    }

    // === 状态回调：由父级维护状态，子组件只负责渲染 ===
    // 小班课多选 Chip 点击：维护选中 ID 集合与姓名列表
    val onGroupToggle: (Student) -> Unit = { s ->
        val sid = s.studentId
        if (sid != null) {
            if (sid in groupSelectedIds) {
                groupSelectedIds = groupSelectedIds - sid
                groupSelectedNames = groupSelectedNames.filter { it != s.name }
            } else {
                groupSelectedIds = groupSelectedIds + sid
                groupSelectedNames = groupSelectedNames + s.name
            }
        }
    }
    // 普通模式选中学员：同步姓名/软关联 ID，清焦点，异步预填训练内容推荐
    val onStudentSelected: (Student) -> Unit = { s ->
        studentName = s.name
        selectedStudentId = s.studentId
        focusManager.clearFocus()
        if (isCreate && content.isEmpty() && recommendedFor != s.name) {
            recommendedFor = s.name
            scope.launch {
                val recommended = withContext(Dispatchers.IO) {
                    vm.recommendTrainingContent(
                        studentName = s.name,
                        latestBmi = s.bmi
                    )
                }
                if (recommended.isNotEmpty() && content.isEmpty()) {
                    content = recommended
                }
            }
        }
    }
    // 新建模式多选周几：多天排课必须搭配"长期排课"，自动勾选并提示
    val onToggleDay: (Int) -> Unit = { day ->
        val newSelectedDays = if (selectedDays.contains(day)) selectedDays - day else selectedDays + day
        selectedDays = newSelectedDays
        if (newSelectedDays.size > 1 && !isLongTerm) {
            isLongTerm = true
            vm.showToast("已自动勾选\u201C长期排课\u201D：多天排课需按周循环生成课程")
        }
        // 同步 dayOfWeek 为首个选中值（用于回退/展示）
        dayOfWeek = newSelectedDays.minOrNull() ?: day
    }
    // 编辑模式单选周几：同步 dayOfWeek 并清除焦点关闭软键盘
    val onDaySelected: (Int) -> Unit = { day ->
        dayOfWeek = day
        focusManager.clearFocus()
    }
    // 历史时间快捷选择：回填开始时间并清除焦点
    val onTimeMemorySelected: (String) -> Unit = { value ->
        startTime = value
        focusManager.clearFocus()
    }
    // 保存：先做 UI 层校验，再走 ValidateScheduleUseCase + saveSchedule，异常暴露为 Toast
    val onSave: () -> Unit = {
        when {
            isCreate && selectedDays.isEmpty() -> vm.showToast("请至少选择一个周几")
            isGroupClass && groupSelectedNames.size < 2 -> vm.showToast("小班课至少需要选择 2 名学员")
            else -> {
                val form = buildForm()
                scope.launch {
                    try {
                        val error = withContext(Dispatchers.IO) {
                            vm.validateScheduleForSave(form)
                        }
                        if (error != null) {
                            vm.showToast(error)
                        } else {
                            vm.saveSchedule(form)
                        }
                    } catch (e: Exception) {
                        vm.showToast(e.message ?: "保存失败：${e.javaClass.simpleName}")
                    }
                }
            }
        }
    }

    // 全屏 Dialog：模拟 iOS NavigationView + Form
    // 使用 Column 布局（非 Scaffold），避免 Dialog 内 WindowInsets 处理不可靠
    // 导致 bottomBar 被系统导航栏遮挡 / 点不到。
    // 结构：TopAppBar + LazyColumn(weight=1f, 可滚动) + 底部固定按钮栏
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
            modifier = Modifier.fillMaxSize()
                .imePadding()
        ) {
            AppTopBar(
                title = {
                    Text(
                        if (isCreate) "新增课程" else "编辑课程",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = glassTopAppBarColors(),
                shareLabel = "课程编辑",
            )

            if (!loaded) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("加载中…", color = appOutline())
                }
                return@Column
            }

            // 表单滚动区（weight=1f 占据中间剩余空间，保证底部按钮栏固定可见）
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = Spacing.screenH,
                    vertical = Spacing.sm
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // === 第一组：基本信息 ===
                item {
                    IOSSectionHeader("基本信息")
                    IOSCard {
                        // === v49 体验课开关：开启后学员选择切换为姓名输入框 ===
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "体验课",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = appOnSurface()
                                )
                                Text(
                                    "为未注册学员安排临时体验课，不消耗课时包",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appOnSurfaceVariant()
                                )
                            }
                            androidx.compose.material3.Switch(
                                checked = isTrial,
                                onCheckedChange = { checked ->
                                    isTrial = checked
                                    if (checked) {
                                        // 体验课不关联注册学员：清空软关联 ID
                                        selectedStudentId = null
                                    } else {
                                        // === v52 数据流加固：从体验课切回普通排课 ===
                                        // 若当前姓名恰好命中已注册学员（编辑历史排课时切了开关再切回），
                                        // 恢复其软关联 ID，避免保存时丢关联导致"数据关联错乱"。
                                        students.firstOrNull { it.name == studentName }?.let { s ->
                                            selectedStudentId = s.studentId
                                        }
                                    }
                                }
                            )
                        }
                        Spacer(Modifier.height(Spacing.md))
                        // === 首次排课自动体验课复选框：仅新建 + 非体验课模式显示 ===
                        if (!isTrial && isCreate) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isFirstLessonAutoTrial,
                                    onCheckedChange = { isFirstLessonAutoTrial = it },
                                    enabled = isFirstLessonAutoTrialEnabled,
                                    colors = androidx.compose.material3.CheckboxDefaults.colors(
                                        checkedColor = appPrimary(),
                                        uncheckedColor = appOnSurfaceVariant()
                                    )
                                )
                                Spacer(Modifier.width(Spacing.sm))
                                Text(
                                    "首次排课自动设为体验课（不消耗课时）",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isFirstLessonAutoTrialEnabled) appOnSurface() else appOutline()
                                )
                            }
                            Spacer(Modifier.height(Spacing.md))
                        }
                        // === 小班课开关：开启后学员选择切换为多选 Chip ===
                        if (!isTrial && isCreate) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "小班课",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = appOnSurface()
                                    )
                                    Text(
                                        "同教练同时段多名学员一起排课，统一签到签退",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = appOnSurfaceVariant()
                                    )
                                }
                                androidx.compose.material3.Switch(
                                    checked = isGroupClass,
                                    onCheckedChange = { checked ->
                                        isGroupClass = checked
                                        if (!checked) {
                                            groupSelectedIds = emptySet()
                                            groupSelectedNames = emptyList()
                                        }
                                    }
                                )
                            }
                            Spacer(Modifier.height(Spacing.md))
                        }
                        // 学员选择：体验课/小班课/普通三种模式由子组件内部切换
                        ScheduleStudentSelector(
                            isTrial = isTrial,
                            isGroupClass = isGroupClass,
                            studentName = studentName,
                            onStudentNameChange = { studentName = it },
                            students = students,
                            studentsLoaded = studentsLoaded,
                            groupSelectedIds = groupSelectedIds,
                            groupSelectedNames = groupSelectedNames,
                            onGroupToggle = onGroupToggle,
                            onStudentSelected = onStudentSelected
                        )
                        Spacer(Modifier.height(Spacing.md))
                        // 教练
                        AppTextField(
                            value = coachName,
                            onValueChange = { coachName = it },
                            label = { Text("教练") },
                            leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // === 第二组：时间安排 ===
                item {
                    IOSSectionHeader("时间安排")
                    IOSCard {
                        ScheduleDatePicker(
                            isCreate = isCreate,
                            dayOfWeek = dayOfWeek,
                            selectedDays = selectedDays,
                            onToggleDay = onToggleDay,
                            onDaySelected = onDaySelected
                        )
                        Spacer(Modifier.height(Spacing.md))
                        ScheduleTimePicker(
                            startTime = startTime,
                            onStartTimeChange = { startTime = it },
                            durationMinutes = durationMinutes,
                            onDurationChange = { durationMinutes = it.filter { c -> c.isDigit() } },
                            timeMemories = timeMemories,
                            onTimeMemorySelected = onTimeMemorySelected
                        )
                        Spacer(Modifier.height(Spacing.md))
                        ScheduleRepeatSection(
                            isLongTerm = isLongTerm,
                            onLongTermChange = { isLongTerm = it }
                        )
                    }
                }
                // === 第三组：课程详情 ===
                item {
                    IOSSectionHeader("课程详情")
                    IOSCard {
                        // 上课地点（历史地点记忆下拉）
                        StyledDropdown(
                            selected = location,
                            options = locationMemories.map { it.value },
                            optionLabel = { it },
                            optionIcon = { Icons.Outlined.LocationOn },
                            onSelected = { value ->
                                location = value
                                // 选择历史地点后立即清除焦点，关闭软键盘
                                focusManager.clearFocus()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(Spacing.md))
                        // 课时类型下拉（预设：训练课/体验课）
                        val typePresets = listOf("训练课", "体验课")
                        StyledDropdown(
                            selected = lessonType,
                            options = typePresets,
                            optionLabel = { it },
                            optionIcon = { Icons.AutoMirrored.Outlined.Label },
                            onSelected = { value ->
                                lessonType = value
                                // 选择课时类型后立即清除焦点，关闭软键盘
                                focusManager.clearFocus()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(Spacing.md))
                        // 颜色选择器（胶囊状）
                        Text(
                            "卡片颜色",
                            style = MaterialTheme.typography.labelMedium,
                            color = appOnSurface()
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        IOSColorPillSelector(
                            selected = color,
                            onSelect = { color = it }
                        )
                    }
                }
                // === 第五组：训练内容 ===
                item {
                    IOSSectionHeader("训练内容")
                    IOSCard {
                        Spacer(Modifier.height(Spacing.md))
                        ContentImagesSection(
                            images = contentImages,
                            onAddImages = { newPaths ->
                                contentImages = contentImages + newPaths
                            },
                            onRemoveImage = { idx ->
                                contentImages = contentImages.toMutableList().also { it.removeAt(idx) }
                            }
                        )
                    }
                }

                // 编辑模式：删除课程按钮（红色文字）
                if (!isCreate && editing != null) {
                    item {
                        Spacer(Modifier.height(Spacing.sm))
                        TextButton(
                            onClick = {
                                vm.deleteSchedule(editing!!.id)
                                onSaved()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(Spacing.xs))
                            Text(
                                "删除课程",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 底部固定按钮栏：主珊瑚橙填充保存按钮（iOS 风格 Bottom Bar）
            ScheduleSubmitButton(onSave = onSave)

            // === v25 优化5：教练时间冲突确认框（用户可选择"强制替换"）===
            // 收到 CoachConflictException 时弹出，提示"该时间段已有其他学员排课，是否强制替换？"
            // 用户确认后用 forceReplace=true 重新调用 saveSchedule（先删旧排课再写新排课）
            pendingConflict?.let { conflict ->
                GlassAlertDialog(
                    onDismissRequest = { pendingConflict = null },
                    title = "排课冲突",
                    content = {
                        Column {
                            Text(
                                conflict.userMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = appOnSurface()
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            Text(
                                "是否强制替换？强制替换将删除原有冲突排课并写入新排课。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                pendingConflict = null
                                // 用 forceReplace=true 重新保存（复用当前表单状态）
                                vm.saveSchedule(buildForm(), forceReplace = true)
                            }
                        ) { Text("确认替换") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingConflict = null }) { Text("取消") }
                    }
                )
            }
        }

        // === 修复：弹窗内 SnackbarHost（显示保存失败/校验提示，不再被宿主页遮挡）===
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
        )
        }
    }
}

/**
 * 训练内容图片区域：支持从相册多选图片导入（用户从电脑截图发手机场景）。
 *
 * 功能：
 * - 点击"导入图片"按钮打开系统 PhotoPicker，支持多选
 * - 选中的图片复制到应用内部存储（filesDir/content_images/），持久化保存
 * - 以网格缩略图展示已导入图片，右上角带删除按钮
 *
 * @param images 图片路径列表（应用内部存储绝对路径）
 * @param onAddImages 新增图片路径列表回调
 * @param onRemoveImage 删除指定索引图片回调
 */
@Composable
private fun ContentImagesSection(
    images: List<String>,
    onAddImages: (List<String>) -> Unit,
    onRemoveImage: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // PhotoPicker：支持多选图片
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val savedPaths = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri -> copyUriToInternal(context, uri) }
                }
                if (savedPaths.isNotEmpty()) {
                    onAddImages(savedPaths)
                }
            }
        }
    }

    Text(
        "训练内容图片",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = appPrimary()
    )
    Spacer(Modifier.height(Spacing.sm))

    // 图片网格（每行2张）
    if (images.isNotEmpty()) {
        images.chunked(2).forEachIndexed { rowIdx, rowImages ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                rowImages.forEachIndexed { colIdx, path ->
                    val absoluteIdx = rowIdx * 2 + colIdx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(appGroupedBackground())
                    ) {
                        Image(
                            bitmap = loadImageBitmapFromFile(path),
                            contentDescription = "训练内容图片",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        // 右上角删除按钮
                        IconButton(
                            onClick = { onRemoveImage(absoluteIdx) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(appSurface().copy(alpha = 0.8f))
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "删除图片",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                // 不足2张时填充空白保持对齐
                if (rowImages.size < 2) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(Spacing.sm))
        }
    } else {
        Text(
            "暂无图片，可从电脑截图后点击下方按钮导入",
            style = MaterialTheme.typography.bodySmall,
            color = appOutline()
        )
        Spacer(Modifier.height(Spacing.sm))
    }

    // 导入图片按钮（浅蓝填充胶囊）
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(appPrimary().copy(alpha = 0.08f))
            .clickable {
                launcher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
            .padding(Spacing.md),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.AddPhotoAlternate,
            contentDescription = "导入图片",
            tint = appPrimary(),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            "导入图片（支持多选）",
            color = appPrimary(),
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * 将 Uri 图片复制到应用内部存储目录（filesDir/content_images/）。
 * 返回保存后的文件绝对路径，失败返回 null。
 */
private fun copyUriToInternal(context: Context, uri: Uri): String? {
    return try {
        val dir = File(context.filesDir, "content_images").apply { if (!exists()) mkdirs() }
        val fileName = "img_${System.currentTimeMillis()}_${uri.lastPathSegment?.hashCode() ?: 0}.jpg"
        val destFile = File(dir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        destFile.absolutePath
    } catch (e: Exception) {
        null
    }
}

/**
 * 从文件路径加载 ImageBitmap（同步，适用于小图缩略图）。
 * 文件不存在或解码失败返回空透明图。
 */
private fun loadImageBitmapFromFile(path: String): androidx.compose.ui.graphics.ImageBitmap {
    return try {
        val file = File(path)
        if (file.exists()) {
            val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            bitmap?.asImageBitmap() ?: androidx.compose.ui.graphics.ImageBitmap(1, 1)
        } else {
            androidx.compose.ui.graphics.ImageBitmap(1, 1)
        }
    } catch (e: Exception) {
        androidx.compose.ui.graphics.ImageBitmap(1, 1)
    }
}
