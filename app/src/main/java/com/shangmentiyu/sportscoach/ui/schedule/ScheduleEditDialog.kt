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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shangmentiyu.sportscoach.data.model.ExerciseItem
import com.shangmentiyu.sportscoach.data.repo.CoachConflictException
import com.shangmentiyu.sportscoach.ui.operation.OperationViewModel
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import com.shangmentiyu.sportscoach.ui.theme.IOSCard
import com.shangmentiyu.sportscoach.ui.theme.IOSColorPillSelector
import com.shangmentiyu.sportscoach.ui.theme.IOSSectionHeader
import com.shangmentiyu.sportscoach.ui.theme.PrimaryButton
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appGroupedBackground
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOutline
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appSurface
import com.shangmentiyu.sportscoach.ui.theme.glassTopAppBarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.shangmentiyu.sportscoach.ui.theme.AppTextFieldShape
import com.shangmentiyu.sportscoach.ui.theme.appTextFieldColors

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
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    // === v46 数据流诊断：Logcat 过滤 DataFlow 查看学员列表是否加载成功 ===
    Log.d("DataFlow", "下拉列表加载到的学员数量: ${students.size}")
    val editing by vm.editingSchedule.collectAsStateWithLifecycle()
    // 排课记忆：时间/地点历史下拉
    val timeMemories by vm.timeMemories.collectAsStateWithLifecycle()
    val locationMemories by vm.locationMemories.collectAsStateWithLifecycle()
    // === v24 优化6：最近操作的上课周几记忆（新建模式默认选中） ===
    val dayOfWeekMemories by vm.dayOfWeekMemories.collectAsStateWithLifecycle()
    // === 焦点管理器：从下拉菜单选择项目后立即清除焦点，关闭软键盘 ===
    // 用户痛点：从历史记录选择学员/上课地点后，OutlinedTextField 仍保持焦点，
    // 导致系统自动弹出软键盘遮挡视线。选择后 clearFocus() 即可关闭键盘。
    // 若用户想手动输入新内容，单击输入框仍可正常唤起键盘（保留默认行为）。
    val focusManager = LocalFocusManager.current
    // === v28 优化3：协程作用域，用于学员选中后异步加载训练内容推荐 ===
    val scope = rememberCoroutineScope()
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
            coachName = s.coachName.ifBlank { "李" }
            dayOfWeek = s.dayOfWeek
            startTime = s.startTime
            durationMinutes = s.durationMinutes.toString()
            location = s.location
            lessonType = s.lessonType
            isLongTerm = s.isLongTerm
            color = s.color
            note = s.note
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
            studentName = studentName,
            studentId = selectedStudentId,
            coachName = coachName,
            dayOfWeek = dayOfWeek,
            daysOfWeek = if (isCreate) selectedDays else emptySet(),
            startTime = startTime,
            durationMinutes = durationMinutes.toIntOrNull() ?: 60,
            location = location,
            lessonType = lessonType,
            isLongTerm = isLongTerm,
            content = content,
            contentImages = contentImages,
            color = color,
            note = note,
            equipment = equipment
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
        Column(
            modifier = Modifier.fillMaxSize()
                .imePadding()
        ) {
            TopAppBar(
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
                colors = glassTopAppBarColors()
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
                        // 学员下拉选择
                        var studentExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = studentExpanded,
                            onExpandedChange = { studentExpanded = !studentExpanded }
                        ) {
                            OutlinedTextField(
                                value = studentName,
                                onValueChange = { studentName = it },
                                readOnly = false,
                                label = { Text("学员") },
                                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(studentExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true),

                             shape = AppTextFieldShape,
                             colors = editDialogFieldColors(),)
                            DropdownMenu(
                                expanded = studentExpanded,
                                onDismissRequest = { studentExpanded = false }
                            ) {
                                students.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text("${s.name} (${s.gender})") },
                                        onClick = {
                                            studentName = s.name
                                            // v46：同步记录选中学员的 studentId（软关联精确匹配）
                                            selectedStudentId = s.studentId
                                            studentExpanded = false
                                            // 选择学员后立即清除焦点，关闭软键盘
                                            focusManager.clearFocus()
                                            // === v28 优化3：新建模式下选中学员后异步预填训练内容推荐 ===
                                            // 触发条件：
                                            // 1. 仅新建模式（isCreate=true）触发，编辑模式不动用户已有内容
                                            // 2. 当前 content 为空（用户尚未添加动作）
                                            // 3. 同一学员只触发一次（避免用户清空后又自动填充）
                                            // 异步执行不阻塞 UI；推荐失败静默忽略，保持空白由教练填写
                                            if (isCreate && content.isEmpty() && recommendedFor != s.name) {
                                                recommendedFor = s.name
                                                scope.launch {
                                                    val recommended = withContext(Dispatchers.IO) {
                                                        vm.recommendTrainingContent(
                                                            studentName = s.name,
                                                            latestBmi = s.bmi
                                                        )
                                                    }
                                                    // 再次校验 content 仍为空（用户可能在加载期间手动添加）
                                                    if (recommended.isNotEmpty() && content.isEmpty()) {
                                                        content = recommended
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(Spacing.md))
                        // 教练
                        OutlinedTextField(
                            value = coachName,
                            onValueChange = { coachName = it },
                            label = { Text("教练") },
                            leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),

                         shape = AppTextFieldShape,
                         colors = editDialogFieldColors(),)
                    }
                }

                // === 第二组：时间安排 ===
                item {
                    IOSSectionHeader("时间安排")
                    IOSCard {
                        val dayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
                        // 周几选择：新建模式多选 Chip（避免重复添加），编辑模式保留单选下拉
                        if (isCreate) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.CalendarToday,
                                    contentDescription = null,
                                    tint = Color(0xFF6B6B6B),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(Spacing.sm))
                                Text(
                                    "选择周几",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF1A1A1A)
                                )
                            }
                            Spacer(Modifier.height(Spacing.sm))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                dayLabels.forEachIndexed { idx, label ->
                                    val day = idx + 1
                                    FilterChip(
                                        selected = selectedDays.contains(day),
                                        onClick = {
                                            val newSelectedDays = if (selectedDays.contains(day)) {
                                                selectedDays - day
                                            } else {
                                                selectedDays + day
                                            }
                                            selectedDays = newSelectedDays
                                            // === Bug 2 UI 修复 ===
                                            // 多天排课必须搭配"长期排课"：
                                            // 单次排课只能对应一个 dayOfWeek，多选天时若不勾选长期排课，
                                            // 后续 ensureLongTermLessonsForWeek 也不会为这些天生成课时记录，
                                            // 会导致"排了课却没有课时"的混乱。
                                            // 解决方案：自动勾选"长期排课"并 Toast 提示用户。
                                            if (newSelectedDays.size > 1 && !isLongTerm) {
                                                isLongTerm = true
                                                vm.showToast("已自动勾选\u201C长期排课\u201D：多天排课需按周循环生成课程")
                                            }
                                            // 同步 dayOfWeek 为首个选中值（用于回退/展示）
                                            dayOfWeek = newSelectedDays.minOrNull() ?: day
                                        },
                                        label = { Text(label) },
                                        leadingIcon = if (selectedDays.contains(day)) {
                                            { Icon(Icons.Outlined.Check, contentDescription = null) }
                                        } else null
                                    )
                                }
                            }
                        } else {
                            // 编辑模式：保留原下拉单选（编辑单条记录）
                            var dayExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = dayExpanded,
                                onExpandedChange = { dayExpanded = !dayExpanded }
                            ) {
                                OutlinedTextField(
                                    value = dayLabels.getOrNull(dayOfWeek - 1) ?: "周一",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("周几") },
                                    leadingIcon = { Icon(Icons.Outlined.CalendarToday, contentDescription = null) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dayExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),

                                 shape = AppTextFieldShape,
                                 colors = editDialogFieldColors(),)
                                DropdownMenu(
                                    expanded = dayExpanded,
                                    onDismissRequest = { dayExpanded = false }
                                ) {
                                    dayLabels.forEachIndexed { idx, label ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                dayOfWeek = idx + 1
                                                dayExpanded = false
                                                // 选择周几后立即清除焦点，关闭软键盘
                                                focusManager.clearFocus()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(Spacing.md))
                        // 开始时间（支持下拉选择历史时间记忆）+ 时长
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            // 开始时间：ExposedDropdownMenuBox 支持自由输入 + 历史下拉
                            var timeExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = timeExpanded,
                                onExpandedChange = { timeExpanded = !timeExpanded },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = startTime,
                                    onValueChange = {
                                        startTime = it
                                        timeExpanded = false
                                    },
                                    label = { Text("开始时间") },
                                    placeholder = { Text("09:00") },
                                    leadingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(timeExpanded) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true),

                                 shape = AppTextFieldShape,
                                 colors = editDialogFieldColors(),)
                                DropdownMenu(
                                    expanded = timeExpanded,
                                    onDismissRequest = { timeExpanded = false }
                                ) {
                                    if (timeMemories.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("暂无历史时间", color = appOutline()) },
                                            onClick = { timeExpanded = false }
                                        )
                                    } else {
                                        timeMemories.forEach { mem ->
                                            DropdownMenuItem(
                                                text = { Text(mem.value) },
                                                onClick = {
                                                    startTime = mem.value
                                                    timeExpanded = false
                                                    // 选择历史时间后立即清除焦点，关闭软键盘
                                                    focusManager.clearFocus()
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = durationMinutes,
                                onValueChange = { durationMinutes = it.filter { c -> c.isDigit() } },
                                label = { Text("时长(分)") },
                                leadingIcon = { Icon(Icons.Outlined.Timer, contentDescription = null) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),

                             shape = AppTextFieldShape,
                             colors = editDialogFieldColors(),)
                        }
                        Spacer(Modifier.height(Spacing.md))
                        // 长期排课勾选：勾选后每周自动生成对应时间的课表
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "长期排课",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "勾选后每周自动生成对应时间的课记录",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appOutline()
                                )
                            }
                            androidx.compose.material3.Switch(
                                checked = isLongTerm,
                                onCheckedChange = { isLongTerm = it }
                            )
                        }
                    }
                }
                // === 第三组：课程详情 ===
                item {
                    IOSSectionHeader("课程详情")
                    IOSCard {
                        // 上课地点（支持下拉选择历史地点记忆 + 自由输入）
                        var locExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = locExpanded,
                            onExpandedChange = { locExpanded = !locExpanded }
                        ) {
                            OutlinedTextField(
                                value = location,
                                onValueChange = {
                                    location = it
                                    locExpanded = false
                                },
                                readOnly = false,
                                label = { Text("上课地点") },
                                leadingIcon = { Icon(Icons.Outlined.LocationOn, contentDescription = null) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(locExpanded) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true),

                             shape = AppTextFieldShape,
                             colors = editDialogFieldColors(),)
                            DropdownMenu(
                                expanded = locExpanded,
                                onDismissRequest = { locExpanded = false }
                            ) {
                                if (locationMemories.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("暂无历史地点", color = appOutline()) },
                                        onClick = { locExpanded = false }
                                    )
                                } else {
                                    locationMemories.forEach { mem ->
                                        DropdownMenuItem(
                                            text = { Text(mem.value) },
                                            onClick = {
                                                location = mem.value
                                                locExpanded = false
                                                // 选择历史地点后立即清除焦点，关闭软键盘
                                                focusManager.clearFocus()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(Spacing.md))
                        // 课时类型下拉（预设：训练课/体验课，支持自定义输入）
                        var typeExpanded by remember { mutableStateOf(false) }
                        val typePresets = listOf("训练课", "体验课")
                        ExposedDropdownMenuBox(
                            expanded = typeExpanded,
                            onExpandedChange = { typeExpanded = !typeExpanded }
                        ) {
                            OutlinedTextField(
                                value = lessonType,
                                onValueChange = { lessonType = it },
                                readOnly = false,
                                label = { Text("课时类型") },
                                leadingIcon = { Icon(Icons.Outlined.Label, contentDescription = null) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true),

                             shape = AppTextFieldShape,
                             colors = editDialogFieldColors(),)
                            DropdownMenu(
                                expanded = typeExpanded,
                                onDismissRequest = { typeExpanded = false }
                            ) {
                                typePresets.forEach { t ->
                                    DropdownMenuItem(
                                        text = { Text(t) },
                                        onClick = {
                                            lessonType = t
                                            typeExpanded = false
                                            // 选择课时类型后立即清除焦点，关闭软键盘
                                            focusManager.clearFocus()
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(Spacing.md))
                        // 颜色选择器（胶囊状）
                        Text(
                            "卡片颜色",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF1A1A1A)
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        IOSColorPillSelector(
                            selected = color,
                            onSelect = { color = it }
                        )
                    }
                }
                // === 第四组：器材准备（多选 FilterChip） ===
                item {
                    IOSSectionHeader("器材准备")
                    IOSCard {
                        val equipmentPresets = listOf(
                            "绳梯", "小栏架", "敏捷圈", "瑜伽垫", "泡沫轴",
                            "平衡垫", "标志桶", "标志碟", "秒表", "口哨"
                        )
                        // 自适应换行的多选 Chip 网格
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            equipmentPresets.forEach { name ->
                                androidx.compose.material3.FilterChip(
                                    selected = equipment.contains(name),
                                    onClick = {
                                        equipment = if (equipment.contains(name)) {
                                            equipment - name
                                        } else {
                                            equipment + name
                                        }
                                    },
                                    label = { Text(name) },
                                    leadingIcon = if (equipment.contains(name)) {
                                        {
                                            Icon(
                                                Icons.Outlined.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null
                                )
                            }
                        }
                        if (equipment.isNotEmpty()) {
                            Spacer(Modifier.height(Spacing.sm))
                            Text(
                                "已选 ${equipment.size} 项：${equipment.joinToString("、")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = appOutline()
                            )
                        }
                    }
                }
                // === 第五组：训练内容 ===
                item {
                    IOSSectionHeader("训练内容")
                    IOSCard {
                        Spacer(Modifier.height(Spacing.md))
                        if (content.isEmpty()) {
                            Text(
                                "暂无训练内容，点击下方按钮添加",
                                style = MaterialTheme.typography.bodySmall,
                                color = appOutline()
                            )
                        } else {
                            content.forEachIndexed { idx, item ->
                                ExerciseEditCard(
                                    item = item,
                                    onUpdate = { newItem ->
                                        content = content.toMutableList().also { it[idx] = newItem }
                                    },
                                    onDelete = {
                                        content = content.toMutableList().also { it.removeAt(idx) }
                                    }
                                )
                                if (idx < content.size - 1) {
                                    Spacer(Modifier.height(Spacing.sm))
                                }
                            }
                        }
                        Spacer(Modifier.height(Spacing.sm))
                        // === v29 优化2：一键复制上次训练内容按钮 ===
                        // 仅当选中有效学员时显示，点击后异步从该学员 lessons 表
                        // 取最近一条非空 content 填充到当前表单
                        if (studentName.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(appPrimary().copy(alpha = 0.05f))
                                    .clickable {
                                        // 异步加载上次训练内容，覆盖当前 content
                                        scope.launch {
                                            val lastContent = withContext(Dispatchers.IO) {
                                                vm.fetchLastTrainingContent(studentName)
                                            }
                                            if (lastContent.isNotEmpty()) {
                                                // 直接覆盖当前 content（用户主动点击，无需保留原内容）
                                                content = lastContent
                                                vm.showToast("已复制上次训练内容（${lastContent.size} 项）")
                                            } else {
                                                vm.showToast("该学员暂无可复用的训练内容")
                                            }
                                        }
                                    }
                                    .padding(Spacing.md),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.ContentCopy,
                                    contentDescription = "复制上次训练内容",
                                    tint = appPrimary(),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(Spacing.xs))
                                Text(
                                    "复制上次训练内容",
                                    color = appPrimary(),
                                    fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(Modifier.height(Spacing.sm))
                        }
                        // 添加动作按钮（浅蓝填充胶囊）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(appPrimary().copy(alpha = 0.08f))
                                .clickable {
                                    content = content + ExerciseItem(name = "新动作")
                                }
                                .padding(Spacing.md),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = "添加动作",
                                tint = appPrimary(),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(Spacing.xs))
                            Text(
                                "添加动作",
                                color = appPrimary(),
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // === 训练内容图片（从电脑截图导入） ===
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

                // === 第七组：备注 ===
                item {
                    IOSSectionHeader("备注")
                    IOSCard {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("备注信息") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Notes, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,

                         shape = AppTextFieldShape,
                         colors = editDialogFieldColors(),)
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
            // 使用 PrimaryButton 统一全局按钮系统（v39 设计令牌）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.screenH)
                    .padding(top = Spacing.sm, bottom = 48.dp)
            ) {
                PrimaryButton(
                    text = "保存课程",
                    onClick = {
                        // 新建模式校验：至少选择一个周几
                        if (isCreate && selectedDays.isEmpty()) {
                            vm.showToast("请至少选择一个周几")
                            return@PrimaryButton
                        }
                        // v25 优化5：不再立即 onSaved()
                        // 由 vm.saveSuccessEvent 触发关闭，由 vm.coachConflictEvent 触发冲突确认框
                        vm.saveSchedule(buildForm())
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

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
    }
}

/**
 * 训练内容/课前任务编辑卡片（iOS 子卡片风格）。
 *
 * 视觉规格：
 * - 浅灰色背景圆角子卡片（10dp 圆角）
 * - 第一行：动作名 + 删除按钮
 * - 第二行：组数 / 次数 / 强度（三等分）
 *
 * 业务逻辑保持不变： onUpdate 回写修改，onDelete 移除该项。
 */
@Composable
private fun ExerciseEditCard(
    item: ExerciseItem,
    onUpdate: (ExerciseItem) -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(appGroupedBackground())
            .padding(Spacing.md)
    ) {
        // 第一行：动作名 + 删除
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            OutlinedTextField(
                value = item.name,
                onValueChange = { onUpdate(item.copy(name = it)) },
                label = { Text("动作") },
                singleLine = true,
                modifier = Modifier.weight(1.5f),

             shape = AppTextFieldShape,
             colors = editDialogFieldColors(),)
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        // 第二行：组数 / 次数 / 强度
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            OutlinedTextField(
                value = item.sets.toString(),
                onValueChange = { v ->
                    val n = v.filter { it.isDigit() }.toIntOrNull() ?: 0
                    onUpdate(item.copy(sets = n))
                },
                label = { Text("组数") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),

             shape = AppTextFieldShape,
             colors = editDialogFieldColors(),)
            OutlinedTextField(
                value = item.reps,
                onValueChange = { onUpdate(item.copy(reps = it)) },
                label = { Text("次数") },
                singleLine = true,
                modifier = Modifier.weight(1f),

             shape = AppTextFieldShape,
             colors = editDialogFieldColors(),)
            OutlinedTextField(
                value = item.intensity,
                onValueChange = { onUpdate(item.copy(intensity = it)) },
                label = { Text("强度") },
                singleLine = true,
                modifier = Modifier.weight(1f),

             shape = AppTextFieldShape,
             colors = editDialogFieldColors(),)
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

/**
 * 新增/编辑课程弹窗专用输入框配色（UI 一致性修复）。
 *
 * 视觉规格：
 * - 浅灰 #F0F0F0 圆角底色（与全局 appTextFieldColors 一致，融入白色卡片，消除"白底块补丁感"）
 * - 边框全透明
 * - 标签文字对比度修复：未聚焦深黑 #1A1A1A / 聚焦珊瑚橙 #FF6B47
 *   （绝对禁止 #B0B0B0 及更浅的灰色作为表单标签颜色）
 * - 光标/错误态珊瑚橙
 */
@Composable
private fun editDialogFieldColors(): TextFieldColors {
    val container = Color(0xFFF0F0F0)
    val labelDark = Color(0xFF1A1A1A)
    val accent = Color(0xFFFF6B47)
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = container,
        unfocusedContainerColor = container,
        disabledContainerColor = container.copy(alpha = 0.5f),
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
        errorBorderColor = accent.copy(alpha = 0.9f),
        focusedLabelColor = accent,
        unfocusedLabelColor = labelDark,
        disabledLabelColor = labelDark.copy(alpha = 0.5f),
        errorLabelColor = accent,
        cursorColor = accent,
    )
}
