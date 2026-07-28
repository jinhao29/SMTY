package com.shangmentiyu.sportscoach.ui.score

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.shangmentiyu.sportscoach.ui.theme.appSurface

/**
 * 成绩查看页：底部导航主入口，整合"录入成绩"与"查看成绩"两个 Tab。
 *
 * 设计要点：
 * - PrimaryTabRow 双 Tab 切换
 * - 默认展示"查看成绩"Tab（tabIndex = 1）
 * - Tab 内容分别由 ScoreInputTab / ScoreViewTab 承载
 *
 * @param onBack 返回回调（底部 Tab 页通常为 null）
 * @param onOpenLesson 打开课时详情回调
 * @param onEditScore 编辑已有成绩回调，参数为课时 ID（跳转 ScoringScreen 加载已有成绩）
 */
@OptIn(ExperimentalMaterial3Api::class)
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
            PrimaryTabRow(selectedTabIndex = tabIndex) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    text = { Text("录入成绩") }
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    text = { Text("查看成绩") }
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