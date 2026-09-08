package com.shangmentiyu.sportscoach.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 全局设计令牌（Design Tokens）— 活力珊瑚橙主题
 *
 * 设计策略：Restrained（tinted neutrals + 单一珊瑚橙强调 ≤10%）
 * 物理场景：教练在户外场地强光下用手机签到、查学员课时。
 *   → 高对比度文字、大点击区、留白充足、避免低饱和度色块。
 *
 * 色板层级：
 * 1. 背景：浅冷灰底 #F5F6F8 + 暖灰分组底 #F2F2F5
 * 2. 表面：纯白卡片 #FFFFFF（浮在分组底之上）
 * 3. 强调：珊瑚橙 #FF6B47（单一强调色，≤10% 面积）
 * 4. 文字：主 #1A1A1A（≥12:1）/ 次 #6B6B6B（≥4.6:1 WCAG AA）
 * 5. 分割：Black 6% alpha（极浅，无感分割）
 *
 * 亮色 / 暗色两套色板统一收敛为 [LightColorScheme] / [DarkColorScheme]
 * Material 3 ColorScheme，跟随系统 Dark Mode 或「设置 → 深色模式」开关切换。
 *
 * 命名规范（v39 重构：清理冗余别名，统一语义命名）：
 * - 所有令牌以 Light 或 Night 前缀区分主题
 * - 废弃别名（GlowCyan/GlowBlue/FeatureIconBlue/Indigo80 等）已删除
 * - 历史渐变名（VitalOrangeStart 等）已合并为 BrandGradientStart / BrandGradientEnd
 *
 * v48 终极打磨：主题感知访问器（appBackground() 等）不再自行判断
 * isSystemInDarkTheme()，统一读取 MaterialTheme.colorScheme 令牌，
 * 手动「深色模式」开关与系统切换都能即时生效。
 */

// === 1. 亮色主题（Light）===
val LightBackground = Color(0xFFF5F6F8)              // 浅冷灰主背景（白卡片的衬托底）
val LightSurface = Color(0xFFFFFFFF)                 // 纯白卡片表面
val LightSurfaceVariant = Color(0xFFF2F2F5)           // 次级表面（输入框底色）

val LightPrimary = Color(0xFFFF6B47)                 // 主珊瑚橙（唯一强调色）
val LightOnPrimary = Color(0xFFFFFFFF)               // 主色上的文字
val LightPrimaryContainer = Color(0xFFFFD4C2)        // 主色浅色容器（淡橙背景）
val LightOnPrimaryContainer = Color(0xFFE64A19)      // 主色容器上的文字
val LightSecondary = Color(0xFFFF9E7A)                // 浅橙（辅助强调，少用）
val LightTertiary = Color(0xFFFFB74D)                // 暖金黄（成绩/数据可视化）

// 文字色（两级灰度层次，符合 WCAG AA）
val LightOnBackground = Color(0xFF1A1A1A)             // 主文字（标题/正文）
val LightOnSurface = Color(0xFF1A1A1A)               // 卡片上的主文字
val LightOnSurfaceVariant = Color(0xFF6B6B6B)        // 次级文字（副标题/占位/空状态）

// 分割与边框
val LightOutline = Color(0xFFC6C6C8)                 // 边框基色
val LightDivider = Color(0x0F000000)                 // 极浅分割线 = Black 6%

// 品牌渐变（单一珊瑚橙色系渐变）
val BrandGradientStart = Color(0xFFFF6B47)
val BrandGradientEnd = Color(0xFFFF9E7A)

// === 2. 暗色主题（Night，跟随系统）===
// v65：按李哥提供的深色参考稿（深色结账页）重定风格——
// 「近黑多层底」：页面底近黑（#101114）→ 卡片微亮一档（#1B1C21）→ 输入框再亮一档
// （#25272D），层与层之间靠亮度差 + 低对比描边（#2F3237）分层，不用阴影。
// 大面积纯黑（旧 #000000）夜间刺眼，近黑底更接近参考稿的柔和质感。
// 主强调仍为珊瑚橙亮色版（参考稿的蓝是其品牌色，不照搬）。
val NightBackground = Color(0xFF101114)              // 页面主底（参考稿面板近黑档）
val NightSurface = Color(0xFF1B1C21)                 // 卡片表面（比底亮一档）
val NightSurfaceVariant = Color(0xFF25272D)          // 输入框/分组底（再亮一档）
val NightPrimary = Color(0xFFFF8A65)                // 暗色珊瑚橙（更亮保证对比度）
val NightOnPrimary = Color(0xFF1A1208)
val NightPrimaryContainer = Color(0xFF3A241A)
val NightOnPrimaryContainer = Color(0xFFFFD4C2)
val NightSecondary = Color(0xFFFFAB91)
val NightOnSecondary = Color(0xFF1A1208)
val NightTertiary = Color(0xFFFFCC80)
val NightOnBackground = Color(0xFFF5F5F7)            // 主文字（参考稿近纯白）
val NightOnSurface = Color(0xFFF5F5F7)
val NightOnSurfaceVariant = Color(0xFF9A9BA1)        // 次级文字（参考稿灰）
val NightOutline = Color(0xFF2F3237)                 // 低对比描边（参考稿 #2A2D31 档）
val NightDivider = Color(0x17FFFFFF)                 // White 9%（参考稿低对比分隔）
val NightGlassSurface = Color(0xFF1E1F24)            // 毛玻璃/悬浮层底

// === 2. 语义色令牌（v48 补充：信息横幅/状态提示，M3 无对应字段）===
val LightSuccessContainer = Color(0xFFE8F5E9)        // 成功/健康建议容器（浅绿）
val LightOnSuccessContainer = Color(0xFF2E7D32)      // 容器上的文字（深绿）
val NightSuccessContainer = Color(0xFF1E3320)        // 暗色容器（墨绿）
val NightOnSuccessContainer = Color(0xFF81C784)      // 暗色容器文字（亮绿）
val LightWarningContainer = Color(0xFFFFF3E0)        // 警示容器（浅橙）
val LightOnWarningContainer = Color(0xFFE65100)      // 容器上的文字（深橙）
val NightWarningContainer = Color(0xFF33271A)        // 暗色容器（深棕橙）
val NightOnWarningContainer = Color(0xFFFFB74D)      // 暗色容器文字（亮金橙）
val LightInfoBlue = Color(0xFF42A5F5)                // 数据分级：偏低（蓝）
val NightInfoBlue = Color(0xFF90CAF9)                // 暗色板：柔和亮蓝

// === 3. 语义色（数据可视化/状态指示）===
// 成绩等级（暖色系递进，避免冷色）
val ScoreExcellent = Color(0xFFFF6B47)               // 优秀 = 主珊瑚橙
val ScoreGood = Color(0xFFFF9E7A)                    // 良好 = 浅橙
val ScorePass = Color(0xFFFFD4C2)                    // 及格 = 淡橙
val ScoreFail = Color(0xFF6B6B6B)                   // 不及格 = 次级灰

// 出勤状态
val AttendanceOnTime = Color(0xFFFF6B47)             // 准时 = 主珊瑚橙
val AttendanceLate = Color(0xFFFFD4C2)               // 迟到 = 淡橙
val AttendanceLeave = Color(0xFF6B6B6B)              // 请假 = 次级灰
val AttendanceAbsent = Color(0xFF6B6B6B)             // 旷课 = 深灰

// 奖牌渐变（保留金/银/铜语义）
val MedalGoldStart = Color(0xFFFFD700)
val MedalGoldEnd = Color(0xFFFFA500)
val MedalSilverStart = Color(0xFFE8E8E8)
val MedalSilverEnd = Color(0xFFB0B0B0)
val MedalBronzeStart = Color(0xFFCD7F32)
val MedalBronzeEnd = Color(0xFF8B4513)

// === 4. 废弃别名兼容区 ===
// v39 已全局替换所有历史引用，废弃别名已全部删除。
// 新代码请直接使用 Light*/Night*/BrandGradient* 语义令牌。

// === 5. Material 3 ColorScheme 定义（亮 / 暗）===
// 由 Theme.kt 的 SportsCoachTheme(darkTheme) 选择，所有界面通过
// MaterialTheme.colorScheme.* 或下方 app*() 访问器读取，自动随主题切换。
val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnPrimary,
    tertiary = LightTertiary,
    onTertiary = LightOnPrimary,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    // 无感分割线（极浅），appDividerColor() 直接映射本令牌
    outlineVariant = LightDivider,
)

val DarkColorScheme = darkColorScheme(
    primary = NightPrimary,
    onPrimary = NightOnPrimary,
    primaryContainer = NightPrimaryContainer,
    onPrimaryContainer = NightOnPrimaryContainer,
    secondary = NightSecondary,
    onSecondary = NightOnSecondary,
    tertiary = NightTertiary,
    onTertiary = NightOnPrimary,
    background = NightBackground,
    onBackground = NightOnBackground,
    surface = NightSurface,
    onSurface = NightOnSurface,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = NightOnSurfaceVariant,
    outline = NightOutline,
    outlineVariant = NightDivider,
)

// === 6. 主题感知 @Composable 颜色访问器 ===
// 在 Compose 中调用：appBackground() / appSurface() / appPrimary() 等，
// 统一读取 MaterialTheme.colorScheme 令牌：手动「深色模式」开关或系统切换
// Dark Mode 时自动返回对应色板，与 SportsCoachTheme 保持同步。

@Composable
fun appBackground(): Color = MaterialTheme.colorScheme.background

@Composable
fun appGroupedBackground(): Color = MaterialTheme.colorScheme.surfaceVariant

@Composable
fun appSurface(): Color = MaterialTheme.colorScheme.surface

@Composable
fun appSurfaceVariant(): Color = MaterialTheme.colorScheme.surfaceVariant

@Composable
fun appPrimary(): Color = MaterialTheme.colorScheme.primary

@Composable
fun appOnPrimary(): Color = MaterialTheme.colorScheme.onPrimary

@Composable
fun appPrimaryContainer(): Color = MaterialTheme.colorScheme.primaryContainer

@Composable
fun appOnPrimaryContainer(): Color = MaterialTheme.colorScheme.onPrimaryContainer

@Composable
fun appSecondary(): Color = MaterialTheme.colorScheme.secondary

@Composable
fun appOnSecondary(): Color = MaterialTheme.colorScheme.onSecondary

@Composable
fun appTertiary(): Color = MaterialTheme.colorScheme.tertiary

@Composable
fun appOnSurface(): Color = MaterialTheme.colorScheme.onSurface

/**
 * 次级文字色（副标题、辅助说明、未选中导航栏、占位文字、空状态提示）。
 * 亮色 #6B6B6B / 暗色 #9E9E9E，符合 WCAG AA 对比度标准。
 *
 * v39 统一：原 appTextPlaceholder() 已合并到本函数，
 * 消除"占位文字 #9E9E9E → 次级文字 #6B6B6B"的视觉断层。
 */
@Composable
fun appOnSurfaceVariant(): Color = MaterialTheme.colorScheme.onSurfaceVariant

/**
 * 极浅分割线色（替代彩色粗线，符合"无感分割"高级感设计）。
 * 亮色 Black 6% alpha / 暗色 White 20% alpha，0.5dp 厚度使用。
 */
@Composable
fun appDividerColor(): Color = MaterialTheme.colorScheme.outlineVariant

@Composable
fun appOutline(): Color = MaterialTheme.colorScheme.outline

/**
 * 分段控件（AppSegmentedTabs）选中滑块底色。
 * 亮色：纯白 #FFFFFF，浮于浅灰轨道之上（微缩的白卡片）。
 * 暗色：#34373D，比轨道 #25272D 亮一档（v65 参考稿分段选中 #33363B 档的处理，
 * M3 无对应字段，material3 darkColorScheme 的 surface 反而比 surfaceVariant 深）。
 */
@Composable
fun appSegmentThumb(): Color = if (isDarkScheme()) Color(0xFF34373D) else Color(0xFFFFFFFF)

// === 7. 语义色访问器（v48：信息横幅/状态提示，随主题切换）===
// M3 1.3.1 无 success/warning 字段，按当前生效色板（三态开关已收敛到
// colorScheme）选择亮/暗语义色。

@Composable
private fun isDarkScheme(): Boolean = MaterialTheme.colorScheme.background == NightBackground

@Composable
fun appSuccessContainer(): Color = if (isDarkScheme()) NightSuccessContainer else LightSuccessContainer

@Composable
fun appOnSuccessContainer(): Color = if (isDarkScheme()) NightOnSuccessContainer else LightOnSuccessContainer

@Composable
fun appWarningContainer(): Color = if (isDarkScheme()) NightWarningContainer else LightWarningContainer

@Composable
fun appOnWarningContainer(): Color = if (isDarkScheme()) NightOnWarningContainer else LightOnWarningContainer

@Composable
fun appInfoBlue(): Color = if (isDarkScheme()) NightInfoBlue else LightInfoBlue
