package com.shangmentiyu.sportscoach.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 参考图复刻：现代化下拉选择器（替换旧单线框下拉样式）。
 *
 * - 收起态：白色卡片（大圆角 16dp + 柔和阴影），左图标 + 中间文字 + 右侧 ⌄ 箭头
 * - 展开态：独立白色卡片，大圆角 16dp、柔和阴影，无 Material 生硬边框
 * - 菜单项：左侧小图标 + 中间文字 + 右侧圆形单选框
 *   （未选中 = 浅灰空圈 #D1D5DB；选中 = 实心珊瑚橙圈 #FF6B47，
 *   且选中项文字与图标同步变珊瑚橙，未选中项保持深灰）
 */
@Composable
fun <T> StyledDropdown(
    selected: T?,
    options: List<T>,
    optionLabel: (T) -> String,
    optionIcon: (T) -> ImageVector,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "请选择",
    // 数据源为空时收起态显示的占位文案（如"暂无教练"）
    emptyText: String = "暂无数据",
    // === v52 闪退加固：数据源为空时自动禁用 ===
    // options 为空（如 Room Flow 首帧未到达 / 学员列表为空 / 无历史记忆）时，
    // 点击不再展开空菜单，显示 emptyText 占位文字，绝不允许访问 items[0] 等越界行为。
    enabled: Boolean = options.isNotEmpty()
) {
    var expanded by remember { mutableStateOf(false) }

    // v66：主题感知（深色模式随色板变蓝/变深底，不再硬编码浅色值）
    val selectedCoral = MaterialTheme.colorScheme.primary
    val unselectedGray = MaterialTheme.colorScheme.onSurfaceVariant
    val emptyCircle = MaterialTheme.colorScheme.outline

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = ShadowTokens.softAmbient,
                    spotColor = ShadowTokens.softSpot
                )
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (selected != null) optionIcon(selected) else Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = unselectedGray,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = when {
                    selected != null -> optionLabel(selected)
                    options.isEmpty() -> emptyText
                    else -> placeholder
                },
                color = if (selected != null) MaterialTheme.colorScheme.onSurface else unselectedGray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = unselectedGray,
                modifier = Modifier.size(20.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White,
            shadowElevation = 4.dp
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelected(option)
                            expanded = false
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = optionIcon(option),
                        contentDescription = null,
                        tint = if (isSelected) selectedCoral else unselectedGray,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = optionLabel(option),
                        color = if (isSelected) selectedCoral else MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .then(
                                if (isSelected) {
                                    Modifier.background(selectedCoral)
                                } else {
                                    Modifier
                                        .border(2.dp, emptyCircle, CircleShape)
                                        .background(Color.White, CircleShape)
                                }
                            )
                    )
                }
            }
        }
    }
}
