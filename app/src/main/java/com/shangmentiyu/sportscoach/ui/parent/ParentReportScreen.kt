package com.shangmentiyu.sportscoach.ui.parent

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shangmentiyu.sportscoach.core.ReportGenerator
import com.shangmentiyu.sportscoach.data.model.ParentReport
import com.shangmentiyu.sportscoach.ui.AppViewModelFactory
import com.shangmentiyu.sportscoach.ui.theme.GlassCard
import com.shangmentiyu.sportscoach.ui.theme.GlassSectionTitle
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.glassTopAppBarColors

/**
 * 家长服务主页面：生成周报/月报，查看历史报告并一键分享给家长。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentReportScreen(onBack: () -> Unit) {
    val vm: ParentReportViewModel = viewModel(
        factory = AppViewModelFactory(LocalContextProvider())
    )
    val students by vm.students.collectAsState()
    val reports by vm.reports.collectAsState()
    val selectedStudent by vm.selectedStudent.collectAsState()
    val viewingReport by vm.viewingReport.collectAsState()
    val toast by vm.toast.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(toast) {
        toast?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeToast()
        }
    }

    var showGenerateDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color(0xFF121212),  // v38：全局深色背景
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("家长服务", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showGenerateDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "生成报告")
                    }
                },
                colors = glassTopAppBarColors()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .padding(horizontal = Spacing.screenH, vertical = Spacing.screenV)
        ) {
            // 学员筛选
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                FilterChip(
                    selected = selectedStudent == null,
                    onClick = { vm.selectStudent(null) },
                    label = { Text("全部") }
                )
                students.take(5).forEach { s ->
                    FilterChip(
                        selected = selectedStudent == s.name,
                        onClick = { vm.selectStudent(s.name) },
                        label = { Text(s.name) }
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xl))

            GlassSectionTitle("已生成报告 · 点击查看详情")
            Spacer(Modifier.height(Spacing.md))

            // 用 remember 缓存过滤结果，避免每次重组都重新 filter
            val filtered = remember(reports, selectedStudent) {
                vm.filteredReports(reports)
            }
            if (filtered.isEmpty()) {
                Text(
                    "暂无报告，点击右上角 + 生成",
                    style = MaterialTheme.typography.bodyMedium,
                    color = appOnSurface().copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 32.dp).align(Alignment.CenterHorizontally)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    items(filtered, key = { it.id }) { report ->
                        ReportCard(
                            report = report,
                            onClick = { vm.viewReport(report) }
                        )
                    }
                }
            }
        }
    }

    // 生成报告对话框
    if (showGenerateDialog) {
        GenerateReportDialog(
            students = students.map { it.name },
            onDismiss = { showGenerateDialog = false },
            onGenerate = { name, type ->
                vm.generateReport(name, type)
                showGenerateDialog = false
            }
        )
    }

    // 详情对话框
    viewingReport?.let { r ->
        ReportDetailDialog(
            report = r,
            onDismiss = { vm.clearViewing() },
            onMarkShared = { vm.markShared(r.id) }
        )
    }
}

@Composable
private fun ReportCard(report: ParentReport, onClick: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        report.studentName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        report.reportType,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (report.hasMilestones) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Outlined.Flag,
                            contentDescription = "含里程碑",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(16.dp)
                        )
                    }
                }
                Text(
                    report.startDate + " ~ " + report.endDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                if (report.shared) {
                    Text(
                        "已分享",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            TextButton(onClick = onClick, shape = RoundedCornerShape(8.dp)) {
                Text("查看", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenerateReportDialog(
    students: List<String>,
    onDismiss: () -> Unit,
    onGenerate: (String, String) -> Unit
) {
    var selectedName by remember { mutableStateOf(students.firstOrNull() ?: "") }
    var selectedType by remember { mutableStateOf(ReportGenerator.TYPE_WEEKLY) }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (selectedName.isNotBlank()) onGenerate(selectedName, selectedType) }
            ) { Text("生成") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = "生成家长报告",
        content = {
            Column {
                OutlinedTextField(
                    value = selectedName,
                    onValueChange = { selectedName = it },
                    label = { Text("学员姓名") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedType == ReportGenerator.TYPE_WEEKLY,
                        onClick = { selectedType = ReportGenerator.TYPE_WEEKLY },
                        label = { Text("周报（近7天）") }
                    )
                    FilterChip(
                        selected = selectedType == ReportGenerator.TYPE_MONTHLY,
                        onClick = { selectedType = ReportGenerator.TYPE_MONTHLY },
                        label = { Text("月报（近30天）") }
                    )
                }
            }
        }
    )
}

/** 简化 LocalContext 获取 */
@Composable
private fun LocalContextProvider(): Application {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return ctx.applicationContext as Application
}
