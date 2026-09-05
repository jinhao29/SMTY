package com.shangmentiyu.sportscoach.ui.coach

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangmentiyu.sportscoach.data.model.Coach
import com.shangmentiyu.sportscoach.data.model.CoachRole
import com.shangmentiyu.sportscoach.data.repo.CoachTreeNode
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.StyledDropdown
import com.shangmentiyu.sportscoach.ui.theme.appOnPrimary
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appPrimary

/**
 * Tab 3：合伙人团队管理。
 * - 团队层级树（一级合伙人 → 二级合伙人 → 全职/兼职），按 superiorId 递归渲染
 * - 团队数据看板：总人数 / 各角色分布 / 每个节点的下属规模
 * - 成员转让：把某教练名下全部直接下属整体转给另一教练（Repository 层防环 + 层级校验）
 */
@Composable
internal fun CoachTeamTab(viewModel: CoachManageViewModel) {
    val tree by viewModel.teamTree.collectAsStateWithLifecycle()
    val allCoaches by viewModel.allCoaches.collectAsStateWithLifecycle()

    var showTransfer by remember { mutableStateOf(false) }

    // 团队规模统计（递归计数）
    val stats = remember(tree) {
        val activeCount = tree.sumOf { countActive(it) }
        val roleCount = CoachRole.ALL.associateWith { role ->
            tree.sumOf { countByRole(it, role) }
        }
        Triple(activeCount, roleCount, tree.sumOf { countAll(it) })
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Spacing.screenH, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            SectionCard(
                title = "团队看板",
                trailing = {
                    TextButton(onClick = { showTransfer = true }) {
                        Icon(
                            Icons.Filled.SwapHoriz,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = appPrimary()
                        )
                        Spacer(Modifier.width(2.dp))
                        Text("转让成员", color = appPrimary())
                    }
                }
            ) {
                StatRow(
                    "团队总人数" to "${stats.third} 人",
                    "一级合伙人" to "${stats.second[CoachRole.PARTNER_L1] ?: 0} 人",
                    "二级合伙人" to "${stats.second[CoachRole.PARTNER_L2] ?: 0} 人",
                    "全职教练" to "${stats.second[CoachRole.FULLTIME] ?: 0} 人",
                    "兼职教练" to "${stats.second[CoachRole.PARTTIME] ?: 0} 人"
                )
            }
        }

        item {
            SectionCard(title = "团队层级") {
                if (tree.isEmpty()) {
                    Text(
                        "暂无教练。先在「教练档案」中新增教练并设置所属上级（合伙人），层级树将在此展示。",
                        style = MaterialTheme.typography.bodySmall,
                        color = appOnSurfaceVariant()
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        tree.forEach { node -> TreeNodeRow(node = node, depth = 0) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(160.dp)) }
    }

    if (showTransfer) {
        TransferDialog(
            coaches = allCoaches,
            onDismiss = { showTransfer = false },
            onConfirm = { from, to ->
                viewModel.transferMembers(from, to)
                showTransfer = false
            }
        )
    }
}

/** 树节点行：缩进 + 头像 + 姓名 + 角色标签 + 下属数，递归渲染子节点 */
@Composable
private fun TreeNodeRow(node: CoachTreeNode, depth: Int) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Spacing.sm + (depth * 20).dp,
                    top = 4.dp, bottom = 4.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 层级指示：非根节点画竖向连接线
            if (depth > 0) {
                Box(
                    modifier = Modifier
                        .padding(end = Spacing.sm)
                        .width(2.dp)
                        .height(32.dp)
                        .background(appPrimary().copy(alpha = 0.15f), RoundedCornerShape(1.dp))
                )
            }
            // 头像（合伙人实底橙渐变，其余浅橙）
            val isPartner = node.coach.role == CoachRole.PARTNER_L1 ||
                node.coach.role == CoachRole.PARTNER_L2
            val avatarBrush = if (isPartner) {
                Brush.horizontalGradient(listOf(appPrimary(), appPrimary().copy(alpha = 0.75f)))
            } else {
                Brush.horizontalGradient(
                    listOf(appPrimary().copy(alpha = 0.12f), appPrimary().copy(alpha = 0.12f))
                )
            }
            Box(
                modifier = Modifier
                    .size(if (isPartner) 36.dp else 30.dp)
                    .background(avatarBrush, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    node.coach.name.take(1),
                    color = if (isPartner) appOnPrimary() else appPrimary(),
                    fontSize = if (isPartner) 15.sp else 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        node.coach.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        CoachRole.label(node.coach.role),
                        color = if (isPartner) appPrimary() else appOnSurfaceVariant(),
                        fontSize = 11.sp
                    )
                    if (node.coach.status != "在职") {
                        Spacer(Modifier.width(4.dp))
                        Text(node.coach.status, color = appOnSurfaceVariant(), fontSize = 11.sp)
                    }
                }
                val memberCount = countAll(node) - 1
                if (memberCount > 0) {
                    Text(
                        "团队 $memberCount 人",
                        style = MaterialTheme.typography.labelSmall,
                        color = appOnSurfaceVariant()
                    )
                }
            }
            if (node.children.isEmpty()) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = appOnSurfaceVariant().copy(alpha = 0.5f)
                )
            } else {
                Icon(
                    Icons.Outlined.AccountTree,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = appPrimary().copy(alpha = 0.6f)
                )
            }
        }
        node.children.forEach { child -> TreeNodeRow(node = child, depth = depth + 1) }
    }
}

/** 转让弹窗：原上级（有下属的人）→ 新上级（层级 ≥ 原上级） */
@Composable
private fun TransferDialog(
    coaches: List<Coach>,
    onDismiss: () -> Unit,
    onConfirm: (fromName: String, toName: String) -> Unit
) {
    // 原上级候选：名下有直接下属的教练
    val fromCandidates = remember(coaches) {
        coaches.filter { c -> coaches.any { it.superiorId == c.name } }
    }
    var fromName by remember { mutableStateOf(fromCandidates.firstOrNull()?.name ?: "") }
    // 新上级候选：非自己、层级不低于原上级（合法性最终由 Repository 校验兜底）
    val toCandidates = remember(coaches, fromName) {
        val fromRank = coaches.find { it.name == fromName }?.let { CoachRole.rank(it.role) } ?: 0
        coaches.filter {
            it.name != fromName && it.status == "在职" && CoachRole.rank(it.role) >= fromRank
        }
    }
    var toName by remember { mutableStateOf("") }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "转让团队成员",
        confirmButton = {
            Button(
                enabled = fromName.isNotBlank() && toName.isNotBlank(),
                onClick = { onConfirm(fromName, toName) }
            ) { Text("确认转让") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                "将原上级名下的全部直接下属整体转给新上级，转让后原上级名下不再有成员。",
                style = MaterialTheme.typography.bodySmall,
                color = appOnSurfaceVariant()
            )
            if (fromCandidates.isEmpty()) {
                Text(
                    "当前没有名下带成员的教练，无需转让。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = appOnSurfaceVariant()
                )
            } else {
                Text("原上级", style = MaterialTheme.typography.labelMedium, color = appOnSurfaceVariant())
                StyledDropdown(
                    selected = fromCandidates.firstOrNull { it.name == fromName },
                    options = fromCandidates,
                    optionLabel = { "${it.name}（${CoachRole.label(it.role)}）" },
                    optionIcon = { Icons.Outlined.Person },
                    onSelected = { fromName = it.name },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "选择原上级"
                )
                Text("新上级", style = MaterialTheme.typography.labelMedium, color = appOnSurfaceVariant())
                StyledDropdown(
                    selected = toCandidates.firstOrNull { it.name == toName },
                    options = toCandidates,
                    optionLabel = { "${it.name}（${CoachRole.label(it.role)}）" },
                    optionIcon = { Icons.Outlined.Person },
                    onSelected = { toName = it.name },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "选择新上级"
                )
            }
        }
    }
}

/** 节点子树在职人数（含自己） */
private fun countActive(node: CoachTreeNode): Int =
    (if (node.coach.status == "在职") 1 else 0) + node.children.sumOf { countActive(it) }

/** 节点子树总人数（含自己，含离职） */
private fun countAll(node: CoachTreeNode): Int = 1 + node.children.sumOf { countAll(it) }

/** 节点子树内指定角色人数（含自己） */
private fun countByRole(node: CoachTreeNode, role: String): Int =
    (if (node.coach.role == role) 1 else 0) + node.children.sumOf { countByRole(it, role) }
