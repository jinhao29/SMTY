package com.shangmentiyu.sportscoach.ui.lessoncheckin

import com.shangmentiyu.sportscoach.ui.theme.ShadowTokens
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import com.shangmentiyu.sportscoach.ui.theme.AppTopBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.data.repo.LessonRepository
import com.shangmentiyu.sportscoach.data.repo.OperationRepository
import com.shangmentiyu.sportscoach.data.repo.StudentRepository
import com.shangmentiyu.sportscoach.domain.model.BatchSignResult
import org.koin.androidx.compose.koinViewModel
import com.shangmentiyu.sportscoach.ui.home.SignResult
import com.shangmentiyu.sportscoach.ui.theme.AttendanceOnTime
import com.shangmentiyu.sportscoach.ui.theme.LightSecondary
import com.shangmentiyu.sportscoach.ui.theme.LightTertiary
import com.shangmentiyu.sportscoach.ui.theme.LightPrimary
import com.shangmentiyu.sportscoach.ui.theme.LightPrimaryContainer
import com.shangmentiyu.sportscoach.ui.theme.LightPrimary
import com.shangmentiyu.sportscoach.ui.theme.ScoreFail
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appDividerColor
import com.shangmentiyu.sportscoach.ui.theme.appGroupedBackground
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appSurface
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 上课签到 ViewModel（协调层）。
 *
 * 协调 StudentRepository / LessonRepository / OperationRepository 完成：
 * - 展示所有学员及其剩余课时
 * - 展示当日已签到学员列表
 * - 触发签到流程（v47 起与新路径统一：仅创建 status="已签到" 的 Lesson，
 *   不扣减课时包；签退时由 saveFeedbackAndCheckOut → consumeLessonForCheckOut 统一扣减，
 *   避免旧路径签到即消课 + 新路径签退再消课导致重复扣费）
 */
class LessonCheckInViewModel(
    private val studentRepo: StudentRepository,
    private val lessonRepo: LessonRepository,
    private val opRepo: OperationRepository
) : ViewModel() {

    val students: StateFlow<List<Student>> = studentRepo.getAllStudents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 今日日期 YYYY-MM-DD（线程安全：基于 [java.time.LocalDate] + [java.time.format.DateTimeFormatter]） */
    val today: String = java.time.LocalDate.now()
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()))

    /** 今日所有签到课时（按时间倒序） */
    // 优化：直接用 SQL WHERE date = today 查询，命中 idx_lessons_date 索引，
    // 避免加载全部历史课时再内存过滤（15000 条时可节省 50-150ms 主线程耗时）。
    // LessonDao.getByDate 已按 time DESC 排序，无需再次 sortedByDescending。
    val todayLessons: StateFlow<List<Lesson>> = lessonRepo.getTodayLessons()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 学员姓名 → 剩余课时总数。
     *  统计所有未过期、未退费的课时包（含"活跃"与"已用完"），
     *  使有包但用完的学员显示"已用完"(0) 而非"无课时包"(-1)。 */
    val remainingMap: StateFlow<Map<String, Int>> = opRepo.getAllPackages()
        .map { list ->
            list.filter { !it.isExpired && it.status != "已退费" }
                .groupBy { it.studentName }
                .mapValues { (_, pkgs) -> pkgs.sumOf { it.remainingLessons } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * 签到：仅创建 status="已签到" 的 Lesson，不扣减课时包（与 HomeViewModel.sign 语义一致）。
     *
     * 扣减课时统一发生在签退环节（saveFeedbackAndCheckOut → consumeLessonForCheckOut），
     * 保证同一课时全程只扣减一次，杜绝"签到即消课 + 签退再消课"的重复扣费。
     *
     * @param studentId 学员唯一 ID（软关联外键，v50：补传以支撑双通道查询，杜绝改名断链）
     */
    fun sign(studentName: String, studentId: String? = null, onCreated: (SignResult) -> Unit) {
        viewModelScope.launch {
            val result = try {
                val r = opRepo.signIn(studentName, studentId)
                SignResult(
                    lessonId = r.lessonId,
                    consumed = false,
                    packageName = "",
                    remainingAfter = 0,
                    message = r.message
                )
            } catch (e: Exception) {
                SignResult(
                    lessonId = "",
                    consumed = false,
                    packageName = "",
                    remainingAfter = 0,
                    message = "签到失败：${e.message ?: "未知异常"}"
                )
            }
            onCreated(result)
        }
    }

    /** 过去日期未签退课时（date < today 且 status='已签到'），供首页提醒跳转的筛选列表 */
    val unsignedOutLessons: StateFlow<List<Lesson>> = lessonRepo.getUnsignedOutLessonsBefore(today)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 今日小班课分组：同 date+time+location 的课时聚合（学员数 ≥ 2 才展示为小班课）。
     *
     * 分组键取 (time, location)——本页所有课时均为同一天，date 分量恒定。
     * 含待签到占位课时：教练可在开课前对整组执行"全班签到"。
     */
    val smallClassGroups: StateFlow<List<SmallClassGroup>> = todayLessons
        .map { lessons ->
            lessons.groupBy { it.time to it.location }
                .map { (key, list) ->
                    SmallClassGroup(
                        time = key.first,
                        location = key.second,
                        lessons = list.sortedBy { it.studentName }
                    )
                }
                .filter { it.lessons.size >= 2 }
                .sortedBy { it.time }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 小班课批量签到：对组内待签到课时执行定向签到，已签到/已签退自动跳过。
     *
     * @param lessonIds 组内全部课时 ID（含各状态，由数据层逐一判定跳过）
     * @param onDone 完成回调（主线程），参数为结果提示文案
     */
    fun batchSignIn(lessonIds: List<String>, onDone: (String) -> Unit) {
        if (lessonIds.isEmpty()) return
        viewModelScope.launch {
            onDone(
                try {
                    batchResultMessage("签到", opRepo.batchSignIn(lessonIds))
                } catch (e: Exception) {
                    "批量签到失败：${e.message ?: "未知异常"}"
                }
            )
        }
    }

    /**
     * 小班课批量签退：对组内已签到未签退课时执行签退消课，未签到/已签退自动跳过。
     *
     * @param lessonIds 组内全部课时 ID（含各状态，由数据层逐一判定跳过）
     * @param onDone 完成回调（主线程），参数为结果提示文案
     */
    fun batchSignOut(lessonIds: List<String>, onDone: (String) -> Unit) {
        if (lessonIds.isEmpty()) return
        viewModelScope.launch {
            onDone(
                try {
                    batchResultMessage("签退", opRepo.batchSignOut(lessonIds))
                } catch (e: Exception) {
                    "批量签退失败：${e.message ?: "未知异常"}"
                }
            )
        }
    }

    /**
     * 批量删除未签退课时记录（用户反馈部分记录有误，需手动删除）。
     *
     * 仅删除 Lesson 表记录，不退还已扣减课时包次数。
     *
     * @param ids 课时 ID 列表
     * @param onDone 完成回调（主线程），参数为结果提示文案
     */
    fun deleteLessons(ids: List<String>, onDone: (String) -> Unit) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            onDone(
                try {
                    val count = lessonRepo.deleteLessons(ids)
                    "已删除 $count 条未签退记录"
                } catch (e: Exception) {
                    "删除失败：${e.message ?: "未知异常"}"
                }
            )
        }
    }

    /** 组装批量操作结果提示文案（仅展示非零计数项） */
    private fun batchResultMessage(action: String, r: BatchSignResult): String = buildString {
        append("成功${action} ${r.successCount} 人")
        if (r.checkedOutCount > 0) append("，已签退 ${r.checkedOutCount} 人")
        if (r.skippedCount > 0) append("，跳过 ${r.skippedCount} 人")
        if (r.failedCount > 0) append("，失败 ${r.failedCount} 人")
    }
}

/**
 * 小班课分组（处理层纯数据）：同一日期+时间段+地点的课时聚合。
 *
 * 同一小班课由多条 Lesson 记录构成（studentName 不同，date/time/location 相同）。
 */
data class SmallClassGroup(
    val time: String,
    val location: String,
    val lessons: List<Lesson>
) {
    val studentCount: Int get() = lessons.size

    /** 组内存在待签到学员 → "全班签到"按钮可用 */
    val canBatchSignIn: Boolean get() = lessons.any { it.status == "待签到" }

    /** 组内存在已签到未签退学员 → "全班签退"按钮可用 */
    val canBatchSignOut: Boolean get() = lessons.any { it.status == "已签到" }
}

/**
 * 上课签到页面：独立入口展示课堂签到流程。
 *
 * 设计要点（iOS 18 Light 风格）：
 * - Large Title「上课签到」+ 今日日期副标题
 * - 今日已签学员横向卡片列表（按时间倒序）
 * - 全部学员列表（彩色头像 + 剩余课时徽章 + 蓝色签到按钮）
 * - 签到后 SnackBar 反馈消课结果，可点击进入课时详情
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonCheckInScreen(
    onBack: () -> Unit,
    onOpenLesson: (String) -> Unit,
    /** true 时进入"未签退筛选"模式（首页忘记签退提醒卡片跳转入口） */
    filterUnsignedOut: Boolean = false
) {
        val vm: LessonCheckInViewModel = koinViewModel()

    val students by vm.students.collectAsStateWithLifecycle()
    val todayLessons by vm.todayLessons.collectAsStateWithLifecycle()
    val remainingMap by vm.remainingMap.collectAsStateWithLifecycle()
    val smallClassGroups by vm.smallClassGroups.collectAsStateWithLifecycle()
    val unsignedOutLessons by vm.unsignedOutLessons.collectAsStateWithLifecycle()
    var snackbar by remember { mutableStateOf<String?>(null) }
    var snackbarLessonId by remember { mutableStateOf<String?>(null) }
    // 未签退筛选模式：由导航参数初始化，可通过"退出筛选"返回正常视图
    var showUnsignedOutFilter by remember { mutableStateOf(filterUnsignedOut) }
    // 已取消签到后自动跳转拍照：用户需求是正常上课结算即可，不再需要拍照
    // 签到成功后仅显示 Snackbar 反馈，用户可主动点击「查看课时」进入详情

    Scaffold(
        containerColor = appGroupedBackground(),
        topBar = {
            AppTopBar(
                title = { Text("上课签到", style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appSurface(),
                    scrolledContainerColor = appSurface(),
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary
                ),
                shareLabel = "上课签到",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (showUnsignedOutFilter) {
                // === 未签退筛选模式（首页提醒卡片跳转入口） ===
                UnsignedOutFilterList(
                    lessons = unsignedOutLessons,
                    onExitFilter = { showUnsignedOutFilter = false },
                    onSignOut = { lesson ->
                        snackbarLessonId = null
                        vm.batchSignOut(listOf(lesson.id)) { msg -> snackbar = msg }
                    },
                    onDeleteLessons = { ids ->
                        vm.deleteLessons(ids) { msg -> snackbar = msg }
                    }
                )
            } else if (students.isEmpty()) {
                EmptyHint(
                    title = "暂无学员",
                    subtitle = "请先在「学员管理」中添加学员"
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = Spacing.screenH,
                        end = Spacing.screenH,
                        top = Spacing.screenV,
                        bottom = 88.dp
                    )
                ) {
                    // Large Title 区
                    item {
                        Column {
                            Spacer(Modifier.height(Spacing.lg))
                            Text("上课签到", style = MaterialTheme.typography.displayLarge,
                                color = MaterialTheme.colorScheme.onBackground)
                            Spacer(Modifier.height(Spacing.xs))
                            Text("今日 ${vm.today} · 已签到 ${todayLessons.size} 人",
                                style = MaterialTheme.typography.bodyMedium,
                                color = appOnSurfaceVariant())
                            Spacer(Modifier.height(Spacing.lg))
                        }
                    }

                    // 今日小班课分组（同时间段+地点多学员聚合，支持全班签到/签退）
                    if (smallClassGroups.isNotEmpty()) {
                        item {
                            IosSectionHeader(text = "今日小班课")
                        }
                        items(smallClassGroups, key = { "${it.time}|${it.location}" }) { group ->
                            SmallClassGroupCard(
                                group = group,
                                onBatchSignIn = {
                                    snackbarLessonId = null
                                    vm.batchSignIn(group.lessons.map { it.id }) { msg ->
                                        snackbar = msg
                                    }
                                },
                                onBatchSignOut = {
                                    snackbarLessonId = null
                                    vm.batchSignOut(group.lessons.map { it.id }) { msg ->
                                        snackbar = msg
                                    }
                                }
                            )
                        }
                    }

                    // 全部学员签到列表（置于顶部，方便优先操作）
                    item {
                        IosSectionHeader(text = "全部学员（点击 + 签到）")
                    }
                    // 学员逐项懒加载，避免一次性组合全部学员导致滑动卡顿
                    itemsIndexed(students, key = { _, s -> s.name }) { index, student ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Color.White,
                                    // 仅首行有顶部圆角，末行无特殊处理（视觉上为一张完整卡片）
                                    if (index == 0) RoundedCornerShape(10.dp) else RoundedCornerShape(0.dp)
                                )
                        ) {
                            if (index > 0) {
                                Box(
                                    modifier = Modifier
                                        .padding(start = 76.dp)
                                        .fillMaxWidth()
                                        .height(0.5.dp)
                                        .background(appDividerColor())
                                )
                            }
                            SignRow(
                                student = student,
                                remaining = remainingMap[student.name] ?: -1,
                                signStatus = todayLessons.firstOrNull { it.studentName == student.name }?.status ?: "待签到",
                                showTopDivider = false,
                                onSign = {
                                    vm.sign(student.name, student.studentId) { result ->
                                        snackbar = result.message
                                        snackbarLessonId = result.lessonId
                                    }
                                }
                            )
                        }
                    }

                    // 今日已签到学员卡片（置于全部学员之下）
                    if (todayLessons.isNotEmpty()) {
                        item {
                            IosSectionHeader(text = "今日已签到")
                        }
                        items(todayLessons, key = { it.id }) { lesson ->
                            TodaySignedCard(
                                lesson = lesson,
                                onClick = { onOpenLesson(lesson.id) }
                            )
                        }
                    }
                }
            }

            // Snackbar 反馈（含「查看课时」按钮）
            snackbar?.let { msg ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = snackbarLessonId?.let { lid ->
                        {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    snackbar = null
                                    onOpenLesson(lid)
                                }
                            ) {
                                Text(
                                    "查看课时",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                ) { Text(msg) }
            }
        }
    }
}

/**
 * 已签到学员卡片：姓名 + 时间 + 出勤状态 + 右箭头 + 1.5dp 蓝紫渐变全包裹边框。
 */
@Composable
private fun TodaySignedCard(lesson: Lesson, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(10.dp),
                ambientColor = ShadowTokens.strongAmbient,
                spotColor = ShadowTokens.strongSpot
            )
            .background(appSurface(), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(AttendanceOnTime.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Check, contentDescription = null,
                tint = AttendanceOnTime, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(lesson.studentName, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text("${lesson.time} · ${lesson.attendance} · ${lesson.lessonType}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
        }
        Icon(Icons.Outlined.PlayArrow, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

/**
 * 学员签到行：彩色头像 + 姓名 + 剩余课时徽章 + 蓝色 + 签到按钮。
 */
@Composable
private fun SignRow(
    student: Student,
    remaining: Int,
    signStatus: String,
    showTopDivider: Boolean,
    onSign: () -> Unit
) {
    val canSign = signStatus == "待签到"
    Column {
        if (showTopDivider) {
            Box(
                modifier = Modifier
                    .padding(start = 76.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(appDividerColor())
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // 彩色头像
            val avatarColor = avatarColorFor(student.name)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(avatarColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    student.name.firstOrNull()?.toString() ?: "?",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            // 姓名 + 副信息
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(student.name, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    if (signStatus != "待签到") {
                        Spacer(Modifier.width(Spacing.sm))
                        Box(
                            modifier = Modifier
                                .background(AttendanceOnTime.copy(alpha = 0.15f),
                                    RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(signStatus, style = MaterialTheme.typography.labelSmall,
                                fontSize = 14.sp,
                                color = AttendanceOnTime, fontWeight = FontWeight.Normal)
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitleFor(student, remaining),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
            // 签到按钮：待签到=蓝色可点，已签到/已签退=灰色置灰
            val btnBg = if (canSign) MaterialTheme.colorScheme.primary else Color(0xFFBDBDBD)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickable(enabled = canSign, onClick = onSign)
                    .background(btnBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Add, contentDescription = if (canSign) "签到" else signStatus,
                    tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun IosSectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = Spacing.xs)
    )
}

@Composable
private fun EmptyHint(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(LightPrimary.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null,
                tint = LightPrimary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(Spacing.md))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(Spacing.xs))
        Text(subtitle, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
    }
}

/** 根据姓名 hash 分配 iOS 系统色头像背景 */
@Composable
private fun avatarColorFor(name: String): Color {
    val colors = listOf(
        LightPrimary,
        LightSecondary,
        LightPrimaryContainer,
        LightTertiary,
        LightPrimary
    )
    val hash = if (name.isNotEmpty()) name.first().hashCode() else 0
    return colors[((hash % colors.size) + colors.size) % colors.size]
}

/** 拼接副信息：年级 · 剩余课时 */
private fun subtitleFor(student: Student, remaining: Int): String {
    val parts = mutableListOf<String>()
    parts.add(com.shangmentiyu.sportscoach.core.Standards.gradeFullLabel(student.grade))
    parts.add(
        when {
            remaining < 0 -> "无课时包"
            remaining == 0 -> "课时已用完"
            else -> "剩余 $remaining 课时"
        }
    )
    return parts.joinToString(" · ")
}

/**
 * 小班课分组卡片：时间段 + 地点 + 学员数，学员姓名状态标签流式排布，
 * 底部"全班签到 / 全班签退"批量按钮（无可操作学员时置灰）。
 *
 * - 全班签到：组内待签到学员翻转为已签到，已签到/已签退自动跳过
 * - 全班签退：组内已签到未签退学员执行签退消课，未签到/已签退自动跳过
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SmallClassGroupCard(
    group: SmallClassGroup,
    onBatchSignIn: () -> Unit,
    onBatchSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(10.dp),
                ambientColor = ShadowTokens.strongAmbient,
                spotColor = ShadowTokens.strongSpot
            )
            .background(appSurface(), RoundedCornerShape(10.dp))
            .padding(Spacing.md)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Groups,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${group.time} · ${group.location}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "共 ${group.studentCount} 名学员 · 待签到 " +
                        "${group.lessons.count { it.status == "待签到" }} · 已签到 " +
                        "${group.lessons.count { it.status == "已签到" }} · 已签退 " +
                        "${group.lessons.count { it.status == "已签退" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        // 学员姓名 + 状态标签（流式排布，直接展示全部学员）
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            group.lessons.forEach { lesson ->
                StudentStatusTag(lesson)
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            BatchActionButton(
                text = "全班签到",
                filled = true,
                enabled = group.canBatchSignIn,
                onClick = onBatchSignIn,
                modifier = Modifier.weight(1f)
            )
            BatchActionButton(
                text = "全班签退",
                filled = false,
                enabled = group.canBatchSignOut,
                onClick = onBatchSignOut,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 学员姓名 + 签到状态标签（状态色区分：待签到灰 / 已签到绿 / 已签退主色） */
@Composable
private fun StudentStatusTag(lesson: Lesson) {
    val (status, color) = when (lesson.status) {
        "已签到" -> lesson.status to AttendanceOnTime
        "已签退" -> lesson.status to MaterialTheme.colorScheme.primary
        else -> "待签到" to appOnSurfaceVariant()
    }
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            lesson.studentName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(status, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/**
 * 批量操作按钮：小尺寸非侵入式胶囊。
 *
 * @param filled true = 主色填充（全班签到），false = 主色描边（全班签退）
 * @param enabled 组内无可操作学员时置灰（38% 透明度）
 */
@Composable
private fun BatchActionButton(
    text: String,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alpha = if (enabled) 1f else 0.38f
    val primary = MaterialTheme.colorScheme.primary
    val contentColor = if (filled) Color.White else primary.copy(alpha = alpha)
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (filled) {
                    Modifier.background(primary.copy(alpha = alpha), RoundedCornerShape(8.dp))
                } else {
                    Modifier.border(
                        androidx.compose.foundation.BorderStroke(1.dp, primary.copy(alpha = alpha)),
                        RoundedCornerShape(8.dp)
                    )
                }
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}

/**
 * 未签退筛选列表（首页忘记签退提醒卡片跳转入口）。
 *
 * 展示过去日期已签到但未签退的课时。支持：
 * - 单行签退（"签退"按钮）
 * - 单行删除（trash 图标，用于清理错误记录）
 * - 多选批量删除（"多选删除"进入选择模式 → 勾选 → "删除选中"）
 *
 * 签退/删除后 Room Flow 自动回流，行即时消失，列表清空后显示空状态。
 */
@Composable
private fun UnsignedOutFilterList(
    lessons: List<Lesson>,
    onExitFilter: () -> Unit,
    onSignOut: (Lesson) -> Unit,
    onDeleteLessons: (List<String>) -> Unit
) {
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Spacing.screenH,
                end = Spacing.screenH,
                top = Spacing.screenV,
                bottom = if (selectionMode) 72.dp else 88.dp
            )
        ) {
            item {
                Column {
                    Spacer(Modifier.height(Spacing.lg))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "未签退课时",
                                style = MaterialTheme.typography.displayLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                "过去日期已签到但未签退的课时，请及时处理",
                                style = MaterialTheme.typography.bodyMedium,
                                color = appOnSurfaceVariant()
                            )
                        }
                        if (!selectionMode) {
                            androidx.compose.material3.TextButton(onClick = { selectionMode = true }) {
                                Text(
                                    "多选删除",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            androidx.compose.material3.TextButton(onClick = onExitFilter) {
                                Text(
                                    "退出筛选",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        } else {
                            androidx.compose.material3.TextButton(onClick = {
                                selectedIds = if (selectedIds.size == lessons.size) emptySet() else lessons.map { it.id }.toSet()
                            }) {
                                Text(
                                    if (selectedIds.size == lessons.size) "取消全选" else "全选",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(Spacing.lg))
                }
            }
            if (lessons.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "暂无未签退记录",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            "所有过去课时均已签退",
                            style = MaterialTheme.typography.labelMedium,
                            color = appOnSurfaceVariant()
                        )
                    }
                }
            } else {
                item {
                    IosSectionHeader(text = "共 ${lessons.size} 节课未签退")
                }
                items(lessons, key = { it.id }) { lesson ->
                    UnsignedOutRow(
                        lesson = lesson,
                        selectionMode = selectionMode,
                        isSelected = lesson.id in selectedIds,
                        onToggleSelect = {
                            selectedIds = if (lesson.id in selectedIds) selectedIds - lesson.id else selectedIds + lesson.id
                        },
                        onSignOut = { onSignOut(lesson) },
                        onDelete = { onDeleteLessons(listOf(lesson.id)) }
                    )
                }
            }
        }

        // 底部操作栏：多选模式下显示
        if (selectionMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenH, vertical = Spacing.sm)
                    .clip(RoundedCornerShape(12.dp))
                    .background(appSurface())
                    .shadow(4.dp, RoundedCornerShape(12.dp),
                        ambientColor = ShadowTokens.strongAmbient,
                        spotColor = ShadowTokens.strongSpot)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    "已选 ${selectedIds.size} 项",
                    style = MaterialTheme.typography.bodyMedium,
                    color = appOnSurfaceVariant(),
                    modifier = Modifier.weight(1f)
                )
                BatchActionButton(
                    text = "删除选中",
                    filled = true,
                    enabled = selectedIds.isNotEmpty(),
                    onClick = {
                        onDeleteLessons(selectedIds.toList())
                        selectedIds = emptySet()
                        selectionMode = false
                    }
                )
                BatchActionButton(
                    text = "取消",
                    filled = false,
                    enabled = true,
                    onClick = {
                        selectedIds = emptySet()
                        selectionMode = false
                    }
                )
            }
        }
    }
}

/**
 * 未签退课时行。
 *
 * 正常模式：头像 + 姓名 + 日期时间地点 + 签退按钮 + 删除图标
 * 选择模式：勾选圆 + 头像 + 姓名 + 日期时间地点
 */
@Composable
private fun UnsignedOutRow(
    lesson: Lesson,
    selectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onSignOut: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(10.dp),
                ambientColor = ShadowTokens.strongAmbient,
                spotColor = ShadowTokens.strongSpot
            )
            .background(appSurface(), RoundedCornerShape(10.dp))
            .then(if (selectionMode) Modifier.clickable(onClick = onToggleSelect) else Modifier)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Transparent
                    )
                    .border(
                        2.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else appOnSurfaceVariant().copy(alpha = 0.4f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(avatarColorFor(lesson.studentName), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                lesson.studentName.firstOrNull()?.toString() ?: "?",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                lesson.studentName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${lesson.date} ${lesson.time} · ${lesson.location}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
        if (!selectionMode) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onSignOut)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "签退",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除",
                    tint = appOnSurfaceVariant().copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7FA)
@Composable
private fun LessonCheckInScreenPreview() {
    LessonCheckInScreen(onBack = {}, onOpenLesson = {})
}
