package com.shangmentiyu.sportscoach.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsScore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shangmentiyu.sportscoach.core.ScheduleReminderWorker
import com.shangmentiyu.sportscoach.ui.growth.GrowthScreen
import com.shangmentiyu.sportscoach.ui.home.AddStudentScreen
import com.shangmentiyu.sportscoach.ui.home.HomeScreen
import com.shangmentiyu.sportscoach.ui.home.HomeViewModel
import com.shangmentiyu.sportscoach.ui.diet.DietManageScreen
import com.shangmentiyu.sportscoach.ui.heightprediction.HeightPredictionScreen
import com.shangmentiyu.sportscoach.ui.lesson.LessonScreen
import com.shangmentiyu.sportscoach.ui.score.ScoreScreen
import com.shangmentiyu.sportscoach.ui.settings.SettingsScreen
import com.shangmentiyu.sportscoach.ui.settings.SettingsViewModel
import com.shangmentiyu.sportscoach.ui.summary.SummaryScreen
import com.shangmentiyu.sportscoach.ui.theme.LightPrimary
import com.shangmentiyu.sportscoach.ui.theme.appBackground
import com.shangmentiyu.sportscoach.ui.theme.appGroupedBackground
import com.shangmentiyu.sportscoach.ui.training.TrainingPlanScreen
import com.shangmentiyu.sportscoach.ui.operation.OperationScreen
import com.shangmentiyu.sportscoach.ui.schedule.ScheduleScreen
import com.shangmentiyu.sportscoach.update.UpdateInstaller
import com.shangmentiyu.sportscoach.update.UpdateManager
import com.shangmentiyu.sportscoach.update.UpdateProgressBus
import kotlinx.coroutines.delay

/** 底部导航项 */
// === 性能优化：@Stable 注解 ===
// BottomItem 含 ImageVector 接口类型字段，Compose 编译器无法自动推断稳定性，
// 会默认视为 Unstable，导致 FloatingBottomBar 在 currentRoute 变化时全量重组所有 NavTabItem。
// 加 @Stable 后，编译器按字段对比，仅在 item 实例真正变化时才重组对应 NavTabItem。
@androidx.compose.runtime.Stable
data class BottomItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

/**
 * === 性能优化 H1：更新下载进度浮层独立组件 ===
 *
 * 设计原理：
 * - 在组件内部 `collectAsState` 订阅 [UpdateProgressBus]，把进度变化引起的重组
 *   完全隔离在 [UpdateProgressOverlay] 内部，外层 [SportsApp] 不会被波及
 * - 原代码把 `updateProgress` 订阅放在 SportsApp 顶层，每次进度从 1% → 99% 变化时
 *   都会触发整个 NavHost + FloatingBottomBar 重绘，是主页滑动卡顿的主因之一
 *
 * 行为：
 * - [UpdateProgressBus.UpdateProgress.Downloading]：1~99% 显示进度浮层
 * - [UpdateProgressBus.UpdateProgress.Done]：触发外层安装确认弹窗 + 重置总线
 * - [UpdateProgressBus.UpdateProgress.Failed]：短暂提示后重置总线
 * - [UpdateProgressBus.UpdateProgress.Idle]：不渲染
 *
 * @param onDownloadComplete 下载完成回调（用于触发外层 AlertDialog 安装确认弹窗）
 */
@Composable
private fun UpdateProgressOverlay(onDownloadComplete: (version: String) -> Unit) {
    val p by UpdateProgressBus.progress.collectAsStateWithLifecycle()
    when (p) {
        is UpdateProgressBus.UpdateProgress.Downloading -> {
            val downloading = p as UpdateProgressBus.UpdateProgress.Downloading
            // 仅在 1~99% 时显示浮层（0% 与 100% 由通知承载，避免闪烁）
            if (downloading.percent in 1..99) {
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
                                progress = { downloading.percent / 100f },
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 4.dp
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "正在下载新版本 ${downloading.version}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Black
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${downloading.percent}%",
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
            val done = p as UpdateProgressBus.UpdateProgress.Done
            // 用 LaunchedEffect + key(version) 保证每次新版本下载完成只触发一次
            LaunchedEffect(done.version) {
                onDownloadComplete(done.version)
                UpdateProgressBus.reset()
            }
            // 下载完成瞬间短暂浮层提示（主交互由外层 AlertDialog 承载）
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
                            imageVector = Icons.Outlined.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "新版本 ${done.version} 下载完成",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Black
                        )
                    }
                }
            }
        }
        is UpdateProgressBus.UpdateProgress.Failed -> {
            val failed = p as UpdateProgressBus.UpdateProgress.Failed
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
                            color = LightPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = failed.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
        UpdateProgressBus.UpdateProgress.Idle -> { /* 无更新进行中，不显示浮层 */ }
    }
}

/**
 * 桌面端连接状态栏（v32 优化3 新增）。
 *
 * - 桌面端在线（60 秒内收到心跳）：显示绿色指示灯 + "已连接：电脑端 192.168.x.x"
 * - 桌面端离线：不显示（AnimatedVisibility 自动收起，避免占用顶部空间）
 *
 * 数据来源：[SettingsViewModel.desktopConnection] StateFlow
 * 内部 5 秒轮询一次 SharedPreferences，保证 UI 与 Service 接收线程同步
 */
@Composable
private fun DesktopConnectionBanner(
    connection: SettingsViewModel.DesktopConnection?
) {
    AnimatedVisibility(
        visible = connection?.isAlive == true,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
    ) {
        val host = connection?.host ?: ""
        Surface(
            color = Color(0xFFE8F5E9),  // 浅绿背景
            contentColor = Color(0xFF2E7D32),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Computer,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color(0xFF2E7D32)
                )
                // 绿色脉冲指示灯
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFF4CAF50), CircleShape)
                )
                Text(
                    text = "已连接：电脑端 $host",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun SportsApp() {
    val navController = rememberNavController()
    val bottomItems = listOf(
        BottomItem(Routes.HOME, "主页", Icons.Outlined.Home),
        BottomItem(Routes.SCORE, "成绩查看", Icons.Outlined.SportsScore),
        BottomItem(Routes.SETTINGS, "设置详情", Icons.Outlined.Settings),
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // === 性能优化：用 derivedStateOf 缓存底部导航相关派生状态 ===
    // 只有当 currentDestination.route 真正变化时才触发 FloatingBottomBar 重组
    // 避免其他 State 变化（如 updateProgress）连带触发底部导航重绘
    val currentRoute by remember(currentDestination) {
        derivedStateOf { currentDestination?.route }
    }
    val showBottomBar by remember(currentRoute) {
        derivedStateOf {
            currentRoute in setOf(Routes.HOME, Routes.SCORE, Routes.SETTINGS)
        }
    }

    // === 性能优化 H1：移除顶层 updateProgress 订阅 ===
    // 原 collectAsState 在顶层订阅，每次进度变化（1-99%）都会触发整个 SportsApp 重组，
    // 连带 NavHost + FloatingBottomBar 全部重绘，造成卡顿。
    // 现在把进度订阅下沉到 UpdateProgressOverlay 内部，重组范围隔离在 Overlay 内。
    val context = LocalContext.current

    // === 安装确认弹窗状态（无感下载 + 弹窗安装） ===
    // 下载完成后不直接跳转安装器，先弹窗让用户确认
    // - showInstallDialog：控制 AlertDialog 显示
    // - pendingInstallVersion：待安装版本号（用于弹窗文案展示）
    var showInstallDialog by remember { mutableStateOf(false) }
    var pendingInstallVersion by remember { mutableStateOf("") }

    // === v28 优化6：订阅首页未签到数与今日排课红点状态 ===
    // 用于底部导航栏主页 Tab 显示数字角标（未签到数）或红点（仅有排课）
    val homeVm: HomeViewModel = koinViewModel()
    val unsignedTodayCount by homeVm.unsignedTodayCount.collectAsStateWithLifecycle()
    val hasTodayScheduleBadge by homeVm.hasTodayScheduleBadge.collectAsStateWithLifecycle()

    // === 终极修复：全局 SnackbarHostState + toast 订阅 ===
    // 业务背景：HomeScreen 在 NavHost 内部，其 Scaffold/Box 的 z-axis 始终低于
    // SportsApp 外层 Box 中的 FloatingBottomBar，导致 Snackbar 被悬浮导航遮挡。
    // 解决方案：
    // - snackbarHostState 定义在 SportsApp 顶层（全局唯一）
    // - 订阅 homeVm.toast（HomeViewModel 的全局 toast StateFlow）
    // - FloatingSnackbarHost 放在外层 Box 末尾（与 FloatingBottomBar 同级），z-axis 最顶层
    // - showSnackbar 强制带 SnackbarDuration.Short（约 2 秒自动消失，无需手动关闭）
    val appSnackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val appToast by homeVm.toast.collectAsStateWithLifecycle()
    LaunchedEffect(appToast) {
        appToast?.let { msg ->
            appSnackbarHostState.showSnackbar(
                message = msg,
                duration = androidx.compose.material3.SnackbarDuration.Short
            )
            homeVm.clearToast()
        }
    }

    // === v32 优化3：桌面端连接状态订阅（绿色指示灯）===
    // 5 秒轮询一次 SharedPreferences，让 UI 与 UdpDesktopDiscoveryService 接收线程保持同步
    val settingsVm: SettingsViewModel = koinViewModel()
    val desktopConnection by settingsVm.desktopConnection.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        while (true) {
            settingsVm.refreshDesktopConnection()
            delay(5000L)
        }
    }

    // === v34：App 启动时检查"待安装更新"持久化标志 ===
    // 场景：App 在后台或被杀进程时，Worker 完成下载并发出通知
    // 用户点击通知（现在只打开 App）或下次进入 App 时，必须触发 AlertDialog 询问
    // 不持久化的话，进程重启后 UpdateProgressBus 状态丢失，弹窗永远不会出现
    LaunchedEffect(Unit) {
        val pendingVersion = UpdateManager.consumeUpdateReady(context)
        if (pendingVersion != null) {
            pendingInstallVersion = pendingVersion
            showInstallDialog = true
        }
    }

    // === v28 优化4：监听通知点击 Intent，自动跳转到今日排课页 ===
    // 业务背景：ScheduleReminderWorker 发送的通知点击后会启动 MainActivity，
    // Intent 中带 EXTRA_NAVIGATE_TO = "operation"，SportsApp 读取后跳转
    // 使用 singleTop + LaunchedEffect 确保只触发一次（读完即清空 extra）
    var pendingNavigateTarget by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        // 从 Activity Intent 读取跳转目标
        val targetActivity = context as? android.app.Activity
        val intent = targetActivity?.intent
        val target = intent?.getStringExtra(ScheduleReminderWorker.EXTRA_NAVIGATE_TO)
        if (!target.isNullOrEmpty()) {
            pendingNavigateTarget = target
            // 立即清除 extra，避免旋转屏 / 重建时再次触发
            intent?.removeExtra(ScheduleReminderWorker.EXTRA_NAVIGATE_TO)
        }
    }
    // 当 pendingNavigateTarget 变化时执行跳转
    LaunchedEffect(pendingNavigateTarget) {
        when (pendingNavigateTarget) {
            ScheduleReminderWorker.EXTRA_VALUE_OPERATION -> {
                navController.navigate(Routes.OPERATION) {
                    // 从主页 Tab 跳过去，主页保留返回栈
                    launchSingleTop = true
                }
            }
        }
        // 跳转完成，清空待处理目标
        pendingNavigateTarget = null
    }

    // === 终极重构：彻底放弃 Scaffold 的 bottomBar，改用 Box 覆盖法 ===
    // 之前 Scaffold 的 bottomBar slot 即使内部 Box 透明，slot 容器本身仍会
    // 在系统导航栏区域露出 containerColor，导致胶囊两侧出现灰白方块。
    // 现在把悬浮导航作为独立元素摆在外层 Box 底部，完全脱离 Scaffold，
    // 胶囊两侧直接透出主页面底色（appBackground #FAFAFA），真正"无影悬浮"。
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = appBackground(),
            topBar = {
                // v32 优化3：桌面端连接状态栏（仅在线时显示绿色指示灯）
                DesktopConnectionBanner(desktopConnection)
            }
            // === 坚决不写 bottomBar = { ... } 参数！ ===
            // 底部导航作为独立悬浮元素放在外层 Box 底部，彻底脱离 Scaffold 容器
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
                    onSchedule = { navController.navigate(Routes.SCHEDULE) },
                    onHeightPrediction = { studentName -> navController.navigate(Routes.heightPrediction(studentName)) },
                    onDietManage = { studentName -> navController.navigate(Routes.dietManage(studentName)) }
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
                SettingsScreen(
                    onNavigate = { route -> navController.navigate(route) }
                )
            }

            // === 学员管理 ===
            composable(Routes.ADD_STUDENT) {
                // v46：传 Activity 级 homeVm，保证 addStudent 写入协程不被 pop 取消
                AddStudentScreen(onBack = { navController.popBackStack() }, vm = homeVm)
            }
            composable(
                route = Routes.EDIT_STUDENT,
                arguments = listOf(navArgument("studentName") { type = NavType.StringType })
            ) { backStackEntry ->
                val studentName = backStackEntry.arguments?.getString("studentName") ?: ""
                // v46：复用 SportsApp 顶层 Activity 级 homeVm（原局部 koinViewModel 绑定 entry，pop 即取消）
                val students by homeVm.students.collectAsStateWithLifecycle()
                val target = students.firstOrNull { it.name == studentName }
                when {
                    target != null -> AddStudentScreen(
                        onBack = { navController.popBackStack() },
                        student = target,
                        vm = homeVm
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
            composable(
                route = Routes.HEIGHT_PREDICTION,
                arguments = listOf(navArgument("studentName") { type = NavType.StringType })
            ) { backStackEntry ->
                val studentName = backStackEntry.arguments?.getString("studentName") ?: ""
                HeightPredictionScreen(
                    studentName = studentName,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.DIET_MANAGE,
                arguments = listOf(navArgument("studentName") { type = NavType.StringType })
            ) { backStackEntry ->
                val studentName = backStackEntry.arguments?.getString("studentName") ?: ""
                DietManageScreen(
                    studentName = studentName,
                    onBack = { navController.popBackStack() }
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
            composable(Routes.BMI_CALCULATOR) {
                com.shangmentiyu.sportscoach.ui.tools.BmiCalculatorScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // === 话术管理 ===
            composable(Routes.SCRIPT_LIST) {
                com.shangmentiyu.sportscoach.ui.script.ScriptListScreen(
                    onBack = { navController.popBackStack() },
                    onOpen = { id -> navController.navigate(Routes.scriptDetail(id)) },
                    onAdd = { navController.navigate(Routes.scriptDetail(null)) }
                )
            }
            composable(
                route = Routes.SCRIPT_DETAIL,
                arguments = listOf(navArgument("scriptId") { type = NavType.StringType })
            ) { backStackEntry ->
                val rawId = backStackEntry.arguments?.getString("scriptId") ?: "new"
                val scriptId = if (rawId == "new") null else rawId
                com.shangmentiyu.sportscoach.ui.script.ScriptDetailScreen(
                    scriptId = scriptId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }

        // === 终极悬浮导航：独立于 Scaffold 的 Box 覆盖法 ===
        // 放在外层 Box 底部，align(BottomCenter)，背景强制透明
        // 胶囊左右两侧直接透出主页面底色（appBackground #FAFAFA），无任何灰框
        // FloatingBottomBar 内部已有 Surface(纯白+30dp圆角+阴影)，无需再包一层
        if (showBottomBar) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Transparent)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
            ) {
                FloatingBottomBar(
                    items = bottomItems,
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onFabClick = {
                        // FAB 触发核心操作：添加学员
                        navController.navigate(Routes.ADD_STUDENT)
                    },
                    // 主页 Tab 动态角标（珊瑚橙 #FF6B47）
                    badgeForRoute = { route ->
                        if (route == Routes.HOME) {
                            when {
                                unsignedTodayCount > 0 -> {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(LightPrimary)
                                            .border(1.dp, Color.White, RoundedCornerShape(50)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = unsignedTodayCount.coerceAtMost(99).toString(),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                hasTodayScheduleBadge -> {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(LightPrimary)
                                            .border(1.dp, Color.White, RoundedCornerShape(50))
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }

        // === 终极修复：全局 FloatingSnackbarHost（z-axis 最顶层） ===
        // 放在外层 Box 末尾（FloatingBottomBar 之后、UpdateProgressOverlay 之前），
        // 确保 z-axis 最顶层，彻底不被 FloatingBottomBar 遮挡。
        // - align(BottomCenter)：定位到屏幕底部中央
        // - bottomPadding = 160.dp：避开悬浮导航栏（总高约 130dp = 70dp 导航 + 48dp 系统 + 12dp 间距）
        // - duration = SnackbarDuration.Short：在上方 LaunchedEffect 中已设置，2 秒自动消失
        com.shangmentiyu.sportscoach.ui.theme.FloatingSnackbarHost(
            hostState = appSnackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
            bottomPadding = 160.dp
        )

        // === 性能优化 H1：把进度浮层抽到独立 Composable ===
        // 原代码内联在 SportsApp 顶层 Box 中，每次进度变化触发整树重组
        // 现在 UpdateProgressOverlay 内部独立 collectAsState，重组范围被隔离
        UpdateProgressOverlay(
            onDownloadComplete = { version ->
                pendingInstallVersion = version
                showInstallDialog = true
            }
        )

        // === 安装确认弹窗（无感下载 + 弹窗安装） ===
        // 下载完成后弹出，让用户决定是否立即安装
        // - "立即安装"：触发系统安装器（UpdateInstaller.installApk）
        // - "稍后" / 点外部关闭：仅关闭弹窗，下次进入 App 或下次检查更新时再提示
        if (showInstallDialog) {
            AlertDialog(
                onDismissRequest = {
                    // 用户点击弹窗外部关闭：视为"稍后"
                    showInstallDialog = false
                },
                title = {
                    Text(
                        text = "发现新版本",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                text = {
                    Text(text = "发现新版本 $pendingInstallVersion，是否立即安装更新？")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            // 立即安装：触发系统安装界面，清除待安装标志
                            showInstallDialog = false
                            UpdateManager.clearUpdateReady(context)
                            runCatching { UpdateInstaller.installApk(context) }
                        }
                    ) {
                        Text("立即安装")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            // 稍后：关闭弹窗，清除待安装标志（避免下次进入 App 又弹）
                            showInstallDialog = false
                            UpdateManager.clearUpdateReady(context)
                        }
                    ) {
                        Text("稍后")
                    }
                }
            )
        }
    }
}
