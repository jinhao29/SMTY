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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shangmentiyu.sportscoach.R
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.ui.AppViewModelFactory
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appPrimary

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
    onOperation: () -> Unit = {},
    onSchedule: () -> Unit = {},
    onHeightPrediction: (String) -> Unit = {},
    onDietManage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val vm: HomeViewModel = viewModel(
        factory = AppViewModelFactory(context.applicationContext as android.app.Application)
    )

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // === 仅水平边距，无垂直 padding，无 Spacer，无 top padding ===
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                // === 性能优化：抽出独立 HomeTabItem @Composable ===
                // 原 forEachIndexed 闭包内对 tabIndex 的读取会让整个 Row 在 tabIndex 变化时全量重组。
                // 抽成独立 @Composable 后，Compose 只会重组 isSelected 状态发生变化的两个 Tab 项
                // （旧选中 → 新选中），其余 Tab 项保持不动，切换更丝滑。
                tabLabels.forEachIndexed { index, label ->
                    HomeTabItem(
                        label = label,
                        isSelected = index == tabIndex,
                        onClick = { tabIndex = index }
                    )
                }
            }
            // === 警示横幅：胶囊 Tab 下方固定 4dp 间距 ===
            Spacer(Modifier.height(4.dp))
            // === v25 优化1：全局到期预警横幅 ===
            // === 性能优化：只传 expiringPackages 数据，不传 vm，隔离重组范围 ===
            ExpiryBanner(
                message = expiringBannerText,
                expiringPackages = expiringPackages,
                onAction = { studentName -> onGrowth(studentName) }
            )
            // Crossfade 平滑切换 Tab
            // === 性能优化：显式 label 便于 Layout Inspector 定位重组 ===
            // 注：Crossfade 不支持 contentKey（该参数属于 AnimatedContent），
            // Int targetState 本身即稳定类型，Compose 自动按值对比，无需额外 key
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
 * 单个胶囊 Tab 项（独立 @Composable，隔离重组范围）。
 *
 * === 性能优化说明 ===
 * 原实现将 Tab 内容直接写在 forEachIndexed 闭包内，导致 tabIndex 变化时
 * 整个 Row 都会进入重组。抽成独立 @Composable 后：
 * - Compose 编译器可识别该函数仅依赖 [label] / [isSelected] / [onClick]
 * - 当 tabIndex 变化时，只有旧选中项 + 新选中项两个 HomeTabItem 重组
 * - 其余 Tab 项因参数未变而跳过重组，Tab 切换更丝滑
 *
 * 注：使用 RowScope 接收者，使内部可用 Modifier.weight(1f) 实现等分宽度。
 *
 * @param label Tab 文本
 * @param isSelected 是否选中
 * @param onClick 点击回调
 */
@Composable
private fun RowScope.HomeTabItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // === 动画优化：Tab 切换颜色用 animateColorAsState 平滑过渡 ===
    // 原实现硬切换，切换瞬间有闪烁感；200ms tween 让选中/未选中过渡更丝滑
    val bgColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isSelected) appPrimary() else Color(0xFFF0F0F0),
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        label = "tab_bg"
    )
    val textColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isSelected) Color.White else Color(0xFF6B6B6B),
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        label = "tab_text"
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
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
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
                color = Color(0xFF1A1A1A),    // v40 任务2c：深黑文字（高对比度）
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}
