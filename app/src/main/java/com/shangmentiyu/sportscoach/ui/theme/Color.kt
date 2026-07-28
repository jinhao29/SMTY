package com.shangmentiyu.sportscoach.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
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
 * 1. 背景：暖白底 #FAFAFA + 暖灰分组底 #F2F2F5
 * 2. 表面：纯白卡片 #FFFFFF（浮在分组底之上）
 * 3. 强调：珊瑚橙 #FF6B47（单一强调色，≤10% 面积）
 * 4. 文字：主 #1A1A1A（≥12:1）/ 次 #6B6B6B（≥4.6:1 WCAG AA）
 * 5. 分割：Black 6% alpha（极浅，无感分割）
 *
 * 跟随系统 Dark Mode 自动切换至暗色版色板。
 *
 * 命名规范（v39 重构：清理冗余别名，统一语义命名）：
 * - 所有令牌以 Light 或 Night 前缀区分主题
 * - 废弃别名（GlowCyan/GlowBlue/FeatureIconBlue/Indigo80 等）已删除
 * - 历史渐变名（VitalOrangeStart 等）已合并为 BrandGradientStart / BrandGradientEnd
 */

// === 1. 亮色主题（Light）===
val LightBackground = Color(0xFFFAFAFA)              // 暖白主背景
val LightGroupedBackground = Color(0xFFF2F2F5)       // 暖灰分组背景
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
val NightBackground = Color(0xFF000000)
val NightGroupedBackground = Color(0xFF1C1C1E)
val NightSurface = Color(0xFF2C2C2E)
val NightSurfaceVariant = Color(0xFF3A3A3C)
val NightPrimary = Color(0xFFFF8A65)                // 暗色珊瑚橙（更亮保证对比度）
val NightOnPrimary = Color(0xFFFFFFFF)
val NightPrimaryContainer = Color(0xFF5C2E1A)
val NightOnPrimaryContainer = Color(0xFFFFD4C2)
val NightSecondary = Color(0xFFFFAB91)
val NightOnSecondary = Color(0xFFFFFFFF)
val NightTertiary = Color(0xFFFFCC80)
val NightOnBackground = Color(0xFFFFFFFF)
val NightOnSurface = Color(0xFFEBEBF5)
val NightOnSurfaceVariant = Color(0xFF9E9E9E)
val NightOutline = Color(0xFF38383A)
val NightDivider = Color(0x33FFFFFF)                 // White 20%
val NightGlassSurface = Color(0xFF1C1C1E)
val NightGlassSurfaceStrong = Color(0xFF2C2C2E)

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

// === 4. 废弃别名兼容区（v39 过渡期保留，新代码禁止使用）===
// 这些别名指向新令牌，仅为避免大量历史引用一次性破坏编译。
// 后续清理 PR 应全局替换后删除此区块。
@Deprecated("v39：使用 LightBackground", replaceWith = ReplaceWith("LightBackground"))
val GalaxyBackground get() = LightBackground
@Deprecated("v39：使用 LightBackground", replaceWith = ReplaceWith("LightBackground"))
val GalaxyBackgroundEnd get() = LightBackground
@Deprecated("v39：使用 LightBackground", replaceWith = ReplaceWith("LightBackground"))
val DarkBackground get() = LightBackground
@Deprecated("v39：使用 LightGroupedBackground", replaceWith = ReplaceWith("LightGroupedBackground"))
val IOSGroupedBackground get() = LightGroupedBackground
@Deprecated("v39：使用 LightSurface", replaceWith = ReplaceWith("LightSurface"))
val DarkSurface get() = LightSurface
@Deprecated("v39：使用 LightSurface", replaceWith = ReplaceWith("LightSurface"))
val GlassSurface get() = LightSurface
@Deprecated("v39：使用 LightSurface", replaceWith = ReplaceWith("LightSurface"))
val GlassSurfaceStrong get() = LightSurface
@Deprecated("v39：使用 LightSurfaceVariant", replaceWith = ReplaceWith("LightSurfaceVariant"))
val DarkSurfaceVariant get() = LightSurfaceVariant
@Deprecated("v39：使用 LightPrimary", replaceWith = ReplaceWith("LightPrimary"))
val DarkPrimary get() = LightPrimary
@Deprecated("v39：使用 LightOnPrimary", replaceWith = ReplaceWith("LightOnPrimary"))
val DarkOnPrimary get() = LightOnPrimary
@Deprecated("v39：使用 LightSecondary", replaceWith = ReplaceWith("LightSecondary"))
val DarkSecondary get() = LightSecondary
@Deprecated("v39：使用 LightOnPrimary", replaceWith = ReplaceWith("LightOnPrimary"))
val DarkOnSecondary get() = LightOnPrimary
@Deprecated("v39：使用 LightOnBackground", replaceWith = ReplaceWith("LightOnBackground"))
val DarkOnBackground get() = LightOnBackground
@Deprecated("v39：使用 LightOnSurface", replaceWith = ReplaceWith("LightOnSurface"))
val DarkOnSurface get() = LightOnSurface
@Deprecated("v39：使用 LightOnSurfaceVariant", replaceWith = ReplaceWith("LightOnSurfaceVariant"))
val DarkOnSurfaceVariant get() = LightOnSurfaceVariant
@Deprecated("v39：使用 LightOnSurfaceVariant", replaceWith = ReplaceWith("LightOnSurfaceVariant"))
val DarkTextPlaceholder get() = LightOnSurfaceVariant
@Deprecated("v39：使用 LightOutline", replaceWith = ReplaceWith("LightOutline"))
val DarkOutline get() = LightOutline
@Deprecated("v39：使用 LightDivider", replaceWith = ReplaceWith("LightDivider"))
val DarkDividerColor get() = LightDivider
@Deprecated("v39：使用 LightPrimary", replaceWith = ReplaceWith("LightPrimary"))
val GlowCyan get() = LightPrimary
@Deprecated("v39：使用 LightSecondary", replaceWith = ReplaceWith("LightSecondary"))
val GlowPurple get() = LightSecondary
@Deprecated("v39：使用 LightPrimary", replaceWith = ReplaceWith("LightPrimary"))
val GlowBlue get() = LightPrimary
@Deprecated("v39：使用 BrandGradientStart", replaceWith = ReplaceWith("BrandGradientStart"))
val GradientStart get() = BrandGradientStart
@Deprecated("v39：使用 BrandGradientEnd", replaceWith = ReplaceWith("BrandGradientEnd"))
val GradientEnd get() = BrandGradientEnd
@Deprecated("v39：使用 LightPrimaryContainer", replaceWith = ReplaceWith("LightPrimaryContainer"))
val Indigo80 get() = LightPrimaryContainer
@Deprecated("v39：使用 LightOutline", replaceWith = ReplaceWith("LightOutline"))
val IndigoGrey80 get() = LightOutline
@Deprecated("v39：使用 LightSecondary", replaceWith = ReplaceWith("LightSecondary"))
val Pink80 get() = LightSecondary
@Deprecated("v39：使用 LightOnPrimaryContainer", replaceWith = ReplaceWith("LightOnPrimaryContainer"))
val Indigo40 get() = LightOnPrimaryContainer
@Deprecated("v39：使用 LightOnSurfaceVariant", replaceWith = ReplaceWith("LightOnSurfaceVariant"))
val IndigoGrey40 get() = LightOnSurfaceVariant
@Deprecated("v39：使用 LightOnPrimaryContainer", replaceWith = ReplaceWith("LightOnPrimaryContainer"))
val Pink40 get() = LightOnPrimaryContainer
// 功能入口图标背景色（统一为珊瑚橙色系，保留语义命名）
@Deprecated("v39：使用 LightSecondary", replaceWith = ReplaceWith("LightSecondary"))
val FeatureIconBlue get() = LightSecondary
@Deprecated("v39：使用 LightPrimary", replaceWith = ReplaceWith("LightPrimary"))
val FeatureIconOrange get() = LightPrimary
@Deprecated("v39：使用 LightTertiary", replaceWith = ReplaceWith("LightTertiary"))
val FeatureIconGreen get() = LightTertiary
@Deprecated("v39：使用 LightPrimary", replaceWith = ReplaceWith("LightPrimary"))
val FeatureIconPurple get() = LightPrimary
@Deprecated("v39：使用 LightPrimaryContainer", replaceWith = ReplaceWith("LightPrimaryContainer"))
val FeatureIconPink get() = LightPrimaryContainer
@Deprecated("v39：使用 LightOnSurfaceVariant", replaceWith = ReplaceWith("LightOnSurfaceVariant"))
val FeatureIconTeal get() = LightOnSurfaceVariant
// 渐变别名
@Deprecated("v39：使用 BrandGradientStart", replaceWith = ReplaceWith("BrandGradientStart"))
val HeroGradientStart get() = BrandGradientStart
@Deprecated("v39：使用 BrandGradientEnd", replaceWith = ReplaceWith("BrandGradientEnd"))
val HeroGradientEnd get() = BrandGradientEnd
@Deprecated("v39：使用 BrandGradientStart", replaceWith = ReplaceWith("BrandGradientStart"))
val VitalOrangeStart get() = BrandGradientStart
@Deprecated("v39：使用 BrandGradientEnd", replaceWith = ReplaceWith("BrandGradientEnd"))
val VitalOrangeEnd get() = BrandGradientEnd
@Deprecated("v39：使用 LightSecondary", replaceWith = ReplaceWith("LightSecondary"))
val VitalBlueStart get() = LightSecondary
@Deprecated("v39：使用 LightPrimary", replaceWith = ReplaceWith("LightPrimary"))
val VitalBlueEnd get() = LightPrimary
@Deprecated("v39：使用 BrandGradientStart", replaceWith = ReplaceWith("BrandGradientStart"))
val VitalPurpleStart get() = BrandGradientStart
@Deprecated("v39：使用 BrandGradientEnd", replaceWith = ReplaceWith("BrandGradientEnd"))
val VitalPurpleEnd get() = BrandGradientEnd
@Deprecated("v39：使用 LightTertiary", replaceWith = ReplaceWith("LightTertiary"))
val VitalGreenStart get() = LightTertiary
@Deprecated("v39：使用 LightPrimaryContainer", replaceWith = ReplaceWith("LightPrimaryContainer"))
val VitalGreenEnd get() = LightPrimaryContainer
// 统计数字颜色
@Deprecated("v39：使用 LightPrimary", replaceWith = ReplaceWith("LightPrimary"))
val StatLessonColor get() = LightPrimary
@Deprecated("v39：使用 LightSecondary", replaceWith = ReplaceWith("LightSecondary"))
val StatDurationColor get() = LightSecondary
@Deprecated("v39：使用 LightTertiary", replaceWith = ReplaceWith("LightTertiary"))
val StatOnTimeColor get() = LightTertiary
// 暗色占位文字别名
@Deprecated("v39：使用 NightOnSurfaceVariant", replaceWith = ReplaceWith("NightOnSurfaceVariant"))
val NightTextPlaceholder get() = NightOnSurfaceVariant
@Deprecated("v39：使用 NightDivider", replaceWith = ReplaceWith("NightDivider"))
val NightDividerColor get() = NightDivider

// === 5. 主题感知 @Composable 颜色访问器 ===
// 在 Compose 中调用：appBackground() / appSurface() / appPrimary() 等，
// 系统切换 Dark Mode 时自动返回对应色板。

@Composable
fun appBackground(): Color = if (isSystemInDarkTheme()) NightBackground else LightBackground

@Composable
fun appGroupedBackground(): Color = if (isSystemInDarkTheme()) NightGroupedBackground else LightGroupedBackground

@Composable
fun appSurface(): Color = if (isSystemInDarkTheme()) NightSurface else LightSurface

@Composable
fun appSurfaceVariant(): Color = if (isSystemInDarkTheme()) NightSurfaceVariant else LightSurfaceVariant

@Composable
fun appPrimary(): Color = if (isSystemInDarkTheme()) NightPrimary else LightPrimary

@Composable
fun appOnPrimary(): Color = if (isSystemInDarkTheme()) NightOnPrimary else LightOnPrimary

@Composable
fun appPrimaryContainer(): Color = if (isSystemInDarkTheme()) NightPrimaryContainer else LightPrimaryContainer

@Composable
fun appOnPrimaryContainer(): Color = if (isSystemInDarkTheme()) NightOnPrimaryContainer else LightOnPrimaryContainer

@Composable
fun appSecondary(): Color = if (isSystemInDarkTheme()) NightSecondary else LightSecondary

@Composable
fun appTertiary(): Color = if (isSystemInDarkTheme()) NightTertiary else LightTertiary

@Composable
fun appOnBackground(): Color = if (isSystemInDarkTheme()) NightOnBackground else LightOnBackground

@Composable
fun appOnSurface(): Color = if (isSystemInDarkTheme()) NightOnSurface else LightOnSurface

/**
 * 次级文字色（副标题、辅助说明、未选中导航栏、占位文字、空状态提示）。
 * 亮色 #6B6B6B / 暗色 #9E9E9E，符合 WCAG AA 对比度标准。
 *
 * v39 统一：原 appTextPlaceholder() 已合并到本函数，
 * 消除"占位文字 #9E9E9E → 次级文字 #6B6B6B"的视觉断层。
 */
@Composable
fun appOnSurfaceVariant(): Color = if (isSystemInDarkTheme()) NightOnSurfaceVariant else LightOnSurfaceVariant

/**
 * 占位文字色（已统一为次级文字色）。
 * @deprecated v39：使用 [appOnSurfaceVariant]
 */
@Deprecated("v39：使用 appOnSurfaceVariant", ReplaceWith("appOnSurfaceVariant()"))
@Composable
fun appTextPlaceholder(): Color = appOnSurfaceVariant()

/**
 * 极浅分割线色（替代彩色粗线，符合"无感分割"高级感设计）。
 * 亮色 Black 6% alpha / 暗色 White 20% alpha，0.5dp 厚度使用。
 */
@Composable
fun appDividerColor(): Color = if (isSystemInDarkTheme()) NightDivider else LightDivider

@Composable
fun appOutline(): Color = if (isSystemInDarkTheme()) NightOutline else LightOutline

@Composable
fun appGlassSurface(): Color = if (isSystemInDarkTheme()) NightGlassSurface else LightSurface

@Composable
fun appGlassSurfaceStrong(): Color = if (isSystemInDarkTheme()) NightGlassSurfaceStrong else LightSurface
