package com.shangmentiyu.sportscoach.ui.trainingcycle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shangmentiyu.sportscoach.data.model.TrainingCycle
import com.shangmentiyu.sportscoach.data.model.WeeklyPlan
import org.koin.androidx.compose.koinViewModel
import com.shangmentiyu.sportscoach.ui.theme.GlassCard
import com.shangmentiyu.sportscoach.ui.theme.ScoreExcellent
import com.shangmentiyu.sportscoach.ui.theme.glassTopAppBarColors
import java.util.Locale
import com.shangmentiyu.sportscoach.ui.theme.AppTextFieldShape
import com.shangmentiyu.sportscoach.ui.theme.appTextFieldColors

/**
 * 周期训练计划页面：列出学员的周期、查看周计划、创建新周期。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingCycleScreen(onBack: () -> Unit) {
        val vm: TrainingCycleViewModel = koinViewModel()

    val students by vm.students.collectAsStateWithLifecycle()
    val selectedStudent by vm.selectedStudent.collectAsStateWithLifecycle()
    val cycles by vm.cycles.collectAsStateWithLifecycle()
    val currentCycle by vm.currentCycle.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("周期训练计划") },
                colors = glassTopAppBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedStudent.isNotBlank()) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = appPrimary(),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "新建周期")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 学员选择：下拉框选择 + 手动输入（二合一）
            GlassCard {
                Text("选择学员", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                if (students.isEmpty()) {
                    Text("暂无学员", color = MaterialTheme.colorScheme.outline)
                } else {
                    // 可编辑下拉框：既支持从已有学员中选择，也支持手动输入新姓名
                    var studentExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = studentExpanded,
                        onExpandedChange = { studentExpanded = !studentExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedStudent,
                            onValueChange = { vm.selectStudent(it) },
                            label = { Text("学员姓名（可选择或输入）") },
                            singleLine = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(studentExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true),

                         shape = AppTextFieldShape,
                         colors = appTextFieldColors(),)
                        DropdownMenu(
                            expanded = studentExpanded,
                            onDismissRequest = { studentExpanded = false }
                        ) {
                            students.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        vm.selectStudent(name)
                                        studentExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 当前查看的周期详情
            currentCycle?.let { cycle ->
                CycleDetailCard(
                    cycle = cycle,
                    onUpdateWeekly = vm::updateWeeklyPlan,
                    onMarkCompleted = vm::markCompleted,
                    onClose = { vm.closeCycle() },
                    onDelete = { vm.deleteCycle(cycle.id) }
                )
            }

            // 周期列表
            if (selectedStudent.isNotBlank() && currentCycle == null) {
                Text("周期列表", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                if (cycles.isEmpty()) {
                    GlassCard {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp),
                            contentAlignment = Alignment.Center) {
                            Text("暂无周期，点击右下角 + 创建", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                } else {
                    cycles.forEach { cycle ->
                        CycleListCard(cycle = cycle, onClick = { vm.openCycle(cycle) })
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateCycleDialog(
            studentName = selectedStudent,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, goal, weeks, startDate ->
                vm.createCycle(selectedStudent, name, goal, weeks, startDate)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun CycleListCard(cycle: TrainingCycle, onClick: () -> Unit) {
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
                Text(cycle.name, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (cycle.status == "已完成") {
                    Text("已完成", style = MaterialTheme.typography.labelSmall,
                        color = ScoreExcellent)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("${cycle.totalWeeks}周 · ${cycle.startDate} ~ ${cycle.endDate}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            if (cycle.goal.isNotBlank()) {
                Text("目标：${cycle.goal}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline)
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("查看周计划", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun CycleDetailCard(
    cycle: TrainingCycle,
    onUpdateWeekly: (Int, String, String, String) -> Unit,
    onMarkCompleted: () -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit
) {
    GlassCard(glow = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(cycle.name, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("关闭") }
        }
        Text("${cycle.startDate} ~ ${cycle.endDate} · ${cycle.totalWeeks}周 · 状态：${cycle.status}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        if (cycle.goal.isNotBlank()) {
            Text("周期目标：${cycle.goal}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("周计划", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        val plans = cycle.parseWeeklyPlans()
        plans.forEach { p ->
            WeeklyPlanEditor(p, onUpdate = onUpdateWeekly)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (cycle.status != "已完成") {
                TextButton(
                    onClick = onMarkCompleted,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("标记完成", color = MaterialTheme.colorScheme.primary)
                }
            }
            TextButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp).height(16.dp),
                    tint = MaterialTheme.colorScheme.error)
                Text("删除周期", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun WeeklyPlanEditor(
    plan: WeeklyPlan,
    onUpdate: (Int, String, String, String) -> Unit
) {
    var title by remember(plan.weekIndex, plan.title) { mutableStateOf(plan.title) }
    var goal by remember(plan.weekIndex, plan.goal) { mutableStateOf(plan.goal) }
    var focus by remember(plan.weekIndex, plan.focus) { mutableStateOf(plan.focus) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("第${plan.weekIndex}周 标题") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,

             shape = AppTextFieldShape,
             colors = appTextFieldColors(),)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = goal, onValueChange = { goal = it },
                label = { Text("本周目标") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,

             shape = AppTextFieldShape,
             colors = appTextFieldColors(),)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = focus, onValueChange = { focus = it },
                label = { Text("训练重点") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,

             shape = AppTextFieldShape,
             colors = appTextFieldColors(),)
            Spacer(modifier = Modifier.height(6.dp))
            TextButton(onClick = { onUpdate(plan.weekIndex, title, goal, focus) }) {
                Text("保存本周修改")
            }
        }
    }
}

@Composable
private fun CreateCycleDialog(
    studentName: String,
    onDismiss: () -> Unit,
    onCreate: (name: String, goal: String, weeks: Int, startDate: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    var weeks by remember { mutableStateOf("4") }
    val today = remember {
        // 线程安全：基于 [java.time.LocalDate] 替代 [SimpleDateFormat]
        java.time.LocalDate.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()))
    }
    var startDate by remember { mutableStateOf(today) }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "新建训练周期",
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("周期名称（如：暑期4周体能强化）") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,

                 shape = AppTextFieldShape,
                 colors = appTextFieldColors(),)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = goal, onValueChange = { goal = it },
                    label = { Text("周期目标") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,

                 shape = AppTextFieldShape,
                 colors = appTextFieldColors(),)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weeks, onValueChange = { weeks = it.filter { c -> c.isDigit() } },
                        label = { Text("总周数") },
                        modifier = Modifier.weight(1f), singleLine = true,

                     shape = AppTextFieldShape,
                     colors = appTextFieldColors(),)
                    OutlinedTextField(
                        value = startDate, onValueChange = { startDate = it },
                        label = { Text("开始日期") },
                        modifier = Modifier.weight(1f), singleLine = true,

                     shape = AppTextFieldShape,
                     colors = appTextFieldColors(),)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val w = weeks.toIntOrNull()?.coerceIn(1, 52) ?: 4
                    if (name.isNotBlank()) onCreate(name, goal, w, startDate)
                }
            ) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
