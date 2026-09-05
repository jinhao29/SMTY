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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.shangmentiyu.sportscoach.ui.sportcategory.SportCategoryContent
import com.shangmentiyu.sportscoach.ui.theme.AppSegmentedTabs
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appSurfaceVariant

/**
 * 成绩查看页：底部导航主入口，整合"录入成绩" / "查看成绩" / "中考体育"三个 Tab。
 *
 * 设计要点：
 * - 顶部为分段控件 Tab（theme/SegmentedTabs.kt 的 AppSegmentedTabs，与主页共用同一组件）
 * - 默认展示"查看成绩"Tab（tabIndex = 1）
 * - "中考体育"Tab 复用 [SportCategoryContent] 分类网格，点击项目跳转标准详情
 * - Tab 内容分别由 ScoreInputTab / ScoreViewTab / SportCategoryContent 承载
 *
 * @param onBack 返回回调（底部 Tab 页通常为 null）
 * @param onOpenLesson 打开课时详情回调
 * @param onEditScore 编辑已有成绩回调，参数为课时 ID（跳转 ScoringScreen 加载已有成绩）
 * @param onOpenSportDetail 打开中考体育项目标准详情回调，参数为项目 ID
 */
@Composable
fun ScoreScreen(
    onBack: (() -> Unit)? = null,
    onOpenLesson: (String) -> Unit,
    onEditScore: (String) -> Unit = {},
    onOpenSportDetail: (String) -> Unit = {}
) {
    var tabIndex by remember { mutableStateOf(1) } // 默认"查看成绩"

    Scaffold(contentWindowInsets = WindowInsets(0)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // === 顶部分段控件（iOS 风轨道+滑块，theme/SegmentedTabs.kt 统一组件）===
                AppSegmentedTabs(
                    labels = listOf("录入成绩", "查看成绩", "中考体育"),
                    selectedIndex = tabIndex,
                    onSelect = { tabIndex = it },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Crossfade(
                    targetState = tabIndex,
                    animationSpec = tween(durationMillis = 220),
                    label = "ScoreTabCrossfade"
                ) { index ->
                    when (index) {
                        0 -> ScoreInputTab()
                        1 -> ScoreViewTab(onEditScore = onEditScore)
                        else -> SportCategoryContent(onItemClick = onOpenSportDetail)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7FA)
@Composable
private fun ScoreScreenPreview() {
    ScoreScreen(onOpenLesson = {})
}
