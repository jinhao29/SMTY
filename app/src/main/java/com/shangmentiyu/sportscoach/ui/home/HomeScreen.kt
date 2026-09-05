package com.shangmentiyu.sportscoach.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangmentiyu.sportscoach.R
import com.shangmentiyu.sportscoach.app.framework.LanSyncManager
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.domain.usecase.UnsignedOutReminderState
import org.koin.androidx.compose.koinViewModel
import org.koin.core.context.GlobalContext
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.AppSegmentedTabs
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appSurfaceVariant

/**
 * 主页：4 Tab 结构（课前准备 / 课时管理 / 课后反馈 / 学员列表）。
 *
 * UI 层仅负责 Tab 切换与状态分发，具体内容由各 Tab 组件渲染。
 *
 * === v25 优化1 ===：在 Tab 下方增加全局课时包到期预警横幅，
 * 点击后跳转至最紧急学员的成长档案，便于教练快速处理。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSign: (String) -> Unit,
    onAddStudent: () -> Unit,
    onGrowth: (String) -> Unit,
    onEditStudent: (Student) -> Unit = {},
    onLessonCheckIn: () -> Unit = {},
    onSchedule: () -> Unit = {},
    onHeightPrediction: (String) -> Unit = {},
    onDietManage: (String) -> Unit = {},
    onOpenUnsignedOutLessons: () -> Unit = {}
) {
        val vm: HomeViewModel = koinViewModel()

    var tabIndex by remember { mutableStateOf(0) } // 默认显示课前准备 Tab（123.txt 重构后首页为今日概览）
    // === 终极修复：toast 订阅与 SnackbarHost 已提升到 SportsApp 外层 Box ===
    // 原因：HomeScreen 在 NavHost 内部，其 Box 仍被 SportsApp 外层 Box 的 FloatingBottomBar 覆盖。
    // 无论在 HomeScreen 内部怎么加 Box/padding，z-axis 始终低于 FloatingBottomBar。
    // 解决方案：snackbarHostState 定义在 SportsApp 顶层，FloatingSnackbarHost 放在
    // SportsApp 外层 Box 末尾（与 FloatingBottomBar 同级），z-axis 最顶层。
    // === v25 优化1：到期预警横幅文案与目标学员 ===
    // === 性能优化：在 HomeScreen 顶层订阅一次 expiringPackages，传入 ExpiryBanner ===
    // 避免向 ExpiryBanner 传整个 vm 导致其因 vm 引用变化而重组范围扩大
    val expiringBannerText by vm.expiringBannerText.collectAsStateWithLifecycle()
    val expiringPackages by vm.expiringPackages.collectAsStateWithLifecycle()
    val unsignedOutReminder by vm.unsignedOutReminder.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // === 终极修复：完全删除 topBar 参数 ===
        // 不写 topBar 即默认无顶栏，Scaffold 不会叠加任何状态栏避让
        // === 关键：contentWindowInsets 设为 0，避免 Scaffold 默认 inset 叠加 ===
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0)
    ) { paddingValues ->
        // paddingValues 此时为 0（因 contentWindowInsets 设为 0）
        Column(
            modifier = Modifier
                .fillMaxSize()
                // === 移除 statusBarsPadding()：状态栏避让由外层 SportsApp 的 topBar 负责 ===
                // SportsApp 的 Scaffold topBar 已自动应用 statusBars padding，
                // HomeScreen 在 NavHost 内，顶部已是状态栏下方，无需再避让
                // 之前双重避让导致胶囊离状态栏过远
                .padding(paddingValues) // paddingValues=0，保留以兼容 Scaffold 契约
        ) {
            // --- 胶囊 Tab 块（紧贴状态栏，无任何额外 Spacer）---
            // 注：4 个 string 资源构建 List 开销极小，无需 remember 包裹；
            // 且 stringResource 在配置变更（如语言切换）时需重新读取，remember 反而会持有旧值
            val tabLabels = listOf(
                stringResource(R.string.home_tab_pre_class),
                stringResource(R.string.home_tab_lesson_manage),
                stringResource(R.string.home_tab_post_class),
                stringResource(R.string.home_tab_student_list)
            )
            // === 顶部分段控件（iOS 风轨道+滑块，theme/SegmentedTabs.kt 统一组件）===
            AppSegmentedTabs(
                labels = tabLabels,
                selectedIndex = tabIndex,
                onSelect = { tabIndex = it },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            // === 警示横幅：胶囊 Tab 下方固定 4dp 间距 ===
            Spacer(Modifier.height(4.dp))
            // === v23.6：PC 连接状态横幅（自动识别 Wi-Fi/USB，在线时展示，未连接不占位） ===
            DesktopSyncBanner()
            // === v25 优化1：全局到期预警横幅 ===
            // === 性能优化：只传 expiringPackages 数据，不传 vm，隔离重组范围 ===
            ExpiryBanner(
                message = expiringBannerText,
                expiringPackages = expiringPackages,
                onAction = { studentName -> onGrowth(studentName) }
            )
            // === 忘记签退提醒卡片（小班课集体签到签退功能） ===
            // === 性能优化：只传 state 数据与两个回调，不传 vm，隔离重组范围 ===
            UnsignedOutReminderCard(
                state = unsignedOutReminder,
                onView = onOpenUnsignedOutLessons,
                onDismiss = { vm.dismissUnsignedOutReminder() }
            )
            // === 修复：Tab 切换动画拖沓 ===
            // 原 220ms Crossfade 会让整页内容先淡出再淡入，体感延迟明显。
            // 改 80ms 短 fade：保留轻微衔接避免硬切突兀，同时体感接近"瞬切"，
            // 满足"简单迅速"的诉求。
            // === 性能优化：显式 label 便于 Layout Inspector 定位重组 ===
            // 注：Crossfade 不支持 contentKey（该参数属于 AnimatedContent），
            // Int targetState 本身即稳定类型，Compose 自动按值对比，无需额外 key
            Crossfade(
                targetState = tabIndex,
                animationSpec = tween(durationMillis = 80),
                label = "HomeTabCrossfade"
            ) { index ->
                when (index) {
                    0 -> PreClassTab(vm = vm, onLessonCheckIn = onLessonCheckIn, onSchedule = onSchedule)
                    1 -> LessonManageTab(vm = vm)
                    2 -> PostClassTab(vm = vm, onSign = onSign)
                    3 -> StudentListTab(
                        vm = vm,
                        onSign = onSign,
                        onAddStudent = onAddStudent,
                        onGrowth = onGrowth,
                        onEditStudent = onEditStudent,
                        onHeightPrediction = onHeightPrediction,
                        onDietManage = onDietManage
                    )
                }
            }
        }
    }
}

/**
 * === v23.6：PC 连接状态横幅 ===
 *
 * 订阅 [LanSyncManager.desktopOnline]（10s 轮询：Wi-Fi 心跳优先，/health 探测兜底，
 * USB 回环自动识别）。在线时展开一条浅绿细横幅提示「已连接电脑端」；
 * 未连接时不占任何空间（不干扰正常使用）。
 *
 * 设计要点：
 * - 自包含：不依赖 HomeViewModel（避免 God 类继续膨胀），直接读 Koin 单例
 * - 克制：仅在线时出现，无关闭按钮（状态类信息不打断操作）
 */
@Composable
private fun DesktopSyncBanner() {
    val lanSync = remember { GlobalContext.get().get<LanSyncManager>() }
    val link by lanSync.desktopOnline.collectAsStateWithLifecycle()

    AnimatedVisibility(
        visible = link != null,
        enter = fadeIn(tween(120)) + expandVertically(tween(120)),
        exit = fadeOut(tween(120)) + shrinkVertically(tween(120))
    ) {
        val current = link ?: return@AnimatedVisibility
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFE7F8EF))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF34D399))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "已连接 ${current.pcName}（${current.host}:${current.port}）",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF15803D),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                if (current.viaUsb) "USB" else "Wi-Fi",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF15803D).copy(alpha = 0.75f)
            )
        }
    }
}

/**
 * === v25 优化1：课时包到期预警横幅 ===
 *
 * 软性警告色横幅，仅当 [message] 非空时通过 [AnimatedVisibility] 平滑展开。
 * 点击后调用 [onAction] 跳转至最紧急学员详情。
 *
 * 设计要点：
 * - 软性橙色背景（不刺眼，与主色调和谐）
 * - 圆角胶囊形态，与卡片风格一致
 * - 带通知图标，文案左对齐
 * - 点击区充足，方便教练快速点击跳转
 *
 * === 性能优化说明 ===
 * 原实现接收整个 [HomeViewModel]，导致 vm 内任意 StateFlow 变化都可能触发本横幅重组。
 * 改为只接收 [expiringPackages] 数据后，本横幅仅在该列表变化时重组，
 * 与 vm 的其他状态（学员列表、签到记录等）完全解耦。
 *
 * @param message 预警文案（null 时隐藏横幅）
 * @param expiringPackages 即将过期的课时包列表（取首位作为跳转目标）
 * @param onAction 点击跳转回调，携带最紧急学员姓名
 */
@Composable
private fun ExpiryBanner(
    message: String?,
    expiringPackages: List<LessonPackage>,
    onAction: (String) -> Unit
) {
    // === 性能优化：缓存首位学员名，避免每次重组都遍历列表 ===
    val targetStudentName by remember(expiringPackages) {
        derivedStateOf { expiringPackages.firstOrNull()?.studentName }
    }
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        val bannerText = message ?: return@AnimatedVisibility
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenH, vertical = Spacing.sm)
                .clip(RoundedCornerShape(10.dp))
                .background(appPrimary().copy(alpha = 0.08f))   // v40 任务2c：浅珊瑚橙背景
                .clickable {
                    // === 性能优化：使用缓存的 targetStudentName，避免点击时再遍历 ===
                    targetStudentName?.let { onAction(it) }
                }
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Icon(
                Icons.Outlined.NotificationsActive,
                contentDescription = null,
                tint = appPrimary(),   // v40 任务2c：珊瑚橙图标
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = bannerText,
                style = MaterialTheme.typography.bodyMedium,
                color = appOnSurface(),    // v40 任务2c：深黑文字（高对比度）
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

/**
 * === 忘记签退提醒卡片（小班课集体签到签退功能） ===
 *
 * 仅当 [UnsignedOutReminderState.shouldShow] 为 true 时通过 [AnimatedVisibility]
 * 平滑展开：存在过去日期已签到未签退的课时，且未被用户以相同记录集签名关闭。
 *
 * 交互：
 * - 点击卡片主体 → [onView] 跳转签到页并自动筛选未签退列表
 * - 点击右侧关闭图标 → [onDismiss] 记录当前记录集签名，本次记录集不再提醒；
 *   出现新的未签退记录（签名变化）时卡片自动再次显示
 *
 * @param state 提醒状态（学员汇总 + 记录集签名 + 是否显示）
 * @param onView 点击跳转回调（查看未签退详情）
 * @param onDismiss 关闭提醒回调
 */
@Composable
private fun UnsignedOutReminderCard(
    state: UnsignedOutReminderState,
    onView: () -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = state.shouldShow,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenH, vertical = Spacing.sm)
                .clip(RoundedCornerShape(10.dp))
                .background(appPrimary().copy(alpha = 0.08f))
                .clickable(onClick = onView)
                .padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.sm, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Icon(
                Icons.Outlined.NotificationsActive,
                contentDescription = null,
                tint = appPrimary(),
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "您有 ${state.students.size} 个学员未签退（共 ${state.totalLessonCount} 节课），请及时处理",
                    style = MaterialTheme.typography.bodyMedium,
                    color = appOnSurface(),
                    fontWeight = FontWeight.Medium,
                    maxLines = 2
                )
                Text(
                    text = "点击查看过去日期已签到未签退的课时",
                    style = MaterialTheme.typography.labelSmall,
                    color = appOnSurfaceVariant()
                )
            }
            // 关闭提醒：小尺寸非侵入式按钮（仅记录当前记录集签名）
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onDismiss)
                    .padding(Spacing.xs),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "关闭提醒",
                    tint = appOnSurfaceVariant(),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7FA)
@Composable
private fun HomeScreenPreview() {
    HomeScreen(
        onSign = {},
        onAddStudent = {},
        onGrowth = {},
        onEditStudent = {},
        onLessonCheckIn = {},
        onSchedule = {},
        onHeightPrediction = {},
        onDietManage = {}
    )
}
