package com.shangmentiyu.sportscoach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangmentiyu.sportscoach.ui.theme.CardBackground
import com.shangmentiyu.sportscoach.ui.theme.DataChipBg
import com.shangmentiyu.sportscoach.ui.theme.DataChipText
import com.shangmentiyu.sportscoach.ui.theme.DeleteButtonBg
import com.shangmentiyu.sportscoach.ui.theme.DeleteButtonText
import com.shangmentiyu.sportscoach.ui.theme.EditButtonBg
import com.shangmentiyu.sportscoach.ui.theme.EditButtonText
import com.shangmentiyu.sportscoach.ui.theme.LessonTypeChipBg
import com.shangmentiyu.sportscoach.ui.theme.LessonTypeChipText
import com.shangmentiyu.sportscoach.ui.theme.RemainingChipBg
import com.shangmentiyu.sportscoach.ui.theme.RemainingChipText
import com.shangmentiyu.sportscoach.ui.theme.SignButtonEnd
import com.shangmentiyu.sportscoach.ui.theme.SignButtonStart

/**
 * v36 视觉重构：全局通用深色卡片组件（确立为 App UI 标准）。
 *
 * 设计令牌（参考学员列表最新截图）：
 * - 卡片背景：#1C1C1E（[CardBackground]）
 * - 卡片圆角：统一 20dp
 * - 柔和阴影：offsetY=4dp, blurRadius=12dp, color=#0A000000
 * - 主标题：纯白 #FFFFFF（[CardOnDark]）
 * - 副标题/标签：浅灰 #A0A0A5（[CardSubOnDark]）
 * - 无 border，仅依赖阴影与深色背景区隔层级
 *
 * 内置胶囊组件（与设计令牌严格对齐）：
 * - [DarkChip_LessonType]：课时类型标签（深紫底 + 浅紫字）
 * - [DarkChip_Remaining]：剩余课时标签（深紫底 + 浅紫字）
 * - [DarkChip_Data]：身高/年龄等数据标签（深灰底 + 青字）
 * - [DarkButton_Edit]：编辑按钮（深灰底 + 浅灰字）
 * - [DarkButton_Delete]：删除按钮（深红底 + 红字）
 * - [DarkButton_Sign]：签到按钮（青色渐变底 + 白字）
 *
 * 使用方式：
 * ```
 * BaseDarkCard {
 *     Row { ... 头像、姓名、剩余课时 ... }
 *     Row { ... 数据标签 ... }
 *     Row { DarkButton_Edit(...); DarkButton_Delete(...); DarkButton_Sign(...) }
 * }
 * ```
 *
 * @param modifier 外部 Modifier
 * @param cornerRadius 圆角，默认 20dp
 * @param contentPadding 内边距，默认 16dp
 * @param content 卡片内容
 */
@Composable
fun BaseDarkCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    // v37 性能优化：移除额外的 .shadow() modifier，改用 Card 原生 elevation
    // 原实现同时使用 Card + .shadow() 导致双重 shadow 绘制，滑动时每帧都创建 RenderNode
    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground   // #1C1C1E
        ),
        // 使用 Card 原生 elevation，走 Material3 优化路径（native RenderNode 缓存）
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * 深色卡内"课时类型"胶囊标签。
 *
 * 设计：深紫底（[LessonTypeChipBg] #2D2D31）+ 浅紫字（[LessonTypeChipText] #B8A4FF）。
 * 用于课程卡片上的"训练课"/"私教课"等课时类型标识。
 *
 * @param text 标签文字（如 "训练课"）
 */
@Composable
fun DarkChip_LessonType(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = LessonTypeChipText,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(LessonTypeChipBg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/**
 * 深色卡内"剩余课时"胶囊标签。
 *
 * 设计：深紫底（[RemainingChipBg] #2D2D31）+ 浅紫字（[RemainingChipText] #B8A4FF）。
 * 用于强调学员剩余课时数（如"剩余 5 节"）。
 *
 * @param text 标签文字（如 "剩余 5 节"）
 */
@Composable
fun DarkChip_Remaining(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = RemainingChipText,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(RemainingChipBg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/**
 * 深色卡内"数据"胶囊标签。
 *
 * 设计：深灰底（[DataChipBg] #2C2C2E）+ 青字（[DataChipText] #00D2FF）。
 * 用于身高/年龄/体重/BMI 等客观数据标签。
 *
 * @param text 标签文字（如 "170cm"）
 */
@Composable
fun DarkChip_Data(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = DataChipText,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(DataChipBg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/**
 * 深色卡内"编辑"按钮：深灰底 + 浅灰字。
 *
 * 用于卡片底部操作区，极简圆角风格。
 *
 * @param text 按钮文字（如 "编辑"）
 * @param onClick 点击回调
 */
@Composable
fun DarkButton_Edit(
    text: String,
    onClick: () -> Unit
) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = EditButtonText,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(EditButtonBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

/**
 * 深色卡内"删除"按钮：深红底 + 红字。
 *
 * 保留语义红色，但采用深红底（#3A1A1A）保证与深色卡协调。
 *
 * @param text 按钮文字（如 "删除"）
 * @param onClick 点击回调
 */
@Composable
fun DarkButton_Delete(
    text: String,
    onClick: () -> Unit
) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = DeleteButtonText,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(DeleteButtonBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

/**
 * 深色卡内"签到"按钮：青色渐变底 + 白字。
 *
 * 渐变方向：#3A7BD5 → #00D2FF（与头像渐变一致）。
 * 作为深色卡的主操作按钮，视觉突出。
 *
 * @param text 按钮文字（如 "签到"）
 * @param onClick 点击回调
 */
@Composable
fun DarkButton_Sign(
    text: String,
    onClick: () -> Unit
) {
    // v37 性能优化：缓存渐变 Brush，避免每次重组都创建
    val signBrush = remember(SignButtonStart, SignButtonEnd) {
        Brush.linearGradient(colors = listOf(SignButtonStart, SignButtonEnd))
    }
    Text(
        text = text,
        fontSize = 12.sp,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(brush = signBrush)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
