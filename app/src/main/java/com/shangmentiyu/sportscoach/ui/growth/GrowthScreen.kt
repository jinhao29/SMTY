package com.shangmentiyu.sportscoach.ui.growth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shangmentiyu.sportscoach.core.AbilityAnalyzer
import com.shangmentiyu.sportscoach.core.BmiProcessor
import com.shangmentiyu.sportscoach.core.Standards
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.ui.AppViewModelFactory
import com.shangmentiyu.sportscoach.ui.theme.AttendanceAbsent
import com.shangmentiyu.sportscoach.ui.theme.AttendanceLate
import com.shangmentiyu.sportscoach.ui.theme.AttendanceLeave
import com.shangmentiyu.sportscoach.ui.theme.AttendanceOnTime
import com.shangmentiyu.sportscoach.ui.theme.HeroGradientEnd
import com.shangmentiyu.sportscoach.ui.theme.HeroGradientStart
import com.shangmentiyu.sportscoach.ui.theme.MedalBronzeEnd
import com.shangmentiyu.sportscoach.ui.theme.MedalBronzeStart
import com.shangmentiyu.sportscoach.ui.theme.MedalGoldEnd
import com.shangmentiyu.sportscoach.ui.theme.MedalGoldStart
import com.shangmentiyu.sportscoach.ui.theme.MedalSilverEnd
import com.shangmentiyu.sportscoach.ui.theme.MedalSilverStart
import com.shangmentiyu.sportscoach.ui.theme.ScoreExcellent
import com.shangmentiyu.sportscoach.ui.theme.ScoreFail
import com.shangmentiyu.sportscoach.ui.theme.ScoreGood
import com.shangmentiyu.sportscoach.ui.theme.ScorePass
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.StatDurationColor
import com.shangmentiyu.sportscoach.ui.theme.StatLessonColor
import com.shangmentiyu.sportscoach.ui.theme.StatOnTimeColor
import com.shangmentiyu.sportscoach.ui.theme.VitalBlueEnd
import com.shangmentiyu.sportscoach.ui.theme.VitalBlueStart
import com.shangmentiyu.sportscoach.ui.theme.VitalGreenEnd
import com.shangmentiyu.sportscoach.ui.theme.VitalGreenStart
import com.shangmentiyu.sportscoach.ui.theme.VitalOrangeEnd
import com.shangmentiyu.sportscoach.ui.theme.VitalOrangeStart
import com.shangmentiyu.sportscoach.ui.theme.VitalPurpleEnd
import com.shangmentiyu.sportscoach.ui.theme.VitalPurpleStart
import com.shangmentiyu.sportscoach.ui.theme.appGroupedBackground

/**
 * 成长档案页：明亮活力渐变风格（Dribbble-inspired）。
 *
 * 设计原则：
 * - 头部学员卡片：蓝紫渐变 + 装饰圆圈，视觉冲击力强
 * - 身体形态：4 个独立彩色渐变卡片，每项独立色系
 * - 统计卡：3 个彩色数字（蓝/紫/绿）+ 白底卡片
 * - 个人最佳：金银铜奖牌渐变
 * - 保留 iOS Inset Grouped 整体结构
 *
 * 数据驱动：即使学员只有 1-2 次成绩，也能展示有意义内容。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrowthScreen(
    studentName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val vm: GrowthViewModel = viewModel(
        factory = AppViewModelFactory(context.applicationContext as android.app.Application)
    )

    val student by vm.student.collectAsState()
    val lessons by vm.lessons.collectAsState()
    val scores by vm.scores.collectAsState()
    val latestScores by vm.latestScores.collectAsState()
    val personalBests by vm.personalBests.collectAsState()
    val stats by vm.stats.collectAsState()

    LaunchedEffect(studentName) {
        vm.load(studentName)
    }

    Scaffold(
        containerColor = appGroupedBackground(),
        topBar = {
            TopAppBar(
                title = { Text("成长档案", color = Color.White, fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    navigationIconContentColor = Color.White,
                    titleContentColor = Color.White
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                bottom = 88.dp
            )
        ) {
            // === 1. 头部学员卡片：蓝紫渐变 + 装饰圆圈 ===
            item {
                HeroStudentCard(student = student, fallbackName = studentName)
            }

            // === 2. 身体形态：4 个彩色渐变卡片 ===
            val s = student
            if (s != null && (s.age > 0 || s.heightCm > 0 || s.weightKg > 0f || s.bmi > 0f)) {
                item {
                    VitalMetricsGrid(student = s)
                }
            }

            // === 3. 统计卡：彩色数字（蓝/紫/绿） ===
            item {
                StatsCard(stats = stats)
            }

            // === 4. 最近成绩 ===
            if (latestScores.isNotEmpty()) {
                item {
                    IosSectionHeader(text = "最近成绩 · ${latestScores.first().date}")
                }
                item {
                    IosGroupedListCard {
                        latestScores.forEachIndexed { index, score ->
                            val previous = scores
                                .filter { it.projectName == score.projectName && it.date < score.date }
                                .maxByOrNull { it.date }
                            ScoreRow(
                                score = score,
                                previousScore = previous,
                                showTopDivider = index > 0
                            )
                        }
                    }
                }
            }

            // === 5. 个人最佳：金银铜奖牌 ===
            if (personalBests.isNotEmpty()) {
                item {
                    IosSectionHeader(text = "个人最佳")
                }
                item {
                    IosGroupedListCard {
                        personalBests.forEachIndexed { index, best ->
                            PersonalBestRow(
                                best = best,
                                rank = index + 1,
                                showTopDivider = index > 0
                            )
                        }
                    }
                }
            }

            // === 6. 训练历程（按月分组） ===
            if (lessons.isNotEmpty()) {
                item {
                    IosSectionHeader(text = "训练历程")
                }
                val grouped = lessons.groupBy { it.date.take(7) }.toSortedMap(reverseOrder())
                grouped.forEach { (month, lessonsInMonth) ->
                    item(key = "month_header_$month") {
                        Text(
                            text = month,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = Spacing.xs)
                        )
                    }
                    item(key = "month_group_$month") {
                        IosGroupedListCard {
                            lessonsInMonth
                                .sortedByDescending { it.date }
                                .forEachIndexed { index, lesson ->
                                    HistoryRow(
                                        lesson = lesson,
                                        showTopDivider = index > 0
                                    )
                                }
                        }
                    }
                }
            }

            // === 空状态 ===
            if (lessons.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xxl),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                            Spacer(Modifier.height(Spacing.md))
                            Text(
                                "暂无训练记录",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                "签到后即可查看成长档案",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============ 私有 Composable ============

/**
 * 头部学员卡片：蓝紫渐变背景 + 大头像 + 装饰圆圈。
 *
 * 视觉亮点：
 * - 蓝紫渐变（HeroGradientStart → HeroGradientEnd）作为整卡片背景
 * - 右上角两个半透明装饰大圆圈，营造层次感
 * - 64dp 圆形头像（白色描边）+ 白色文字
 * - 副信息使用半透明白色
 */
@Composable
private fun HeroStudentCard(
    student: com.shangmentiyu.sportscoach.data.model.Student?,
    fallbackName: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(HeroGradientStart, HeroGradientEnd)
                ),
                shape = RoundedCornerShape(0.dp)
            )
    ) {
        // 装饰圆圈：右上角两个大圆，半透明白色
        Box(
            modifier = Modifier
                .size(180.dp)
                .offset(x = 220.dp, y = (-60).dp)
                .background(Color.White.copy(alpha = 0.08f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(120.dp)
                .offset(x = 280.dp, y = 80.dp)
                .background(Color.White.copy(alpha = 0.06f), CircleShape)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 64dp 圆形头像（白色描边）
            val displayName = student?.name ?: fallbackName
            val initial = displayName.firstOrNull()?.toString() ?: "?"
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.White.copy(alpha = 0.25f), CircleShape)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    initial,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                )
            }
            // 姓名 + 副信息（白色文字）
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                val s = student
                val subtitle = if (s != null) {
                    listOf(
                        s.gender,
                        Standards.gradeLabel(s.grade),
                        s.school.ifEmpty { null }
                    ).filterNotNull().joinToString(" · ")
                } else ""
                Text(
                    subtitle.ifEmpty { "暂无基本信息" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

/**
 * 身体形态网格：2×2 彩色渐变卡片。
 *
 * 每个卡片独立色系（橙/蓝/紫/绿），渐变背景 + 白色图标 + 大号数值。
 */
@Composable
private fun VitalMetricsGrid(student: com.shangmentiyu.sportscoach.data.model.Student) {
    // BMI 兜底：旧数据 bmi=0 但身高体重有效时实时计算（与 HomeScreen 保持一致）
    val displayBmi = if (student.bmi > 0f) {
        student.bmi
    } else if (student.heightCm > 0 && student.weightKg > 0f) {
        BmiProcessor.compute(student.heightCm, student.weightKg).bmi
    } else 0f

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            VitalCard(
                label = "年龄",
                value = if (student.age > 0) "${student.age}" else "—",
                unit = if (student.age > 0) "岁" else "",
                icon = Icons.Filled.Cake,
                gradientStart = VitalOrangeStart,
                gradientEnd = VitalOrangeEnd,
                modifier = Modifier.weight(1f)
            )
            VitalCard(
                label = "身高",
                value = if (student.heightCm > 0) "${student.heightCm}" else "—",
                unit = if (student.heightCm > 0) "cm" else "",
                icon = Icons.Filled.Height,
                gradientStart = VitalBlueStart,
                gradientEnd = VitalBlueEnd,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            VitalCard(
                label = "体重",
                value = if (student.weightKg > 0f) String.format("%.1f", student.weightKg) else "—",
                unit = if (student.weightKg > 0f) "kg" else "",
                icon = Icons.Filled.MonitorWeight,
                gradientStart = VitalPurpleStart,
                gradientEnd = VitalPurpleEnd,
                modifier = Modifier.weight(1f)
            )
            VitalCard(
                label = "BMI",
                value = if (displayBmi > 0f) String.format("%.1f", displayBmi) else "—",
                unit = if (displayBmi > 0f) BmiProcessor.classify(displayBmi).label else "",
                icon = Icons.Filled.Analytics,
                gradientStart = VitalGreenStart,
                gradientEnd = VitalGreenEnd,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 单个身体形态卡片：彩色渐变背景 + 白色图标 + 白色数值/标签。
 */
@Composable
private fun VitalCard(
    label: String,
    value: String,
    unit: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    gradientStart: Color,
    gradientEnd: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1.1f)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(gradientStart, gradientEnd)
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
    ) {
        // 左上角图标
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(32.dp)
                .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        // 左下角数值 + 标签
        Column(
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (unit.isNotEmpty()) {
                    Text(
                        unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

/**
 * 统计卡：白底卡片 + 3 个彩色数字（蓝/紫/绿）。
 */
@Composable
private fun StatsCard(stats: GrowthStats) {
    IosGroupedListCard(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(
                label = "累计课时",
                value = stats.totalLessons.toString(),
                valueColor = StatLessonColor,
                modifier = Modifier.weight(1f)
            )
            StatDivider()
            StatItem(
                label = "训练时长",
                value = "${String.format("%.1f", stats.totalHours)}h",
                valueColor = StatDurationColor,
                modifier = Modifier.weight(1f)
            )
            StatDivider()
            StatItem(
                label = "准时率",
                value = "${(stats.onTimeRate * 100).toInt()}%",
                valueColor = StatOnTimeColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * iOS 分组头：13pt SemiBold 次级文字。
 */
@Composable
private fun IosSectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = Spacing.xs)
    )
}

/**
 * iOS Inset Grouped 卡片：纯白 + 10pt 圆角 + 无阴影。
 */
@Composable
private fun IosGroupedListCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(10.dp))
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(VitalBlueStart, VitalPurpleEnd)
                ),
                shape = RoundedCornerShape(10.dp)
            )
    ) {
        content()
    }
}

/**
 * 统计项：彩色大数字（指定颜色）+ 灰色标签。
 */
@Composable
private fun StatItem(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

/**
 * 统计项分隔线：1dp 宽 + 40dp 高 + 浅灰。
 */
@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(40.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    )
}

/**
 * 成绩行：项目名 + 实测值 + 与上次对比箭头 + 分数徽章。
 */
@Composable
private fun ScoreRow(
    score: AbilityAnalyzer.ScoreEntry,
    previousScore: AbilityAnalyzer.ScoreEntry?,
    showTopDivider: Boolean
) {
    Column {
        if (showTopDivider) {
            Box(
                modifier = Modifier
                    .padding(start = Spacing.cardPadding)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.cardPadding, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    score.projectName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    score.value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
            if (previousScore != null) {
                val diff = score.score - previousScore.score
                val arrow = if (diff > 0.1) "↑" else if (diff < -0.1) "↓" else "→"
                val color = when {
                    diff > 0.1 -> ScoreExcellent
                    diff < -0.1 -> ScoreFail
                    else -> MaterialTheme.colorScheme.outline
                }
                Text(
                    "$arrow ${String.format("%.1f", kotlin.math.abs(diff))}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = color
                )
            }
            ScoreBadge(score = score.score, grade = score.grade)
        }
    }
}

/**
 * 个人最佳行：奖牌渐变图标 + 项目名 + 最佳成绩/日期 + 分数徽章。
 *
 * @param rank 排名（1=金，2=银，3=铜，其余=蓝色）
 */
@Composable
private fun PersonalBestRow(
    best: AbilityAnalyzer.ScoreEntry,
    rank: Int,
    showTopDivider: Boolean
) {
    val (medalStart, medalEnd) = when (rank) {
        1 -> MedalGoldStart to MedalGoldEnd
        2 -> MedalSilverStart to MedalSilverEnd
        3 -> MedalBronzeStart to MedalBronzeEnd
        else -> VitalBlueStart to VitalBlueEnd
    }
    Column {
        if (showTopDivider) {
            Box(
                modifier = Modifier
                    .padding(start = 60.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.cardPadding, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // 奖牌渐变方形图标
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(medalStart, medalEnd)
                        ),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    best.projectName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${best.value} · ${best.date}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
            ScoreBadge(score = best.score, grade = best.grade)
        }
    }
}

/**
 * 历史课堂行：出勤状态圆点 + 日期/时间 + 课时类型/时长 + 出勤标签。
 */
@Composable
private fun HistoryRow(
    lesson: Lesson,
    showTopDivider: Boolean
) {
    val attendanceColor = when (lesson.attendance) {
        "准时" -> AttendanceOnTime
        "迟到" -> AttendanceLate
        "请假" -> AttendanceLeave
        "旷课" -> AttendanceAbsent
        else -> MaterialTheme.colorScheme.outline
    }
    val scoreCount = if (lesson.scores.isBlank() || lesson.scores == "{}") 0
                     else try { org.json.JSONObject(lesson.scores).length() } catch (e: Exception) { 0 }

    Column {
        if (showTopDivider) {
            Box(
                modifier = Modifier
                    .padding(start = 44.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.cardPadding, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(attendanceColor, CircleShape)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${lesson.date.takeLast(5)} · ${lesson.time}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${lesson.lessonType} · ${lesson.duration}分钟 · 成绩${scoreCount}项",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
            Text(
                lesson.attendance,
                style = MaterialTheme.typography.bodyMedium,
                color = attendanceColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 分数徽章：根据等级显示不同颜色背景 + 白色粗体分数。
 */
@Composable
private fun ScoreBadge(score: Double, grade: String) {
    val bgColor = when (grade) {
        "优秀" -> ScoreExcellent
        "良好" -> ScoreGood
        "及格" -> ScorePass
        else -> ScoreFail
    }
    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            String.format("%.1f", score),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}
