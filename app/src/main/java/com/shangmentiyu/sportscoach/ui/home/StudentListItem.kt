package com.shangmentiyu.sportscoach.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appOutline
import com.shangmentiyu.sportscoach.ui.theme.appPrimary

/**
 * 学员列表项卡片：展示学员基本信息、下一节课、身高体重 BMI、底部操作按钮。
 *
 * === 性能优化4：从 StudentListTab.kt 提取为独立 Composable ===
 * - 状态全部通过参数传入（student / remaining / nextLesson / 各回调），不读取 ViewModel
 * - 使用 remember 缓存字符串拼接与 BMI 计算，避免每次重组重算
 * - 颜色提取为局部 val，避免重复调用 @Composable 颜色函数
 * - 拆分后 StudentListTab 主文件重组时，卡片本身可被 Compose 编译器跳过（参数未变则不重组）
 *
 * @param student 学员数据
 * @param remaining 剩余课时数
 * @param nextLesson 下一节课（可为 null）
 * @param showTopDivider 是否显示顶部分割线（首项不显示）
 * @param onSign 签到回调
 * @param onGrowth 成长档案回调
 * @param onEdit 编辑回调
 * @param onDelete 删除回调
 * @param onEditNextLesson 编辑下节课回调
 * @param onHeightPrediction 身高预测回调
 * @param onDietManage 饮食管理回调
 */
@Composable
internal fun StudentListItem(
    student: Student,
    remaining: Int,
    nextLesson: Lesson?,
    showTopDivider: Boolean,
    onSign: () -> Unit,
    onGrowth: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onEditNextLesson: () -> Unit,
    onHeightPrediction: () -> Unit = {},
    onDietManage: () -> Unit = {}
) {
    // === 性能优化：用 remember 缓存字符串/数值计算，避免每次重组重算 ===
    val basicInfo = remember(student.school, student.age, student.gender, student.grade) {
        val gradeLabel = com.shangmentiyu.sportscoach.core.Standards.gradeLabel(student.grade)
        buildString {
            if (student.school.isNotBlank()) append(student.school)
            if (student.age > 0) {
                if (isNotEmpty()) append(" · ")
                append("${student.age}岁")
            }
            if (student.gender.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(student.gender)
            }
            if (gradeLabel.isNotEmpty()) {
                if (isNotEmpty()) append(" · ")
                append(gradeLabel)
            }
        }
    }
    val avatarBg = remember(student.name) { avatarColorFor(student.name) }
    val bmiValue = remember(student.bmi, student.heightCm, student.weightKg) {
        if (student.bmi > 0f) student.bmi
        else if (student.heightCm > 0 && student.weightKg > 0f)
            student.weightKg / ((student.heightCm / 100f) * (student.heightCm / 100f))
        else 0f
    }
    // 颜色提取，避免每次调用函数
    val onSurfaceColor = appOnSurface()
    val onSurfaceVariantColor = appOnSurfaceVariant()
    val primaryColor = appPrimary()
    val editIconBgColor = remember(primaryColor) { primaryColor.copy(alpha = 0.12f) }

    // === 痛点二 方案A：iOS 分组列表行样式 ===
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onGrowth)
            .padding(Spacing.md)
    ) {
        // === 顶部细灰分割线（首项不显示）===
        if (showTopDivider) {
            Box(
                modifier = Modifier
                    .padding(bottom = Spacing.md)
                    .height(0.5.dp)
                    .fillMaxWidth()
                    .background(Color(0xFFE5E5EA))
            )
        }
        // === 第一行：头像 + 姓名 + 剩余课时徽章 ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(avatarBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    student.name.firstOrNull()?.toString() ?: "?",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.size(12.dp))

            Text(
                student.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = onSurfaceColor,
                modifier = Modifier.weight(1f, fill = false)
            )

            RemainingBadge(remaining)
        }

        // === 第二行：学校 · 年龄 · 性别 · 年级 ===
        if (basicInfo.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                basicInfo,
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariantColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // === 第三行：下一节课信息 ===
        if (nextLesson != null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Schedule, contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(14.dp))
                Spacer(Modifier.size(4.dp))
                Text("下一节：${nextLesson.date} ${nextLesson.time}",
                    style = MaterialTheme.typography.labelMedium,
                    color = primaryColor)
                if (!nextLesson.location.isNullOrBlank()) {
                    Spacer(Modifier.size(8.dp))
                    Icon(Icons.Outlined.LocationOn, contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(2.dp))
                    Text(nextLesson.location,
                        style = MaterialTheme.typography.labelMedium,
                        color = primaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "编辑",
                    style = MaterialTheme.typography.labelMedium,
                    color = primaryColor,
                    modifier = Modifier.clickable(onClick = onEditNextLesson)
                )
            }
        }

        // === 第四行：身高体重BMI chips ===
        if (student.heightCm > 0 || student.weightKg > 0f || bmiValue > 0f) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (student.heightCm > 0) {
                    MetricChip("${student.heightCm}cm",
                        com.shangmentiyu.sportscoach.ui.theme.LightPrimary)
                }
                if (student.weightKg > 0f) {
                    MetricChip("${student.weightKg}kg",
                        com.shangmentiyu.sportscoach.ui.theme.LightPrimary)
                }
                if (bmiValue > 0f) {
                    MetricChip("BMI ${"%.1f".format(bmiValue)}",
                        com.shangmentiyu.sportscoach.ui.theme.LightPrimary)
                }
            }
        }

        // === 第五行：底部操作按钮组（编辑 / 删除 / 签到）===
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CardActionButton(
                text = "编辑",
                onClick = onEdit,
                modifier = Modifier.weight(1f),
                type = CardActionType.NEUTRAL
            )
            CardActionButton(
                text = "删除",
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                type = CardActionType.DANGER
            )
            CardActionButton(
                text = "签到",
                onClick = onSign,
                modifier = Modifier.weight(1f),
                type = CardActionType.PRIMARY
            )
        }
    }
}

/**
 * 卡片底部按钮样式类型。
 * - [CardActionType.PRIMARY]：浅珊瑚橙填充背景 + 珊瑚橙文字（主操作：签到）
 * - [CardActionType.NEUTRAL]：浅灰填充背景 + 次级文字（中性操作：编辑）
 * - [CardActionType.DANGER]：浅红填充背景 + 红色文字（危险操作：删除）
 *
 * === UI 规范更新（前端设计美化）===
 * - 移除实线边框，改用半透明填充背景区分操作层级
 * - 圆角 12dp，与全局卡片圆角规范一致
 * - 充足垂直 padding（11dp）保证 44dp 触控区
 */
enum class CardActionType { PRIMARY, NEUTRAL, DANGER }

/**
 * 卡片底部按钮：浅色填充背景 + 文字，等宽排列，无边框。
 *
 * 设计要点：
 * - 半透明填充背景（主色 8-12% 透明度），无边框
 * - 圆角 12dp，与全局圆角规范一致
 * - 充足的垂直 padding（11dp）保证 44dp 触控区
 * - 文字居中，FontWeight.Medium
 */
@Composable
internal fun CardActionButton(
    text: String,
    onClick: () -> Unit,
    type: CardActionType,
    modifier: Modifier = Modifier
) {
    val bgColor = when (type) {
        CardActionType.PRIMARY -> appPrimary().copy(alpha = 0.12f)
        CardActionType.NEUTRAL -> appOnSurfaceVariant().copy(alpha = 0.10f)
        CardActionType.DANGER -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
    }
    val textColor = when (type) {
        CardActionType.PRIMARY -> appPrimary()
        CardActionType.NEUTRAL -> appOnSurfaceVariant()
        CardActionType.DANGER -> MaterialTheme.colorScheme.error
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            fontWeight = FontWeight.Medium
        )
    }
}
