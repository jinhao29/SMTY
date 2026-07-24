package com.shangmentiyu.sportscoach.ui.coachreport

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.ui.AppViewModelFactory
import com.shangmentiyu.sportscoach.ui.theme.GlassCard
import com.shangmentiyu.sportscoach.ui.theme.ScoreExcellent
import com.shangmentiyu.sportscoach.ui.theme.ScoreFail
import com.shangmentiyu.sportscoach.ui.theme.ScorePass
import com.shangmentiyu.sportscoach.ui.theme.glassTopAppBarColors

/**
 * 教练日报页面：按日期查看当日所有签到、消课与训练效果评估。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachDailyReportScreen(
    onBack: () -> Unit,
    onOpenLesson: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val vm: CoachDailyReportViewModel = viewModel(
        factory = AppViewModelFactory(context.applicationContext as android.app.Application)
    )

    val selectedDate by vm.selectedDate.collectAsState()
    val selectedStudent by vm.selectedStudent.collectAsState()
    val students by vm.students.collectAsState()
    val lessons by vm.lessons.collectAsState()
    val stats by vm.stats.collectAsState()
    var studentPickerExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("教练日报") },
                colors = glassTopAppBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            // 学员选择器（iOS Settings 风格下拉）
            GlassCard {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("筛选学员", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        androidx.compose.material3.TextButton(onClick = {
                            studentPickerExpanded = !studentPickerExpanded
                        }) {
                            Text(selectedStudent ?: "全部学员",
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            Icon(if (studentPickerExpanded) Icons.Filled.KeyboardArrowUp
                                 else Icons.Filled.KeyboardArrowDown,
                                contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (studentPickerExpanded) {
                        Spacer(modifier = Modifier.height(4.dp))
                        students.forEach { s ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable {
                                        vm.selectStudent(
                                            if (selectedStudent == s.name) null else s.name
                                        )
                                        studentPickerExpanded = false
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(s.name, modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (selectedStudent == s.name) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface)
                                if (selectedStudent == s.name) {
                                    Icon(Icons.Filled.Check, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    vm.selectStudent(null)
                                    studentPickerExpanded = false
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("显示全部学员", modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selectedStudent == null) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface)
                            if (selectedStudent == null) {
                                Icon(Icons.Filled.Check, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // 日期切换
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { vm.previousDay() }) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "前一天")
                    }
                    Text(selectedDate, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { vm.nextDay() }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "后一天")
                    }
                }
                OutlinedButton(onClick = { vm.goToday() }, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)) { Text("回到今天") }
            }

            // 日报概览
            GlassCard {
                Text("日报概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                StatRow("当日课时", "${stats.totalLessons} 节")
                StatRow("消课记录", "${stats.consumedLessons} 节")
                StatRow("服务学员", "${stats.uniqueStudents} 人")
                StatRow("平均表现", "%.1f/10".format(stats.avgPerformance))
                StatRow("平均时长", "${stats.avgDuration} 分钟")
            }

            // 出勤分布
            if (stats.attendanceDistribution.isNotEmpty()) {
                GlassCard {
                    Text("出勤分布", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    stats.attendanceDistribution.forEach { (a, n) ->
                        StatRow(a, "$n 节")
                    }
                }
            }

            // 每节课训练效果评估
            if (lessons.isEmpty()) {
                GlassCard {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center) {
                        Text("当日无课时记录", color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                Text("训练效果评估", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                lessons.forEach { lesson ->
                    val effect = vm.evaluateLesson(lesson)
                    LessonEffectCard(lesson = lesson, effect = effect,
                        onClick = { onOpenLesson(lesson.id) })
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LessonEffectCard(
    lesson: Lesson,
    effect: CoachDailyReportViewModel.TrainingEffect,
    onClick: () -> Unit
) {
    val levelColor = when (effect.level) {
        "优秀" -> ScoreExcellent
        "良好" -> MaterialTheme.colorScheme.primary
        "一般" -> ScorePass
        else -> ScoreFail
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(lesson.studentName, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(effect.level, style = MaterialTheme.typography.labelSmall,
                    color = levelColor, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("${lesson.time} · ${lesson.lessonType} · ${lesson.duration}分钟",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("动作完成", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline)
                Text("${effect.doneExercises}/${effect.totalExercises}（${"%.0f%%".format(effect.completionRate * 100)}）",
                    style = MaterialTheme.typography.bodySmall)
            }
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("表现评分", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline)
                Text("${effect.performance}/10", style = MaterialTheme.typography.bodySmall)
            }
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("综合得分", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline)
                Text("%.2f".format(effect.overallScore),
                    style = MaterialTheme.typography.bodySmall,
                    color = levelColor, fontWeight = FontWeight.Medium)
            }

            if (lesson.summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(lesson.summary, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2)
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)) { Text("查看课时详情") }
        }
    }
}
