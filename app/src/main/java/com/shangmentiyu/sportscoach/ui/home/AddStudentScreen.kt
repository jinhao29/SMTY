package com.shangmentiyu.sportscoach.ui.home

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shangmentiyu.sportscoach.core.BmiProcessor
import com.shangmentiyu.sportscoach.core.Standards
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.ui.AppViewModelFactory
import com.shangmentiyu.sportscoach.ui.theme.appDividerColor
import com.shangmentiyu.sportscoach.ui.theme.appGroupedBackground
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.IosDatePickerRow
import com.shangmentiyu.sportscoach.ui.theme.appOnPrimary
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appSurface
import com.shangmentiyu.sportscoach.ui.theme.ScoreExcellent
import com.shangmentiyu.sportscoach.ui.theme.ScoreFail
import com.shangmentiyu.sportscoach.ui.theme.ScoreGood
import com.shangmentiyu.sportscoach.ui.theme.ScorePass
import com.shangmentiyu.sportscoach.ui.theme.Spacing

/**
 * 学员编辑页：iOS Inset Grouped 表单风格。
 *
 * - 编辑模式传入 [student]：姓名只读，修改其他字段后调用 vm.updateStudent
 * - 新增模式 [student]=null：姓名可输入，保存后调用 vm.addStudent
 *
 * iOS 表单特征：
 * - 浅灰分组底 + 纯白圆角卡片
 * - 字段左侧 Label + 右侧值，分隔线
 * - 顶部白色 Navigation Bar，蓝色保存按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStudentScreen(
    onBack: () -> Unit,
    student: Student? = null
) {
    val context = LocalContext.current
    val vm: HomeViewModel = viewModel(
        factory = AppViewModelFactory(context.applicationContext as Application)
    )

    val isEdit = student != null
    // 用 student 作为 key：进入编辑模式时状态会随传入的学员对象重新初始化
    var name by remember(student) { mutableStateOf(student?.name ?: "") }
    var gender by remember(student) { mutableStateOf(student?.gender ?: "男") }
    var grade by remember(student) { mutableStateOf(student?.grade ?: "1") }
    var school by remember(student) { mutableStateOf(student?.school ?: "") }
    var phone by remember(student) { mutableStateOf(student?.phone ?: "") }
    var age by remember(student) { mutableStateOf(student?.age?.toString() ?: "") }
    var heightStr by remember(student) { mutableStateOf(student?.heightCm?.takeIf { it > 0 }?.toString() ?: "") }
    var weightStr by remember(student) { mutableStateOf(student?.weightKg?.takeIf { it > 0f }?.toString() ?: "") }
    var expanded by remember { mutableStateOf(false) }
    var snackbar by remember { mutableStateOf<String?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    // 学龄前判定：年龄 1-7 岁隐藏年级字段（3-7岁幼儿不要求年级）
    // 年龄为空或 0 时显示年级（默认状态），年龄 >7 时显示年级（学龄期）
    val ageInt = age.toIntOrNull() ?: 0
    val isPreschool = ageInt in 1..7
    // 实际保存的年级：学龄前自动设为 "0"（学龄前编码）
    val effectiveGrade = if (isPreschool) "0" else grade

    // 课时包字段（仅新增模式使用）
    var pkgEnabled by remember { mutableStateOf(false) }  // 是否同步创建课时包
    var pkgName by remember { mutableStateOf("") }
    var pkgTotal by remember { mutableStateOf("") }
    var pkgPrice by remember { mutableStateOf("") }
    var pkgPurchaseDate by remember { mutableStateOf(todayStr()) }
    var pkgExpireDate by remember { mutableStateOf("") }

    // 实时计算 BMI
    val heightCmInt = heightStr.toIntOrNull() ?: 0
    val weightKgFloat = weightStr.toFloatOrNull() ?: 0f
    val bmiResult = if (heightCmInt > 0 && weightKgFloat > 0f) {
        BmiProcessor.compute(heightCmInt, weightKgFloat)
    } else BmiProcessor.BmiResult.INVALID

    Scaffold(
        containerColor = appGroupedBackground(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEdit) "编辑学员" else "添加学员",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appSurface(),
                    scrolledContainerColor = Color.White,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary,
                    actionIconContentColor = MaterialTheme.colorScheme.primary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val bmiVal = if (bmiResult.valid) bmiResult.bmi else 0f
                        when {
                            !isEdit && name.isBlank() -> snackbar = "请输入姓名"
                            !isEdit && pkgEnabled && pkgTotal.toIntOrNull()?.let { it <= 0 } ?: true -> snackbar = "请输入有效的课时数"
                            isEdit -> showConfirmDialog = true
                            else -> {
                                val total = pkgTotal.toIntOrNull() ?: 0
                                if (pkgEnabled && total > 0) {
                                    vm.addStudentWithPackage(
                                        name, gender, effectiveGrade, school, phone,
                                        ageInt, heightCmInt, weightKgFloat, bmiVal,
                                        packageName = pkgName,
                                        packageTotal = total,
                                        price = pkgPrice.toDoubleOrNull() ?: 0.0,
                                        purchaseDate = pkgPurchaseDate,
                                        expireDate = pkgExpireDate
                                    ) { onBack() }
                                } else {
                                    vm.addStudent(name, gender, effectiveGrade, school, phone,
                                        ageInt, heightCmInt, weightKgFloat, bmiVal)
                                    onBack()
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Outlined.Check, contentDescription = "保存", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.screenH, vertical = Spacing.lg)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl)
            ) {
                // 分组 1：基本信息
                IosFormSectionHeader("基本信息")
                IosFormCard {
                    // 姓名（编辑模式下可修改，改名会级联更新全部关联数据）
                    IosFormRow(
                        label = "姓名",
                        value = name,
                        onValueChange = { name = it },
                        readOnly = false,
                        showDivider = true,
                        placeholder = "请输入姓名"
                    )
                    // 性别（用选择器行）
                    IosFormSelectorRow(
                        label = "性别",
                        value = gender,
                        options = listOf("男", "女"),
                        onSelect = { gender = it },
                        showDivider = true
                    )
                    // 年龄（移至基本信息组顶部，便于根据年龄决定是否显示年级）
                    IosFormRow(
                        label = "年龄",
                        value = age,
                        onValueChange = { age = it.filter { c -> c.isDigit() } },
                        showDivider = !isPreschool,
                        placeholder = "请输入年龄",
                        keyboardType = KeyboardType.Number,
                        unit = "岁"
                    )
                    // 年级（下拉）—— 仅学龄期（年龄>7 或未填）显示，3-7岁自动归为"学龄前"
                    if (!isPreschool) {
                        IosFormDropdownRow(
                            label = "年级",
                            displayValue = Standards.gradeFullLabel(grade),
                            expanded = expanded,
                            onExpandedChange = { expanded = it },
                            options = Standards.GRADE_OPTIONS.filter { it.first != "0" }.map { (code, label) -> code to label },
                            onSelect = { grade = it },
                            showDivider = false
                        )
                    } else {
                        // 学龄前学员显示只读提示行
                        IosFormRow(
                            label = "年级",
                            value = "学龄前(3-7岁)",
                            onValueChange = {},
                            readOnly = true,
                            showDivider = false,
                            placeholder = ""
                        )
                    }
                }

                // 分组 2：身体信息
                IosFormSectionHeader("身体信息")
                IosFormCard {
                    IosFormRow(
                        label = "身高",
                        value = heightStr,
                        onValueChange = { heightStr = it.filter { c -> c.isDigit() } },
                        showDivider = true,
                        placeholder = "请输入身高",
                        keyboardType = KeyboardType.Number,
                        unit = "cm"
                    )
                    IosFormRow(
                        label = "体重",
                        value = weightStr,
                        onValueChange = { weightStr = it },
                        showDivider = true,
                        placeholder = "请输入体重",
                        keyboardType = KeyboardType.Decimal,
                        unit = "kg"
                    )
                    // BMI 自动计算显示行
                    IosBmiDisplayRow(bmiResult = bmiResult, showDivider = false)
                }

                // 分组 3：联系信息
                IosFormSectionHeader("联系信息")
                IosFormCard {
                    IosFormRow(
                        label = "学校",
                        value = school,
                        onValueChange = { school = it },
                        showDivider = true,
                        placeholder = "请输入学校"
                    )
                    IosFormRow(
                        label = "电话",
                        value = phone,
                        onValueChange = { phone = it },
                        showDivider = false,
                        placeholder = "请输入联系电话"
                    )
                }

                // 分组 4：课时包（新增/编辑模式均显示，编辑时为"添加课时包"）
                IosFormSectionHeader(if (isEdit) "添加课时包（可选）" else "课时包（可选）")
                IosFormCard {
                    // 顶部开关行：是否同步创建课时包
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            Text(
                                if (isEdit) "添加课时包" else "同步创建课时包",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                            androidx.compose.material3.Switch(
                                checked = pkgEnabled,
                                onCheckedChange = { pkgEnabled = it }
                            )
                        }
                        if (pkgEnabled) {
                            Box(
                                modifier = Modifier
                                    .padding(start = 80.dp)
                                    .fillMaxWidth()
                                    .height(0.5.dp)
                                    .background(appDividerColor())
                            )
                        }
                    }
                    if (pkgEnabled) {
                        IosFormRow(
                            label = "套餐名称",
                            value = pkgName,
                            onValueChange = { pkgName = it },
                            showDivider = true,
                            placeholder = "如：10次卡（留空自动命名）"
                        )
                        IosFormRow(
                            label = "总课时",
                            value = pkgTotal,
                            onValueChange = { pkgTotal = it.filter { c -> c.isDigit() } },
                            showDivider = true,
                            placeholder = "如：10",
                            keyboardType = KeyboardType.Number,
                            unit = "次"
                        )
                        IosFormRow(
                            label = "价格",
                            value = pkgPrice,
                            onValueChange = { pkgPrice = it },
                            showDivider = true,
                            placeholder = "0",
                            keyboardType = KeyboardType.Decimal,
                            unit = "元"
                        )
                        IosDatePickerRow(
                            label = "购买日期",
                            dateStr = pkgPurchaseDate,
                            onDateChange = { pkgPurchaseDate = it },
                            showDivider = true,
                            placeholder = "选择购买日期"
                        )
                        IosDatePickerRow(
                            label = "过期日期",
                            dateStr = pkgExpireDate,
                            onDateChange = { pkgExpireDate = it },
                            showDivider = false,
                            placeholder = "留空表示永不过期"
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.lg))

                // iOS 风格主按钮：系统蓝填充，圆角 10pt
                Button(
                    onClick = {
                        val bmiVal = if (bmiResult.valid) bmiResult.bmi else 0f
                        when {
                            !isEdit && name.isBlank() -> snackbar = "请输入姓名"
                            !isEdit && pkgEnabled && (pkgTotal.toIntOrNull() ?: 0) <= 0 -> snackbar = "请输入有效的课时数"
                            isEdit -> showConfirmDialog = true
                            else -> {
                                val total = pkgTotal.toIntOrNull() ?: 0
                                if (pkgEnabled && total > 0) {
                                    vm.addStudentWithPackage(
                                        name, gender, effectiveGrade, school, phone,
                                        ageInt, heightCmInt, weightKgFloat, bmiVal,
                                        packageName = pkgName,
                                        packageTotal = total,
                                        price = pkgPrice.toDoubleOrNull() ?: 0.0,
                                        purchaseDate = pkgPurchaseDate,
                                        expireDate = pkgExpireDate
                                    ) { onBack() }
                                } else {
                                    vm.addStudent(name, gender, effectiveGrade, school, phone,
                                        ageInt, heightCmInt, weightKgFloat, bmiVal)
                                    onBack()
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = appPrimary(),
                        contentColor = appOnPrimary()
                    )
                ) {
                    Text("保存", style = MaterialTheme.typography.labelLarge)
                }
            }

            snackbar?.let {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text(it)
                }
            }

            // 编辑模式确认修改对话框
            if (showConfirmDialog) {
                GlassAlertDialog(
                    onDismissRequest = { showConfirmDialog = false },
                    title = "确认修改",
                    content = {
                        Column {
                            Text("即将修改 ${student?.name} 的学员信息：")
                            Spacer(Modifier.height(8.dp))
                            // 姓名变化提示（改名会级联更新全部关联数据）
                            val trimmedName = name.trim()
                            if (student?.name != trimmedName) {
                                Text("姓名：${student?.name} → $trimmedName（将同步更新全部课时/排课/报告等数据）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold)
                            }
                            if (student?.gender != gender) Text("性别：${student?.gender} → $gender", style = MaterialTheme.typography.bodySmall)
                            if (student?.grade != effectiveGrade) Text("年级：${Standards.gradeFullLabel(student?.grade ?: "1")} → ${Standards.gradeFullLabel(effectiveGrade)}", style = MaterialTheme.typography.bodySmall)
                            if (student?.school != school && school.isNotBlank()) Text("学校：${student?.school} → $school", style = MaterialTheme.typography.bodySmall)
                            if (student?.phone != phone && phone.isNotBlank()) Text("电话：${student?.phone} → $phone", style = MaterialTheme.typography.bodySmall)
                            if (student?.age != ageInt && ageInt > 0) Text("年龄：${student?.age} → $ageInt", style = MaterialTheme.typography.bodySmall)
                            if (student?.heightCm != heightCmInt && heightCmInt > 0) Text("身高：${student?.heightCm}cm → ${heightCmInt}cm", style = MaterialTheme.typography.bodySmall)
                            if (student?.weightKg != weightKgFloat && weightKgFloat > 0f) Text("体重：${student?.weightKg}kg → ${weightKgFloat}kg", style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showConfirmDialog = false
                            val target = student ?: return@TextButton
                            val bmiVal = if (bmiResult.valid) bmiResult.bmi else 0f
                            val trimmedNewName = name.trim()
                            // 使用 updateStudentFull 支持改名 + 更新全部字段（事务级联）
                            vm.updateStudentFull(target, trimmedNewName,
                                gender, effectiveGrade, school, phone,
                                ageInt, heightCmInt, weightKgFloat, bmiVal
                            ) { success, _ ->
                                if (success) {
                                    // 编辑模式下如果开启了课时包，同时为该学员添加课时包（用新姓名）
                                    if (pkgEnabled) {
                                        val total = pkgTotal.toIntOrNull() ?: 0
                                        if (total > 0) {
                                            vm.addPackage(
                                                studentName = trimmedNewName,
                                                packageName = pkgName,
                                                totalLessons = total,
                                                price = pkgPrice.toDoubleOrNull() ?: 0.0,
                                                purchaseDate = pkgPurchaseDate,
                                                expireDate = pkgExpireDate
                                            )
                                        }
                                    }
                                    onBack()
                                }
                            }
                        }) {
                            Text("确认修改", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirmDialog = false }) {
                            Text("取消")
                        }
                    }
                )
            }
        }
    }
}

/**
 * iOS 表单分组头：13pt SemiBold 次级文字，左对齐。
 */
@Composable
private fun IosFormSectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = appOnSurfaceVariant(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = Spacing.xs)
    )
}

/**
 * iOS 表单卡片容器：纯白 + 10pt 圆角 + 1.5dp 活力蓝紫渐变全包裹边框。
 *
 * 边框颜色与首页 IosCard 保持一致，确保全局蓝色边框包裹视觉效果统一。
 */
@Composable
private fun IosFormCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(10.dp),
                ambientColor = Color(0x1A000000),
                spotColor = Color(0x1A000000)
            )
            .background(Color.White, RoundedCornerShape(10.dp))
    ) {
        content()
    }
}

/**
 * iOS 表单行：左侧 Label（灰色）+ 右侧输入框。
 *
 * @param showDivider 是否显示底部分隔线（iOS 表单分隔线，左侧 80dp 缩进）
 */
@Composable
private fun IosFormRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    readOnly: Boolean = false,
    showDivider: Boolean = true,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    unit: String = ""
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.width(72.dp)
            )
            // iOS 表单输入：无外框，仅文字
            if (readOnly) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = appOnSurfaceVariant(),
                    modifier = Modifier.weight(1f)
                )
            } else {
                androidx.compose.material3.OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = {
                        Text(placeholder, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    trailingIcon = if (unit.isNotEmpty()) {
                        { Text(unit, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) }
                    } else null,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge
                )
            }
        }
        if (showDivider) {
            // iOS 分隔线：左侧 80dp 缩进（label + padding 对齐）
            Box(
                modifier = Modifier
                    .padding(start = 80.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(appDividerColor())
            )
        }
    }
}

/**
 * iOS 选择器行：左侧 Label + 右侧 SegmentedChoice 风格的选择按钮。
 */
@Composable
private fun IosFormSelectorRow(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    showDivider: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.width(72.dp)
            )
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                options.forEach { opt ->
                    val selected = value == opt
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .background(
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelect(opt) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            opt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onBackground,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .padding(start = 80.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(appDividerColor())
            )
        }
    }
}

/**
 * iOS 下拉选择行：左侧 Label + 右侧下拉框。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IosFormDropdownRow(
    label: String,
    displayValue: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<Pair<String, String>>,  // (code, label)
    onSelect: (String) -> Unit,
    showDivider: Boolean = true
) {
    Column {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.width(72.dp)
                )
                Text(
                    displayValue,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) }
            ) {
                options.forEach { (code, lbl) ->
                    DropdownMenuItem(
                        text = { Text(lbl) },
                         onClick = { onSelect(code); onExpandedChange(false) }
                    )
                }
            }
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .padding(start = 80.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(appDividerColor())
            )
        }
    }
}

/**
 * BMI 显示条：身体信息分组末尾独立成行展示。
 *
 * 设计要点：
 * - 上下两行布局：第一行 BMI 标签 + 右侧提示文字，第二行数值 + 徽章
 * - 单独成行：数值徽章不与其他字段并排，视觉独立
 * - 圆角浅底胶囊：包裹数值与徽章，与表单行视觉区隔
 * - 体型徽章跟随分类颜色
 *
 * @param bmiResult BmiProcessor 计算结果
 * @param showDivider 保留参数兼容（实际不显示分隔线，BMI 条本身已是末尾装饰）
 */
@Composable
private fun IosBmiDisplayRow(
    bmiResult: BmiProcessor.BmiResult,
    showDivider: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        // 第一行：BMI 标签 + 右侧提示
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "BMI",
                style = MaterialTheme.typography.bodyLarge,
                color = appOnSurfaceVariant(),
                fontWeight = FontWeight.SemiBold
            )
            if (bmiResult.valid) {
                Text(
                    "自动计算结果",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        // 第二行：数值 + 徽章 单独一行展示
        if (bmiResult.valid) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    String.format("%.1f", bmiResult.bmi),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.weight(1f))
                // 体型徽章
                val badgeColor = when (bmiResult.category) {
                    BmiProcessor.BmiCategory.THIN -> ScoreGood
                    BmiProcessor.BmiCategory.NORMAL -> ScoreExcellent
                    BmiProcessor.BmiCategory.OVERWEIGHT -> ScorePass
                    BmiProcessor.BmiCategory.OBESE -> ScoreFail
                }
                Box(
                    modifier = Modifier
                        .background(badgeColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        bmiResult.category.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            // 无有效值时显示提示胶囊
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "填写身高体重后自动计算",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

/** 返回今天的日期字符串 YYYY-MM-DD（线程安全：基于 [java.time.LocalDate]） */
private fun todayStr(): String {
    return java.time.LocalDate.now()
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd", java.util.Locale.getDefault()))
}
