package com.shangmentiyu.sportscoach.ui.score

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shangmentiyu.sportscoach.core.ScoreResult
import com.shangmentiyu.sportscoach.core.Scorer
import com.shangmentiyu.sportscoach.core.Std
import com.shangmentiyu.sportscoach.core.Standards
import com.shangmentiyu.sportscoach.ui.AppViewModelFactory
import com.shangmentiyu.sportscoach.ui.scoring.ScoringViewModel
import com.shangmentiyu.sportscoach.ui.theme.FloatingSnackbarHost
import com.shangmentiyu.sportscoach.ui.theme.GlassCard
import com.shangmentiyu.sportscoach.ui.theme.ScoreExcellent
import com.shangmentiyu.sportscoach.ui.theme.ScoreFail
import com.shangmentiyu.sportscoach.ui.theme.ScoreGood
import com.shangmentiyu.sportscoach.ui.theme.ScorePass
import kotlinx.coroutines.launch

/**
 * 录入成绩 Tab：学员选择 + 体测项目成绩输入 + 保存。
 *
 * 复用 ScoringViewModel 管理学员列表、评分标准与成绩输入。
 * 无关联课时场景下，ViewModel.save() 会自动创建新课时记录。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreInputTab() {
    val context = LocalContext.current
    val vm: ScoringViewModel = viewModel(
        factory = AppViewModelFactory(context.applicationContext as android.app.Application)
    )

    val students by vm.students.collectAsStateWithLifecycle()
    val selectedStudent by vm.selectedStudent.collectAsStateWithLifecycle()
    val standards by vm.standards.collectAsStateWithLifecycle()
    val scoreInputs by vm.scoreInputs.collectAsStateWithLifecycle()
    val scoreResults by vm.scoreResults.collectAsStateWithLifecycle()
    var studentExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 学员选择器
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
                        MenuAnchorType.PrimaryNotEditable,
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
                    items(standards, key = { it.name }) { std ->
                        ScoreInputRow(
                            std = std,
                            gender = selectedStudent!!.gender,
                            inputValue = scoreInputs[std.name] ?: "",
                            result = scoreResults[std.name],
                            onValueChange = { vm.updateScore(std.name, it) }
                        )
                    }
                }
                Button(
                    onClick = {
                        vm.save(
                            onSuccess = {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "保存成功",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            },
                            onError = { msg ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = msg,
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
        FloatingSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * 单项成绩输入行：项目名 + 满分参考 + 输入框 + 得分显示。
 */
@Composable
private fun ScoreInputRow(
    std: Std,
    gender: String,
    inputValue: String,
    result: ScoreResult?,
    onValueChange: (String) -> Unit
) {
    val fullValue = if (gender == "男") std.boysFull else std.girlsFull
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