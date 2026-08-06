package com.shangmentiyu.sportscoach.ui.lessoncheckin

import android.app.Application
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
     */
    fun sign(studentName: String, onCreated: (SignResult) -> Unit) {
        viewModelScope.launch {
            val result = try {
                val lessonId = lessonRepo.createLesson(
                    studentName = studentName,
                    coach = "",
                    packageId = ""
                )
                SignResult(
                    lessonId = lessonId,
                    consumed = false,
                    packageName = "",
                    remainingAfter = 0,
                    message = "签到成功（签退时再扣减课时）"
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
    onOpenLesson: (String) -> Unit
) {
        val vm: LessonCheckInViewModel = koinViewModel()

    val students by vm.students.collectAsStateWithLifecycle()
    val todayLessons by vm.todayLessons.collectAsStateWithLifecycle()
    val remainingMap by vm.remainingMap.collectAsStateWithLifecycle()
    var snackbar by remember { mutableStateOf<String?>(null) }
    var snackbarLessonId by remember { mutableStateOf<String?>(null) }
    // 已取消签到后自动跳转拍照：用户需求是正常上课结算即可，不再需要拍照
    // 签到成功后仅显示 Snackbar 反馈，用户可主动点击「查看课时」进入详情

    Scaffold(
        containerColor = appGroupedBackground(),
        topBar = {
            TopAppBar(
                title = { Text("上课签到", style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appSurface(),
                    scrolledContainerColor = appSurface(),
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (students.isEmpty()) {
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
                                alreadySignedToday = todayLessons.any { it.studentName == student.name },
                                showTopDivider = false,
                                onSign = {
                                    vm.sign(student.name) { result ->
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
                ambientColor = Color(0x1A000000),
                spotColor = Color(0x1A000000)
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
    alreadySignedToday: Boolean,
    showTopDivider: Boolean,
    onSign: () -> Unit
) {
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
                    if (alreadySignedToday) {
                        Spacer(Modifier.width(Spacing.sm))
                        Box(
                            modifier = Modifier
                                .background(AttendanceOnTime.copy(alpha = 0.15f),
                                    RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("已签到", style = MaterialTheme.typography.labelSmall,
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
            // 签到按钮：蓝色圆形 + 白色加号
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickable(onClick = onSign)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "签到",
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
