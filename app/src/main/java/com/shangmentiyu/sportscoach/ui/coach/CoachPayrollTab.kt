package com.shangmentiyu.sportscoach.ui.coach

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangmentiyu.sportscoach.data.model.CoachPayout
import com.shangmentiyu.sportscoach.data.model.CoachPayoutRequest
import com.shangmentiyu.sportscoach.data.model.CoachRole
import com.shangmentiyu.sportscoach.ui.theme.AppTextField
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.StyledDropdown
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import kotlinx.coroutines.launch

/**
 * Tab 4：薪资与分成。
 * - 月份切换 + 一键结算（需录入机构当月净利润，作为一级合伙人分红基数）
 * - 当月结算单列表：底薪 / 课时费 / 分成 / 合计，可标记发放状态
 * - 提现申请 + 审核流（待审核 → 通过 / 驳回）
 * - 报表导出（系统分享面板，纯文本）
 */
@Composable
internal fun CoachPayrollTab(viewModel: CoachManageViewModel) {
    val month by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val payouts by viewModel.payouts.collectAsStateWithLifecycle()
    val requests by viewModel.requests.collectAsStateWithLifecycle()
    val allCoaches by viewModel.allCoaches.collectAsStateWithLifecycle()

    // 提现候选：在职 + 休假（离职不可发起）
    val withdrawCandidates = remember(allCoaches) { allCoaches.filter { it.status != "离职" } }

    var showSettle by remember { mutableStateOf(false) }
    var showWithdraw by remember { mutableStateOf(false) }

    val period = viewModel.periodOf(month)
    val monthPayouts = remember(payouts, period) {
        payouts.filter { it.periodStart == period.first && it.periodEnd == period.second }
            .sortedByDescending { it.totalAmount }
    }
    val pendingRequests = remember(requests) {
        requests.filter { it.status == CoachPayoutRequest.STATUS_PENDING }
    }
    val handledRequests = remember(requests) {
        requests.filter { it.status != CoachPayoutRequest.STATUS_PENDING }
            .sortedByDescending { it.appliedAt }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Spacing.screenH, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        // 月份切换 + 结算入口
        item {
            SectionCard(
                title = "薪资结算",
                trailing = {
                    Row {
                        IconButton(onClick = { viewModel.shiftMonth(-1) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "上个月", tint = appPrimary()
                            )
                        }
                        Text(
                            CoachManageViewModel.monthLabel(month),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                        IconButton(onClick = { viewModel.shiftMonth(1) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "下个月", tint = appPrimary()
                            )
                        }
                    }
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    // 统计卡：三列均分，数值醒目、标签次之
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        PayrollStatCard("结算人数（人）", "${monthPayouts.size}", Modifier.weight(1f))
                        PayrollStatCard("合计应发（元）", fmtMoney(monthPayouts.sumOf { it.totalAmount }), Modifier.weight(1f))
                        PayrollStatCard("已发放（人）", "${monthPayouts.count { it.status == CoachPayout.STATUS_PAID }}", Modifier.weight(1f))
                    }
                    // 主操作：两列均分
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { showSettle = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("结算本月")
                        }
                        OutlinedButton(onClick = { showWithdraw = true }, modifier = Modifier.weight(1f)) {
                            Text("提现申请", color = appPrimary())
                        }
                    }
                    // 次要操作：单独一行降低视觉权重
                    Row { ShareReportButton(viewModel, month) }
                }
            }
        }

        // 当月结算单
        item {
            SectionCard(title = "结算单 · ${CoachManageViewModel.monthLabel(month)}") {
                if (monthPayouts.isEmpty()) {
                    Text(
                        "本月尚未结算。点击「结算本月」生成在职/休假教练的薪资结算单。",
                        style = MaterialTheme.typography.bodySmall,
                        color = appOnSurfaceVariant()
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        monthPayouts.forEach { payout ->
                            PayoutRow(
                                payout = payout,
                                roleLabel = allCoaches.find { it.name == payout.coachName }
                                    ?.let { CoachRole.label(it.role) } ?: "",
                                onToggleStatus = {
                                    val next = if (payout.status == CoachPayout.STATUS_PAID)
                                        CoachPayout.STATUS_PENDING else CoachPayout.STATUS_PAID
                                    viewModel.markPayoutStatus(payout.id, next)
                                }
                            )
                        }
                    }
                }
            }
        }

        // 分割线：区分「结算单」与「提现审核」两个功能区块
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) }

        // 待审核提现
        item {
            SectionCard(title = "提现审核（待处理 ${pendingRequests.size}）") {
                if (requests.isEmpty()) {
                    Text(
                        "暂无提现申请。",
                        style = MaterialTheme.typography.bodySmall,
                        color = appOnSurfaceVariant()
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        pendingRequests.forEach { request ->
                            RequestRow(
                                request = request,
                                actions = {
                                    TextButton(onClick = { viewModel.reviewRequest(request.id, true) }) {
                                        Text("通过", color = appPrimary())
                                    }
                                    TextButton(onClick = { viewModel.reviewRequest(request.id, false) }) {
                                        Text("驳回", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            )
                        }
                        if (pendingRequests.isEmpty()) {
                            Text(
                                "当前没有待审核的申请",
                                style = MaterialTheme.typography.labelMedium,
                                color = appOnSurfaceVariant()
                            )
                        }
                        handledRequests.take(5).forEach { request ->
                            RequestRow(request = request, actions = {})
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(160.dp)) }
    }

    if (showSettle) {
        SettleDialog(
            monthLabel = CoachManageViewModel.monthLabel(month),
            onDismiss = { showSettle = false },
            onConfirm = { profit ->
                viewModel.settle(profit)
                showSettle = false
            }
        )
    }
    if (showWithdraw) {
        WithdrawDialog(
            coaches = withdrawCandidates,
            onDismiss = { showWithdraw = false },
            onConfirm = { name, amount, note ->
                viewModel.submitWithdraw(name, amount, note)
                showWithdraw = false
            }
        )
    }
}

/** 薪资统计卡：三列均分，数值 20sp 醒目在上，12sp 灰标签在下，内边距防贴边 */
@Composable
private fun PayrollStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = appPrimary(),
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = appOnSurfaceVariant(), maxLines = 1)
    }
}

/** 结算单行：教练 / 角色 / 消课数 + 三段金额 + 合计 + 状态切换 */
@Composable
private fun PayoutRow(
    payout: CoachPayout,
    roleLabel: String,
    onToggleStatus: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                RoundedCornerShape(12.dp)
            )
            .padding(Spacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(payout.coachName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (roleLabel.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(roleLabel, fontSize = 11.sp, color = appOnSurfaceVariant())
            }
            Spacer(Modifier.weight(1f))
            Text(
                "合计 ${fmtMoney(payout.totalAmount)} 元",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = appPrimary()
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "消课 ${payout.lessonCount} 节 · 底薪 ${fmtMoney(payout.baseAmount)} + " +
                "课时费 ${fmtMoney(payout.lessonFee)} + 分成 ${fmtMoney(payout.commission)}",
            style = MaterialTheme.typography.labelSmall,
            color = appOnSurfaceVariant()
        )
        Row {
            TextButton(onClick = onToggleStatus) {
                Text(
                    payout.status,
                    color = if (payout.status == CoachPayout.STATUS_PAID)
                        MaterialTheme.colorScheme.outline
                    else appPrimary()
                )
            }
        }
    }
}

/** 提现申请行：教练 + 金额 + 时间 + 状态 + 操作 */
@Composable
private fun RequestRow(
    request: CoachPayoutRequest,
    actions: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(request.coachName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(6.dp))
                Text("${fmtMoney(request.amount)} 元", color = appPrimary(), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Text(request.status, fontSize = 11.sp, color = appOnSurfaceVariant())
            }
            val time = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(request.appliedAt))
            Text(
                if (request.note.isBlank()) time else "$time · ${request.note}",
                style = MaterialTheme.typography.labelSmall,
                color = appOnSurfaceVariant()
            )
        }
        actions()
    }
}

/** 报表导出：生成文本 → 系统分享面板 */
@Composable
private fun ShareReportButton(viewModel: CoachManageViewModel, month: String) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    TextButton(onClick = {
        scope.launch {
            val text = viewModel.buildReport(month)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_TITLE, "教练薪资报表")
            }
            context.startActivity(Intent.createChooser(send, "分享薪资报表"))
        }
    }) {
        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = appPrimary())
        Spacer(Modifier.width(2.dp))
        Text("导出报表", color = appPrimary())
    }
}

/** 结算弹窗：录入机构当月净利润（一级合伙人分红基数） */
@Composable
private fun SettleDialog(
    monthLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (orgNetProfit: Double) -> Unit
) {
    var profitText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "结算 $monthLabel 薪资",
        confirmButton = {
            Button(onClick = {
                val profit = profitText.trim().toDoubleOrNull()
                if (profit == null || profit < 0) {
                    error = "请输入有效的净利润金额（≥0）"
                } else {
                    onConfirm(profit)
                }
            }) { Text("开始结算") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                "将按在职教练的底薪、课时费、分成规则生成结算单，同月重复结算会覆盖旧结果。",
                style = MaterialTheme.typography.bodySmall,
                color = appOnSurfaceVariant()
            )
            AppTextField(
                value = profitText,
                onValueChange = { profitText = it; error = null },
                label = { Text("机构当月净利润（元，选填 0）") },
                singleLine = true
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** 提现申请弹窗：选教练 + 金额 + 备注 */
@Composable
private fun WithdrawDialog(
    coaches: List<com.shangmentiyu.sportscoach.data.model.Coach>,
    onDismiss: () -> Unit,
    onConfirm: (coachName: String, amount: Double, note: String) -> Unit
) {
    var coach by remember { mutableStateOf<com.shangmentiyu.sportscoach.data.model.Coach?>(null) }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "提现申请",
        confirmButton = {
            Button(enabled = coach != null, onClick = {
                val amount = amountText.trim().toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    error = "请输入有效的提现金额"
                } else {
                    onConfirm(coach!!.name, amount, note.trim())
                }
            }) { Text("提交申请") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StyledDropdown(
                selected = coach,
                options = coaches,
                optionLabel = { "${it.name}（${CoachRole.label(it.role)}）" },
                optionIcon = { Icons.Outlined.Person },
                onSelected = { coach = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "选择教练"
            )
            AppTextField(
                value = amountText,
                onValueChange = { amountText = it; error = null },
                label = { Text("提现金额（元）") },
                singleLine = true
            )
            AppTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("收款方式 / 备注（选填）") },
                singleLine = true
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** 金额格式化：整数百位以内直接省小数，否则保留两位 */
internal fun fmtMoney(v: Double): String = if (v == v.toLong().toDouble()) {
    v.toLong().toString()
} else {
    String.format("%.2f", v)
}
