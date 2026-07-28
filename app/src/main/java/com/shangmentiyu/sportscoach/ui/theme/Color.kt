package com.shangmentiyu.sportscoach.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * v38 全局深色主题重构：彻底替换浅色背景，统一为深色/黑色主题 UI。
 *
 * 设计令牌（全局深色视觉规范）：
 * - 全局应用背景：#121212（纯黑灰底色）
 * - 全局主要卡片背景：#1C1C1E（深灰）
 * - 所有卡片圆角：统一 20.dp
 * - 主色调（按钮、胶囊、强调文字）：#6C5CE7（柔和紫）
 * - 主标题/名字：#FFFFFF（纯白）
 * - 次要信息/副标题：#A6A8AB（浅灰）
 * - 标签/高亮文字：#C4B5FD（浅紫）
 * - 阴影与边框：彻底移除所有实线边框，仅极弱内外阴影
 *
 * 说明：本版本取消"亮色/暗色"双轨制，统一只保留深色主题。
 * isSystemInDarkTheme() 不再切换到浅色，所有路径均返回深色令牌。
 */

// === 全局背景 ===
val GalaxyBackground = Color(0xFF121212)           // 全局应用背景 纯黑灰
val GalaxyBackgroundEnd = Color(0xFF181A1C)        // 渐变终点 深灰
val DarkBackground = Color(0xFF121212)              // 主背景
val IOSGroupedBackground = Color(0xFF121212)        // 分组背景

// === 表面层 ===
val DarkSurface = Color(0xFF1C1C1E)                 // 全局主要卡片背景 深灰
val LightSurface = Color(0xFF1C1C1E)                // 统一深色（不再保留白底）
val GlassSurface = Color(0xFF1C1C1E)
val GlassSurfaceStrong = Color(0xFF242426)
val DarkSurfaceVariant = Color(0xFF242426)

// === 胶囊按钮色板（顶部 Tab 栏）===
// 未选中：深灰色背景 #2C2C2E + 白字（用户要求）
// 选中：紫色 #6C5CE7 + 白字
val CapsuleSelectedBg = Color(0xFF6C5CE7)
val CapsuleSelectedText = Color(0xFFFFFFFF)
val CapsuleUnselectedBg = Color(0xFF2C2C2E)
val CapsuleUnselectedText = Color(0xFFFFFFFF)

// === 搜索框背景：深灰色胶囊 #2C2C2E ===
val SearchFieldBg = Color(0xFF2C2C2E)

// === 底部导航栏背景：深黑色 #121212（不透明）===
val BottomNavBg = Color(0xFF121212)

// === 强调色：柔和紫 #6C5CE7 ===
val DarkPrimary = Color(0xFF6C5CE7)
val DarkOnPrimary = Color(0xFFFFFFFF)
val DarkSecondary = Color(0xFF3A7BD5)
val DarkOnSecondary = Color(0xFFFFFFFF)

// === 文字色 ===
val DarkOnBackground = Color(0xFFFFFFFF)            // 主标题/名字 纯白
val DarkOnSurface = Color(0xFFFFFFFF)               // 卡片主文字 纯白
val DarkOnSurfaceVariant = Color(0xFFA6A8AB)         // 次要信息/副标题 浅灰
val DarkTextPlaceholder = Color(0xFF6E7072)          // 占位文字 深灰
val DarkOutline = Color.Transparent                  // 全局禁用边框
val DarkDividerColor = Color(0x1AFFFFFF)            // 极弱分割线 White 10%

// === 主题辅助色 ===
val GlowCyan = Color(0xFF00D2FF)
val GlowPurple = Color(0xFF6C5CE7)
val GlowBlue = Color(0xFF3A7BD5)

// === 渐变色对 ===
val GradientStart = Color(0xFF00D2FF)
val GradientEnd = Color(0xFF3A7BD5)

// === 兼容旧引用 ===
val Indigo80 = Color(0xFF9B8AE5)
val IndigoGrey80 = Color(0xFFA6A8AB)
val Pink80 = Color(0xFFB8A4FF)
val Indigo40 = Color(0xFF6C5CE7)
val IndigoGrey40 = Color(0xFFA6A8AB)
val Pink40 = Color(0xFF5849C7)

// === 全局深色卡片标准 ===
val CardBackground = Color(0xFF1C1C1E)                // 全局卡片背景
val CardOnDark = Color(0xFFFFFFFF)                   // 主标题 纯白
val CardSubOnDark = Color(0xFFA6A8AB)               // 副标题/标签 浅灰

// === 高亮文字色（标签/高亮文字：浅紫 #C4B5FD）===
val HighlightText = Color(0xFFC4B5FD)

// === 深色卡内胶囊标签色板 ===
val LessonTypeChipBg = Color(0xFF2D2D31)
val LessonTypeChipText = Color(0xFFC4B5FD)           // 浅紫字
val RemainingChipBg = Color(0xFF2D2D31)
val RemainingChipText = Color(0xFFC4B5FD)            // 浅紫字
val DataChipBg = Color(0xFF2C2C2E)
val DataChipText = Color(0xFF00D2FF)

// === 深色卡内底部操作按钮色板 ===
val EditButtonBg = Color(0xFF2C2C2E)
val EditButtonText = Color(0xFFA6A8AB)
val DeleteButtonBg = Color(0xFF3A1A1A)
val DeleteButtonText = Color(0xFFFF453A)
val SignButtonStart = Color(0xFF3A7BD5)
val SignButtonEnd = Color(0xFF00D2FF)

// === 学员卡片专用别名（向后兼容）===
val StudentCardDark = CardBackground
val StudentCardOnDark = CardOnDark
val StudentCardSubOnDark = CardSubOnDark
val StudentCardButtonBg = DataChipBg
val StudentCardButtonIcon = DataChipText

// === 成绩等级颜色 ===
val ScoreExcellent = Color(0xFF6C5CE7)
val ScoreGood = Color(0xFF00D2FF)
val ScorePass = Color(0xFFA6A8AB)
val ScoreFail = Color(0xFFFF453A)

// === 出勤状态颜色 ===
val AttendanceOnTime = Color(0xFF30D158)
val AttendanceLate = Color(0xFFFFD60A)
val AttendanceLeave = Color(0xFFA6A8AB)
val AttendanceAbsent = Color(0xFFFF453A)

// === 功能入口图标背景色 ===
val FeatureIconBlue = Color(0xFF3A7BD5)
val FeatureIconOrange = Color(0xFF6C5CE7)
val FeatureIconGreen = Color(0xFF30D158)
val FeatureIconPurple = Color(0xFF6C5CE7)
val FeatureIconPink = Color(0xFFB8A4FF)
val FeatureIconTeal = Color(0xFF00D2FF)

// === 渐变配色 ===
val HeroGradientStart = Color(0xFF00D2FF)
val HeroGradientEnd = Color(0xFF3A7BD5)

val VitalOrangeStart = Color(0xFF6C5CE7)
val VitalOrangeEnd = Color(0xFF9B8AE5)
val VitalBlueStart = Color(0xFF00D2FF)
val VitalBlueEnd = Color(0xFF3A7BD5)
val VitalPurpleStart = Color(0xFF6C5CE7)
val VitalPurpleEnd = Color(0xFF9B8AE5)
val VitalGreenStart = Color(0xFF30D158)
val VitalGreenEnd = Color(0xFF5AC85B)

val StatLessonColor = Color(0xFF6C5CE7)
val StatDurationColor = Color(0xFF00D2FF)
val StatOnTimeColor = Color(0xFF30D158)

val MedalGoldStart = Color(0xFFFFD700)
val MedalGoldEnd = Color(0xFFFFA500)
val MedalSilverStart = Color(0xFFE8E8E8)
val MedalSilverEnd = Color(0xFFB0B0B0)
val MedalBronzeStart = Color(0xFFCD7F32)
val MedalBronzeEnd = Color(0xFF8B4513)

// === v38 起：暗色令牌统一为基础令牌（不再双轨）===
val NightBackground = Color(0xFF121212)
val NightGroupedBackground = Color(0xFF121212)
val NightSurface = Color(0xFF1C1C1E)
val NightSurfaceVariant = Color(0xFF242426)
val NightPrimary = Color(0xFF6C5CE7)
val NightOnPrimary = Color(0xFFFFFFFF)
val NightSecondary = Color(0xFF3A7BD5)
val NightOnSecondary = Color(0xFFFFFFFF)
val NightOnBackground = Color(0xFFFFFFFF)
val NightOnSurface = Color(0xFFFFFFFF)
val NightOnSurfaceVariant = Color(0xFFA6A8AB)
val NightTextPlaceholder = Color(0xFF6E7072)
val NightOutline = Color.Transparent
val NightDividerColor = Color(0x1AFFFFFF)
val NightGlassSurface = Color(0xFF1C1C1E)
val NightGlassSurfaceStrong = Color(0xFF242426)

// === v38 起：lightCardBackground 统一为深色卡 ===
val LightCardBackground = Color(0xFF1C1C1E)

// === v38 起：VitalAppBar 背景改为深色 ===
val VitalAppBarBgStart = Color(0xFF1C1C1E)
val VitalAppBarBgEnd = Color(0xFF1C1C1E)

// === 主题感知 @Composable 颜色访问器（v38：统一返回深色令牌）===
// isSystemInDarkTheme() 不再切换浅色路径，所有访问器返回深色令牌
// 保留函数签名是为了避免修改所有调用方，仅令牌值改变

@Composable
fun appBackground(): Color = DarkBackground

@Composable
fun appGroupedBackground(): Color = IOSGroupedBackground

@Composable
fun appSurface(): Color = DarkSurface

@Composable
fun appSurfaceVariant(): Color = DarkSurfaceVariant

@Composable
fun appPrimary(): Color = DarkPrimary

@Composable
fun appOnPrimary(): Color = DarkOnPrimary

@Composable
fun appSecondary(): Color = DarkSecondary

@Composable
fun appOnBackground(): Color = DarkOnBackground

@Composable
fun appOnSurface(): Color = DarkOnSurface

@Composable
fun appOnSurfaceVariant(): Color = DarkOnSurfaceVariant

@Composable
fun appTextPlaceholder(): Color = DarkTextPlaceholder

@Composable
fun appDividerColor(): Color = DarkDividerColor

@Composable
fun appOutline(): Color = DarkOutline

@Composable
fun appGlassSurface(): Color = GlassSurface

@Composable
fun appGlassSurfaceStrong(): Color = GlassSurfaceStrong
