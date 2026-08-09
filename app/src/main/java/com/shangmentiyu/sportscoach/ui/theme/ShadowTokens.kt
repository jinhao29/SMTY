package com.shangmentiyu.sportscoach.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 全局阴影 Token。
 *
 * 三档黑色投影，明暗主题通用（此前散落在各文件的直写常量收拢为单一改动点）：
 * - [soft]：柔和投影（白色卡片常用，如 StyledDropdown / StudentListItem）
 * - [card]：标准卡片投影（4dp 场景常用，如 BmiCalculator / ScheduleCalendar / HomeComponents）
 * - [strong]：强投影（较深卡片 / 浮层常用）
 *
 * 保留特例（彩色投影，不强制收拢）：
 * - FloatingBottomBar FAB 的 primary 泛光投影
 * - HomeComponents 首页特色卡片的 primary 泛光投影
 */
object ShadowTokens {
    val softAmbient = Color(0x04000000)
    val softSpot = Color(0x06000000)

    val cardAmbient = Color(0x0D000000)
    val cardSpot = Color(0x14000000)

    val strongAmbient = Color(0x1A000000)
    val strongSpot = Color(0x1A000000)
}