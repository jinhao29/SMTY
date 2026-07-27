package com.shangmentiyu.sportscoach.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * iOS 18 字体层级（对标 SF Pro Text / Display）。
 *
 * 安卓无 SF Pro，使用系统默认 sans-serif（Roboto Flex 在 Android 12+ 可动态调节字重），
 * 通过字重和字号还原 iOS Large Title / Title / Body 的视觉节奏。
 *
 * 关键规格（iOS HIG）：
 * - Large Title  34pt Bold    （首页大标题）
 * - Title 1      28pt Bold    （页面主标题）
 * - Title 2      22pt Bold    （段落大标题）
 * - Title 3      20pt SemiBold
 * - Headline     17pt SemiBold（列表项主文字）
 * - Body         17pt Regular （正文）
 * - Callout      16pt Regular
 * - Subheadline  15pt Regular （列表项副文字）
 * - Footnote     13pt Regular
 * - Caption      12pt Regular （辅助说明）
 */
val Typography = Typography(
    // displayLarge → Large Title
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        letterSpacing = (-0.5).sp,
        lineHeight = 41.sp
    ),
    // displayMedium → Title 1
    displayMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = (-0.3).sp,
        lineHeight = 34.sp
    ),
    // displaySmall → Title 2
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    // headlineLarge → Title 3
    headlineLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp
    ),
    // headlineMedium → Title 2 备用
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    // headlineSmall → Headline（列表项主标题）—— v35 视觉重构：16sp 标题
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    // titleLarge → Navigation Bar Title —— v35：16sp
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    // titleMedium → Headline 备用 —— v35：16sp 标题统一
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    // titleSmall → Subheadline Bold —— v35：14sp 副标题
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    // bodyLarge → Body —— v35：16sp
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    // bodyMedium → Callout —— v35：16sp
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 21.sp
    ),
    // bodySmall → Subheadline（列表项副文字）—— v35：14sp 副标题统一
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    // labelLarge → Button Text
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        letterSpacing = (-0.1).sp,
        lineHeight = 22.sp
    ),
    // labelMedium → Footnote
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    // labelSmall → Caption
    labelSmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
)
