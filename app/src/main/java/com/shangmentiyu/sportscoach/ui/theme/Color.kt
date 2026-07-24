package com.shangmentiyu.sportscoach.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// === iOS 18 Light 风格配色 ===
// 设计原则：对标 Apple Human Interface Guidelines
// - 系统底色：纯白 + 浅灰分组背景（#F2F2F7）
// - 主强调色：iOS SystemBlue (#007AFF)
// - 圆角规格：卡片 10pt / 按钮 8pt / 输入框 10pt
// - 文字色：iOS Label Color (#000000) + Secondary Label (#3C3C43 60% alpha)
// - 触碰目标 ≥44pt
// - 跟随系统 Dark Mode：夜间自动切换至 iOS Dark 配色

// 主背景：iOS SystemBackground（纯白）
val GalaxyBackground = Color(0xFFFFFFFF)
val GalaxyBackgroundEnd = Color(0xFFFFFFFF)
val DarkBackground = Color(0xFFFFFFFF)

// 分组背景：iOS SystemGroupedBackground（浅灰底，用于 Inset Grouped 表单/列表底色）
val IOSGroupedBackground = Color(0xFFF2F2F7)

// 表面层：iOS SecondarySystemGroupedBackground（纯白卡片，浮在浅灰分组底之上）
val DarkSurface = Color(0xFFFFFFFF)
val GlassSurface = Color(0xFFFFFFFF)
val GlassSurfaceStrong = Color(0xFFFFFFFF)
val DarkSurfaceVariant = Color(0xFFF2F2F7)

// 强调色：iOS SystemBlue
val DarkPrimary = Color(0xFF007AFF)              // iOS SystemBlue
val DarkOnPrimary = Color(0xFFFFFFFF)
val DarkSecondary = Color(0xFF5856D6)            // iOS SystemIndigo
val DarkOnSecondary = Color(0xFFFFFFFF)

// 文字色：iOS Label Color 系列
val DarkOnBackground = Color(0xFF000000)         // Label（主文字）
val DarkOnSurface = Color(0xFF3C3C43)            // Label（次级文字，未加 alpha）
val DarkOutline = Color(0xFFC6C6C8)              // iOS Separator（细分隔线）

// iOS 系统色（保留发光辅助色名称兼容，统一改用 iOS 系统色）
val GlowCyan = Color(0xFF007AFF)                 // iOS SystemBlue
val GlowPurple = Color(0xFF5856D6)               // iOS SystemIndigo
val GlowBlue = Color(0xFF007AFF)                 // iOS SystemBlue

// 渐变色对（iOS 风格少用渐变，保留接口兼容但用单色）
val GradientStart = Color(0xFF007AFF)
val GradientEnd = Color(0xFF007AFF)

// 兼容旧引用
val Indigo80 = Color(0xFFC5CAE9)
val IndigoGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Indigo40 = Color(0xFF4338CA)
val IndigoGrey40 = Color(0xFF625B71)
val Pink40 = Color(0xFF7D5260)

// 成绩等级颜色（对齐 iOS 系统色：Green/Blue/Orange/Red）
val ScoreExcellent = Color(0xFF34C759)           // iOS SystemGreen
val ScoreGood = Color(0xFF007AFF)                // iOS SystemBlue
val ScorePass = Color(0xFFFF9500)                // iOS SystemOrange
val ScoreFail = Color(0xFFFF3B30)                // iOS SystemRed

// 出勤状态颜色
val AttendanceOnTime = Color(0xFF34C759)         // iOS SystemGreen
val AttendanceLate = Color(0xFFFF9500)           // iOS SystemOrange
val AttendanceLeave = Color(0xFF8E8E93)          // iOS SystemGray
val AttendanceAbsent = Color(0xFFFF3B30)         // iOS SystemRed

// 功能入口图标背景色（iOS Settings 风格：每个功能独特彩色方形背景）
val FeatureIconBlue = Color(0xFF007AFF)          // iOS SystemBlue
val FeatureIconOrange = Color(0xFFFF9500)        // iOS SystemOrange
val FeatureIconGreen = Color(0xFF34C759)         // iOS SystemGreen
val FeatureIconPurple = Color(0xFF5856D6)        // iOS SystemIndigo
val FeatureIconPink = Color(0xFFFF2D55)          // iOS SystemPink
val FeatureIconTeal = Color(0xFF30B0C7)          // iOS SystemTeal

// === 活力渐变配色（Dribbble 风格，明亮通透）===
// 头部学员卡片渐变（蓝紫渐变，活力感强）
val HeroGradientStart = Color(0xFF5B5FDE)        // 活力靛蓝
val HeroGradientEnd = Color(0xFF7B68EE)          // 中紫罗兰

// 身体形态卡片渐变（4 色系，每项独立）
val VitalOrangeStart = Color(0xFFFFB347)         // 暖橙
val VitalOrangeEnd = Color(0xFFFF9500)           // iOS SystemOrange
val VitalBlueStart = Color(0xFF5AC8FA)           // 活力青蓝
val VitalBlueEnd = Color(0xFF007AFF)             // iOS SystemBlue
val VitalPurpleStart = Color(0xFFAF52DE)         // 活力紫
val VitalPurpleEnd = Color(0xFF5856D6)           // iOS SystemIndigo
val VitalGreenStart = Color(0xFF5ED8A4)          // 活力薄荷绿
val VitalGreenEnd = Color(0xFF34C759)            // iOS SystemGreen

// 统计数字活力色
val StatLessonColor = Color(0xFF007AFF)          // 课时=蓝
val StatDurationColor = Color(0xFFAF52DE)        // 时长=紫
val StatOnTimeColor = Color(0xFF34C759)          // 准时率=绿

// 奖牌渐变（个人最佳）
val MedalGoldStart = Color(0xFFFFD700)
val MedalGoldEnd = Color(0xFFFFA500)
val MedalSilverStart = Color(0xFFE8E8E8)
val MedalSilverEnd = Color(0xFFB0B0B0)
val MedalBronzeStart = Color(0xFFCD7F32)
val MedalBronzeEnd = Color(0xFF8B4513)

// === iOS 18 Dark 风格配色 ===
// 跟随系统 Dark Mode 自动切换。色值对齐 Apple HIG Dark Mode：
// - 系统底色：#000000（纯黑）
// - 分组背景：#1C1C1E
// - 表面：#2C2C2E
// - 主色：#0A84FF（Dark Mode SystemBlue，比亮色更亮）
// - 文字：#FFFFFF + Secondary Label #EBEBF5 60% alpha
// - 分隔线：#38383A

val NightBackground = Color(0xFF000000)
val NightGroupedBackground = Color(0xFF1C1C1E)
val NightSurface = Color(0xFF2C2C2E)
val NightSurfaceVariant = Color(0xFF3A3A3C)
val NightPrimary = Color(0xFF0A84FF)              // Dark Mode SystemBlue
val NightOnPrimary = Color(0xFFFFFFFF)
val NightSecondary = Color(0xFF5E5CE6)            // Dark Mode SystemIndigo
val NightOnSecondary = Color(0xFFFFFFFF)
val NightOnBackground = Color(0xFFFFFFFF)
val NightOnSurface = Color(0xFFEBEBF5)
val NightOutline = Color(0xFF38383A)
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

@Composable
fun appOutline(): Color = if (isSystemInDarkTheme()) NightOutline else DarkOutline

@Composable
fun appGlassSurface(): Color = if (isSystemInDarkTheme()) NightGlassSurface else GlassSurface

@Composable
fun appGlassSurfaceStrong(): Color = if (isSystemInDarkTheme()) NightGlassSurfaceStrong else GlassSurfaceStrong
