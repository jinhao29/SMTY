package com.shangmentiyu.sportscoach.ui.diet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shangmentiyu.sportscoach.data.model.DietTemplateEntity
import com.shangmentiyu.sportscoach.data.model.MealItem
import com.shangmentiyu.sportscoach.data.repo.DietRepository
import com.shangmentiyu.sportscoach.domain.ActivityLevel
import com.shangmentiyu.sportscoach.domain.TdeeResult
import com.shangmentiyu.sportscoach.ui.AppViewModelFactory
import com.shangmentiyu.sportscoach.ui.theme.IOSCard
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appGroupedBackground
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appTextPlaceholder
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appSurface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalContentColor

/**
 * 学员饮食管理页面（3+2 饮食法）。
 *
 * UI 结构（自上而下）：
 * 1. 学员信息卡：姓名 + 已绑定方案
 * 2. 模板选择胶囊 Tab：3 套预置模板一键切换
 * 3. 训练前后黄金饮食提示卡（紫色调，强调运动场景）
 * 4. 5 餐条目卡（早 / 上午加餐 / 午 / 下午加餐 / 晚）：
 *    - 左侧时间点
 *    - 中间食谱（类别 + 食材建议）
 *    - 右侧教练备注输入框
 * 5. 应用方案按钮（主紫色，写入数据库）
 *
 * 设计：白色卡片 + 柔和阴影 + 10dp 圆角 + 主紫色 #6A5ACD，无边框。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietManageScreen(
    studentName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val vm: DietViewModel = viewModel(
        factory = AppViewModelFactory(context.applicationContext as android.app.Application)
    )

    val templates by vm.templates.collectAsState()
    val selectedTemplateId by vm.selectedTemplateId.collectAsState()
    val selectedTemplate by vm.selectedTemplate.collectAsState()
    val boundRecord by vm.boundRecord.collectAsState()
    val breakfastNote by vm.breakfastNote.collectAsState()
    val morningSnackNote by vm.morningSnackNote.collectAsState()
    val lunchNote by vm.lunchNote.collectAsState()
    val afternoonSnackNote by vm.afternoonSnackNote.collectAsState()
    val dinnerNote by vm.dinnerNote.collectAsState()
    // 5 餐自定义食材内容（空串表示使用模板默认）
    val breakfastMeals by vm.breakfastMeals.collectAsState()
    val morningSnackMeals by vm.morningSnackMeals.collectAsState()
    val lunchMeals by vm.lunchMeals.collectAsState()
    val afternoonSnackMeals by vm.afternoonSnackMeals.collectAsState()
    val dinnerMeals by vm.dinnerMeals.collectAsState()
    val toast by vm.toast.collectAsState()

    // TDEE 计算器状态
    val gender by vm.gender.collectAsState()
    val age by vm.age.collectAsState()
    val heightCm by vm.heightCm.collectAsState()
    val weightKg by vm.weightKg.collectAsState()
    val activityLevel by vm.activityLevel.collectAsState()
    val tdeeResult by vm.tdeeResult.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(studentName) {
        vm.load(studentName)
    }

    LaunchedEffect(toast) {
        toast?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearToast()
        }
    }

    Scaffold(
        containerColor = appGroupedBackground(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("饮食管理", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appSurface()
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenH, vertical = Spacing.screenV),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // 1. 学员信息 + 已绑定方案
            StudentDietHeaderCard(studentName = studentName, boundRecord = boundRecord?.templateName)

            // 2. 模板选择胶囊 Tab
            if (templates.isNotEmpty()) {
                TemplateChipRow(
                    templates = templates,
                    selectedId = selectedTemplateId,
                    onSelect = vm::selectTemplate
                )
            }

            // 3. 训练前后黄金饮食提示
            selectedTemplate?.let { tpl ->
                if (tpl.preWorkoutTip.isNotBlank() || tpl.postWorkoutTip.isNotBlank()) {
                    WorkoutTipCard(tpl)
                }

                // 4. 5 餐条目（支持教练自定义食材内容，空串表示用模板默认）
                MealCard(
                    title = "早餐",
                    time = "07:00 - 08:00",
                    icon = Icons.Outlined.Restaurant,
                    templateMealsJson = tpl.breakfast,
                    customMealsJson = breakfastMeals,
                    onCustomMealsChange = vm::updateBreakfastMeals,
                    mealsFor = vm::mealsFor,
                    encodeMealsFromText = vm::encodeMealsFromText,
                    decodeMealsToText = vm::decodeMealsToText,
                    note = breakfastNote,
                    onNoteChange = vm::updateBreakfastNote
                )
                MealCard(
                    title = "上午加餐",
                    time = "10:00 - 10:30",
                    icon = Icons.Outlined.Spa,
                    templateMealsJson = tpl.morningSnack,
                    customMealsJson = morningSnackMeals,
                    onCustomMealsChange = vm::updateMorningSnackMeals,
                    mealsFor = vm::mealsFor,
                    encodeMealsFromText = vm::encodeMealsFromText,
                    decodeMealsToText = vm::decodeMealsToText,
                    note = morningSnackNote,
                    onNoteChange = vm::updateMorningSnackNote
                )
                MealCard(
                    title = "午餐",
                    time = "12:00 - 13:00",
                    icon = Icons.Outlined.Restaurant,
                    templateMealsJson = tpl.lunch,
                    customMealsJson = lunchMeals,
                    onCustomMealsChange = vm::updateLunchMeals,
                    mealsFor = vm::mealsFor,
                    encodeMealsFromText = vm::encodeMealsFromText,
                    decodeMealsToText = vm::decodeMealsToText,
                    note = lunchNote,
                    onNoteChange = vm::updateLunchNote
                )
                MealCard(
                    title = "下午加餐",
                    time = "15:30 - 16:00",
                    icon = Icons.Outlined.Spa,
                    templateMealsJson = tpl.afternoonSnack,
                    customMealsJson = afternoonSnackMeals,
                    onCustomMealsChange = vm::updateAfternoonSnackMeals,
                    mealsFor = vm::mealsFor,
                    encodeMealsFromText = vm::encodeMealsFromText,
                    decodeMealsToText = vm::decodeMealsToText,
                    note = afternoonSnackNote,
                    onNoteChange = vm::updateAfternoonSnackNote
                )
                MealCard(
                    title = "晚餐",
                    time = "18:00 - 19:00",
                    icon = Icons.Outlined.Restaurant,
                    templateMealsJson = tpl.dinner,
                    customMealsJson = dinnerMeals,
                    onCustomMealsChange = vm::updateDinnerMeals,
                    mealsFor = vm::mealsFor,
                    encodeMealsFromText = vm::encodeMealsFromText,
                    decodeMealsToText = vm::decodeMealsToText,
                    note = dinnerNote,
                    onNoteChange = vm::updateDinnerNote
                )

                // 5. 应用方案按钮
                Button(
                    onClick = {
                        keyboard?.hide()
                        vm.applyTemplate {}
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = appPrimary(),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("应用该饮食方案给此学员", fontWeight = FontWeight.SemiBold)
                }
            }

            // 6. 折叠的"热量评估"面板（TDEE 计算器）
            TdeeEvaluationCard(
                gender = gender,
                age = age,
                heightCm = heightCm,
                weightKg = weightKg,
                activityLevel = activityLevel,
                tdeeResult = tdeeResult,
                onGenderChange = vm::updateGender,
                onAgeChange = vm::updateAge,
                onHeightChange = vm::updateHeightCm,
                onWeightChange = vm::updateWeightKg,
                onActivityLevelChange = vm::updateActivityLevel
            )

            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

/**
 * 学员信息 + 已绑定方案头部卡。
 */
@Composable
private fun StudentDietHeaderCard(studentName: String, boundRecord: String?) {
    IOSCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(appPrimary()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    studentName.firstOrNull()?.toString() ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    studentName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (boundRecord.isNullOrBlank()) {
                    Text(
                        "尚未绑定饮食方案",
                        style = MaterialTheme.typography.bodySmall,
                        color = appOnSurfaceVariant()
                    )
                } else {
                    Text(
                        "已绑定：$boundRecord",
                        style = MaterialTheme.typography.bodySmall,
                        color = appPrimary()
                    )
                }
            }
        }
    }
}

/**
 * 模板选择胶囊 Tab 行：3 个 Chip 一键切换。
 *
 * 使用 2 字简称映射，避免 `name.take(4)` 截断"减脂 / 控制体重型"时
 * 产生"减脂 / "这种带斜杠空格的怪异显示。
 */
@Composable
private fun TemplateChipRow(
    templates: List<DietTemplateEntity>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        templates.forEach { tpl ->
            val isSelected = tpl.id == selectedId
            // 2 字简称映射，保证胶囊内文字完整显示
            val shortName = when (tpl.id) {
                DietRepository.TemplateIds.REGULAR -> "常规"
                DietRepository.TemplateIds.TRAINING -> "高强"
                DietRepository.TemplateIds.FAT_LOSS -> "减脂"
                else -> tpl.name.take(2)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isSelected) appPrimary()
                        else appSurface()
                    )
                    .clickable { onSelect(tpl.id) }
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    shortName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) Color.White else appOnSurfaceVariant()
                )
            }
        }
    }
}

/**
 * 训练前后黄金饮食提示卡。
 *
 * 紫色调强调运动场景，区别于普通餐次卡。
 */
@Composable
private fun WorkoutTipCard(tpl: DietTemplateEntity) {
    IOSCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.FitnessCenter,
                contentDescription = null,
                tint = appPrimary(),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "运动前后黄金饮食".uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = appPrimary()
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        if (tpl.preWorkoutTip.isNotBlank()) {
            TipRow(label = "训练前", content = tpl.preWorkoutTip)
        }
        if (tpl.preWorkoutTip.isNotBlank() && tpl.postWorkoutTip.isNotBlank()) {
            Spacer(Modifier.height(Spacing.xs))
        }
        if (tpl.postWorkoutTip.isNotBlank()) {
            TipRow(label = "训练后", content = tpl.postWorkoutTip)
        }
    }
}

@Composable
private fun TipRow(label: String, content: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(appPrimary().copy(alpha = 0.12f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = appPrimary(),
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(
            content,
            style = MaterialTheme.typography.bodyMedium,
            color = appOnSurface()
        )
    }
}

/**
 * 单餐次条目卡：可折叠，左侧时间点，中间食谱（支持教练自定义覆写），右侧教练备注。
 *
 * - 折叠默认展开；教练可点击标题栏折叠以快速浏览全天安排。
 * - 标题栏右侧"编辑"按钮：弹出对话框，教练可输入自定义食材（每行一条，可选"类别|内容"格式）。
 * - 自定义内容非空时，食谱区显示"已自定义"小徽章；清空输入则恢复模板默认。
 *
 * @param templateMealsJson   模板默认食材 JSON
 * @param customMealsJson     教练自定义食材 JSON（空串表示用模板默认）
 * @param onCustomMealsChange 自定义食材变更回调（接收 JSON 字符串）
 * @param mealsFor            根据模板/自定义 JSON 取展示用 [MealItem] 列表
 * @param encodeMealsFromText 文本 → JSON 序列化
 * @param decodeMealsToText   JSON → 文本反序列化（用于对话框回显）
 */
@Composable
private fun MealCard(
    title: String,
    time: String,
    icon: ImageVector,
    templateMealsJson: String,
    customMealsJson: String,
    onCustomMealsChange: (String) -> Unit,
    mealsFor: (templateMeals: String, customMeals: String) -> List<MealItem>,
    encodeMealsFromText: (String) -> String,
    decodeMealsToText: (String) -> String,
    note: String,
    onNoteChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    var showEditDialog by remember { mutableStateOf(false) }

    // 实际展示的食谱列表：自定义优先，空则回退模板默认
    val meals = mealsFor(templateMealsJson, customMealsJson)
    val isCustom = customMealsJson.isNotBlank()

    IOSCard {
        // 标题行：图标 + 餐次名 + 时间 + 编辑 + 折叠箭头
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(appPrimary().copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = appPrimary(), modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall,
                    color = appOnSurfaceVariant()
                )
            }
            // 编辑自定义食材按钮
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isCustom) appPrimary().copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { showEditDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "编辑食谱",
                    tint = if (isCustom) appPrimary() else appOnSurfaceVariant(),
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(Spacing.xs))
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "折叠" else "展开",
                tint = appOnSurfaceVariant()
            )
        }

        // 食谱 + 备注（可折叠）
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(Modifier.height(Spacing.sm))
                // 自定义徽章
                if (isCustom) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(appPrimary().copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "已自定义",
                                style = MaterialTheme.typography.labelSmall,
                                color = appPrimary(),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            "清空编辑框内容可恢复模板默认",
                            style = MaterialTheme.typography.labelSmall,
                            color = appOnSurfaceVariant()
                        )
                    }
                    Spacer(Modifier.height(Spacing.xs))
                }
                // 食谱内容
                if (meals.isEmpty()) {
                    Text(
                        "暂无食谱数据",
                        style = MaterialTheme.typography.bodySmall,
                        color = appOnSurfaceVariant()
                    )
                } else {
                    meals.forEachIndexed { idx, item ->
                        if (idx > 0) Spacer(Modifier.height(Spacing.xs))
                        MealItemRow(item)
                    }
                }

                Spacer(Modifier.height(Spacing.sm))

                // 教练备注输入框
                Text(
                    "教练备注",
                    style = MaterialTheme.typography.labelSmall,
                    color = appOnSurfaceVariant(),
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(Spacing.xs))
                OutlinedTextField(
                    value = note,
                    onValueChange = onNoteChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "可填写过敏、口味、替换建议",
                            style = MaterialTheme.typography.bodySmall,
                            color = appOnSurfaceVariant()
                        )
                    },
                    singleLine = false,
                    minLines = 1,
                    maxLines = 3,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }
    }

    // 自定义食材编辑对话框
    if (showEditDialog) {
        CustomMealsEditDialog(
            title = title,
            templateMealsText = decodeMealsToText(templateMealsJson),
            initialText = decodeMealsToText(customMealsJson),
            onDismiss = { showEditDialog = false },
            onConfirm = { inputText ->
                onCustomMealsChange(encodeMealsFromText(inputText))
                showEditDialog = false
            }
        )
    }
}

/**
 * 自定义食材编辑对话框。
 *
 * - 顶部显示"模板默认"参考文本（只读，灰底）
 * - 中部多行输入框：教练可每行一条输入自定义食材，格式"类别|内容"或纯"内容"
 * - 清空所有内容后确认 = 恢复使用模板默认
 */
@Composable
private fun CustomMealsEditDialog(
    title: String,
    templateMealsText: String,
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var input by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "编辑「$title」食谱",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                Text(
                    "模板默认食谱（仅供参考）",
                    style = MaterialTheme.typography.labelSmall,
                    color = appOnSurfaceVariant(),
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(Spacing.xs))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = appGroupedBackground()
                ) {
                    Text(
                        templateMealsText.ifBlank { "（无）" },
                        style = MaterialTheme.typography.bodySmall,
                        color = appOnSurfaceVariant(),
                        modifier = Modifier.padding(Spacing.sm)
                    )
                }
                Spacer(Modifier.height(Spacing.md))
                Text(
                    "自定义食谱（每行一条，可选格式：类别|内容）",
                    style = MaterialTheme.typography.labelSmall,
                    color = appOnSurfaceVariant(),
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(Spacing.xs))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "如：\n主食|全麦面包 2 片\n优质蛋白|水煮蛋 1 个\n牛奶 250ml",
                            style = MaterialTheme.typography.bodySmall,
                            color = appOnSurfaceVariant()
                        )
                    },
                    singleLine = false,
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "提示：清空全部内容后点击确定，可恢复使用模板默认食谱。",
                    style = MaterialTheme.typography.labelSmall,
                    color = appOnSurfaceVariant()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(input) }) { Text("确定", color = appPrimary()) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun MealItemRow(item: MealItem) {
    Row(verticalAlignment = Alignment.Top) {
        // 类别小标签
        if (item.category.isNotBlank()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(appPrimary().copy(alpha = 0.08f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    item.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = appPrimary(),
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.width(Spacing.sm))
        }
        // 食材内容
        Text(
            item.content,
            style = MaterialTheme.typography.bodyMedium,
            color = appOnSurface(),
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 折叠的"热量评估"面板（TDEE 计算器）。
 *
 * UI 结构（折叠默认收起，点击标题栏展开）：
 * 1. 标题行：火焰图标 + "热量评估（TDEE 计算器）" + 折叠箭头
 * 2. 展开后：
 *    - 性别切换胶囊（男 / 女）
 *    - 年龄输入框（已自动从学员档案读取，可覆写）
 *    - 身高输入框（大圆角浅灰背景）
 *    - 体重输入框（大圆角浅灰背景）
 *    - 活动水平下拉选择框（ExposedDropdownMenuBox）
 *    - 结果卡片：BMR + TDEE 大字显示 + 减脂建议/发育期警告
 *
 * 算法纯本地，无网络请求。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TdeeEvaluationCard(
    gender: String,
    age: String,
    heightCm: String,
    weightKg: String,
    activityLevel: ActivityLevel,
    tdeeResult: TdeeResult?,
    onGenderChange: (String) -> Unit,
    onAgeChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onActivityLevelChange: (ActivityLevel) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    IOSCard {
        // 标题行：点击折叠/展开
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(appPrimary().copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.LocalFireDepartment,
                    contentDescription = null,
                    tint = appPrimary(),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "热量评估",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "TDEE 计算器 · Mifflin-St Jeor 公式",
                    style = MaterialTheme.typography.labelSmall,
                    color = appOnSurfaceVariant()
                )
            }
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "折叠" else "展开",
                tint = appOnSurfaceVariant()
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(Modifier.height(Spacing.sm))

                // 性别切换胶囊
                Text(
                    "性别",
                    style = MaterialTheme.typography.labelSmall,
                    color = appOnSurfaceVariant(),
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(Spacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    listOf("男", "女").forEach { g ->
                        val isSelected = gender == g
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) appPrimary() else appGroupedBackground())
                                .clickable { onGenderChange(g) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                g,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) Color.White else appOnSurface().copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.sm))

                // 年龄 / 身高 / 体重 输入框（大圆角浅灰背景）
                TdeeInputField(
                    label = "年龄（岁）",
                    value = age,
                    onValueChange = onAgeChange,
                    placeholder = "如 12",
                    suffix = "岁"
                )
                Spacer(Modifier.height(Spacing.sm))
                TdeeInputField(
                    label = "身高（cm）",
                    value = heightCm,
                    onValueChange = onHeightChange,
                    placeholder = "如 150",
                    suffix = "cm"
                )
                Spacer(Modifier.height(Spacing.sm))
                TdeeInputField(
                    label = "体重（kg）",
                    value = weightKg,
                    onValueChange = onWeightChange,
                    placeholder = "如 40",
                    suffix = "kg"
                )

                Spacer(Modifier.height(Spacing.sm))

                // 活动水平下拉选择框
                Text(
                    "运动强度",
                    style = MaterialTheme.typography.labelSmall,
                    color = appOnSurfaceVariant(),
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(Spacing.xs))
                ActivityLevelDropdown(
                    selected = activityLevel,
                    onSelect = onActivityLevelChange
                )

                // 结果卡片
                if (tdeeResult != null) {
                    Spacer(Modifier.height(Spacing.md))
                    TdeeResultCard(tdeeResult)
                } else {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        "请填写完整的性别 / 年龄 / 身高 / 体重后自动计算",
                        style = MaterialTheme.typography.bodySmall,
                        color = appOnSurfaceVariant()
                    )
                }
            }
        }
    }
}

/**
 * TDEE 输入框：大圆角（12dp） + 浅灰背景 + 无边框感。
 *
 * 与 [MealCard] 中的 OutlinedTextField 区分，此处强调"录入"场景，
 * 采用浅灰填充背景而非描边，视觉更柔和。
 */
@Composable
private fun TdeeInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    suffix: String
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = appOnSurfaceVariant(),
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(Spacing.xs))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = appTextPlaceholder()
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedContainerColor = appGroupedBackground(),
                unfocusedContainerColor = appGroupedBackground(),
                disabledContainerColor = appGroupedBackground(),
                focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedLabelColor = appPrimary(),
                cursorColor = appPrimary()
            ),
            trailingIcon = {
                Text(
                    suffix,
                    style = MaterialTheme.typography.labelMedium,
                    color = appOnSurfaceVariant()
                )
            }
        )
    }
}

/**
 * 活动水平下拉选择框（DropdownMenu）。
 *
 * 5 个选项对应 [ActivityLevel] 枚举，下拉显示中文标签 + 系数。
 *
 * 实现说明（v2 修复点击不响应问题）：
 * - 旧实现：OutlinedTextField.readOnly=true + 外部 clickable 修饰符
 *   → TextField 内部消费点击事件，clickable 不触发，下拉菜单弹不出来
 * - 新实现：Box 包裹整体并加 clickable，OutlinedTextField 设置 enabled=false
 *   完全禁用 TextField 的点击消费，让 Box 的 clickable 正常触发下拉菜单
 * - 保留 OutlinedTextField 仅用于视觉一致（圆角浅灰背景）
 */
@Composable
private fun ActivityLevelDropdown(
    selected: ActivityLevel,
    onSelect: (ActivityLevel) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
    ) {
        // 触发器：浅灰背景的只读输入框 + 箭头图标
        // enabled=false 防止 TextField 内部消费点击事件，让外层 Box 的 clickable 生效
        OutlinedTextField(
            value = "${selected.label}  (×${selected.factor})",
            onValueChange = {},
            readOnly = true,
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                disabledContainerColor = appGroupedBackground(),
                disabledTextColor = appOnSurface(),
                disabledBorderColor = Color.Transparent,
                disabledTrailingIconColor = appOnSurfaceVariant(),
                disabledLabelColor = appOnSurfaceVariant()
            ),
            trailingIcon = {
                Icon(
                    Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                    tint = appOnSurfaceVariant()
                )
            }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ActivityLevel.entries.forEach { level ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                level.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (level == selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (level == selected) appPrimary() else appOnSurface()
                            )
                            Text(
                                "系数 ×${level.factor}",
                                style = MaterialTheme.typography.labelSmall,
                                color = appOnSurfaceVariant()
                            )
                        }
                    },
                    onClick = {
                        onSelect(level)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * TDEE 结果卡片：显示 BMR / TDEE / 减脂建议或发育期警告。
 *
 * 视觉：浅紫渐变背景 + 大字 TDEE 数值 + 区分成人/发育期不同提示色。
 */
@Composable
private fun TdeeResultCard(result: TdeeResult) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        appPrimary().copy(alpha = 0.08f),
                        appPrimary().copy(alpha = 0.03f)
                    )
                )
            )
            .padding(Spacing.md)
    ) {
        Column {
            // 顶部标签
            Text(
                "每日总热量评估".uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = appPrimary(),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(Spacing.xs))

            // 大字 TDEE 数值
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${result.tdee.toInt()}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = appPrimary()
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "大卡 / 天",
                    style = MaterialTheme.typography.bodyMedium,
                    color = appOnSurfaceVariant(),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "该学员每日所需的总热量（TDEE）",
                style = MaterialTheme.typography.bodySmall,
                color = appOnSurfaceVariant()
            )

            Spacer(Modifier.height(Spacing.md))

            // BMR 行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "基础代谢率 BMR",
                    style = MaterialTheme.typography.bodyMedium,
                    color = appOnSurfaceVariant()
                )
                Text(
                    "${result.bmr.toInt()} 大卡",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = appOnSurface()
                )
            }

            // 减脂建议 / 发育期警告
            if (result.isAdult && result.deficitAdvice != null) {
                Spacer(Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE8F5E9))   // 柔和浅绿
                        .padding(Spacing.sm),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("减脂建议", fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        "建议每日热量缺口 ${result.deficitAdvice} 大卡（约 300~500 大卡），即摄入 ${result.tdee.toInt() - result.deficitAdvice} 大卡。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2E7D32)
                    )
                }
            } else if (!result.isAdult && result.warningText != null) {
                Spacer(Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFFF3E0))   // 柔和浅橙警示
                        .padding(Spacing.sm),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("⚠ 注意", fontWeight = FontWeight.SemiBold, color = Color(0xFFE65100))
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        result.warningText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE65100)
                    )
                }
            }
        }
    }
}
