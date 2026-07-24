package com.shangmentiyu.sportscoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shangmentiyu.sportscoach.ui.growth.GrowthScreen
import com.shangmentiyu.sportscoach.ui.home.AddStudentScreen
import com.shangmentiyu.sportscoach.ui.home.HomeScreen
import com.shangmentiyu.sportscoach.ui.home.HomeViewModel
import com.shangmentiyu.sportscoach.ui.lesson.LessonScreen
import com.shangmentiyu.sportscoach.ui.score.ScoreScreen
import com.shangmentiyu.sportscoach.ui.settings.SettingsScreen
import com.shangmentiyu.sportscoach.ui.summary.SummaryScreen
import com.shangmentiyu.sportscoach.ui.theme.FeatureIconPurple
import com.shangmentiyu.sportscoach.ui.theme.VitalPurpleStart
import com.shangmentiyu.sportscoach.ui.theme.appGroupedBackground
import com.shangmentiyu.sportscoach.ui.theme.appSurface
import com.shangmentiyu.sportscoach.ui.training.TrainingPlanScreen
import com.shangmentiyu.sportscoach.ui.operation.OperationScreen
import com.shangmentiyu.sportscoach.ui.schedule.ScheduleScreen
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Download
import com.shangmentiyu.sportscoach.update.UpdateInstaller
import com.shangmentiyu.sportscoach.update.UpdateProgressBus

/** 底部导航项 */
data class BottomItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun SportsApp() {
    val navController = rememberNavController()
    val bottomItems = listOf(
        BottomItem(Routes.HOME, "主页", Icons.Filled.Home),
        BottomItem(Routes.SCORE, "成绩查看", Icons.Filled.SportsScore),
        BottomItem(Routes.SETTINGS, "设置详情", Icons.Filled.Settings),
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.route in setOf(
        Routes.HOME, Routes.SCORE, Routes.SETTINGS
    )

    // 更新下载进度订阅：来自 UpdateProgressBus（Worker → UI 跨进程通信）
    val updateProgress by UpdateProgressBus.progress.collectAsState()
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        containerColor = appGroupedBackground(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = appSurface(),
                    tonalElevation = 0.dp
                ) {
                    bottomItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = FeatureIconPurple,
                                selectedTextColor = FeatureIconPurple,
                                unselectedIconColor = Color(0xFF8E8E93),
                                unselectedTextColor = Color(0xFF8E8E93),
                                indicatorColor = VitalPurpleStart.copy(alpha = 0.1f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            // === 底部 Tab ===
            composable(Routes.HOME) {
                HomeScreen(
                    onSign = { lessonId -> navController.navigate(Routes.lesson(lessonId)) },
                    onAddStudent = { navController.navigate(Routes.ADD_STUDENT) },
                    onGrowth = { studentName -> navController.navigate(Routes.growth(studentName)) },
                    onEditStudent = { student -> navController.navigate(Routes.editStudent(student.name)) },
                    onLessonCheckIn = { navController.navigate(Routes.LESSON_CHECKIN) },
                    onOperation = { navController.navigate(Routes.OPERATION) },
                    onSchedule = { navController.navigate(Routes.SCHEDULE) }
                )
            }
            composable(Routes.SCORE) {
                ScoreScreen(
                    onBack = null,
                    onOpenLesson = { lessonId -> navController.navigate(Routes.lesson(lessonId)) },
                    onEditScore = { lessonId -> navController.navigate(Routes.scoringWithLesson(lessonId)) }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }

            // === 学员管理 ===
            composable(Routes.ADD_STUDENT) {
                AddStudentScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.EDIT_STUDENT,
                arguments = listOf(navArgument("studentName") { type = NavType.StringType })
            ) { backStackEntry ->
                val studentName = backStackEntry.arguments?.getString("studentName") ?: ""
                val context = LocalContext.current
                val homeVm: HomeViewModel = viewModel(
                    factory = AppViewModelFactory(context.applicationContext as android.app.Application)
                )
                val students by homeVm.students.collectAsState()
                val target = students.firstOrNull { it.name == studentName }
                when {
                    target != null -> AddStudentScreen(
                        onBack = { navController.popBackStack() },
                        student = target
                    )
                    students.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    else -> {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("学员不存在")
                        }
                    }
                }
            }

            // === 课时相关 ===
            composable(Routes.LESSON) { backStackEntry ->
                val lessonId = backStackEntry.arguments?.getString("lessonId") ?: ""
                LessonScreen(
                    lessonId = lessonId,
                    onBack = { navController.popBackStack() },
                    onScoring = { navController.navigate(Routes.scoringWithLesson(lessonId)) },
                    onSummary = { navController.navigate(Routes.summary(lessonId)) }
                )
            }
            composable(Routes.SUMMARY) { backStackEntry ->
                val lessonId = backStackEntry.arguments?.getString("lessonId") ?: ""
                SummaryScreen(
                    lessonId = lessonId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SCORING_WITH_LESSON) { backStackEntry ->
                val lessonId = backStackEntry.arguments?.getString("lessonId") ?: ""
                com.shangmentiyu.sportscoach.ui.scoring.ScoringScreen(
                    lessonId = lessonId,
                    onBack = { navController.popBackStack() }
                )
            }

            // === 成长与训练计划 ===
            composable(
                route = Routes.GROWTH,
                arguments = listOf(navArgument("studentName") { type = NavType.StringType })
            ) { backStackEntry ->
                val studentName = backStackEntry.arguments?.getString("studentName") ?: ""
                GrowthScreen(
                    studentName = studentName,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.TRAINING_PLAN,
                arguments = listOf(navArgument("studentName") { type = NavType.StringType })
            ) { backStackEntry ->
                val studentName = backStackEntry.arguments?.getString("studentName") ?: ""
                TrainingPlanScreen(
                    studentName = studentName,
                    onBack = { navController.popBackStack() },
                    onApplied = { lessonId -> navController.navigate(Routes.lesson(lessonId)) }
                )
            }

            // === 运营/排课/签到 ===
            composable(Routes.OPERATION) {
                OperationScreen(
                    onBack = { navController.popBackStack() },
                    onSign = { _ -> navController.navigate(Routes.LESSON_CHECKIN) },
                    onOpenLesson = { lessonId -> navController.navigate(Routes.lesson(lessonId)) }
                )
            }
            composable(Routes.LESSON_CHECKIN) {
                com.shangmentiyu.sportscoach.ui.lessoncheckin.LessonCheckInScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLesson = { lessonId -> navController.navigate(Routes.lesson(lessonId)) }
                )
            }
            composable(Routes.SCHEDULE) {
                ScheduleScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // === 训练规划类（设置详情页二级入口） ===
            composable(Routes.STAGE_SUMMARY) {
                com.shangmentiyu.sportscoach.ui.stagesummary.StageSummaryScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.TRAINING_CYCLE) {
                com.shangmentiyu.sportscoach.ui.trainingcycle.TrainingCycleScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.BODY_METRIC) {
                com.shangmentiyu.sportscoach.ui.bodymetric.BodyMetricChartScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.COACH_REPORT) {
                com.shangmentiyu.sportscoach.ui.coachreport.CoachDailyReportScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLesson = { lessonId -> navController.navigate(Routes.lesson(lessonId)) }
                )
            }
        }
    }

        // === 更新下载进度浮层 ===
        // 订阅 UpdateProgressBus，前台时实时展示下载进度/完成/失败
        when (val p = updateProgress) {
            is UpdateProgressBus.UpdateProgress.Downloading -> {
                // 仅在 1~99% 时显示浮层（0% 与 100% 由通知承载，避免闪烁）
                if (p.percent in 1..99) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            tonalElevation = 6.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    progress = { p.percent / 100f },
                                    modifier = Modifier.size(48.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 4.dp
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "正在下载新版本 ${p.version}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Black
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${p.percent}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }
            }
            is UpdateProgressBus.UpdateProgress.Done -> {
                // 下载完成：触发系统安装器，重置总线
                LaunchedEffect(Unit) {
                    runCatching { UpdateInstaller.installApk(context) }
                    UpdateProgressBus.reset()
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        tonalElevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "新版本 ${p.version} 下载完成，正在启动安装…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Black
                            )
                        }
                    }
                }
            }
            is UpdateProgressBus.UpdateProgress.Failed -> {
                // 下载失败：短暂提示后重置总线（不阻塞用户操作）
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(2500)
                    UpdateProgressBus.reset()
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        tonalElevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "更新下载失败",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFFF3B30)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = p.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF3C3C43)
                            )
                        }
                    }
                }
            }
            UpdateProgressBus.UpdateProgress.Idle -> { /* 无更新进行中，不显示浮层 */ }
        }
    }
}
