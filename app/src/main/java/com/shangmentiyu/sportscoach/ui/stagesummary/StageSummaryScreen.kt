package com.shangmentiyu.sportscoach.ui.stagesummary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shangmentiyu.sportscoach.data.repo.OperationRepository
import com.shangmentiyu.sportscoach.ui.AppViewModelFactory
import com.shangmentiyu.sportscoach.ui.theme.GlassCard
import com.shangmentiyu.sportscoach.ui.theme.ScoreExcellent
import com.shangmentiyu.sportscoach.ui.theme.ScoreFail
import com.shangmentiyu.sportscoach.ui.theme.ScorePass
import com.shangmentiyu.sportscoach.ui.theme.glassTopAppBarColors

/**
 * 阶段性总结页面：选择学员 + 时间范围，展示聚合统计与进步对比。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StageSummaryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: StageSummaryViewModel = viewModel(
        factory = AppViewModelFactory(context.applicationContext as android.app.Application)
    )

    val students by vm.students.collectAsStateWithLifecycle()
    val selectedStudent by vm.selectedStudent.collectAsStateWithLifecycle()
    val rangeOption by vm.rangeOption.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("阶段性总结") },
                colors = glassTopAppBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 学员选择
            GlassCard {
                Text("选择学员", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                if (students.isEmpty()) {
                    Text("暂无学员", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline)
                } else {
                    OutlinedTextField(
                        value = selectedStudent,
                        onValueChange = { vm.selectStudent(it) },
                        label = { Text("学员姓名") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("可选学员：", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline)
                    students.take(10).forEach { name ->
                        Text("· $name", style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp, top = 2.dp))
                    }
                }
            }

            // 时间范围选择
            if (selectedStudent.isNotBlank()) {
                GlassCard {
                    Text("时间范围", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("最近7天" to 0, "最近30天" to 1, "最近90天" to 2, "全部" to 3).forEach { (label, opt) ->
                            FilterChip(
                                selected = rangeOption == opt,
                                onClick = { vm.selectRange(opt) },
                                label = { Text(label, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }
                }
            }

            // 加载中
            if (loading) {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            // 展示总结
            summary?.let { s -> SummaryContent(s) }
        }
    }
}

@Composable
private fun SummaryContent(s: OperationRepository.StageSummary) {
    // 时间范围卡片
    GlassCard {
        Text("统计周期", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text("${s.startDate} ~ ${s.endDate}", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary)
        if (s.firstLessonDate.isNotBlank()) {
            Text("首课：${s.firstLessonDate} · 末课：${s.lastLessonDate}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }

    // 核心统计卡片
    GlassCard {
        Text("核心统计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        SummaryStatRow("课时总数", "${s.totalLessons} 节")
        SummaryStatRow("实到课时", "${s.attendedLessons} 节")
        SummaryStatRow("出勤率", "%.0f%%".format(s.attendanceRate * 100))
        SummaryStatRow("平均课时时长", "${s.avgDuration} 分钟")
        SummaryStatRow("平均表现评分", "%.1f/10".format(s.avgPerformance))
        SummaryStatRow("动作完成率", "%.0f%%".format(s.completedExerciseRate * 100))
    }

    // 训练态度分布
    if (s.attitudeDistribution.isNotEmpty()) {
        GlassCard {
            Text("训练态度分布", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            s.attitudeDistribution.forEach { (attitude, count) ->
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(attitude, style = MaterialTheme.typography.bodyMedium)
                    Text("$count 次", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    // 成绩进步对比
    if (s.scoreProgress.isNotEmpty()) {
        GlassCard {
            Text("成绩进步对比", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            s.scoreProgress.forEach { p ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(p.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        val deltaColor = when {
                            p.delta > 0 -> ScoreExcellent
                            p.delta < 0 -> ScoreFail
                            else -> ScorePass
                        }
                        val arrow = if (p.delta > 0) "↑" else if (p.delta < 0) "↓" else "→"
                        Text(
                            "%.1f → %.1f  %s%.1f".format(p.firstScore, p.lastScore, arrow, kotlin.math.abs(p.delta)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = deltaColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    // 自动生成总结文字
    GlassCard(glow = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("阶段总结", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(s.summaryText, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SummaryStatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
