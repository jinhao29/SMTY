package com.shangmentiyu.sportscoach.ui.scoring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangmentiyu.sportscoach.ui.theme.AppTextField
import kotlinx.coroutines.launch
import com.shangmentiyu.sportscoach.core.Standards
import com.shangmentiyu.sportscoach.core.Std
import com.shangmentiyu.sportscoach.core.Scorer
import org.koin.androidx.compose.koinViewModel
import com.shangmentiyu.sportscoach.ui.theme.*
import com.shangmentiyu.sportscoach.ui.theme.FloatingSnackbarHost
import com.shangmentiyu.sportscoach.ui.theme.glassTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoringScreen(
    lessonId: String?,
    onBack: () -> Unit
) {
        val vm: ScoringViewModel = koinViewModel()

    val students by vm.students.collectAsStateWithLifecycle()
    val selectedStudent by vm.selectedStudent.collectAsStateWithLifecycle()
    val standards by vm.standards.collectAsStateWithLifecycle()
    val scoreInputs by vm.scoreInputs.collectAsStateWithLifecycle()
    val scoreResults by vm.scoreResults.collectAsStateWithLifecycle()
    val customProjects by vm.customProjects.collectAsStateWithLifecycle()
    val isCustomMode by vm.isCustomMode.collectAsStateWithLifecycle()
    var showAddCustomDialog by remember { mutableStateOf(false) }

    // Material 3 标准 Snackbar：通过 SnackbarHost 挂载到 Scaffold
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(lessonId) {
        if (lessonId != null) vm.loadLesson(lessonId)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { FloatingSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("数据记录") },
                colors = glassTopAppBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 学员选择
            StyledDropdown(
                selected = selectedStudent,
                options = students,
                optionLabel = { "${it.name} (${it.gender} ${Standards.gradeLabel(it.grade)})" },
                optionIcon = { Icons.Outlined.Person },
                onSelected = { vm.selectStudent(it) },
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                placeholder = "选择学员"
            )

            if (selectedStudent == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("请先选择学员", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                // === v32：学龄前（grade=0）固定自定义录入，学龄后可切换标准/自定义 ===
                val isPreschool = selectedStudent!!.grade == "0" || selectedStudent!!.grade.isBlank()
                val customProjectList = remember(customProjects) { customProjects.toList() }
                Column(modifier = Modifier.weight(1f)) {
                    if (!isPreschool) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EntryModeChip("标准录入", selected = !isCustomMode) { vm.setCustomMode(false) }
                            EntryModeChip("自定义录入", selected = isCustomMode) { vm.setCustomMode(true) }
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 标准体测项目（自定义模式下不显示）
                        if (!isCustomMode) {
                            items(standards, key = { it.name }) { std ->
                                ScoreRow(
                                    std = std,
                                    gender = selectedStudent!!.gender,
                                    inputValue = scoreInputs[std.name] ?: "",
                                    result = scoreResults[std.name],
                                    onValueChange = { vm.updateScore(std.name, it) }
                                )
                            }
                        }

                        // 自定义项目（项目名称/成绩值/单位/备注）
                        items(customProjectList, key = { it.name }) { project ->
                            CustomScoreRow(
                                project = project,
                                inputValue = scoreInputs[project.name] ?: "",
                                onValueChange = { vm.updateScore(project.name, it) },
                                onRemove = { vm.removeCustomProject(project.name) }
                            )
                        }

                        // 添加自定义项目按钮
                        item {
                            AssistChip(
                                onClick = { showAddCustomDialog = true },
                                label = { Text("+ 添加自定义项目") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
                // 保存按钮
                Button(
                    onClick = {
                        vm.save(
                            onSuccess = {
                                // 成功：先弹 Snackbar 提示，延迟 800ms 后返回，让用户看到反馈
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "保存成功",
                                        actionLabel = null,
                                        duration = SnackbarDuration.Short
                                    )
                                }
                                onBack()
                            },
                            onError = { msg ->
                                // 失败：Snackbar 显示错误，停留在当前页
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = msg,
                                        actionLabel = null,
                                        duration = SnackbarDuration.Long
                                    )
                                }
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text("保存成绩")
                }
            }
        }

        // 添加自定义项目对话框
        if (showAddCustomDialog) {
            AddCustomProjectDialog(
                onDismiss = { showAddCustomDialog = false },
                onConfirm = { name, unit, note ->
                    if (vm.addCustomProject(name, unit, note)) {
                        showAddCustomDialog = false
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = "项目名已存在或为空",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ScoreRow(
    std: Std,
    gender: String,
    inputValue: String,
    result: com.shangmentiyu.sportscoach.core.ScoreResult?,
    onValueChange: (String) -> Unit
) {
    val fullValue = if (gender == "男") std.boysFull else std.girlsFull
    val passValue = if (gender == "男") std.boysPass else std.girlsPass

    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(std.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "满分: ${Scorer.formatValue(fullValue, std.unit)}${std.unit}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTextField(
                value = inputValue,
                onValueChange = onValueChange,
                label = { Text("输入成绩(${std.unit})") },
                modifier = Modifier.weight(1f),
                singleLine = true,
)
            Spacer(modifier = Modifier.width(8.dp))
            // 得分和等级
            if (result != null && result.ok && result.score != null) {
                val scoreColor = when (result.grade) {
                    "优秀" -> ScoreExcellent
                    "良好" -> ScoreGood
                    "及格" -> ScorePass
                    else -> ScoreFail
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        String.format("%.1f", result.score),
                        style = MaterialTheme.typography.titleLarge,
                        color = scoreColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(result.grade, style = MaterialTheme.typography.bodySmall, color = scoreColor)
                }
            } else if (result != null && !result.ok) {
                Text(result.msg, style = MaterialTheme.typography.bodySmall, color = ScoreFail)
            }
        }
    }
}

/**
 * 自定义项目成绩行：项目名称 + 单位 + 备注 + 成绩值，仅记录原值不计算得分。
 * 支持移除该自定义项目。
 */
@Composable
internal fun CustomScoreRow(
    project: ScoringViewModel.CustomProject,
    inputValue: String,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(project.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (project.unit.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        project.unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            Text(
                "自定义",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (project.note.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                project.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTextField(
                value = inputValue,
                onValueChange = onValueChange,
                label = { Text("成绩值") },
                modifier = Modifier.weight(1f),
                singleLine = true,
)
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "移除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** 成绩录入模式切换胶囊：标准录入 / 自定义录入 */
@Composable
internal fun RowScope.EntryModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        modifier = Modifier.weight(1f)
    )
}

/**
 * 添加自定义项目对话框：项目名称 + 单位 + 备注自由输入。
 */
@Composable
internal fun AddCustomProjectDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, unit: String, note: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "添加自定义项目",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("项目名称") },
                    singleLine = true,
modifier = Modifier.fillMaxWidth()
)
                AppTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("单位（如：次/秒/米，可空）") },
                    singleLine = true,
modifier = Modifier.fillMaxWidth()
)
                AppTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    singleLine = true,
modifier = Modifier.fillMaxWidth()
)
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onConfirm(name, unit, note) }
            ) { Text("添加") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
