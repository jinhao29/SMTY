package com.shangmentiyu.sportscoach.ui.score

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appSurface
import com.shangmentiyu.sportscoach.ui.theme.appSurfaceVariant

/**
 * 成绩查看页：底部导航主入口，整合"录入成绩"与"查看成绩"两个 Tab。
 *
 * 设计要点：
 * - 顶部为胶囊按钮 Tab（与主页 HomeTabItem 完全一致：选中珊瑚橙 #FF6B47 白字 / 未选中浅灰 #F0F0F0 深灰 #6B6B6B 文字，无下划线）
 * - 默认展示"查看成绩"Tab（tabIndex = 1）
 * - Tab 内容分别由 ScoreInputTab / ScoreViewTab 承载
 *
 * @param onBack 返回回调（底部 Tab 页通常为 null）
 * @param onOpenLesson 打开课时详情回调
 * @param onEditScore 编辑已有成绩回调，参数为课时 ID（跳转 ScoringScreen 加载已有成绩）
 */
@Composable
fun ScoreScreen(
    onBack: (() -> Unit)? = null,
    onOpenLesson: (String) -> Unit,
    onEditScore: (String) -> Unit = {}
) {
    var tabIndex by remember { mutableStateOf(1) } // 默认"查看成绩"

    Scaffold(
        containerColor = appSurface()
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // === 顶部胶囊 Tab 栏（替代原 PrimaryTabRow，彻底移除紫色实线下划线）===
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScoreTabItem(
                    label = "录入成绩",
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 }
                )
                ScoreTabItem(
                    label = "查看成绩",
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 }
                )
            }
            Crossfade(
                targetState = tabIndex,
                animationSpec = tween(durationMillis = 220),
                label = "ScoreTabCrossfade"
            ) { index ->
                when (index) {
                    0 -> ScoreInputTab()
                    1 -> ScoreViewTab(onEditScore = onEditScore)
                }
            }
        }
    }
}

/**
 * 单个胶囊 Tab 项：与主页 HomeTabItem 视觉完全一致（独立 @Composable，隔离重组范围）。
 *
 * - 选中：珊瑚橙 #FF6B47（appPrimary）背景 + 纯白 #FFFFFF 文字
 * - 未选中：浅灰 #F0F0F0 背景 + 深灰 #6B6B6B 文字
 * - 全圆角胶囊 RoundedCornerShape(50)，选中/未选中 200ms 颜色平滑过渡
 */
@Composable
private fun RowScope.ScoreTabItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) appPrimary() else appSurfaceVariant(),
        animationSpec = tween(durationMillis = 200),
        label = "score_tab_bg"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) Color.White else appOnSurfaceVariant(),
        animationSpec = tween(durationMillis = 200),
        label = "score_tab_text"
    )
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}
