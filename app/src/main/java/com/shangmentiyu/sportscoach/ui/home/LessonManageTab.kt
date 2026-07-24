package com.shangmentiyu.sportscoach.ui.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import com.shangmentiyu.sportscoach.ui.AppViewModelFactory
import com.shangmentiyu.sportscoach.ui.operation.OperationViewModel
import com.shangmentiyu.sportscoach.ui.theme.Spacing

/**
 * 课时管理 Tab：展示所有学员的课时包余额，支持增添/减少/赠送。
 *
 * 每个课时包卡片显示：
 * - 学员姓名 + 套餐名
 * - 总课时 / 剩余 / 已用
 * - 购买日期 / 过期日期
 * - 操作：增添课时 / 减少课时 / 额外赠送
 */
@Composable
fun LessonManageTab(vm: HomeViewModel) {
    val context = LocalContext.current
    val opVm: OperationViewModel = viewModel(
        factory = AppViewModelFactory(context.applicationContext as android.app.Application)
    )

    val packages by opVm.packages.collectAsState()

    var adjustingPkg by remember { mutableStateOf<LessonPackage?>(null) }
    var adjustMode by remember { mutableStateOf("") } // "add" / "reduce" / "gift"
    var renamingStudent by remember { mutableStateOf<String?>(null) }
    var editingPkg by remember { mutableStateOf<LessonPackage?>(null) }
    var deletingPkg by remember { mutableStateOf<LessonPackage?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenH, vertical = Spacing.screenV),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // 概览
        IosCard {
            Column(modifier = Modifier.padding(Spacing.md)) {
                Text("课时包概览", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(label = "课时包数", value = "${packages.size}")
                    val totalRemain = packages.sumOf { it.remainingLessons }
                    StatItem(label = "剩余总数", value = "$totalRemain")
                    val totalUsed = packages.sumOf { it.totalLessons - it.remainingLessons }
                    StatItem(label = "已用总数", value = "$totalUsed")
                }
            }
        }

        IosSectionHeader("学员课时余额")

        if (packages.isEmpty()) {
            IosCard {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无课时包", color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            packages.sortedBy { it.studentName }.forEach { pkg ->
                PackageCard(
                    pkg = pkg,
                    onAdd = { adjustingPkg = pkg; adjustMode = "add" },
                    onReduce = { adjustingPkg = pkg; adjustMode = "reduce" },
                    onGift = { adjustingPkg = pkg; adjustMode = "gift" },
                    onRename = { renamingStudent = pkg.studentName },
                    onEdit = { editingPkg = pkg },
                    onDelete = { deletingPkg = pkg }
                )
            }
        }
    }

    // 调整课时对话框
    adjustingPkg?.let { pkg ->
        AdjustDialog(
            pkg = pkg,
            mode = adjustMode,
            onDismiss = { adjustingPkg = null },
            onConfirm = { count ->
                when (adjustMode) {
                    "add" -> opVm.adjustPackage(pkg.id, count)
                    "reduce" -> opVm.adjustPackage(pkg.id, -count)
                    "gift" -> opVm.giftLessons(pkg.studentName, count)
                }
                adjustingPkg = null
            }
        )
    }

    // 学员改名对话框：调用 HomeViewModel 事务级联改名
    renamingStudent?.let { oldName ->
        RenameStudentDialog(
            oldName = oldName,
            onDismiss = { renamingStudent = null },
            onConfirm = { newName ->
                vm.renameStudent(oldName, newName) { success, _ ->
                    if (success) renamingStudent = null
                }
            }
        )
    }

    // 编辑课时包对话框：修改购买/过期时间、已用课时、直接消课
    editingPkg?.let { pkg ->
        EditPackageDialog(
            pkg = pkg,
            onDismiss = { editingPkg = null },
            onConsume = {
                // 直接消课：usedLessons + 1，达到上限则标记已用完
                val newUsed = (pkg.usedLessons + 1).coerceAtMost(pkg.totalLessons)
                val newStatus = if (newUsed >= pkg.totalLessons) "已用完" else pkg.status
                opVm.updatePackage(pkg.copy(usedLessons = newUsed, status = newStatus))
                editingPkg = null
            },
            onSave = { newPurchase, newExpire, newUsed ->
                val safeUsed = newUsed.coerceIn(0, pkg.totalLessons)
                val today = java.text.SimpleDateFormat(
                    "yyyy-MM-dd", java.util.Locale.getDefault()
                ).format(java.util.Date())
                val newStatus = when {
                    safeUsed >= pkg.totalLessons -> "已用完"
                    newExpire.isNotBlank() && newExpire < today -> "已过期"
                    else -> "活跃"
                }
                opVm.updatePackage(pkg.copy(
                    purchaseDate = newPurchase,
                    expireDate = newExpire,
                    usedLessons = safeUsed,
                    status = newStatus
                ))
                editingPkg = null
            }
        )
    }

    // 删除课时包确认对话框：用于删除错误学员的课时包
    deletingPkg?.let { pkg ->
        GlassAlertDialog(
            onDismissRequest = { deletingPkg = null },
            title = "删除课时包",
            content = {
                Text("确认删除学员「${pkg.studentName}」的课时包「${pkg.name}」？\n\n" +
                    "此操作仅删除该课时包记录，不会删除学员其他数据。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        opVm.deletePackage(pkg.id)
                        deletingPkg = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deletingPkg = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun PackageCard(
    pkg: LessonPackage,
    onAdd: () -> Unit,
    onReduce: () -> Unit,
    onGift: () -> Unit,
    onRename: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    IosCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(pkg.studentName, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRename, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "改名",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "编辑课时包",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除课时包",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                RemainingBadge(pkg.remainingLessons)
            }
            Spacer(Modifier.height(4.dp))
            Text("${pkg.name} · ${pkg.price}元",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("总 ${pkg.totalLessons} 课",
                    style = MaterialTheme.typography.bodySmall)
                Text("已用 ${pkg.totalLessons - pkg.remainingLessons}",
                    style = MaterialTheme.typography.bodySmall)
                Text("剩 ${pkg.remainingLessons}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary)
            }
            if (pkg.purchaseDate.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("购买：${pkg.purchaseDate}" +
                    if (pkg.expireDate.isNotBlank()) " · 过期：${pkg.expireDate}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline)
            }

            Spacer(Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onAdd,
                    modifier = Modifier.weight(1f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("增添", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onReduce,
                    modifier = Modifier.weight(1f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("减少", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onGift,
                    modifier = Modifier.weight(1f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("赠送", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/**
 * 学员改名对话框：输入新姓名，提交后由 [HomeViewModel.renameStudent] 在事务中级联更新。
 */
@Composable
private fun RenameStudentDialog(
    oldName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newName by remember { mutableStateOf(oldName) }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "修改学员姓名",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "提示：改名会同步更新该学员的全部课时记录、课时包、排课、身体形态历史、家长报告、训练周期等数据，保证全局统一。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("新姓名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = newName.trim()
                    if (trimmed.isNotBlank() && trimmed != oldName) {
                        onConfirm(trimmed)
                    }
                }
            ) { Text("确认改名") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun AdjustDialog(
    pkg: LessonPackage,
    mode: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var countText by remember { mutableStateOf("1") }
    val title = when (mode) {
        "add" -> "增添课时"
        "reduce" -> "减少课时"
        "gift" -> "额外赠送课时"
        else -> "调整课时"
    }
    val hint = when (mode) {
        "add" -> "请输入要增添的课时数"
        "reduce" -> "请输入要减少的课时数"
        "gift" -> "请输入要赠送的课时数（不影响原套餐）"
        else -> "请输入课时数"
    }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = title,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("学员：${pkg.studentName} (${pkg.name})")
                Text("当前剩余：${pkg.remainingLessons} 课时")
                OutlinedTextField(
                    value = countText,
                    onValueChange = { countText = it.filter { c -> c.isDigit() } },
                    label = { Text(hint) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val n = countText.toIntOrNull() ?: 0
                    if (n > 0) onConfirm(n)
                }
            ) { Text("确认") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/**
 * 编辑课时包对话框：支持修改购买日期、过期日期、已用课时，并提供直接消课按钮。
 *
 * - 购买/过期日期格式为 YYYY-MM-DD，过期日期留空表示永不过期
 * - 已用课时限制在 [0, totalLessons] 范围内
 * - "直接消课"按钮将已用课时 +1（达到上限自动标记为已用完）
 */
@Composable
private fun EditPackageDialog(
    pkg: LessonPackage,
    onDismiss: () -> Unit,
    onConsume: () -> Unit,
    onSave: (purchaseDate: String, expireDate: String, usedLessons: Int) -> Unit
) {
    var purchaseDate by remember { mutableStateOf(pkg.purchaseDate) }
    var expireDate by remember { mutableStateOf(pkg.expireDate) }
    var usedText by remember { mutableStateOf(pkg.usedLessons.toString()) }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "编辑课时包",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("学员：${pkg.studentName} · ${pkg.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline)
                Text("总课时：${pkg.totalLessons} · 当前剩余：${pkg.remainingLessons}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline)

                OutlinedTextField(
                    value = purchaseDate,
                    onValueChange = { purchaseDate = it },
                    label = { Text("购买日期 (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = expireDate,
                    onValueChange = { expireDate = it },
                    label = { Text("过期日期 (留空=永不过期)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = usedText,
                    onValueChange = { usedText = it.filter { c -> c.isDigit() } },
                    label = { Text("已用课时 (0-${pkg.totalLessons})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 直接消课按钮
                OutlinedButton(
                    onClick = onConsume,
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    enabled = pkg.remainingLessons > 0
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("直接消课 1 节 (剩余 ${pkg.remainingLessons})")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val used = usedText.toIntOrNull() ?: pkg.usedLessons
                    onSave(purchaseDate.trim(), expireDate.trim(), used)
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
