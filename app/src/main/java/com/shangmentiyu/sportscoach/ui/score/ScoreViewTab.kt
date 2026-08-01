package com.shangmentiyu.sportscoach.ui.score

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingFlat
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
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
import com.shangmentiyu.sportscoach.ui.analytics.AnalyticsViewModel
import com.shangmentiyu.sportscoach.ui.theme.LightSecondary
import com.shangmentiyu.sportscoach.ui.theme.LightTertiary
import com.shangmentiyu.sportscoach.ui.theme.LightPrimary
import com.shangmentiyu.sportscoach.ui.theme.LightPrimary
import com.shangmentiyu.sportscoach.ui.theme.LightOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.BrandGradientEnd
import com.shangmentiyu.sportscoach.ui.theme.BrandGradientStart
import com.shangmentiyu.sportscoach.ui.theme.ScoreExcellent
import com.shangmentiyu.sportscoach.ui.theme.ScoreFail
import com.shangmentiyu.sportscoach.ui.theme.ScoreGood
import com.shangmentiyu.sportscoach.ui.theme.ScorePass
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appDividerColor

/**
 * 查看成绩 Tab：学员选择 + 概览统计 + 成绩对比 + 按项目分组的历史成绩（含进步/退步趋势 + 编辑/删除）。
 *
 * 复用 AnalyticsViewModel 从课时 scores JSON 解析真实体测成绩记录，
 * 按项目维度聚合展示历史时间线。
 *
 * @param onEditScore 编辑已有成绩回调，参数为课时 ID（跳转 ScoringScreen 加载已有成绩）
 */
@Composable
fun ScoreViewTab(onEditScore: (String) -> Unit = {}) {
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
    var pickerOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // (lessonId, projectName)

    if (loading) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("正在加载…", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        return
    }

    val studentNames = remember(students) { students.map { it.name } }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = PaddingValues(
            start = Spacing.screenH,
            end = Spacing.screenH,
            top = Spacing.screenV,
            bottom = 88.dp
        )
    ) {
        // 空学员
        if (students.isEmpty()) {
            item { EmptyHint(title = "暂无学员", subtitle = "请先在「学员管理」中添加学员") }
            return@LazyColumn
        }

        // 学员选择器
        item {
            StudentPicker(
                students = studentNames,
                selected = selectedStudent,
                expanded = pickerOpen,
                onToggle = { pickerOpen = !pickerOpen },
                onSelect = { vm.selectStudent(it); pickerOpen = false }
            )
        }

        // 概览统计
        item { OverviewStats(overview = overview) }

        // 按项目分组的历史成绩（含成绩对比 + 进步退步 + 编辑删除）
        if (recordsByProject.isEmpty()) {
            item { EmptyHint(title = "暂无成绩记录", subtitle = "在「录入成绩」中保存成绩后，将自动汇总到这里") }
        } else {
            items(recordsByProject.entries.toList(), key = { it.key }) { (projectName, records) ->
                ProjectSection(
                    projectName = projectName,
                    records = records,
                    onEdit = { record -> onEditScore(record.lessonId) },
                    onDelete = { record -> deleteTarget = record.lessonId to record.projectName }
                )
            }
        }
    }

    // 删除确认对话框
    deleteTarget?.let { (lessonId, projectName) ->
        GlassAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = "删除成绩",
            content = { Text("确认删除「$projectName」项目在此次测试中的成绩？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteScore(lessonId, projectName) { _ ->
                            deleteTarget = null
                        }
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

/**
 * 学员选择器卡片：点击展开下拉列表 + 顶部活力渐变装饰条 + 1.5dp 蓝紫渐变全包裹边框。
 */
@Composable
private fun StudentPicker(
    students: List<String>,
    selected: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(10.dp),
                ambientColor = Color(0x1A000000),
                spotColor = Color(0x1A000000)
            )
            .background(Color.White, RoundedCornerShape(10.dp))
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(4.dp).background(
                Brush.linearGradient(colors = listOf(BrandGradientStart, BrandGradientEnd))
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
                .padding(horizontal = Spacing.cardPadding, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
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
                Text("当前学员", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                Text(selected ?: "未选择", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
            }
            Icon(
                if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
        if (expanded) {
            students.forEach { name ->
                Box(
                    modifier = Modifier.padding(start = 28.dp).fillMaxWidth()
                        .height(0.5.dp).background(appDividerColor())
                )
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(name) }
                        .padding(horizontal = Spacing.cardPadding, vertical = Spacing.sm + 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (name == selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onBackground,
                        fontWeight = if (name == selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (name == selected) {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * 概览统计卡片：三宫格（总成绩 / 参与项目 / 最近测试）+ 1.5dp 蓝紫渐变全包裹边框。
 */
@Composable
private fun OverviewStats(overview: AnalyticsViewModel.OverviewStats) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(10.dp),
                ambientColor = Color(0x1A000000),
                spotColor = Color(0x1A000000)
            )
            .background(Color.White, RoundedCornerShape(10.dp))
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(4.dp).background(
                Brush.linearGradient(colors = listOf(BrandGradientStart, BrandGradientEnd))
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatCell(label = "总成绩", value = overview.totalCount.toString(), bgColor = LightPrimary)
            StatDivider()
            StatCell(label = "参与项目", value = overview.projectCount.toString(), bgColor = LightSecondary)
            StatDivider()
            StatCell(label = "最近测试", value = overview.latestDate.takeLast(5), bgColor = LightPrimary, small = true)
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, bgColor: Color, small: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(28.dp).background(bgColor, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Assessment, contentDescription = null,
                tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            style = if (small) MaterialTheme.typography.titleMedium
                   else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
    }
}

@Composable
private fun StatDivider() {
    Box(modifier = Modifier.width(0.5.dp).height(56.dp)
        .background(appDividerColor()))
}

/**
 * 单个项目分组：成绩对比卡片 + 历史成绩时间线（含进步/退步趋势 + 编辑/删除）。
 *
 * - records 按日期降序（最近在前）
 * - 进步/退步通过相邻记录（时间更早的一条）对比计算
 * - 首条记录（最旧）无趋势，显示为"—"
 */
@Composable
private fun ProjectSection(
    projectName: String,
    records: List<AnalyticsViewModel.ScoreRecord>,
    onEdit: (AnalyticsViewModel.ScoreRecord) -> Unit,
    onDelete: (AnalyticsViewModel.ScoreRecord) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(projectName.uppercase(), style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Spacing.sm))
            Text("${records.size} 次记录", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
        }

        // === 成绩对比卡片（首末对比，至少 2 条记录才显示）===
        if (records.size >= 2) {
            ScoreCompareCard(records = records)
            Spacer(Modifier.height(Spacing.sm))
        }

        // === 历史成绩时间线 ===
        Column(
            modifier = Modifier.fillMaxWidth()
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(10.dp),
                    ambientColor = Color(0x1A000000),
                    spotColor = Color(0x1A000000)
                )
                .background(Color.White, RoundedCornerShape(10.dp))
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(4.dp).background(
                    Brush.linearGradient(colors = listOf(BrandGradientStart, BrandGradientEnd))
                )
            )
            records.forEachIndexed { index, record ->
                if (index > 0) {
                    Box(
                        modifier = Modifier.padding(start = Spacing.cardPadding).fillMaxWidth()
                            .height(0.5.dp).background(appDividerColor())
                    )
                }
                // 相邻记录：下标 index+1 是时间更早的一条（降序排列）
                val prevRecord = records.getOrNull(index + 1)
                ScoreRecordRow(
                    record = record,
                    prevRecord = prevRecord,
                    onEdit = { onEdit(record) },
                    onDelete = { onDelete(record) }
                )
            }
        }
    }
}

/**
 * 成绩对比卡片：首末成绩对比 + 总进步幅度。
 *
 * records 按日期降序，records.first() = 最近一次，records.last() = 最早一次。
 * 差值 = 最近 - 最早：正数=进步，负数=退步，零=持平。
 */
@Composable
private fun ScoreCompareCard(records: List<AnalyticsViewModel.ScoreRecord>) {
    val first = records.last()   // 最早一次
    val last = records.first()   // 最近一次
    val delta = last.score - first.score
    val (trendIcon, trendColor, trendText) = when {
        delta > 0.5 -> Triple(Icons.Outlined.TrendingUp, LightPrimary, "进步")
        delta < -0.5 -> Triple(Icons.Outlined.TrendingDown, LightOnSurfaceVariant, "退步")
        else -> Triple(Icons.Outlined.TrendingFlat, MaterialTheme.colorScheme.outline, "持平")
    }

    Row(
        modifier = Modifier.fillMaxWidth()
            .background(
                Brush.linearGradient(
                    colors = listOf(trendColor.copy(alpha = 0.08f), Color.White)
                ),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = Spacing.cardPadding, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 趋势图标
        Box(
            modifier = Modifier.size(36.dp).background(trendColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(trendIcon, contentDescription = trendText, tint = trendColor, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(Spacing.md))

        // 首末对比详情
        Column(modifier = Modifier.weight(1f)) {
            Text("成绩对比", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(first.value, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Text(" → ", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.outline)
                Text(last.value, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = trendColor)
            }
            Text("${first.date.takeLast(5)} → ${last.date.takeLast(5)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
        }

        // 差值徽章
        Column(horizontalAlignment = Alignment.End) {
            val arrow = if (delta > 0.5) "↑" else if (delta < -0.5) "↓" else "→"
            Text("$arrow ${"%.1f".format(kotlin.math.abs(delta))}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, color = trendColor)
            Text(trendText, style = MaterialTheme.typography.labelSmall, color = trendColor)
        }
    }
}

/**
 * 单条成绩记录行：日期 + 实测值 + 项目名 + 进步退步趋势 + 分数徽章 + 编辑/删除按钮。
 *
 * @param record 当前记录
 * @param prevRecord 时间更早的相邻记录（用于计算进步/退步），null 表示当前为最旧记录
 */
@Composable
private fun ScoreRecordRow(
    record: AnalyticsViewModel.ScoreRecord,
    prevRecord: AnalyticsViewModel.ScoreRecord?,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = Spacing.cardPadding, vertical = Spacing.sm + 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 日期
        Text(record.date.takeLast(5), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.width(48.dp))

        // 实测值 + 项目名
        Spacer(Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(record.value, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Text(record.projectName, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))

            // 进步/退步趋势（与上一条记录对比）
            if (prevRecord != null) {
                val delta = record.score - prevRecord.score
                val (trendIcon, trendColor, trendText) = when {
                    delta > 0.5 -> Triple(Icons.Outlined.TrendingUp, LightPrimary,
                        "↑ ${"%.1f".format(delta)} 较上次")
                    delta < -0.5 -> Triple(Icons.Outlined.TrendingDown, LightOnSurfaceVariant,
                        "↓ ${"%.1f".format(kotlin.math.abs(delta))} 较上次")
                    else -> Triple(Icons.Outlined.TrendingFlat, MaterialTheme.colorScheme.outline,
                        "→ 持平")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(trendIcon, contentDescription = null, tint = trendColor,
                        modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(trendText, style = MaterialTheme.typography.labelSmall, color = trendColor)
                }
            } else {
                Text("首次测试", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }
        }

        // 分数徽章
        ScoreBadge(score = record.score, grade = record.grade)
        Spacer(Modifier.width(4.dp))

        // 编辑 + 删除按钮
        IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Outlined.Edit, contentDescription = "编辑",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Outlined.Delete, contentDescription = "删除",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
        }
    }
}

/**
 * 分数彩色徽章：根据分数等级显示不同颜色。
 */
@Composable
private fun ScoreBadge(score: Double, grade: String) {
    val bgColor = when {
        score >= 90 -> ScoreExcellent
        score >= 80 -> ScoreGood
        score >= 60 -> ScorePass
        else -> ScoreFail
    }
    Box(
        modifier = Modifier.background(bgColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            String.format("%.0f", score),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

/**
 * 空状态提示。
 */
@Composable
private fun EmptyHint(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(56.dp).background(
                LightPrimary.copy(alpha = 0.12f), RoundedCornerShape(14.dp)
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Assessment, contentDescription = null,
                tint = LightPrimary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(Spacing.md))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.xs))
        Text(subtitle, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
    }
}
