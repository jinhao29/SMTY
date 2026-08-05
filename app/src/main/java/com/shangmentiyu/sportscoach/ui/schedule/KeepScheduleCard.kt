package com.shangmentiyu.sportscoach.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangmentiyu.sportscoach.data.model.Schedule
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appOutline
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appSurface

/**
 * 课表课程卡片（Keep 风格，模块二性能重组提取）。
 *
 * 从 [ScheduleScreen] 提取为独立 @Composable：
 * - Schedule 数据类已是 @Stable，卡片作为独立 composable 后，参数变化时
 *   只有本卡片重组，不再波及整个列表（重组隔离）。
 * - 视觉规格：白色卡片 + 珊瑚橙垂直装饰线 + 学员首字母渐变头像 + 珊瑚橙"训练课"胶囊标签。
 * - 过去日期叠加 0.4f 透明度，与 isActive=false 的 0.5f 相乘降级。
 *
 * @param schedule 目标排课
 * @param isPastDate 是否为过去日期（视觉降级 + 阻止操作）
 * @param selectionMode 多选模式（左侧显示复选框）
 * @param isSelected 多选模式下是否已选中
 * @param onClick 点击回调
 * @param onLongClick 长按回调
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun KeepScheduleCard(
    schedule: Schedule,
    isPastDate: Boolean = false,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // === Bug 修复3：过去日期叠加 0.4f 透明度，与 isActive=false 的 0.5f 叠加 ===
    val baseAlpha = if (schedule.isActive) 1f else 0.5f
    val pastAlpha = if (isPastDate) 0.4f else 1f
    val inactiveAlpha = baseAlpha * pastAlpha
    // 选中态叠加轻微珊瑚橙边框高亮
    val selectedBorder = if (isSelected) appPrimary() else Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.08f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(appSurface())
            .border(width = if (isSelected) 2.dp else 0.dp, color = selectedBorder, shape = RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .graphicsLayerAlpha(inactiveAlpha)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // === 多选模式：左侧圆形复选框（选中=珊瑚橙实心+白色勾，未选中=空心圆环）===
            if (selectionMode) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) appPrimary() else Color.Transparent)
                        .border(
                            width = 2.dp,
                            color = if (isSelected) appPrimary() else appOutline(),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.sm))
            }

            // === 左侧：圆角矩形学员头像（珊瑚橙渐变底色 + 首字母白色）===
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFF6B47),  // 珊瑚橙
                                Color(0xFFFFA078)   // 浅珊瑚橙
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = schedule.studentName.firstOrNull()?.toString() ?: "?",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(Spacing.md))

            // === 中间：学员名 + 详情（weight=1 撑满剩余空间）===
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 第一行：学员名（纯黑加粗）+ 过去角标
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        text = schedule.studentName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = appOnSurface(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isPastDate) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(appOnSurfaceVariant().copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "已过去",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = appOnSurfaceVariant()
                            )
                        }
                    }
                    if (!schedule.isActive) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(appOnSurfaceVariant().copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "已暂停",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = appOnSurfaceVariant()
                            )
                        }
                    }
                }
                // 第二行：课程时间（珊瑚橙）· 地点 · 教练姓名（灰色 #6B6B6B 小字号，点号分隔）
                // v40 任务2b：时间部分统一珊瑚橙 #FF6B47
                val timeText = "${schedule.startTime}-${schedule.endTime()}"
                val metaText = buildString {
                    if (schedule.location.isNotBlank()) {
                        append(schedule.location)
                    }
                    if (schedule.coachName.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append("教练：${schedule.coachName}")
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = timeText,
                        fontSize = 12.sp,
                        color = appPrimary(),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    if (metaText.isNotBlank()) {
                        Text(
                            text = "· $metaText",
                            fontSize = 12.sp,
                            color = appOnSurfaceVariant(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }

            // === 最右侧：珊瑚橙"训练课"胶囊标签（课时类型）===
            if (schedule.lessonType.isNotBlank()) {
                Spacer(Modifier.width(Spacing.sm))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(appPrimary().copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = schedule.lessonType,
                        fontSize = 11.sp,
                        color = appPrimary(),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }

        // === 左侧珊瑚橙垂直装饰细线（卡片左边缘，强调品牌色）===
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(width = 3.dp, height = 36.dp)
                .background(appPrimary())
        )
    }
}

/**
 * 应用透明度到整个组件（通过 graphicsLayer）。
 * 用于过去日期/已暂停卡片的视觉降级。
 */
internal fun Modifier.graphicsLayerAlpha(alpha: Float): Modifier =
    this.then(
        Modifier.graphicsLayer { this.alpha = alpha }
    )
