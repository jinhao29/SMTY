package com.shangmentiyu.sportscoach.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// === v36 视觉重构：全局深色卡片标准（参考学员列表最新截图）===
// 设计原则：
// - 全局背景：极浅灰白 #F5F7FA（取消 #F4F6F9，更柔和）
// - 全局卡片背景：深色 #1C1C1E（统一替换原浅色白卡）
// - 主色调：柔和紫 #6C5CE7（标签、选中、强调色）
// - 辅色：青色渐变 #3A7BD5 → #00D2FF（头像、签到按钮、数据标签）
// - 卡片圆角：统一 20dp
// - 主标题：纯白 #FFFFFF
// - 副标题/标签：浅灰 #A0A0A5（取消原 #8E8E93，更通透）
// - 阴影：offsetY=4dp, blurRadius=12dp, color=#0A000000（极柔和）
// - 边框：全局禁用，仅依赖阴影与深色卡片色区隔层级
// - 跟随系统 Dark Mode：夜间自动切换至暗色主题

// 主背景：极浅灰白 #F5F7FA（v36 取消 #F4F6F9）
val GalaxyBackground = Color(0xFFF5F7FA)
val GalaxyBackgroundEnd = Color(0xFFF5F7FA)
val DarkBackground = Color(0xFFF5F7FA)

// 分组背景：统一极浅灰白 #F5F7FA
val IOSGroupedBackground = Color(0xFFF5F7FA)

// 表面层：纯白卡片，浮在浅灰底之上
val DarkSurface = Color(0xFFFFFFFF)
val GlassSurface = Color(0xFFFFFFFF)
val GlassSurfaceStrong = Color(0xFFFFFFFF)
val DarkSurfaceVariant = Color(0xFFEFEEF2)

// === 强调色：柔和紫 #6C5CE7（参考图3 主色调）===
val DarkPrimary = Color(0xFF6C5CE7)               // 主色 柔和紫
val DarkOnPrimary = Color(0xFFFFFFFF)
val DarkSecondary = Color(0xFF00D2FF)             // 辅色 青色（学员卡渐变起点）
val DarkOnSecondary = Color(0xFFFFFFFF)

// === 文字色（参考图2/3）===
// - 主文字 #1C1C1E：标题、正文，对比度 ≥ 12:1
// - 次级文字 #8E8E93：副标题、占位文字、未选中导航栏（iOS 标准灰）
val DarkOnBackground = Color(0xFF1C1C1E)         // 主文字
val DarkOnSurface = Color(0xFF1C1C1E)            // 主文字色（卡片上的标题/正文）
val DarkOnSurfaceVariant = Color(0xFF8E8E93)     // 次级文字（iOS 灰）
val DarkTextPlaceholder = Color(0xFFC7C7CC)       // 占位文字（更浅）
val DarkOutline = Color(0xFFE5E5EA)              // 极浅分隔线基色（保留兼容）
val DarkDividerColor = Color(0x0A000000)         // 极浅分割线 = Black 4% alpha

// === 主题辅助色（统一改为紫色/青色系）===
val GlowCyan = Color(0xFF00D2FF)                  // 青色（学员卡按钮高亮）
val GlowPurple = Color(0xFF6C5CE7)               // 主色紫
val GlowBlue = Color(0xFF3A7BD5)                 // 青色渐变终点

// === 渐变色对（参考图2 学员卡头像青色渐变）===
val GradientStart = Color(0xFF00D2FF)
val GradientEnd = Color(0xFF3A7BD5)

// 兼容旧引用（已统一为紫色/青色系）
val Indigo80 = Color(0xFF9B8AE5)
val IndigoGrey80 = Color(0xFFE5E5EA)
val Pink80 = Color(0xFFB8A4FF)
val Indigo40 = Color(0xFF6C5CE7)
val IndigoGrey40 = Color(0xFF8E8E93)
val Pink40 = Color(0xFF5849C7)

// === 全局深色卡片标准（v36：统一为 #1C1C1E，所有页面共用）===
val CardBackground = Color(0xFF1C1C1E)            // 全局深色卡背景
val CardOnDark = Color(0xFFFFFFFF)                // 主标题 纯白
val CardSubOnDark = Color(0xFFA0A0A5)              // 副标题/标签 浅灰（v36: #8E8E93 → #A0A0A5）

// === 深色卡内胶囊标签色板（v36 新增）===
// 课时类型等"高亮标签"：深紫底 + 浅紫字
val LessonTypeChipBg = Color(0xFF2D2D31)          // 深紫底（接近黑紫）
val LessonTypeChipText = Color(0xFFB8A4FF)         // 浅紫字
// 剩余课时等"强调标签"：深紫底 + 浅紫字（与课时类型一致风格）
val RemainingChipBg = Color(0xFF2D2D31)
val RemainingChipText = Color(0xFFB8A4FF)
// 身高/年龄等"数据标签"：深灰底 + 青字
val DataChipBg = Color(0xFF2C2C2E)                 // 深灰底
val DataChipText = Color(0xFF00D2FF)               // 青字

// === 深色卡内底部操作按钮色板（v36 新增）===
val EditButtonBg = Color(0xFF2C2C2E)               // 编辑按钮 深灰底
val EditButtonText = Color(0xFFA0A0A5)             // 编辑按钮文字 浅灰
val DeleteButtonBg = Color(0xFF3A1A1A)            // 删除按钮 深红底
val DeleteButtonText = Color(0xFFFF453A)          // 删除按钮文字 红
// 签到按钮渐变（与头像渐变一致：#3A7BD5 → #00D2FF）
val SignButtonStart = Color(0xFF3A7BD5)
val SignButtonEnd = Color(0xFF00D2FF)

// === 学员卡片专用别名（保持向后兼容，色值随全局令牌同步）===
val StudentCardDark = CardBackground               // = #1C1C1E
val StudentCardOnDark = CardOnDark                // = #FFFFFF
val StudentCardSubOnDark = CardSubOnDark          // = #A0A0A5（v36 更新）
val StudentCardButtonBg = DataChipBg              // = #2C2C2E
val StudentCardButtonIcon = DataChipText           // = #00D2FF

// === 成绩等级颜色（保留语义，使用更柔和的色阶）===
val ScoreExcellent = Color(0xFF6C5CE7)            // 优秀 = 主色紫
val ScoreGood = Color(0xFF00D2FF)                // 良好 = 青色
val ScorePass = Color(0xFF8E8E93)                // 及格 = iOS 灰
val ScoreFail = Color(0xFFFF453A)                // 不及格 = iOS 红

// === 出勤状态颜色（保留语义）===
val AttendanceOnTime = Color(0xFF30D158)         // 准时 = iOS 绿
val AttendanceLate = Color(0xFFFFD60A)           // 迟到 = iOS 黄
val AttendanceLeave = Color(0xFF8E8E93)          // 请假 = iOS 灰
val AttendanceAbsent = Color(0xFFFF453A)         // 旷课 = iOS 红

// === 功能入口图标背景色（统一为紫色/青色系，移除橙色）===
val FeatureIconBlue = Color(0xFF3A7BD5)          // 青色渐变终点
val FeatureIconOrange = Color(0xFF6C5CE7)        // 主色紫（替换原橙色）
val FeatureIconGreen = Color(0xFF30D158)         // iOS 绿
val FeatureIconPurple = Color(0xFF6C5CE7)        // 主色紫
val FeatureIconPink = Color(0xFFB8A4FF)          // 浅紫
val FeatureIconTeal = Color(0xFF00D2FF)          // 青色

// === 渐变配色（参考图2 学员卡青色渐变）===
// 头部学员卡片渐变（青色 → 深蓝）
val HeroGradientStart = Color(0xFF00D2FF)
val HeroGradientEnd = Color(0xFF3A7BD5)

// 身体形态卡片渐变（统一为紫色/青色系）
val VitalOrangeStart = Color(0xFF6C5CE7)          // 主色紫
val VitalOrangeEnd = Color(0xFF9B8AE5)            // 浅紫
val VitalBlueStart = Color(0xFF00D2FF)            // 青色
val VitalBlueEnd = Color(0xFF3A7BD5)              // 深蓝
val VitalPurpleStart = Color(0xFF6C5CE7)          // 主色紫
val VitalPurpleEnd = Color(0xFF9B8AE5)            // 浅紫
val VitalGreenStart = Color(0xFF30D158)           // iOS 绿
val VitalGreenEnd = Color(0xFF5AC85B)             // 浅绿

// 统计数字颜色（紫色/青色系）
val StatLessonColor = Color(0xFF6C5CE7)          // 课时 = 主色紫
val StatDurationColor = Color(0xFF00D2FF)        // 时长 = 青色
val StatOnTimeColor = Color(0xFF30D158)           // 准时率 = iOS 绿

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
// - 主色：暗色下更亮的紫 #8A79E9，保证对比度
// - 文字：#FFFFFF + 次级 #EBEBF5 60% alpha
// - 分隔线：#38383A
val NightBackground = Color(0xFF000000)
val NightGroupedBackground = Color(0xFF1C1C1E)
val NightSurface = Color(0xFF2C2C2E)
val NightSurfaceVariant = Color(0xFF3A3A3C)
val NightPrimary = Color(0xFF8A79E9)              // 暗色紫（更亮）
val NightOnPrimary = Color(0xFFFFFFFF)
val NightSecondary = Color(0xFF5AC8FF)             // 暗色青
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
 * 亮色 #8E8E93（iOS 灰）/ 暗色 #9E9E9E，符合 WCAG AA 对比度标准。
 */
@Composable
fun appOnSurfaceVariant(): Color = if (isSystemInDarkTheme()) NightOnSurfaceVariant else DarkOnSurfaceVariant

/**
 * 占位文字色（placeholder、空状态提示）。
 * 亮色 #C7C7CC（iOS 浅灰）/ 暗色 #636366。
 */
@Composable
fun appTextPlaceholder(): Color = if (isSystemInDarkTheme()) NightTextPlaceholder else DarkTextPlaceholder

/**
 * 极浅分割线色（替代彩色粗线，符合"无感分割"高级感设计）。
 * 亮色 Black 4% alpha / 暗色 White 20% alpha，0.5dp 厚度使用。
 */
@Composable
fun appDividerColor(): Color = if (isSystemInDarkTheme()) NightDividerColor else DarkDividerColor

@Composable
fun appOutline(): Color = if (isSystemInDarkTheme()) NightOutline else DarkOutline

@Composable
fun appGlassSurface(): Color = if (isSystemInDarkTheme()) NightGlassSurface else GlassSurface

@Composable
fun appGlassSurfaceStrong(): Color = if (isSystemInDarkTheme()) NightGlassSurfaceStrong else GlassSurfaceStrong
