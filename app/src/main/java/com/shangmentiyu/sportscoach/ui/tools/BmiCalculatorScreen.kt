package com.shangmentiyu.sportscoach.ui.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangmentiyu.sportscoach.core.BmiProcessor
import com.shangmentiyu.sportscoach.ui.theme.PrimaryButton
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appBackground
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.AppTextFieldShape
import com.shangmentiyu.sportscoach.ui.theme.appTextFieldColors

/**
 * BMI 计算器页面。
 *
 * 独立工具页，教练可直接输入身高体重快速计算 BMI，
 * 不依赖学员数据，复用 [BmiProcessor] 纯逻辑单元。
 *
 * UI 遵循浅色珊瑚橙主题：暖白背景 + 纯白卡片 + 珊瑚橙强调色。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BmiCalculatorScreen(
    onBack: () -> Unit
) {
    var heightText by remember { mutableStateOf("") }
    var weightText by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<BmiProcessor.BmiResult?>(null) }

    Scaffold(
        containerColor = appBackground(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "BMI 计算器",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = appOnSurface()
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回",
                            tint = appOnSurface()
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appBackground()
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenH, vertical = Spacing.screenV),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            // === 输入卡片 ===
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = Color(0x0D000000),
                        spotColor = Color(0x14000000)
                    )
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    text = "输入数据",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = appOnSurface()
                )

                OutlinedTextField(
                    value = heightText,
                    onValueChange = { heightText = it.filter { c -> c.isDigit() } },
                    label = { Text("身高") },
                    suffix = { Text("cm", color = appOnSurfaceVariant()) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                shape = AppTextFieldShape,
                colors = appTextFieldColors(),
                )

                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("体重") },
                    suffix = { Text("kg", color = appOnSurfaceVariant()) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                shape = AppTextFieldShape,
                colors = appTextFieldColors(),
                )

                PrimaryButton(
                    text = "计算 BMI",
                    onClick = {
                        val h = heightText.toIntOrNull() ?: 0
                        val w = weightText.toFloatOrNull() ?: 0f
                        result = BmiProcessor.compute(h, w)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // === 结果卡片 ===
            AnimatedVisibility(
                visible = result?.valid == true,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val res = result ?: return@AnimatedVisibility
                BmiResultCard(bmi = res.bmi, category = res.category)
            }

            // === 参考标准卡片 ===
            BmiStandardCard()

            Spacer(Modifier.height(80.dp))
        }
    }
}

/**
 * BMI 结果展示卡片：大号数值 + 体型标签 + 珊瑚橙渐变背景。
 */
@Composable
private fun BmiResultCard(
    bmi: Float,
    category: BmiProcessor.BmiCategory
) {
    val (bgColor, textColor) = when (category) {
        BmiProcessor.BmiCategory.THIN -> Color(0xFF42A5F5) to Color.White
        BmiProcessor.BmiCategory.NORMAL -> appPrimary() to Color.White
        BmiProcessor.BmiCategory.OVERWEIGHT -> Color(0xFFFFA726) to Color.White
        BmiProcessor.BmiCategory.OBESE -> Color(0xFFEF5350) to Color.White
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color(0x1A000000),
                spotColor = Color(0x1A000000)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(bgColor, bgColor.copy(alpha = 0.8f))
                )
            )
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(
            text = "BMI 值",
            fontSize = 14.sp,
            color = textColor.copy(alpha = 0.8f)
        )
        Text(
            text = BmiProcessor.format(bmi),
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.25f))
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Text(
                text = category.label,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        }
    }
}

/**
 * BMI 参考标准卡片（中国成人 BMI 标准）。
 */
@Composable
private fun BmiStandardCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color(0x0D000000),
                spotColor = Color(0x14000000)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            text = "参考标准",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = appOnSurface()
        )
        BmiStandardRow(label = "偏瘦", range = "< 18.5", color = Color(0xFF42A5F5))
        BmiStandardRow(label = "正常", range = "18.5 ~ 24", color = appPrimary())
        BmiStandardRow(label = "超重", range = "24 ~ 28", color = Color(0xFFFFA726))
        BmiStandardRow(label = "肥胖", range = "≥ 28", color = Color(0xFFEF5350))
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = "注：中国成人 BMI 标准（GB/T 26343-2010），青少年仅作参考。",
            fontSize = 11.sp,
            color = appOnSurfaceVariant()
        )
    }
}

@Composable
private fun BmiStandardRow(
    label: String,
    range: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        Text(
            text = label,
            fontSize = 14.sp,
            color = appOnSurface(),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = range,
            fontSize = 13.sp,
            color = appOnSurfaceVariant()
        )
    }
}
