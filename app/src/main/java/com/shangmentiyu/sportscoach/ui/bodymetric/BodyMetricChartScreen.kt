package com.shangmentiyu.sportscoach.ui.bodymetric

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shangmentiyu.sportscoach.data.model.BodyMetricHistory
import com.shangmentiyu.sportscoach.ui.AppViewModelFactory
import com.shangmentiyu.sportscoach.ui.theme.GlassCard
import com.shangmentiyu.sportscoach.ui.theme.ScoreExcellent
import com.shangmentiyu.sportscoach.ui.theme.ScoreFail
import com.shangmentiyu.sportscoach.ui.theme.glassTopAppBarColors

/**
 * 体型变化曲线页面：展示学员身高/体重/BMI 的历史变化。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyMetricChartScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: BodyMetricChartViewModel = viewModel(
        factory = AppViewModelFactory(context.applicationContext as android.app.Application)
    )

    val students by vm.students.collectAsState()
    val selectedStudent by vm.selectedStudent.collectAsState()
    val history by vm.history.collectAsState()
    val delta by vm.delta.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("体型变化曲线") },
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
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
            }
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
                    Text("暂无学员", color = MaterialTheme.colorScheme.outline)
                } else {
                    OutlinedTextField(
                        value = selectedStudent,
                        onValueChange = { vm.selectStudent(it) },
                        label = { Text("学员姓名") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    students.take(8).forEach { name ->
                        Text("· $name", style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp, top = 2.dp))
                    }
                }
            }

            if (selectedStudent.isNotBlank()) {
                // 首末对比卡片
                if (history.size >= 2 && delta != null) {
                    DeltaCard(history.first(), history.last(), delta!!)
                } else if (history.size == 1) {
                    GlassCard {
                        Text("当前记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        val r = history.first()
                        Text("身高：${r.heightCm} cm")
                        Text("体重：${r.weightKg} kg")
                        Text("BMI：${"%.1f".format(r.bmi)}")
                        Text("测量日期：${r.date}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("再添加一次测量后可查看变化曲线", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    GlassCard {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp),
                            contentAlignment = Alignment.Center) {
                            Text("暂无测量记录，点击右下角 + 添加", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }

                // BMI 曲线
                if (history.size >= 2) {
                    MetricChartCard(history = history, metric = "BMI",
                        values = history.map { it.bmi },
                        color = MaterialTheme.colorScheme.primary)
                    MetricChartCard(history = history, metric = "体重(kg)",
                        values = history.map { it.weightKg },
                        color = ScoreFail)
                    MetricChartCard(history = history, metric = "身高(cm)",
                        values = history.map { it.heightCm.toFloat() },
                        color = ScoreExcellent)
                }

                // 历史记录列表
                if (history.isNotEmpty()) {
                    GlassCard {
                        Text("历史记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        history.reversed().forEach { r ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(r.date, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        Text("身高 ${r.heightCm}cm · 体重 ${r.weightKg}kg · BMI ${"%.1f".format(r.bmi)}",
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        if (r.note.isNotBlank()) {
                                            Text(r.note, style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                    IconButton(onClick = { vm.deleteRecord(r.id) }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = "删除",
                                            tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddRecordDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { h, w, n -> vm.addRecord(h, w, n); showAddDialog = false }
        )
    }
}

@Composable
private fun DeltaCard(
    first: BodyMetricHistory,
    last: BodyMetricHistory,
    delta: com.shangmentiyu.sportscoach.data.repo.BodyMetricRepository.MetricDelta
) {
    GlassCard(glow = true) {
        Text("体型变化对比", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text("${first.date} → ${last.date}（${delta.daysBetween}天）",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(8.dp))

        DeltaRow("身高", "${first.heightCm}cm", "${last.heightCm}cm",
            delta.heightDelta.toFloat(), "cm", positiveGood = true)
        DeltaRow("体重", "${first.weightKg}kg", "${"%.1f".format(last.weightKg)}kg",
            delta.weightDelta, "kg", positiveGood = false)
        DeltaRow("BMI", "%.1f".format(first.bmi), "%.1f".format(last.bmi),
            delta.bmiDelta, "", positiveGood = false)
    }
}

@Composable
private fun DeltaRow(
    label: String, first: String, last: String,
    delta: Float, unit: String, positiveGood: Boolean
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text("$first → $last",
            style = MaterialTheme.typography.bodyMedium)
        val color = when {
            delta == 0f -> MaterialTheme.colorScheme.outline
            (delta > 0) == positiveGood -> ScoreExcellent
            else -> ScoreFail
        }
        val arrow = if (delta > 0) "↑" else if (delta < 0) "↓" else "→"
        Text("$arrow ${"%.1f".format(kotlin.math.abs(delta))}$unit",
            style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MetricChartCard(
    history: List<BodyMetricHistory>,
    metric: String,
    values: List<Float>,
    color: Color
) {
    GlassCard {
        Text(metric, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(160.dp)
        ) {
            LineChartCanvas(values = values, dates = history.map { it.date }, color = color)
        }
    }
}

@Composable
private fun LineChartCanvas(values: List<Float>, dates: List<String>, color: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max()
        val range = (max - min).coerceAtLeast(0.01f)
        val padX = 40f
        val padY = 24f
        val w = size.width - padX * 2
        val h = size.height - padY * 2
        val stepX = w / (values.size - 1)

        // 路径
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = padX + i * stepX
            val y = padY + h - ((v - min) / range) * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = color, style = Stroke(width = 3f))

        // 数据点 + 标签
        values.forEachIndexed { i, v ->
            val x = padX + i * stepX
            val y = padY + h - ((v - min) / range) * h
            drawCircle(color = color, radius = 5f, center = Offset(x, y))
            // 数值标签
            drawContext.canvas.nativeCanvas.drawText(
                "%.1f".format(v),
                x - 16f, y - 10f,
                android.graphics.Paint().apply {
                    this.color = android.graphics.Color.parseColor("#666666")
                    textSize = 24f
                }
            )
            // 日期标签（仅首末）
            if (i == 0 || i == values.size - 1) {
                drawContext.canvas.nativeCanvas.drawText(
                    dates[i].substring(5),  // MM-DD
                    x - 20f, size.height - 4f,
                    android.graphics.Paint().apply {
                        this.color = android.graphics.Color.parseColor("#999999")
                        textSize = 20f
                    }
                )
            }
        }
    }
}

@Composable
private fun AddRecordDialog(
    onDismiss: () -> Unit,
    onAdd: (heightCm: Int, weightKg: Float, note: String) -> Unit
) {
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "新增测量记录",
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = height, onValueChange = { height = it.filter { c -> c.isDigit() } },
                    label = { Text("身高 (cm)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("体重 (kg)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val h = height.toIntOrNull() ?: 0
                val w = weight.toFloatOrNull() ?: 0f
                if (h > 0 || w > 0f) onAdd(h, w, note)
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
