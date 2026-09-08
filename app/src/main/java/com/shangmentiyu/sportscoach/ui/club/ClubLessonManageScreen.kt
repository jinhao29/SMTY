package com.shangmentiyu.sportscoach.ui.club

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.shangmentiyu.sportscoach.ui.home.HomeViewModel
import com.shangmentiyu.sportscoach.ui.home.LessonManageTab
import com.shangmentiyu.sportscoach.ui.theme.AppTopBar
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface

/**
 * EVOLVE 俱乐部 · 课时与课程包管理（壳页）。
 *
 * 直接复用上门体育的 [LessonManageTab]：课时统计 / 费用统计 / 课时包列表 /
 * 新增与调整课时包 / 年级分组导出全部现成，数据经俱乐部库自动隔离，
 * 这里只补一个带返回键的顶栏。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ClubLessonManageScreen(
    vm: HomeViewModel,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = {
                Text(
                    "课时与课程包",
                    style = MaterialTheme.typography.titleLarge,
                    color = appOnSurface(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = appOnSurface()
                    )
                }
            }
        )
        LessonManageTab(vm = vm)
    }
}
