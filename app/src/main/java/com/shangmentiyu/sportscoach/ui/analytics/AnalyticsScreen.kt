package com.shangmentiyu.sportscoach.ui.analytics

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shangmentiyu.sportscoach.ui.AppViewModelFactory
import com.shangmentiyu.sportscoach.ui.theme.LightSecondary
import com.shangmentiyu.sportscoach.ui.theme.LightTertiary
import com.shangmentiyu.sportscoach.ui.theme.LightPrimary
import com.shangmentiyu.sportscoach.ui.theme.LightPrimary
import com.shangmentiyu.sportscoach.ui.theme.BrandGradientEnd
import com.shangmentiyu.sportscoach.ui.theme.BrandGradientStart
import com.shangmentiyu.sportscoach.ui.theme.ScoreExcellent
import com.shangmentiyu.sportscoach.ui.theme.ScoreFail
import com.shangmentiyu.sportscoach.ui.theme.ScoreGood
import com.shangmentiyu.sportscoach.ui.theme.ScorePass
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appDividerColor
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appSurface

/**
 * 成绩记录主页：展示学员真实体测成绩历史。
 *
 * 设计要点（iOS 18 Light + Inset Grouped）：
 * - Large Title "成绩记录" + 副标题
 * - 学员选择器（点击展开下拉）
 * - 概览统计三宫格（总成绩数 / 参与项目 / 最近测试）
 * - 按体测项目分组，每组倒序展示历史成绩
 * - 每行：日期 + 实测值 + 分数彩色徽章
 * - 顶部第一条额外显示进步幅度（首次 → 最近）
 *
 * 不再包含 AI 虚拟分析（趋势预测/分位对比/雷达聚合）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(onBack: () -> Unit) {
    val vm: AnalyticsViewModel = viewModel(
        factory = AppViewModelFactory(
            LocalContext.current.applicationContext as android.app.Application
        )
    )

    val loading by vm.loading.collectAsStateWithLifecycle()
    val students by vm.students.collectAsStateWithLifecycle()
    val selectedStudent by vm.selectedStudent.collectAsStateWithLifecycle()
    val recordsByProject by vm.recordsByProject.collectAsStateWithLifecycle()
    val overview by vm.overview.collectAsStateWithLifecycle()

    var studentPickerOpen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = appSurface()
    ) { padding ->
        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "正在加载…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            return@Scaffold
        }

        val studentNames = remember(students) { students.map { it.name } }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Spacing.screenH,
                end = Spacing.screenH,
                top = Spacing.screenV,
                bottom = 88.dp
            )
        ) {
            // Large Title
            item {
                Column {
                    Spacer(Modifier.height(Spacing.xl))
                    Text(
                        "成绩记录",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary  // 活力蓝紫
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "真实体测成绩追踪 · 跳绳 · 跑步 · 肺活量",
                        style = MaterialTheme.typography.bodyMedium,
                        color = appOnSurfaceVariant()
                    )
                    Spacer(Modifier.height(Spacing.lg))
                }
            }

            // 空学员
            if (students.isEmpty()) {
                item {
                    EmptyHint(
                        icon = Icons.Outlined.Assessment,
                        title = "暂无学员",
                        subtitle = "请先在「学员管理」中添加学员"
                    )
                }
                return@LazyColumn
            }

            // 学员选择器
            item {
                StudentPickerCard(
                    students = studentNames,
                    selected = selectedStudent,
                    expanded = studentPickerOpen,
                    onToggle = { studentPickerOpen = !studentPickerOpen },
                    onSelect = {
                        vm.selectStudent(it)
                        studentPickerOpen = false
                    }
                )
            }

            // 概览统计
            item {
                OverviewStatsCard(overview = overview)
            }

            // 按项目分组的历史成绩
            if (recordsByProject.isEmpty()) {
                item {
                    EmptyHint(
                        icon = Icons.Outlined.Assessment,
                        title = "暂无成绩记录",
                        subtitle = "在「成绩录入」中保存成绩后，将自动汇总到这里"
                    )
                }
            } else {
                items(
                    items = recordsByProject.entries.toList(),
                    key = { it.key }
                ) { (projectName, records) ->
                    ProjectSection(
                        projectName = projectName,
                        records = records
                    )
                }
            }
        }
    }
}

/**
 * 学员选择器卡片（iOS Settings 风格：点击展开下拉列表 + 顶部 4dp 活力渐变装饰条 + 1.5dp 蓝紫渐变全包裹边框）。
 */
@Composable
private fun StudentPickerCard(
    students: List<String>,
    selected: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(10.dp),
                ambientColor = Color(0x1A000000),
                spotColor = Color(0x1A000000)
            )
            .background(Color.White, RoundedCornerShape(10.dp))
    ) {
        // 顶部 4dp 渐变装饰条（活力蓝紫）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(BrandGradientStart, BrandGradientEnd)
                    )
                )
        )
        // 当前选中行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = Spacing.cardPadding, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像圆点（首字母）
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    selected?.firstOrNull()?.toString() ?: "—",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "当前学员",
                    style = MaterialTheme.typography.labelMedium,
                    color = appOnSurfaceVariant()
                )
                Text(
                    selected ?: "未选择",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Icon(
                if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }

        // 展开下拉
        if (expanded) {
            students.forEach { name ->
                Box(
                    modifier = Modifier
                        .padding(start = 28.dp)
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(appDividerColor())
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(name) }
                        .padding(horizontal = Spacing.cardPadding, vertical = Spacing.sm + 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (name == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        fontWeight = if (name == selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (name == selected) {
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 概览统计卡片：三宫格（总成绩数 / 参与项目 / 最近测试）+ 顶部 4dp 活力渐变装饰条 + 1.5dp 蓝紫渐变全包裹边框。
 */
@Composable
private fun OverviewStatsCard(overview: AnalyticsViewModel.OverviewStats) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(10.dp),
                ambientColor = Color(0x1A000000),
                spotColor = Color(0x1A000000)
            )
            .background(Color.White, RoundedCornerShape(10.dp))
    ) {
        // 顶部 4dp 渐变装饰条（活力蓝紫）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(BrandGradientStart, BrandGradientEnd)
                    )
                )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.md),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatCell(
                label = "总成绩",
                value = overview.totalCount.toString(),
                iconBgColor = LightPrimary
            )
            StatDivider()
            StatCell(
                label = "参与项目",
                value = overview.projectCount.toString(),
                iconBgColor = LightSecondary
            )
            StatDivider()
            StatCell(
                label = "最近测试",
                value = overview.latestDate.takeLast(5),  // MM-DD
                iconBgColor = LightPrimary,
                small = true
            )
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    iconBgColor: Color,
    small: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(iconBgColor, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center
        ) {
            when (label) {
                "总成绩" -> Icon(Icons.Outlined.Assessment, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                "参与项目" -> Icon(Icons.Outlined.Category, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                "最近测试" -> Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Text(
            value,
            style = if (small) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = appOnSurfaceVariant()
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(0.5.dp)
            .height(56.dp)
            .background(appDividerColor())
    )
}

/**
 * 单个项目分组：Section Header + 历史成绩卡片。
 */
@Composable
private fun ProjectSection(
    projectName: String,
    records: List<AnalyticsViewModel.ScoreRecord>
) {
    Column {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                projectName.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary  // 活力蓝紫
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                "${records.size} 次记录",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        }

        // 项目历史成绩卡片
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(10.dp),
                    ambientColor = Color(0x1A000000),
                    spotColor = Color(0x1A000000)
                )
                .background(Color.White, RoundedCornerShape(10.dp))
        ) {
            // 顶部 4dp 渐变装饰条（活力蓝紫）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(BrandGradientStart, BrandGradientEnd)
                        )
                    )
            )
            // 进步概要（仅当记录 >= 2 时显示）
            if (records.size >= 2) {
                ProgressSummaryRow(records = records)
                Box(
                    modifier = Modifier
                        .padding(start = Spacing.cardPadding)
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(appDividerColor())
                )
            }

            records.forEachIndexed { index, record ->
                ScoreRecordRow(
                    record = record,
                    showTopDivider = index > 0 || records.size >= 2
                )
            }
        }
    }
}

/**
 * 进步概要行：首次 → 最近，显示进步幅度与方向。
 */
@Composable
private fun ProgressSummaryRow(records: List<AnalyticsViewModel.ScoreRecord>) {
    val oldest = records.last()      // 倒序，最后一条是最早
    val latest = records.first()     // 第一条是最近
    val delta = latest.score - oldest.score
    val deltaText = if (delta >= 0) "+${String.format("%.1f", delta)}" else String.format("%.1f", delta)
    val deltaColor = if (delta >= 0) ScoreExcellent else ScoreFail

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.cardPadding, vertical = Spacing.sm + 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${oldest.value} → ${latest.value}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "${oldest.date} 至 ${latest.date}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                deltaText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = deltaColor
            )
            Text(
                "分数变化",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * 单条成绩记录行：日期 + 实测值 + 分数徽章。
 */
@Composable
private fun ScoreRecordRow(
    record: AnalyticsViewModel.ScoreRecord,
    showTopDivider: Boolean
) {
    Column {
        if (showTopDivider) {
            Box(
                modifier = Modifier
                    .padding(start = Spacing.cardPadding)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(appDividerColor())
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.cardPadding, vertical = Spacing.sm + 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 日期
            Text(
                record.date.takeLast(5),  // MM-DD
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.width(56.dp)
            )
            Spacer(Modifier.width(Spacing.sm))
            // 实测值
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    record.value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    record.projectName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            // 分数徽章
            ScoreBadge(score = record.score, grade = record.grade)
        }
    }
}

/**
 * 分数彩色徽章：根据分数等级显示不同颜色。
 */
@Composable
private fun ScoreBadge(score: Double, grade: String) {
    val (bgColor, textColor) = when {
        score >= 90 -> ScoreExcellent to Color.White
        score >= 80 -> ScoreGood to Color.White
        score >= 60 -> ScorePass to Color.White
        else -> ScoreFail to Color.White
    }
    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            String.format("%.0f", score),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

/**
 * 空状态提示。
 */
@Composable
private fun EmptyHint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(LightPrimary.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = LightPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = appOnSurfaceVariant()
        )
    }
}
