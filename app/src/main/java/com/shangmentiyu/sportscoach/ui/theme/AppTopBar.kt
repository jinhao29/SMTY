package com.shangmentiyu.sportscoach.ui.theme

import android.content.Intent
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * 统一顶栏：固化 windowInsets = WindowInsets(0, 0, 0, 0)。
 *
 * 约定（2026-09-02 全量排查结论）：外层 SportsApp Scaffold 在 enableEdgeToEdge 下
 * 已消费状态栏 inset，内层 TopAppBar 若按默认 statusBars 避让会造成双重留白（24-48dp）。
 * 新页面一律使用 AppTopBar，禁止直接使用 material3.TopAppBar。
 *
 * @param shareLabel 非空时，在 actions 末尾追加一个"分享"图标按钮，点击弹出系统分享面板。
 * 统一 shareLabel 让所有页面顶栏都有分享入口，避免每个页面单独在 body 内重复放分享按钮占位置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = glassTopAppBarColors(),
    shareLabel: String? = null,
) {
    val context = LocalContext.current
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = {
            actions()
            if (!shareLabel.isNullOrBlank()) {
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "SportsCoach - $shareLabel")
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "来自 SportsCoach 教练端的「$shareLabel」\n" +
                                "— 上门体育教学管理工具"
                        )
                    }
                    context.startActivity(
                        Intent.createChooser(send, "分享到").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }) {
                    Icon(Icons.Outlined.Share, contentDescription = "分享$shareLabel")
                }
            }
        },
        colors = colors,
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
}