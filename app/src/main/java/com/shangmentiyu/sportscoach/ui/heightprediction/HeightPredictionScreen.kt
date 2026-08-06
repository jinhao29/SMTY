package com.shangmentiyu.sportscoach.ui.heightprediction

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.domain.HeightPredictionResult
import org.koin.androidx.compose.koinViewModel
import com.shangmentiyu.sportscoach.ui.theme.FloatingSnackbarHost
import com.shangmentiyu.sportscoach.ui.theme.BrandGradientStart
import com.shangmentiyu.sportscoach.ui.theme.IOSCard
import com.shangmentiyu.sportscoach.ui.theme.PrimaryButton
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appDividerColor
import com.shangmentiyu.sportscoach.ui.theme.appGroupedBackground
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSuccessContainer
import com.shangmentiyu.sportscoach.ui.theme.appOnWarningContainer
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appSurface
import com.shangmentiyu.sportscoach.ui.theme.AppTextFieldShape
import com.shangmentiyu.sportscoach.ui.theme.appTextFieldColors

// === 修正值语义色（仅用于小字修正值文本，非卡片背景色块） ===
// 加分绿 / 扣分橙，v48 起走主题令牌（appOnSuccessContainer / appOnWarningContainer）

/**
 * 身高遗传潜力与后天预测页面。
 *
 * UI 结构（自上而下）：
 * 1. 学员信息卡（姓名/性别/年龄）
 * 2. 遗传数据表单卡（父亲身高 / 母亲身高）
 * 3. 生活习惯表单卡（日均睡眠 / 营养评分 1-5 / 每周运动分钟）
 * 4. 保存按钮（写入 Room 数据库）
 * 5. 预测结果卡（大字预测身高 + 修正值 + 区间 + 建议 + 骨龄警告）
 * 6. 医学免责声明（浅灰小字，硬编码）
 *
 * 设计：白色卡片 + 4dp 柔和阴影 + 10dp 圆角 + 主紫色 #6A5ACD，
 * 完全无边框，遵循项目统一 iOS Inset Grouped 风格。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeightPredictionScreen(
    studentName: String,
    onBack: () -> Unit
) {
        val vm: HeightPredictionViewModel = koinViewModel()

    val student by vm.studentInfo.collectAsStateWithLifecycle()
    val fatherHeight by vm.fatherHeight.collectAsStateWithLifecycle()
    val motherHeight by vm.motherHeight.collectAsStateWithLifecycle()
    val avgSleepHours by vm.avgSleepHours.collectAsStateWithLifecycle()
    val nutritionScore by vm.nutritionScore.collectAsStateWithLifecycle()
    val sportsMinsPerWeek by vm.sportsMinsPerWeek.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(studentName) {
        vm.loadStudent(studentName)
    }

    LaunchedEffect(toast) {
        toast?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            vm.clearToast()
        }
    }

    Scaffold(
        containerColor = appGroupedBackground(),
        snackbarHost = { FloatingSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("身高预测", fontWeight = FontWeight.SemiBold) },
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
            StudentInfoCard(student)

            IOSCard {
                FormSectionHeader("遗传数据", Icons.Outlined.Height)
                Spacer(Modifier.height(Spacing.sm))
                NumberInputRow(
                    label = "父亲身高",
                    value = fatherHeight,
                    suffix = "cm",
                    onValueChange = vm::updateFatherHeight
                )
                FormDivider()
                NumberInputRow(
                    label = "母亲身高",
                    value = motherHeight,
                    suffix = "cm",
                    onValueChange = vm::updateMotherHeight
                )
            }

            IOSCard {
                FormSectionHeader("生活习惯", Icons.Outlined.Bedtime)
                Spacer(Modifier.height(Spacing.sm))
                NumberInputRow(
                    label = "日均睡眠",
                    value = avgSleepHours,
                    suffix = "小时",
                    decimal = true,
                    onValueChange = vm::updateAvgSleepHours
                )
                FormDivider()
                NutritionScoreRow(
                    selected = nutritionScore,
                    onSelect = vm::updateNutritionScore
                )
                FormDivider()
                NumberInputRow(
                    label = "每周运动",
                    value = sportsMinsPerWeek,
                    suffix = "分钟",
                    onValueChange = vm::updateSportsMinsPerWeek
                )
            }

            PrimaryButton(
                text = "保存身体数据",
                onClick = {
                    keyboard?.hide()
                    vm.save {}
                },
                modifier = Modifier.fillMaxWidth()
            )

            result?.let { r ->
                PredictionResultCard(r)
            }

            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

/** 学员信息卡：头像 + 姓名 + 性别/年龄 */
@Composable
private fun StudentInfoCard(student: Student?) {
    IOSCard {
        if (student == null) {
            Text(
                "加载学员数据中…",
                color = appOnSurface().copy(alpha = 0.6f)
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(BrandGradientStart),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        student.name.firstOrNull()?.toString() ?: "?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                Column {
                    Text(
                        student.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    val info = buildString {
                        if (student.gender.isNotBlank()) append(student.gender)
                        if (student.age > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("${student.age}岁")
                        }
                    }
                    if (info.isNotBlank()) {
                        Text(
                            info,
                            style = MaterialTheme.typography.bodySmall,
                            color = appOnSurface().copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

/** 表单分组小标题：图标 + 大写文字（主紫色） */
@Composable
private fun FormSectionHeader(text: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = appPrimary(),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = appPrimary()
        )
    }
}

/** 数字输入行：标签 + 输入框 + 后缀单位 */
@Composable
private fun NumberInputRow(
    label: String,
    value: String,
    suffix: String,
    decimal: Boolean = false,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.width(80.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number
            ),
            trailingIcon = {
                Text(
                    suffix,
                    style = MaterialTheme.typography.bodySmall,
                    color = appOnSurface().copy(alpha = 0.5f)
                )
            },
            colors = appTextFieldColors(),
            shape = AppTextFieldShape
        )
    }
}

/** 营养评分行：1-5 圆形 chip 单选 */
@Composable
private fun NutritionScoreRow(
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "营养评分",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.width(80.dp)
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (1..5).forEach { score ->
                val isSelected = score == selected
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) appPrimary()
                            else appOnSurface().copy(alpha = 0.08f)
                        )
                        .clickable { onSelect(score) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        score.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) Color.White
                                else appOnSurface().copy(alpha = 0.6f),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/** 表单内分隔线：浅灰 0.5dp */
@Composable
private fun FormDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(appDividerColor())
    )
}

/**
 * 预测结果卡：大字预测身高 + 遗传靶身高 + 修正值 + 区间 + 建议 + 骨龄警告 + 免责声明。
 *
 * 修正值显示规则：
 * - 加分 → 绿色 "+X.X cm" + 正向短语
 * - 扣分 → 橙色 "-X.X cm" + 建议短语
 */
@Composable
private fun PredictionResultCard(r: HeightPredictionResult) {
    IOSCard(contentPadding = Spacing.lg) {
        Text(
            "预测成年身高",
            style = MaterialTheme.typography.labelLarge,
            color = appOnSurface().copy(alpha = 0.6f),
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(Spacing.xs))

        // 大字预测身高
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "%.1f".format(r.adjustedHeight),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = appPrimary()
            )
            Text(
                "cm",
                style = MaterialTheme.typography.titleMedium,
                color = appOnSurface().copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Spacer(Modifier.height(Spacing.sm))

        // 遗传靶身高 + 修正值
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "遗传靶身高 ",
                style = MaterialTheme.typography.bodyMedium,
                color = appOnSurface().copy(alpha = 0.7f)
            )
            Text(
                "%.1f cm".format(r.targetHeight),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(Spacing.sm))
            val sign = if (r.adjustment > 0) "+" else ""
            val adjColor = when {
                r.adjustment > 0 -> appOnSuccessContainer()
                r.adjustment < 0 -> appOnWarningContainer()
                else -> appOnSurface().copy(alpha = 0.5f)
            }
            Text(
                "${sign}%.1f cm".format(r.adjustment),
                style = MaterialTheme.typography.labelLarge,
                color = adjColor,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(Spacing.xs))

        // 预测区间
        Text(
            "预测区间：%.1f ~ %.1f cm".format(r.lowerBound, r.upperBound),
            style = MaterialTheme.typography.bodySmall,
            color = appOnSurface().copy(alpha = 0.6f)
        )

        // 骨骼闭合警告
        r.boneAgeWarning?.let { warning ->
            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(appOnWarningContainer())
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    warning,
                    style = MaterialTheme.typography.bodySmall,
                    color = appOnWarningContainer(),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 综合建议文案
        Spacer(Modifier.height(Spacing.sm))
        Text(
            r.adviceText,
            style = MaterialTheme.typography.bodySmall,
            color = appOnSurface().copy(alpha = 0.75f)
        )

        // 分隔线
        Spacer(Modifier.height(Spacing.md))
        FormDivider()
        Spacer(Modifier.height(Spacing.sm))

        // === 医学免责声明（硬编码，浅灰小字） ===
        Text(
            "本预测基于遗传学算法与用户生活习惯综合评估，结果仅供参考，精准预测请前往医院进行骨龄检测。",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = appOnSurface().copy(alpha = 0.4f),
            lineHeight = 14.sp
        )
    }
}
