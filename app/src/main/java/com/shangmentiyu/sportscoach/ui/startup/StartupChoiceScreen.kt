package com.shangmentiyu.sportscoach.ui.startup

import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.shangmentiyu.sportscoach.data.internal.ModeManager
import com.shangmentiyu.sportscoach.ui.theme.appBackground
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appSurface

/**
 * v60：启动板块选择页。
 *
 * App 从单一「教练工具」扩展为多板块（教练工作台 / 体育俱乐部），
 * 冷启动先进入本页选择要进入的板块；后续俱乐部模块在此入口下生长。
 * 布局参考李哥给的 onboarding 参考图：欢迎语 → 插画/选择区 → 品牌名 + slogan。
 *
 * v23.13：板块选择接入工作模式（ModeManager，多租户物理隔离）——
 * - 点击与当前模式一致的板块：直接进入；
 * - 点击另一板块：写偏好（commit 落盘）+ 重启 App，Application 重新初始化
 *   时挂载对应模式的独立数据库（sports_coach_db / sports_coach_club_db），
 *   重启后回到本页再点一次即进入（与设置页 ModeSection 同一套语义）。
 * - 当前生效板块卡片右上角显示「当前数据空间」角标。
 */

// v66：不再硬编码珊瑚橙——深色模式下主强调随主题变蓝，统一走 appPrimary()
private val BrandCoral @Composable get() = appPrimary()

@Composable
fun StartupChoiceScreen(
    onEnterCoach: () -> Unit,
    onEnterClub: () -> Unit
) {
    val context = LocalContext.current
    val activeMode = ModeManager.activeMode
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appBackground())
            // v63：去掉页面级 systemBarsPadding——外层 Scaffold 的 innerPadding 已含系统栏
            // inset，此前的双重避让导致顶部空白偏大。系统栏背景色与图标色由 Scaffold/
            // 页面自身按路由接管。
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))

        // 顶部欢迎语（参考图 Welcome! 的位置）
        Text(
            text = "欢迎使用",
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = appOnSurfaceVariant(),
            letterSpacing = 4.sp
        )

        // v64：欢迎语与卡片组之间改为弹性间距，两张卡片在剩余空间垂直居中
        Spacer(Modifier.weight(1f))

        // 板块选择卡片 1：教练工作台（现有全部功能）
        ModuleCard(
            title = "教练工作台",
            subtitle = "学员管理 · 课时排课 · 双端同步",
            icon = { Icon(Icons.Outlined.Home, null, tint = BrandCoral, modifier = Modifier.size(30.dp)) },
            badge = if (activeMode == ModeManager.MODE_COACHING) "当前数据空间" else null,
            onClick = {
                if (activeMode == ModeManager.MODE_COACHING) {
                    onEnterCoach()
                } else {
                    switchMode(context, ModeManager.MODE_COACHING)
                }
            }
        )

        Spacer(Modifier.height(20.dp))

        // 板块选择卡片 2：体育俱乐部（新板块，建设中）
        ModuleCard(
            title = "体育俱乐部",
            subtitle = "EVOLVE · 会员 / 课程 / 教练团队",
            icon = { Icon(Icons.Outlined.EmojiEvents, null, tint = BrandCoral, modifier = Modifier.size(30.dp)) },
            badge = if (activeMode == ModeManager.MODE_CLUB) "当前数据空间" else "建设中",
            onClick = {
                if (activeMode == ModeManager.MODE_CLUB) {
                    onEnterClub()
                } else {
                    switchMode(context, ModeManager.MODE_CLUB)
                }
            }
        )

        Spacer(Modifier.weight(1f))

        // 底部品牌区（参考图 DATIGAM + slogan 的位置）
        Text(
            text = "SPORTSCOACH",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
            color = appOnSurface()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "让每一节课都有据可依",
            fontSize = 13.sp,
            color = appOnSurfaceVariant(),
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(40.dp))
    }
}

/** 切换工作模式：落盘 + 重启（重启后回到本页，再点对应卡片即进入新数据空间） */
private fun switchMode(context: android.content.Context, mode: String) {
    ModeManager.setMode(context, mode)
    val pm = context.packageManager
    val intent = pm.getLaunchIntentForPackage(context.packageName)
    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    android.os.Process.killProcess(android.os.Process.myPid())
}

@Composable
private fun ModuleCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    badge: String?,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = appSurface(),
        border = androidx.compose.foundation.BorderStroke(1.dp, appOnSurfaceVariant().copy(alpha = 0.18f)),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(BrandCoral.copy(alpha = 0.10f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) { icon() }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                // v63：入口/角标移到标题行右上角（与全局"入口按钮放右上角"约定一致），
                // 副标题独占下方整行，不再被右侧元素挤压断行。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = appOnSurface(),
                        modifier = Modifier.weight(1f)
                    )
                    if (badge != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = BrandCoral.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = badge,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = BrandCoral,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    } else {
                        Text("进入 ›", fontSize = 13.sp, color = BrandCoral, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = appOnSurfaceVariant(),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

/**
 * 体育俱乐部板块占位页（v60）：EVOLVE 黑白极简基调，
 * 后续俱乐部功能（会员 / 课程顾问 / 教练团队）在此模块下生长。
 *
 * v63：系统栏背景由 SportsApp 的 Scaffold 按路由切黑（API 35+ 强制 edge-to-edge，
 * statusBarColor 已失效）；本页只负责在进/出时切换状态栏与手势条图标明暗。
 */
@Composable
fun ClubPlaceholderScreen(
    onBack: () -> Unit
) {
    // 黑底页面需要浅色系统栏图标；离开时恢复浅底深图标
    val view = LocalView.current
    DisposableEffect(Unit) {
        var ctx = view.context
        while (ctx is ContextWrapper && ctx !is Activity) ctx = ctx.baseContext
        val window = (ctx as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.isAppearanceLightStatusBars = true
            controller?.isAppearanceLightNavigationBars = true
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111111))
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = Color(0xFFEDEDED)
                )
            }
        }
        Spacer(Modifier.weight(1f))

        // EVOLVE 黑白极简占位标识（手工几何，非位图）
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .background(Color.Transparent)
            ) {
                // 三道斜切白条构成的抽象 "E"
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(3) { i ->
                        Box(
                            Modifier
                                .size(width = if (i == 2) 56.dp else 84.dp, height = 10.dp)
                                .background(Color(0xFFEDEDED))
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = "EVOLVE",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 8.sp,
            color = Color(0xFFEDEDED)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "进化体育 · 俱乐部",
            fontSize = 14.sp,
            letterSpacing = 2.sp,
            color = Color(0xFF9A9A9A)
        )
        Spacer(Modifier.height(36.dp))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A3A3A))
        ) {
            Text(
                text = "俱乐部模块建设中\n会员 / 课程顾问 / 教练团队 即将上线",
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = Color(0xFFB9B9B9),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "返回可重新选择板块",
            fontSize = 12.sp,
            color = Color(0xFF6E6E6E),
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(bottom = 32.dp)
        )
    }
}
