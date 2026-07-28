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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.shangmentiyu.sportscoach.core.Standards
import com.shangmentiyu.sportscoach.core.Std
import com.shangmentiyu.sportscoach.core.Scorer
import com.shangmentiyu.sportscoach.ui.AppViewModelFactory
import com.shangmentiyu.sportscoach.ui.theme.*
import com.shangmentiyu.sportscoach.ui.theme.glassTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoringScreen(
    lessonId: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val vm: ScoringViewModel = viewModel(factory = AppViewModelFactory(context.applicationContext as android.app.Application))

    val students by vm.students.collectAsState()
    val selectedStudent by vm.selectedStudent.collectAsState()
    val standards by vm.standards.collectAsState()
    val scoreInputs by vm.scoreInputs.collectAsState()
    val scoreResults by vm.scoreResults.collectAsState()
    val customProjects by vm.customProjects.collectAsState()
    var studentExpanded by remember { mutableStateOf(false) }
    var showAddCustomDialog by remember { mutableStateOf(false) }

    // Material 3 标准 Snackbar：通过 SnackbarHost 挂载到 Scaffold
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(lessonId) {
        if (lessonId != null) vm.loadLesson(lessonId)
    }

    Scaffold(
        containerColor = Color(0xFF121212),  // v38：全局深色背景
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
            ExposedDropdownMenuBox(
                expanded = studentExpanded,
                onExpandedChange = { studentExpanded = !studentExpanded },
                modifier = Modifier.padding(16.dp)
            ) {
                OutlinedTextField(
                    value = selectedStudent?.let { "${it.name} (${it.gender} ${Standards.gradeLabel(it.grade)})" } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("选择学员") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = studentExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(
                        androidx.compose.material3.MenuAnchorType.PrimaryNotEditable,
                        enabled = true
                    )
                )
                ExposedDropdownMenu(expanded = studentExpanded, onDismissRequest = { studentExpanded = false }) {
                    students.forEach { student ->
                        DropdownMenuItem(
                            text = { Text("${student.name} (${student.gender} ${Standards.gradeLabel(student.grade)})") },
                            onClick = {
                                vm.selectStudent(student)
                                studentExpanded = false
                            }
                        )
                    }
                }
            }

            if (selectedStudent == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("请先选择学员", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 标准体测项目
                    items(standards, key = { it.name }) { std ->
                        ScoreRow(
                            std = std,
                            gender = selectedStudent!!.gender,
                            inputValue = scoreInputs[std.name] ?: "",
                            result = scoreResults[std.name],
                            onValueChange = { vm.updateScore(std.name, it) }
                        )
                    }

                    // 自定义项目（customProjects 为 Set，转 List 以适配 items）
                    items(customProjects.toList(), key = { it }) { name ->
                        CustomScoreRow(
                            name = name,
                            inputValue = scoreInputs[name] ?: "",
                            onValueChange = { vm.updateScore(name, it) },
                            onRemove = { vm.removeCustomProject(name) }
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
                onConfirm = { name ->
                    if (vm.addCustomProject(name)) {
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
            OutlinedTextField(
                value = inputValue,
                onValueChange = onValueChange,
                label = { Text("输入成绩(${std.unit})") },
                modifier = Modifier.weight(1f),
                singleLine = true
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
 * 自定义项目成绩行：不显示满分参考与得分计算，仅记录用户输入的原值。
 * 支持移除该自定义项目。
 */
@Composable
private fun CustomScoreRow(
    name: String,
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
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "自定义",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputValue,
                onValueChange = onValueChange,
                label = { Text("输入成绩") },
                modifier = Modifier.weight(1f),
                singleLine = true
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

/**
 * 添加自定义项目对话框：输入不在体测标准中的项目名。
 */
@Composable
private fun AddCustomProjectDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "添加自定义项目",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "可录入不在体测标准中的项目（如立定跳远、引体向上等），仅记录成绩原值，不计算得分。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("项目名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onConfirm(name) }
            ) { Text("添加") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
