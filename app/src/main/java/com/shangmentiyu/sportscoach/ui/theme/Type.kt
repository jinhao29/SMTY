package com.shangmentiyu.sportscoach.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp

/**
 * Inter（现代几何无衬线）经 Google Fonts API 按需下载；
 * 无 GMS/断网时静默回退系统默认字体，不影响功能。
 */
private val GoogleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = com.shangmentiyu.sportscoach.R.array.com_google_android_gms_fonts_certs
)
private val InterFontFamily = FontFamily(
    Font(GoogleFont("Inter"), GoogleFontProvider, FontWeight.Normal),
    Font(GoogleFont("Inter"), GoogleFontProvider, FontWeight.Medium),
    Font(GoogleFont("Inter"), GoogleFontProvider, FontWeight.SemiBold),
    Font(GoogleFont("Inter"), GoogleFontProvider, FontWeight.Bold),
)

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
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        letterSpacing = (-0.5).sp,
        lineHeight = 41.sp
    ),
    // displayMedium → Title 1
    displayMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = (-0.3).sp,
        lineHeight = 34.sp
    ),
    // displaySmall → Title 2
    displaySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    // headlineLarge → Title 3
    headlineLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp
    ),
    // headlineMedium → Title 2 备用
    headlineMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    // headlineSmall → Headline（列表项主标题）
    headlineSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp
    ),
    // titleLarge → Navigation Bar Title
    titleLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp
    ),
    // titleMedium → Headline 备用
    titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp
    ),
    // titleSmall → Subheadline Bold
    titleSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    // bodyLarge → Body
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 24.sp
    ),
    // bodyMedium → Callout
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    // bodySmall → Subheadline（列表项副文字 / 辅助说明，统一 14sp）
    bodySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    // labelLarge → Button Text
    labelLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        letterSpacing = (-0.1).sp,
        lineHeight = 24.sp
    ),
    // labelMedium → Footnote
    labelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    // labelSmall → Caption
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
)
