package com.shangmentiyu.sportscoach.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.ui.AppViewModelFactory
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.glassTopAppBarColors

/**
 * 主页：4 Tab 结构（课前准备 / 课时管理 / 课后反馈 / 学员列表）。
 *
 * UI 层仅负责 Tab 切换与状态分发，具体内容由各 Tab 组件渲染。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSign: (String) -> Unit,
    onAddStudent: () -> Unit,
    onGrowth: (String) -> Unit,
    onEditStudent: (Student) -> Unit = {},
    onLessonCheckIn: () -> Unit = {},
    onOperation: () -> Unit = {},
    onSchedule: () -> Unit = {}
) {
    val context = LocalContext.current
    val vm: HomeViewModel = viewModel(
        factory = AppViewModelFactory(context.applicationContext as android.app.Application)
    )

    var tabIndex by remember { mutableStateOf(3) } // 默认显示学员列表
    val snackbarHost = remember { SnackbarHostState() }
    val toast by vm.toast.collectAsState()
    LaunchedEffect(toast) {
        toast?.let { msg ->
            snackbarHost.showSnackbar(msg)
            vm.clearToast()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("课前准备") })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("课时管理") })
                Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }, text = { Text("课后反馈") })
                Tab(selected = tabIndex == 3, onClick = { tabIndex = 3 }, text = { Text("学员列表") })
            }
            // Crossfade 平滑切换 Tab，保留各 Tab 滚动位置与输入状态
            Crossfade(
                targetState = tabIndex,
                animationSpec = tween(durationMillis = 220),
                label = "HomeTabCrossfade"
            ) { index ->
                when (index) {
                    0 -> PreClassTab(vm = vm, onLessonCheckIn = onLessonCheckIn, onSchedule = onSchedule)
                    1 -> LessonManageTab(vm = vm)
                    2 -> PostClassTab(vm = vm, onSign = onSign, onOperation = onOperation)
                    3 -> StudentListTab(
                        vm = vm,
                        onSign = onSign,
                        onAddStudent = onAddStudent,
                        onGrowth = onGrowth,
                        onEditStudent = onEditStudent
                    )
                }
            }
        }
    }
}
