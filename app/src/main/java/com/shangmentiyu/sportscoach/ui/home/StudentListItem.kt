package com.shangmentiyu.sportscoach.ui.home

import com.shangmentiyu.sportscoach.ui.theme.ShadowTokens
import androidx.compose.foundation.background
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
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
    expireDate: String?,
    onSign: () -> Unit,
    onGrowth: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onHeightPrediction: () -> Unit = {},
    onDietManage: () -> Unit = {}
) {
    // === 性能优化：用 remember 缓存字符串/数值计算，避免每次重组重算 ===
    val infoLine = remember(student.grade, student.phone) {
        val gradeLabel = com.shangmentiyu.sportscoach.core.Standards.gradeFullLabel(student.grade)
        buildString {
            if (gradeLabel.isNotBlank()) append(gradeLabel)
            if (student.phone.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(student.phone)
            }
        }
    }
    val avatarBg = avatarColorFor(student.name)
    // 颜色提取，避免每次调用函数
    val onSurfaceColor = appOnSurface()
    val onSurfaceVariantColor = appOnSurfaceVariant()
    val primaryColor = appPrimary()
    val editIconBgColor = remember(primaryColor) { primaryColor.copy(alpha = 0.12f) }

    // === 参考图复刻：独立白色卡片（16dp 大圆角 + 4dp 柔和弥散阴影）===
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = ShadowTokens.softAmbient,
                spotColor = ShadowTokens.softSpot
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onGrowth)
                .padding(Spacing.md)
        ) {
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
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = onSurfaceColor,
                modifier = Modifier.weight(1f, fill = false)
            )

            RemainingBadge(remaining)
        }

        // === 第二行：年级 · 联系方式 ===
        if (infoLine.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                infoLine,
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariantColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // === 第三行：到期日 ===
        if (!expireDate.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "到期：$expireDate",
                style = MaterialTheme.typography.labelMedium,
                color = primaryColor
            )
        }

        // === 第四行：底部操作按钮组（编辑 / 删除 / 签到）===
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
/**
 * 卡片底部按钮：圆角胶囊样式（RoundedCornerShape(50)），颜色严格统一为珊瑚橙。
 *
 * 设计要点（参考图复刻）：
 * - 主操作（签到）：珊瑚橙背景 #FF6B47 + 白色文字
 * - 次要操作（编辑/删除）：浅珊瑚橙填充背景（主色 10% 透明度）+ 珊瑚橙文字
 * - 无任何实线边框，50dp 胶囊圆角
 */
@Composable
internal fun CardActionButton(
    text: String,
    onClick: () -> Unit,
    type: CardActionType,
    modifier: Modifier = Modifier
) {
    val primary = appPrimary()
    val bgColor = when (type) {
        CardActionType.PRIMARY -> primary
        CardActionType.NEUTRAL -> primary.copy(alpha = 0.10f)
        CardActionType.DANGER -> primary.copy(alpha = 0.10f)
    }
    val textColor = when (type) {
        CardActionType.PRIMARY -> Color.White
        CardActionType.NEUTRAL -> primary
        CardActionType.DANGER -> primary
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
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
