package com.shangmentiyu.sportscoach.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// === 活力珊瑚橙主题配色（Keep 风格运动活力调性）===
// 设计原则：统一主色调为珊瑚橙 #FF6B47，辅助色为同色系浅橙/淡橙，
// 彻底避免蓝色、生硬绿色、生硬红色等冷色块，传递运动激励感与温暖活力。
// - 系统底色：暖白 #FAFAFA
// - 分组背景：轻微暖灰 #F2F2F5
// - 主强调色：活力珊瑚橙 #FF6B47
// - 辅助强调色：浅橙 #FF9E7A / 淡橙 #FFD4C2
// - 圆角规格：卡片 10dp / 按钮 8dp / 输入框 10dp
// - 文字色：#1A1A1A + 次级 #6B6B6B
// - 跟随系统 Dark Mode：夜间自动切换至暗珊瑚橙主题

// 主背景：暖白
val GalaxyBackground = Color(0xFFFAFAFA)
val GalaxyBackgroundEnd = Color(0xFFFAFAFA)
val DarkBackground = Color(0xFFFAFAFA)

// 分组背景：轻微暖灰色
val IOSGroupedBackground = Color(0xFFF2F2F5)

// 表面层：纯白卡片，浮在暖灰分组底之上
val DarkSurface = Color(0xFFFFFFFF)
val GlassSurface = Color(0xFFFFFFFF)
val GlassSurfaceStrong = Color(0xFFFFFFFF)
val DarkSurfaceVariant = Color(0xFFF2F2F5)

// 强调色：统一为活力珊瑚橙
val DarkPrimary = Color(0xFFFF6B47)              // 主珊瑚橙
val DarkOnPrimary = Color(0xFFFFFFFF)
val DarkSecondary = Color(0xFFFF9E7A)            // 浅橙
val DarkOnSecondary = Color(0xFFFFFFFF)

// 文字色（统一为两级灰度层次，符合 WCAG 对比度标准）
// - 主文字 #1A1A1A：标题、正文，对比度 ≥ 12:1
// - 次级文字 #6B6B6B：副标题、占位文字、未选中导航栏、空状态提示，对比度 ≥ 4.6:1（WCAG AA）
//   统一：原 #9E9E9E 占位文字色已合并到次级文字色 #6B6B6B，避免三色灰度带来的视觉断层
val DarkOnBackground = Color(0xFF1A1A1A)         // 主文字
val DarkOnSurface = Color(0xFF1A1A1A)            // 主文字色（卡片上的标题/正文）
val DarkOnSurfaceVariant = Color(0xFF6B6B6B)     // 次级文字（副标题、辅助说明）
val DarkTextPlaceholder = Color(0xFF6B6B6B)      // 占位文字（placeholder、空状态）— 统一为次级文字色
val DarkOutline = Color(0xFFC6C6C8)              // 分隔线基色（保留兼容）
val DarkDividerColor = Color(0x0F000000)         // 极浅分割线 = Black 6% alpha

// 主题辅助色（保留发光辅助色名称兼容，统一改用珊瑚橙色系）
val GlowCyan = Color(0xFFFF6B47)                 // 主珊瑚橙
val GlowPurple = Color(0xFFFF9E7A)               // 浅橙
val GlowBlue = Color(0xFFFF6B47)                 // 主珊瑚橙

// 渐变色对（珊瑚橙色系渐变）
val GradientStart = Color(0xFFFF6B47)
val GradientEnd = Color(0xFFFF9E7A)

// 兼容旧引用
val Indigo80 = Color(0xFFFFD4C2)
val IndigoGrey80 = Color(0xFFE0E0E0)
val Pink80 = Color(0xFFFFAB91)
val Indigo40 = Color(0xFFE64A19)
val IndigoGrey40 = Color(0xFF8D6E63)
val Pink40 = Color(0xFFD84315)

// 成绩等级颜色（统一改为珊瑚橙/暖灰色系）
val ScoreExcellent = Color(0xFFFF6B47)           // 优秀 = 主珊瑚橙
val ScoreGood = Color(0xFFFF9E7A)                // 良好 = 浅橙
val ScorePass = Color(0xFFFFD4C2)                // 及格 = 淡橙
val ScoreFail = Color(0xFF6B6B6B)                // 不及格 = 次级灰

// 出勤状态颜色（统一改为珊瑚橙/暖灰色系）
val AttendanceOnTime = Color(0xFFFF6B47)         // 准时 = 主珊瑚橙
val AttendanceLate = Color(0xFFFFD4C2)           // 迟到 = 淡橙
val AttendanceLeave = Color(0xFF6B6B6B)          // 请假 = 次级灰
val AttendanceAbsent = Color(0xFF6B6B6B)         // 旷课 = 深灰

// 功能入口图标背景色（统一为主色系或高级暖灰色系）
val FeatureIconBlue = Color(0xFFFF9E7A)          // 浅橙
val FeatureIconOrange = Color(0xFFFF6B47)        // 主珊瑚橙
val FeatureIconGreen = Color(0xFFFFB74D)         // 暖金黄（辅助强调）
val FeatureIconPurple = Color(0xFFFF6B47)        // 主珊瑚橙
val FeatureIconPink = Color(0xFFFFD4C2)          // 淡橙
val FeatureIconTeal = Color(0xFF6B6B6B)          // 次级灰

// === 活力渐变配色（统一为珊瑚橙色系）===
// 头部学员卡片渐变
val HeroGradientStart = Color(0xFFFF6B47)        // 主珊瑚橙
val HeroGradientEnd = Color(0xFFFF9E7A)          // 浅橙

// 身体形态卡片渐变（统一为珊瑚橙/暖灰色系）
val VitalOrangeStart = Color(0xFFFF6B47)         // 主珊瑚橙
val VitalOrangeEnd = Color(0xFFFF9E7A)           // 浅橙
val VitalBlueStart = Color(0xFFFF9E7A)           // 浅橙
val VitalBlueEnd = Color(0xFFFF6B47)             // 主珊瑚橙
val VitalPurpleStart = Color(0xFFFF6B47)         // 主珊瑚橙
val VitalPurpleEnd = Color(0xFFFF9E7A)           // 浅橙
val VitalGreenStart = Color(0xFFFFB74D)          // 暖金黄
val VitalGreenEnd = Color(0xFFFFD4C2)            // 淡橙

// 统计数字颜色（珊瑚橙色系）
val StatLessonColor = Color(0xFFFF6B47)          // 课时 = 主珊瑚橙
val StatDurationColor = Color(0xFFFF9E7A)        // 时长 = 浅橙
val StatOnTimeColor = Color(0xFFFFB74D)          // 准时率 = 暖金黄

// 奖牌渐变（个人最佳）保留金/银/铜语义
val MedalGoldStart = Color(0xFFFFD700)
val MedalGoldEnd = Color(0xFFFFA500)
val MedalSilverStart = Color(0xFFE8E8E8)
val MedalSilverEnd = Color(0xFFB0B0B0)
val MedalBronzeStart = Color(0xFFCD7F32)
val MedalBronzeEnd = Color(0xFF8B4513)

// === iOS 18 Dark 风格配色 ===
// 跟随系统 Dark Mode 自动切换。色值对齐暗色主题：
// - 系统底色：#000000（纯黑）
// - 分组背景：#1C1C1E
// - 表面：#2C2C2E
// - 主色：暗色下更亮的珊瑚橙 #FF8A65，保证对比度
// - 文字：#FFFFFF + 次级 #EBEBF5 60% alpha
// - 分隔线：#38383A

val NightBackground = Color(0xFF000000)
val NightGroupedBackground = Color(0xFF1C1C1E)
val NightSurface = Color(0xFF2C2C2E)
val NightSurfaceVariant = Color(0xFF3A3A3C)
val NightPrimary = Color(0xFFFF8A65)              // 暗色珊瑚橙（更亮）
val NightOnPrimary = Color(0xFFFFFFFF)
val NightSecondary = Color(0xFFFFAB91)            // 暗色浅橙
val NightOnSecondary = Color(0xFFFFFFFF)
val NightOnBackground = Color(0xFFFFFFFF)
val NightOnSurface = Color(0xFFEBEBF5)
val NightOnSurfaceVariant = Color(0xFF9E9E9E)     // 暗色次级文字
val NightTextPlaceholder = Color(0xFF636366)      // 暗色占位文字
val NightOutline = Color(0xFF38383A)
val NightDividerColor = Color(0x33FFFFFF)         // 暗色极浅分割线 = White 20% alpha
val NightGlassSurface = Color(0xFF1C1C1E)
val NightGlassSurfaceStrong = Color(0xFF2C2C2E)

// === 主题感知 @Composable 颜色访问器 ===
// 在 Compose 中调用：appBackground() / appSurface() / appPrimary() 等，
// 系统切换 Dark Mode 时自动返回对应色板。
// 保留原有常量名（DarkPrimary 等）以兼容历史引用，新代码优先使用 @Composable 函数。

@Composable
fun appBackground(): Color = if (isSystemInDarkTheme()) NightBackground else DarkBackground

@Composable
fun appGroupedBackground(): Color = if (isSystemInDarkTheme()) NightGroupedBackground else IOSGroupedBackground

@Composable
fun appSurface(): Color = if (isSystemInDarkTheme()) NightSurface else DarkSurface

@Composable
fun appSurfaceVariant(): Color = if (isSystemInDarkTheme()) NightSurfaceVariant else DarkSurfaceVariant

@Composable
fun appPrimary(): Color = if (isSystemInDarkTheme()) NightPrimary else DarkPrimary

@Composable
fun appOnPrimary(): Color = if (isSystemInDarkTheme()) NightOnPrimary else DarkOnPrimary

@Composable
fun appSecondary(): Color = if (isSystemInDarkTheme()) NightSecondary else DarkSecondary

@Composable
fun appOnBackground(): Color = if (isSystemInDarkTheme()) NightOnBackground else DarkOnBackground

@Composable
fun appOnSurface(): Color = if (isSystemInDarkTheme()) NightOnSurface else DarkOnSurface

/**
 * 次级文字色（副标题、辅助说明、未选中导航栏）。
 * 亮色 #6B6B6B / 暗色 #9E9E9E，符合 WCAG AA 对比度标准。
 */
@Composable
fun appOnSurfaceVariant(): Color = if (isSystemInDarkTheme()) NightOnSurfaceVariant else DarkOnSurfaceVariant

/**
 * 占位文字色（placeholder、空状态提示）。
 * 已统一为次级文字色 #6B6B6B（亮色）/ #636366（暗色），
 * 消除原 #9E9E9E → #6B6B6B 之间的视觉断层。
 */
@Composable
fun appTextPlaceholder(): Color = if (isSystemInDarkTheme()) NightTextPlaceholder else DarkTextPlaceholder

/**
 * 极浅分割线色（替代彩色粗线，符合"无感分割"高级感设计）。
 * 亮色 Black 6% alpha / 暗色 White 20% alpha，0.5dp 厚度使用。
 */
@Composable
fun appDividerColor(): Color = if (isSystemInDarkTheme()) NightDividerColor else DarkDividerColor

@Composable
fun appOutline(): Color = if (isSystemInDarkTheme()) NightOutline else DarkOutline

@Composable
fun appGlassSurface(): Color = if (isSystemInDarkTheme()) NightGlassSurface else GlassSurface

@Composable
fun appGlassSurfaceStrong(): Color = if (isSystemInDarkTheme()) NightGlassSurfaceStrong else GlassSurfaceStrong
