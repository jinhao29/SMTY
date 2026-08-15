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
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import com.shangmentiyu.sportscoach.core.LessonDateCalculator
import com.shangmentiyu.sportscoach.ui.operation.OperationViewModel
import com.shangmentiyu.sportscoach.ui.theme.AppTextField
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.StyledDropdown
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appOutline
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appSurface
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 排课对话框（共享组件，支持三种排课方式）。
 *
 * 用于两个入口：
 * 1. 课表页 TopAppBar「排课」按钮
 * 2. 课时包管理页 PackageCard「一键排课」按钮（preselectedPackageId 预选中）
 *
 * 三种排课方式：
 * 1. 按课时包：选择学员 → 课时包 → 勾选上课日 → 输入总节数，按课包剩余课时批量排课
 * 2. 小班课：多选学员（≥2 名）统一排课，签退时统一消课
 * 3. 体验课：直接输入姓名排课，不创建学员、不消耗课时包
 *
 * 排课规则（按课时包）：
 * - 每个选中的周几创建一条 Schedule（isLongTerm=true）
 * - endDate 由 [com.shangmentiyu.sportscoach.core.LessonDateCalculator] 计算
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
    var startDateStr by remember {
        mutableStateOf(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())))
    }
    var totalLessonsStr by remember { mutableStateOf("") }

    // === 排课方式：0=按课时包，1=小班课，2=体验课 ===
    var mode by remember { mutableStateOf(0) }
    // === 小班课：多选学员（studentId 集合 + 姓名列表，顺序一致） ===
    var groupSelectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var groupSelectedNames by remember { mutableStateOf<List<String>>(emptyList()) }
    // === 体验课：直接输入姓名，不创建学员 ===
    var trialName by remember { mutableStateOf("") }

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

    // 预计结束日期实时计算（纯内存，无数据库访问）
    val totalLessons = totalLessonsStr.toIntOrNull() ?: 0
    val selectedDaysSet = selectedDays.filter { it.value }.keys.toSet()
    val expectedEndDate = remember(startDateStr, totalLessons, selectedDaysSet) {
        if (startDateStr.isNotBlank() && totalLessons > 0 && selectedDaysSet.isNotEmpty()) {
            try {
                val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
                val start = LocalDate.parse(startDateStr, fmt)
                LessonDateCalculator.calculateEndDate(start, totalLessons, selectedDaysSet)
                    .format(fmt)
            } catch (_: Exception) { null }
        } else null
    }
    val isExpired = expectedEndDate != null &&
        selectedPkg?.expireDate?.isNotBlank() == true &&
        expectedEndDate > selectedPkg.expireDate

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "排课",
        confirmButton = {
            TextButton(
                onClick = {
                    val days = selectedDays.filter { it.value }.keys
                    when (mode) {
                        // === 小班课：多选学员 + 统一签到签退 ===
                        1 -> {
                            if (groupSelectedNames.size < 2) {
                                vm.showToast("小班课至少需要选择 2 名学员")
                                return@TextButton
                            }
                            if (days.isEmpty()) {
                                vm.showToast("请至少选择一个上课日")
                                return@TextButton
                            }
                            if (totalLessons <= 0) {
                                vm.showToast("请输入本次总节数")
                                return@TextButton
                            }
                            vm.autoScheduleGroup(
                                groupStudentIds = groupSelectedIds,
                                groupStudentNames = groupSelectedNames,
                                coachName = coachName,
                                daysOfWeek = days,
                                startTime = startTime,
                                totalLessons = totalLessons,
                                startDateStr = startDateStr,
                                location = location,
                                lessonType = lessonType
                            )
                            onDismiss()
                        }
                        // === 体验课：不创建学员、不消耗课时包 ===
                        2 -> {
                            if (trialName.isBlank()) {
                                vm.showToast("请输入体验课学员姓名")
                                return@TextButton
                            }
                            if (days.isEmpty()) {
                                vm.showToast("请至少选择一个上课日")
                                return@TextButton
                            }
                            vm.saveSchedule(
                                ScheduleForm(
                                    studentName = trialName.trim(),
                                    coachName = coachName,
                                    daysOfWeek = days,
                                    startTime = startTime,
                                    location = location,
                                    lessonType = lessonType,
                                    isTrial = true
                                )
                            )
                            onDismiss()
                        }
                        // === 按课时包：默认模式 ===
                        else -> {
                            val pkg = selectedPkg
                            if (pkg == null) {
                                vm.showToast("请选择课时包")
                                return@TextButton
                            }
                            if (days.isEmpty()) {
                                vm.showToast("请至少选择一个上课日")
                                return@TextButton
                            }
                            if (totalLessons <= 0) {
                                vm.showToast("请输入本次总节数")
                                return@TextButton
                            }
                            if (isExpired) {
                                vm.showToast("预计结束日期超过课时包有效期，请减少节数或调整上课日")
                                return@TextButton
                            }
                            vm.autoScheduleFromPackage(
                                packageId = pkg.id,
                                coachName = coachName,
                                daysOfWeek = days,
                                startTime = startTime,
                                totalLessons = totalLessons,
                                startDateStr = startDateStr,
                                location = location,
                                lessonType = lessonType
                            )
                            onDismiss()
                        }
                    }
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
            // === 排课方式选择 ===
            Text(
                "排课方式",
                style = MaterialTheme.typography.labelLarge,
                color = appOnSurface(),
                fontWeight = FontWeight.Medium
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                val modes = listOf("按课时包", "小班课", "体验课")
                modes.forEachIndexed { idx, label ->
                    FilterChip(
                        selected = mode == idx,
                        onClick = { mode = idx },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = appPrimary(),
                            selectedLabelColor = androidx.compose.ui.graphics.Color.White
                        )
                    )
                }
            }

            // === 按课时包：选择学员与课时包 ===
            if (mode == 0) {
                StudentDropdown(
                    students = students.map { it.name },
                    selected = selectedStudent,
                    onSelected = {
                        selectedStudent = it
                        selectedPackageId = ""
                    }
                )

                PackageDropdown(
                    packages = studentPackages,
                    selectedId = selectedPackageId,
                    onSelected = { selectedPackageId = it }
                )

                if (selectedPkg != null) {
                    PackageInfoCard(pkg = selectedPkg)
                }
            }

            // === 小班课：多选学员 ===
            if (mode == 1) {
                Text(
                    "选择学员（至少 2 名）",
                    style = MaterialTheme.typography.labelLarge,
                    color = appOnSurface(),
                    fontWeight = FontWeight.Medium
                )
                if (students.isEmpty()) {
                    Text(
                        "暂无学员，请先到学员管理添加",
                        style = MaterialTheme.typography.bodySmall,
                        color = appOnSurfaceVariant()
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        students.forEach { s ->
                            val sid = s.studentId
                            val isSelected = sid != null && sid in groupSelectedIds
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (sid != null) {
                                        if (isSelected) {
                                            groupSelectedIds = groupSelectedIds - sid
                                            groupSelectedNames = groupSelectedNames.filter { it != s.name }
                                        } else {
                                            groupSelectedIds = groupSelectedIds + sid
                                            groupSelectedNames = groupSelectedNames + s.name
                                        }
                                    }
                                },
                                label = { Text(s.name) }
                            )
                        }
                    }
                }
            }

            // === 体验课：直接输入姓名，不创建学员 ===
            if (mode == 2) {
                AppTextField(
                    value = trialName,
                    onValueChange = { trialName = it },
                    label = { Text("学员姓名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // === 教练输入 ===
            AppTextField(
                value = coachName,
                onValueChange = { coachName = it },
                label = { Text("教练姓名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
)

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

            // === 以下字段按课时包 / 小班课模式使用 ===
            if (mode == 0 || mode == 1) {
                // === 开始日期 ===
                AppTextField(
                    value = startDateStr,
                    onValueChange = { startDateStr = it },
                    label = { Text("开始日期 (yyyy-MM-dd)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // === 本次总节数 ===
                AppTextField(
                    value = totalLessonsStr,
                    onValueChange = { totalLessonsStr = it.filter { c -> c.isDigit() } },
                    label = { Text("本次总节数") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                // === 预计结束日期（实时计算） ===
                if (expectedEndDate != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isExpired) Color(0xFFFFEBEE) else appSurface())
                            .padding(Spacing.md)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "预计结束日期",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appOnSurfaceVariant()
                                )
                                Text(
                                    expectedEndDate,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isExpired) Color.Red else appPrimary(),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (mode == 0 && isExpired) {
                                Text(
                                    "警告：超过课时包有效期 ${selectedPkg?.expireDate}",
                                    color = Color.Red,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            // === 上课时间 ===
            AppTextField(
                value = startTime,
                onValueChange = { startTime = it },
                label = { Text("上课时间 (HH:mm)") },
                singleLine = true,
                trailingIcon = {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.clickable { })
                },
                modifier = Modifier.fillMaxWidth(),
)

            // === 地点 ===
            AppTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("地点（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
)

            // === 课程类型 ===
            AppTextField(
                value = lessonType,
                onValueChange = { lessonType = it },
                label = { Text("课程类型") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
)
        }
    }
}

/**
 * 学员下拉选择器。
 */
@Composable
private fun StudentDropdown(
    students: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    StyledDropdown(
        selected = selected.ifBlank { null },
        options = students,
        optionLabel = { it },
        optionIcon = { Icons.Outlined.Person },
        onSelected = onSelected,
        modifier = Modifier.fillMaxWidth(),
        placeholder = "选择学员"
    )
}

/**
 * 课时包下拉选择器。
 */
@Composable
private fun PackageDropdown(
    packages: List<LessonPackage>,
    selectedId: String,
    onSelected: (String) -> Unit
) {
    val options = packages.map { "${it.name}（剩${it.remainingLessons}/${it.totalLessons}节）" }
    StyledDropdown(
        selected = packages.firstOrNull { it.id == selectedId }
            ?.let { "${it.name}（剩${it.remainingLessons}/${it.totalLessons}节）" },
        options = options,
        optionLabel = { it },
        optionIcon = { Icons.Outlined.Schedule },
        onSelected = { label ->
            packages.firstOrNull {
                "${it.name}（剩${it.remainingLessons}/${it.totalLessons}节）" == label
            }?.let { onSelected(it.id) }
        },
        modifier = Modifier.fillMaxWidth(),
        placeholder = "选择课时包"
    )
}

/**
 * 课时包信息卡片：显示剩余课时、购买日、过期日。
 *
 * @param pkg 选中的课时包
 */
@Composable
private fun PackageInfoCard(pkg: LessonPackage) {
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
