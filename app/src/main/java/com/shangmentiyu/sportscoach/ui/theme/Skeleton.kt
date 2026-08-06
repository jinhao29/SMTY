package com.shangmentiyu.sportscoach.ui.theme

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * === v48 终极打磨：骨架屏（Skeleton Loader）组件库 ===
 *
 * 替代列表加载时的"转圈"：以柔和脉冲动画的占位色块模拟真实卡片结构，
 * 让用户感知"内容即将出现"，比 CircularProgressIndicator 更具高级感。
 *
 * 设计：
 * - 占位底色 = onSurface 8% alpha（亮/暗主题自动适配，零硬编码）
 * - 脉冲动画：1.0 → 0.4 alpha 往复（600ms，无新依赖，纯 Compose 动画 API）
 * - 所有块共用同一 [SkeletonAnimation]，只创建一次 InfiniteTransition
 *
 * 用法：
 * ```
 * if (!vm.loaded) {
 *     StudentListSkeleton()   // 学员列表骨架
 *     ScheduleListSkeleton()  // 排课列表骨架
 * }
 * ```
 */

/** 骨架屏共用的脉冲动画透明度（0.4 → 1.0 往复，600ms） */
@Composable
private fun rememberSkeletonAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "skeletonPulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )
    return alpha
}

/** 骨架占位块：圆角色块 + 脉冲透明度 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    val alpha = rememberSkeletonAlpha()
    Box(
        modifier = modifier.background(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f * alpha),
            shape = shape
        )
    )
}

/** 骨架行：圆形头像 + 两行文字条（学员列表 / 排课卡片通用骨架） */
@Composable
fun SkeletonListRow(
    modifier: Modifier = Modifier,
    avatarSize: Dp = 44.dp,
    titleWidth: Dp = 120.dp,
    subtitleWidth: Dp = 200.dp
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SkeletonBox(
            modifier = Modifier.size(avatarSize),
            shape = CircleShape
        )
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBox(modifier = Modifier.width(titleWidth).height(14.dp))
            SkeletonBox(modifier = Modifier.width(subtitleWidth).height(12.dp))
        }
    }
}

/**
 * 学员列表骨架屏：与 [com.shangmentiyu.sportscoach.ui.home.StudentListItem] 结构对齐
 * （头像 + 姓名/课时条 + 下一节课条），白色卡片容器内渲染。
 */
@Composable
fun StudentListSkeleton(rows: Int = 6) {
    SkeletonCardContainer {
        repeat(rows) { idx ->
            if (idx > 0) SkeletonDivider()
            SkeletonListRow()
        }
    }
}

/**
 * 排课列表骨架屏：与课前准备排课卡片结构对齐
 * （顶部日期切换器胶囊 + 若干排课卡片：时间块 + 姓名/地点条 + 内容条）。
 */
@Composable
fun ScheduleListSkeleton(rows: Int = 4, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // 日期切换器占位
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        )
        repeat(rows) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(Spacing.md)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkeletonBox(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(10.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeletonBox(modifier = Modifier.width(140.dp).height(14.dp))
                        SkeletonBox(modifier = Modifier.width(90.dp).height(12.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                SkeletonBox(modifier = Modifier.fillMaxWidth().height(12.dp))
                Spacer(Modifier.height(8.dp))
                SkeletonBox(modifier = Modifier.fillMaxWidth(0.6f).height(12.dp))
            }
        }
    }
}

/** 骨架卡片容器：白色卡片 + 16dp 圆角（与学员列表分组卡片一致） */
@Composable
private fun SkeletonCardContainer(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        content()
    }
}

/** 骨架行之间的 0.5dp 细分割线 */
@Composable
private fun SkeletonDivider() {
    Box(
        modifier = Modifier
            .padding(start = 72.dp)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(appDividerColor())
    )
}
