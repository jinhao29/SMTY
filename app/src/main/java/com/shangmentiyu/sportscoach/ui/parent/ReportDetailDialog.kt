package com.shangmentiyu.sportscoach.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shangmentiyu.sportscoach.core.JsonSafe
import com.shangmentiyu.sportscoach.data.model.ParentReport
import com.shangmentiyu.sportscoach.ui.theme.GlowCyan

/**
 * 报告详情对话框：展示完整报告内容并提供分享按钮。
 *
 * 安全性：报告 JSON 解析走 [JsonSafe] 兜底，损坏时降级展示提示文本，
 * 不会因为历史脏数据导致对话框打开即崩溃。
 */
@Composable
fun ReportDetailDialog(
    report: ParentReport,
    onDismiss: () -> Unit,
    onMarkShared: () -> Unit
) {
    val context = LocalContext.current
    // 解析失败时降级为空对象，后续 optXxx 全部返回默认值，不会崩溃
    val obj = JsonSafe.parseObject(report.content)

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
        title = "${report.studentName} · ${report.reportType}",
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (obj == null) {
                    // 报告内容损坏时降级提示，不崩溃
                    Text(
                        "（报告内容已损坏，无法解析详情）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        "周期：${obj.optString("period")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(12.dp))

                    // 训练统计
                    SectionTitle("训练统计")
                    val stats = obj.optJSONObject("stats")
                    if (stats != null) {
                        StatRow("课时数", "${stats.optInt("lessonCount")} 节")
                        StatRow("总时长", "${stats.optInt("totalMinutes")} 分钟")
                        StatRow("平均表现", "${stats.optString("avgPerformance")} 分")
                    }
                    Spacer(Modifier.height(12.dp))

                    // 里程碑
                    val milestones = obj.optJSONArray("milestones")
                    if (milestones != null && milestones.length() > 0) {
                        SectionTitle("里程碑")
                        for (i in 0 until milestones.length()) {
                            // 单条里程碑解析失败时跳过
                            val m = milestones.optJSONObject(i) ?: continue
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Flag,
                                    contentDescription = null,
                                    tint = GlowCyan,
                                    modifier = Modifier.width(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        m.optString("title"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${m.optString("desc")}（${m.optString("date")}）",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // 成绩变化
                    val changes = obj.optJSONArray("scoreChanges")
                    if (changes != null && changes.length() > 0) {
                        SectionTitle("成绩变化")
                        for (i in 0 until changes.length()) {
                            // 单条变化解析失败时跳过
                            val c = changes.optJSONObject(i) ?: continue
                            StatRow(
                                c.optString("project"),
                                "${c.optDouble("before", 0.0).toInt()} → ${c.optDouble("after", 0.0).toInt()} (${c.optString("trend")})"
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // 教练寄语
                    SectionTitle("教练寄语")
                    Text(
                        obj.optString("coachComment"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))

                    // 建议
                    SectionTitle("下阶段建议")
                    Text(
                        obj.optString("suggestion"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val text = ReportShareHelper.toTextSummary(report)
                            ReportShareHelper.shareText(
                                context,
                                "${report.studentName} ${report.reportType}",
                                text
                            )
                            onMarkShared()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("分享给家长")
                    }
                    if (!report.shared) {
                        OutlinedButton(
                            onClick = onMarkShared,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("标记已分享")
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary  // 活力蓝紫（替代 primary 默认色，统一全局活力风格）
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
