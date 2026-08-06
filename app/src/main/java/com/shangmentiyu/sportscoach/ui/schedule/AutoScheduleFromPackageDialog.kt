package com.shangmentiyu.sportscoach.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import com.shangmentiyu.sportscoach.ui.operation.OperationViewModel
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appOutline
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appSurface
import com.shangmentiyu.sportscoach.ui.theme.AppTextFieldShape
import com.shangmentiyu.sportscoach.ui.theme.appTextFieldColors

/**
 * 按课时包自动排课对话框（共享组件）。
 *
 * 用于两个入口：
 * 1. 课表页 TopAppBar「按课包排课」按钮
 * 2. 课时包管理页 PackageCard「一键排课」按钮（preselectedPackageId 预选中）
 *
 * 用户流程：
 * 1. 选择学员 → 自动过滤该学员的活跃课时包
 * 2. 选择课时包 → 显示剩余课时、购买日、过期日、自动计算的排课周数
 * 3. 选择多个周几（FilterChip 多选）
 * 4. 填写教练、上课时间、地点、课程类型
 * 5. 点击「确认排课」调用 [OperationViewModel.autoScheduleFromPackage]
 *
 * 排课规则：
 * - 每个选中的周几创建一条 Schedule（isLongTerm=true）
 * - endDate 由 [com.shangmentiyu.sportscoach.core.AutoScheduleCalculator] 计算
 * - 课时消耗依赖签退时的 [com.shangmentiyu.sportscoach.data.repo.OperationRepository.consumeLessonForCheckOut]
 *
 * @param vm 运营管理 ViewModel
 * @param preselectedPackageId 预选中的课时包 ID（从课时包管理页进入时传入），空字符串表示不预选
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AutoScheduleFromPackageDialog(
    vm: OperationViewModel,
    preselectedPackageId: String = "",
    onDismiss: () -> Unit
) {
    val students by vm.students.collectAsStateWithLifecycle()
    val packages by vm.packages.collectAsStateWithLifecycle()

    // === 预选中处理：从课时包管理页进入时，根据 preselectedPackageId 反查学员 ===
    var selectedStudent by remember {
        mutableStateOf(
            packages.firstOrNull { it.id == preselectedPackageId }?.studentName ?: ""
        )
    }
    var selectedPackageId by remember { mutableStateOf(preselectedPackageId) }
    var coachName by remember { mutableStateOf("李") }
    val selectedDays = remember { androidx.compose.runtime.mutableStateMapOf<Int, Boolean>() }
    var startTime by remember { mutableStateOf("09:00") }
    var location by remember { mutableStateOf("") }
    var lessonType by remember { mutableStateOf("训练课") }

    // 当前学员的活跃课时包（remaining > 0 且 status=活跃）
    val studentPackages = remember(packages, selectedStudent) {
        if (selectedStudent.isBlank()) emptyList()
        else packages.filter {
            it.studentName == selectedStudent &&
                it.status == "活跃" &&
                it.remainingLessons > 0
        }
    }
    val selectedPkg = studentPackages.firstOrNull { it.id == selectedPackageId }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "按课时包排课",
        confirmButton = {
            TextButton(
                onClick = {
                    val pkg = selectedPkg
                    val days = selectedDays.filter { it.value }.keys
                    if (pkg == null) {
                        vm.showToast("请选择课时包")
                        return@TextButton
                    }
                    if (days.isEmpty()) {
                        vm.showToast("请至少选择一个上课日")
                        return@TextButton
                    }
                    vm.autoScheduleFromPackage(
                        packageId = pkg.id,
                        coachName = coachName,
                        daysOfWeek = days,
                        startTime = startTime,
                        location = location,
                        lessonType = lessonType
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = appPrimary())
            ) { Text("确认排课", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // === 学员选择 ===
            StudentDropdown(
                students = students.map { it.name },
                selected = selectedStudent,
                onSelected = {
                    selectedStudent = it
                    selectedPackageId = ""
                }
            )

            // === 课时包选择 ===
            PackageDropdown(
                packages = studentPackages,
                selectedId = selectedPackageId,
                onSelected = { selectedPackageId = it }
            )

            // === 课时包信息卡片 ===
            if (selectedPkg != null) {
                PackageInfoCard(pkg = selectedPkg, selectedDays = selectedDays.count { it.value })
            }

            // === 教练输入 ===
            OutlinedTextField(
                value = coachName,
                onValueChange = { coachName = it },
                label = { Text("教练姓名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),

             shape = AppTextFieldShape,
             colors = appTextFieldColors(),)

            // === 上课日多选 ===
            Text(
                "上课日（可多选）",
                style = MaterialTheme.typography.labelLarge,
                color = appOnSurface(),
                fontWeight = FontWeight.Medium
            )
            val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                dayNames.forEachIndexed { idx, name ->
                    val dow = idx + 1
                    val isSelected = selectedDays[dow] == true
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedDays[dow] = !isSelected
                        },
                        label = { Text(name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = appPrimary(),
                            selectedLabelColor = androidx.compose.ui.graphics.Color.White
                        )
                    )
                }
            }

            // === 上课时间 ===
            OutlinedTextField(
                value = startTime,
                onValueChange = { startTime = it },
                label = { Text("上课时间 (HH:mm)") },
                singleLine = true,
                trailingIcon = {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.clickable { })
                },
                modifier = Modifier.fillMaxWidth(),

             shape = AppTextFieldShape,
             colors = appTextFieldColors(),)

            // === 地点 ===
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("地点（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),

             shape = AppTextFieldShape,
             colors = appTextFieldColors(),)

            // === 课程类型 ===
            OutlinedTextField(
                value = lessonType,
                onValueChange = { lessonType = it },
                label = { Text("课程类型") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),

             shape = AppTextFieldShape,
             colors = appTextFieldColors(),)
        }
    }
}

/**
 * 学员下拉选择器。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentDropdown(
    students: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = { },
            readOnly = true,
            label = { Text("选择学员") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),

         shape = AppTextFieldShape,
         colors = appTextFieldColors(),)
        ExposedDropdownMenuBoxScopeFix {
            androidx.compose.material3.DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                students.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            onSelected(name)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * ExposedDropdownMenuBox 内部 DropdownMenu 的占位包装。
 * 仅用于让 DropdownMenu 落在 ExposedDropdownMenuBox 作用域内。
 */
@Composable
private fun ExposedDropdownMenuBoxScopeFix(content: @Composable () -> Unit) {
    content()
}

/**
 * 课时包下拉选择器。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackageDropdown(
    packages: List<LessonPackage>,
    selectedId: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedPkg = packages.firstOrNull { it.id == selectedId }
    val displayText = selectedPkg?.let { "${it.name}（剩${it.remainingLessons}节）" } ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = { },
            readOnly = true,
            label = { Text("选择课时包") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),

         shape = AppTextFieldShape,
         colors = appTextFieldColors(),)
        ExposedDropdownMenuBoxScopeFix {
            androidx.compose.material3.DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                if (packages.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("该学员暂无活跃课时包", color = appOnSurfaceVariant()) },
                        onClick = { expanded = false }
                    )
                } else {
                    packages.forEach { pkg ->
                        DropdownMenuItem(
                            text = {
                                Text("${pkg.name}（剩${pkg.remainingLessons}/${pkg.totalLessons}节）")
                            },
                            onClick = {
                                onSelected(pkg.id)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 课时包信息卡片：显示剩余课时、购买日、过期日、自动计算的排课周数。
 *
 * @param pkg 选中的课时包
 * @param selectedDays 已选中的上课日数量
 */
@Composable
private fun PackageInfoCard(pkg: LessonPackage, selectedDays: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(appSurface())
            .padding(Spacing.md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "课时包详情",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = appOnSurface()
            )
            Spacer(Modifier.height(Spacing.xs))
            InfoRow("剩余课时", "${pkg.remainingLessons} 节")
            InfoRow("购买日期", pkg.purchaseDate.ifBlank { "—" })
            InfoRow("过期日期", pkg.expireDate.ifBlank { "永不过期" })

            if (selectedDays > 0) {
                Spacer(Modifier.height(Spacing.xs))
                val totalWeeks = if (selectedDays > 0) {
                    (pkg.remainingLessons + selectedDays - 1) / selectedDays
                } else 0
                val remainder = pkg.remainingLessons % selectedDays
                val weekText = if (remainder == 0) {
                    "$totalWeeks 周排完"
                } else {
                    "$totalWeeks 周（最后一周排 $remainder 节）"
                }
                InfoRow("自动排课周数", weekText, highlight = true)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = appOnSurfaceVariant()
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = if (highlight) appPrimary() else appOnSurface(),
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
