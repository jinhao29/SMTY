package com.shangmentiyu.sportscoach.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 全局按钮系统（Button System）— v39 设计令牌统一
 *
 * 设计原则：
 * 1. 单一主强调：只有最关键操作用 PrimaryButton（保存/确认/添加）
 * 2. 层级清晰：Primary（填充）> Secondary（描边）> Text（文字）
 * 3. 触碰目标 ≥ 44dp（iOS HIG）
 * 4. 圆角 10dp（与 IOSCard 一致）
 * 5. 字重 SemiBold，字号 16sp（主按钮）
 *
 * 物理场景：教练户外强光下操作 → 按钮必须醒目、易点击
 */

/**
 * 主按钮（Primary Button）— 填充珊瑚橙背景 + 白字。
 *
 * 用于：保存课程、确认删除、添加学员、签到等最关键操作。
 * 每个屏幕最多出现 1 个 PrimaryButton，避免强调失焦。
 *
 * 视觉规格：
 * - 背景：appPrimary()（珊瑚橙 #FF6B47）
 * - 文字：Color.White，SemiBold，16sp
 * - 圆角：10dp（与 IOSCard 一致）
 * - 高度：44dp（iOS HIG 触碰目标最小值）
 * - 阴影：无（Material3 默认禁用 elevation，保持扁平高级感）
 *
 * @param text 按钮文字
 * @param onClick 点击回调
 * @param modifier 修饰符（默认 fillMaxWidth）
 * @param icon 可选前缀图标（如 Icons.Outlined.Add）
 * @param enabled 是否启用
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = appPrimary(),
            contentColor = Color.White,
            disabledContainerColor = appPrimary().copy(alpha = 0.38f),
            disabledContentColor = Color.White.copy(alpha = 0.38f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp
        ),
        enabled = enabled
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
    }
}

/**
 * 次按钮（Secondary Button）— 描边样式。
 *
 * 用于：取消、次要操作、非破坏性辅助操作。
 * 与 PrimaryButton 搭配使用时，Secondary 总是出现在 Primary 左侧或下方。
 *
 * 视觉规格：
 * - 边框：appOutline() 1dp
 * - 背景：透明
 * - 文字：appOnSurface()（主文字色），Medium，15sp
 * - 圆角：10dp
 * - 高度：44dp
 *
 * @param text 按钮文字
 * @param onClick 点击回调
 * @param modifier 修饰符
 * @param enabled 是否启用
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = appOnSurface(),
            disabledContentColor = appOnSurface().copy(alpha = 0.38f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (enabled) appOutline() else appOutline().copy(alpha = 0.38f)
        ),
        enabled = enabled
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp
        )
    }
}

/**
 * 危险按钮（Danger Button）— 红色填充背景 + 白字。
 *
 * 用于：删除学员、清空全部排课等不可撤销的破坏性操作。
 * 必须配合二次确认对话框使用。
 *
 * 视觉规格：
 * - 背景：MaterialTheme.colorScheme.error（红色）
 * - 文字：Color.White，SemiBold，16sp
 * - 圆角：10dp
 * - 高度：44dp
 *
 * @param text 按钮文字（如"删除"、"清空全部"）
 * @param onClick 点击回调
 * @param modifier 修饰符
 * @param icon 可选前缀图标（如 Icons.Outlined.Delete）
 */
@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = Color.White,
            disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.38f),
            disabledContentColor = Color.White.copy(alpha = 0.38f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp
        ),
        enabled = enabled
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
    }
}

/**
 * 文字按钮（Ghost Button）— 无背景无边框。
 *
 * 用于：对话框"取消"、列表项"查看全部"、次要链接操作。
 *
 * 视觉规格：
 * - 背景：透明
 * - 文字：appPrimary()（珊瑚橙）或 appOnSurfaceVariant()（次级灰）
 * - 字重 Medium，14sp
 *
 * @param text 按钮文字
 * @param onClick 点击回调
 * @param modifier 修饰符
 * @param destructive 是否破坏性（true=红色文字，false=主色文字）
 */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (destructive) MaterialTheme.colorScheme.error else appPrimary()
        )
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}
