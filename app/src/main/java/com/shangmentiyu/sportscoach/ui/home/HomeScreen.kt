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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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

    var tabIndex by remember { mutableStateOf(3) } // 默认显示学员列表
    val snackbarHost = remember { SnackbarHostState() }
    val toast by vm.toast.collectAsState()
    // === v25 优化1：到期预警横幅文案与目标学员 ===
    val expiringBannerText by vm.expiringBannerText.collectAsState()
    val expiringPackages by vm.expiringPackages.collectAsState()
    // === v5 新增：双端握手状态（用于顶部 SyncBanner） ===
    val syncHandshake by vm.syncHandshake.collectAsState()
    LaunchedEffect(toast) {
        toast?.let { msg ->
            snackbarHost.showSnackbar(msg)
            vm.clearToast()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
        // === v40 修复：contentWindowInsets 设为 0，让 content 从屏幕顶部开始 ===
        // 状态栏避让由 CapsuleTabBar 内部的 statusBarsPadding() 唯一负责，
        // 避免 padding(padding) + statusBarsPadding() 双重叠加产生大量留白
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0)
    ) { padding ->
        // padding 此时为 0（因 contentWindowInsets 设为 0），保留 .padding(padding) 以兼容 Scaffold 行为
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // === v40 任务2a：自定义胶囊 Tab 栏 ===
            // CapsuleTabBar 内部唯一一次 statusBarsPadding()，胶囊紧贴状态栏底部
            val tabLabels = listOf(
                stringResource(R.string.home_tab_pre_class),
                stringResource(R.string.home_tab_lesson_manage),
                stringResource(R.string.home_tab_post_class),
                stringResource(R.string.home_tab_student_list)
            )
            CapsuleTabBar(
                labels = tabLabels,
                selectedIndex = tabIndex,
                onSelected = { tabIndex = it }
            )
            // === 警示横幅：胶囊 Tab 下方固定 8dp 间距（严禁超过 8dp）===
            Spacer(Modifier.height(8.dp))
            // === v5 新增：双端同步状态横幅 ===
            SyncHandshakeBanner(
                state = syncHandshake,
                onClick = { vm.triggerBackupSync() }
            )
            // === v25 优化1：全局到期预警横幅 ===
            ExpiryBanner(
                message = expiringBannerText,
                onClick = {
                    expiringPackages.firstOrNull()?.let { pkg ->
                        onGrowth(pkg.studentName)
                    }
                }
            )
            // Crossfade 平滑切换 Tab
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
 * 胶囊式 Tab 栏（v40 任务2a）。
 *
 * 4 个等宽胶囊按钮横向排列：
 * - 选中：珊瑚橙背景 #FF6B47 + 白字 + SemiBold
 * - 未选中：浅灰背景 #F0F0F0 + 深灰字 + Medium
 * - 全圆角胶囊，紧凑间距
 *
 * @param labels Tab 标签列表
 * @param selectedIndex 当前选中索引
 * @param onSelected 选中回调
 */
@Composable
private fun CapsuleTabBar(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // v40 修正1：精准设置状态栏避让，只避让 statusBars，不叠加其他 inset
            // 紧贴状态栏底部，不额外加 vertical padding，消除胶囊与状态栏之间的空隙
            .windowInsetsPadding(
                WindowInsets.systemBars
                    .only(WindowInsetsSides.Top)
            )
            .padding(horizontal = Spacing.screenH),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            val bgColor = if (isSelected) appPrimary() else Color(0xFFF0F0F0)
            val textColor = if (isSelected) Color.White else Color(0xFF6B6B6B)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(bgColor)
                    .clickable { onSelected(index) }
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
    }
}

/**
 * === v25 优化1：课时包到期预警横幅 ===
 *
 * 软性警告色横幅，仅当 [message] 非空时通过 [AnimatedVisibility] 平滑展开。
 * 点击后调用 [onClick] 跳转至最紧急学员详情。
 *
 * 设计要点：
 * - 软性橙色背景（不刺眼，与主色调和谐）
 * - 圆角胶囊形态，与卡片风格一致
 * - 带通知图标，文案左对齐
 * - 点击区充足，方便教练快速点击跳转
 *
 * @param message 预警文案（null 时隐藏横幅）
 * @param onClick 点击跳转回调
 */
@Composable
private fun ExpiryBanner(
    message: String?,
    onClick: () -> Unit
) {
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
                .clickable(onClick = onClick)
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

/**
 * === v5 新增：双端同步握手状态横幅 ===
 *
 * 根据握手状态展示：
 * - [HomeViewModel.SyncHandshakeState.Unknown]：不显示（首次启动未探测完毕）
 * - [HomeViewModel.SyncHandshakeState.Online]：绿色"双端已握手"，点击触发同步
 * - [HomeViewModel.SyncHandshakeState.Offline]：不显示（离线时不打扰教练）
 * - [HomeViewModel.SyncHandshakeState.Syncing]：橙色"同步中…"，不响应点击
 *
 * 设计要点：
 * - 仅在 [Online] 与 [Syncing] 状态显示，避免无效占位
 * - 配色与珊瑚橙主色和谐：绿色用 #E8F5E9 / #2E7D32，橙色沿用 ExpiryBanner 体系
 * - 圆角胶囊形态，与 ExpiryBanner 一致
 * - 点击区充足，方便教练快速点击同步
 *
 * @param state 握手状态（来自 [HomeViewModel.syncHandshake]）
 * @param onClick 点击"同步"按钮回调（仅 Online 时响应）
 */
@Composable
private fun SyncHandshakeBanner(
    state: HomeViewModel.SyncHandshakeState,
    onClick: () -> Unit
) {
    // 离线 / 未探测时不显示横幅，节省屏幕空间
    if (state == HomeViewModel.SyncHandshakeState.Offline ||
        state == HomeViewModel.SyncHandshakeState.Unknown) {
        return
    }

    val (bg, fg, iconTint, text, clickable) = when (state) {
        HomeViewModel.SyncHandshakeState.Online -> SyncBannerStyle(
            bgColor = Color(0xFFE8F5E9),       // 浅绿背景
            fgColor = Color(0xFF1B5E20),        // 深绿文字
            iconTint = Color(0xFF2E7D32),       // 中绿图标
            text = "双端已握手，点击立即同步",
            clickable = true
        )
        HomeViewModel.SyncHandshakeState.Syncing -> SyncBannerStyle(
            bgColor = Color(0xFFFFF4E5),        // 浅橙背景（与 ExpiryBanner 一致）
            fgColor = Color(0xFF7A4A0E),        // 深棕文字
            iconTint = Color(0xFFE08A2B),       // 中橙图标
            text = "正在同步中，请稍候…",
            clickable = false
        )
        else -> return  // Unknown / Offline 已在上方拦截，此处仅为编译器兜底
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenH, vertical = Spacing.sm)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Icon(
            imageVector = if (state == HomeViewModel.SyncHandshakeState.Online)
                Icons.Outlined.CloudDone
            else
                Icons.Outlined.Sync,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = fg,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

/** SyncHandshakeBanner 内部使用的样式打包（避免 when 分支返回多值时 Pair 装箱开销） */
private data class SyncBannerStyle(
    val bgColor: Color,
    val fgColor: Color,
    val iconTint: Color,
    val text: String,
    val clickable: Boolean
)
