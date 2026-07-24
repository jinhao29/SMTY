package com.shangmentiyu.sportscoach.ui.theme

import androidx.compose.ui.unit.dp

/**
 * iOS 8pt 网格间距令牌（对标 Apple Human Interface Guidelines）。
 *
 * 层级（iOS 标准）：
 * - xs   4pt   图标与文字间距、行内紧凑
 * - sm   8pt   同组元素
 * - md   12pt  小段落内
 * - lg   16pt  屏幕水平外边距 / iOS Standard Margin
 * - xl   24pt  区块间
 * - xxl  32pt  大段落分隔
 *
 * iOS HIG 关键规格：
 * - 屏幕水平外边距 16pt（Standard Margin）
 * - 触碰目标 ≥44pt
 * - 卡片内边距 16pt
 * - 列表项高度 ≥44pt（紧凑 36pt / 标准 44pt / 大 56pt）
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** 屏幕水平外边距（iOS Standard Margin = 16pt） */
    val screenH = 16.dp
    /** 屏幕垂直外边距 */
    val screenV = 16.dp
    /** 卡片内边距（iOS 标准 16pt） */
    val cardPadding = 16.dp
    /** 触碰目标最小高度（iOS HIG = 44pt） */
    val touchTarget = 44.dp
}
