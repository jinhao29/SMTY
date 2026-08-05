package com.shangmentiyu.sportscoach.ui.growth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shangmentiyu.sportscoach.core.AbilityAnalyzer
import com.shangmentiyu.sportscoach.ui.theme.LightPrimary
import com.shangmentiyu.sportscoach.ui.theme.BrandGradientEnd
import com.shangmentiyu.sportscoach.ui.theme.BrandGradientStart
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import androidx.compose.foundation.Canvas
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 能力画像卡片：5 维能力雷达图（Canvas 自绘）。
 *
 * 设计原则：
 * - 纯白卡片 + 10dp 圆角 + 柔和阴影（与 IosGroupedListCard 风格一致）
 * - 5 维雷达：速度 / 力量 / 耐力 / 柔韧 / 灵敏
 * - 珊瑚橙渐变填充（BrandGradientStart → BrandGradientEnd），与头部卡片视觉呼应
 * - 5 个同心五边形作为标尺（20/40/60/80/100）
 * - 数据空状态：显示"暂无足够成绩数据"，避免 0 分雷达误导
 *
 * 数据来源：[AbilityAnalyzer.computeRadar] 基于学员最近一次各维度相关项目得分。
 */
@Composable
fun AbilityRadarCard(radar: AbilityAnalyzer.AbilityRadar) {
    val values = remember(radar) { radar.toList() }
    val hasData = remember(values) { values.any { it > 0f } }

    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(10.dp),
                ambientColor = Color(0x1A000000),
                spotColor = Color(0x1A000000)
            )
            .background(Color.White, RoundedCornerShape(10.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        // 标题行：能力画像 + 数据来源说明 + 综合等级徽章
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "能力画像",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "基于近期体测成绩的五维能力分布",
                    style = MaterialTheme.typography.labelSmall,
                    color = appOnSurfaceVariant()
                )
            }
            val overall = if (hasData) values.average().toFloat() else 0f
            OverallBadge(overall)
        }

        Spacer(Modifier.height(Spacing.md))

        if (hasData) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                RadarCanvasWithLabels(values = values)
            }
            Spacer(Modifier.height(Spacing.md))
            DimensionChipsRow(values = values)
        } else {
            // 空状态：暂无成绩数据
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xxl),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "暂无足够成绩数据",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF8A8A8A)
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "签到并录入体测成绩后，将自动生成能力画像",
                        style = MaterialTheme.typography.labelSmall,
                        color = appOnSurfaceVariant()
                    )
                }
            }
        }
    }
}

/**
 * 雷达图 + 标签一体化绘制：
 *
 * 使用 [BoxWithConstraints] 获取像素尺寸，在同一 Box 内同时绘制 Canvas 与
 * 5 个维度文字标签。标签位置按角度精确计算（顶/左下/右下等）。
 */
@Composable
private fun RadarCanvasWithLabels(values: List<Float>) {
    val dimensions = AbilityAnalyzer.DIMENSIONS
    val gridColor = Color(0xFFE8E8EA)
    val axisColor = Color(0xFFD8D8DC)
    val polygonStroke = LightPrimary
    val labelColor = Color(0xFF4A4A4A)
    val scoreColor = LightPrimary

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxW = maxWidth
        val sizePx = with(LocalDensity.current) { maxW.toPx() }
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val radius = sizePx / 2f * 0.72f
        val n = dimensions.size
        val labelOffset = sizePx / 2f * 0.92f  // 标签到中心的距离

        Canvas(modifier = Modifier.fillMaxWidth()) {
            // 1. 5 个同心五边形标尺（从外向内 100/80/60/40/20）
            for (level in 5 downTo 1) {
                val r = radius * level / 5f
                val path = Path()
                for (i in 0 until n) {
                    val angle = (-90.0 + i * 360.0 / n) * PI / 180.0
                    val x = cx + (r * cos(angle)).toFloat()
                    val y = cy + (r * sin(angle)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, color = gridColor, style = Stroke(width = 1f))
            }

            // 2. 5 条放射轴线
            for (i in 0 until n) {
                val angle = (-90.0 + i * 360.0 / n) * PI / 180.0
                val x = cx + (radius * cos(angle)).toFloat()
                val y = cy + (radius * sin(angle)).toFloat()
                drawLine(
                    color = axisColor,
                    start = Offset(cx, cy),
                    end = Offset(x, y),
                    strokeWidth = 1f
                )
            }

            // 3. 数据多边形（珊瑚橙渐变填充 + 描边）
            val dataPath = Path()
            for (i in 0 until n) {
                val v = (values[i].coerceIn(0f, 100f) / 100f) * radius
                val angle = (-90.0 + i * 360.0 / n) * PI / 180.0
                val x = cx + (v * cos(angle)).toFloat()
                val y = cy + (v * sin(angle)).toFloat()
                if (i == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
            }
            dataPath.close()
            drawPath(
                path = dataPath,
                brush = Brush.radialGradient(
                    colors = listOf(BrandGradientStart, BrandGradientEnd),
                    center = Offset(cx, cy),
                    radius = radius
                ),
                alpha = 0.35f
            )
            drawPath(path = dataPath, color = polygonStroke, style = Stroke(width = 2.5f))

            // 4. 数据顶点圆点（白底 + 主色圆心）
            for (i in 0 until n) {
                val v = (values[i].coerceIn(0f, 100f) / 100f) * radius
                val angle = (-90.0 + i * 360.0 / n) * PI / 180.0
                val x = cx + (v * cos(angle)).toFloat()
                val y = cy + (v * sin(angle)).toFloat()
                drawCircle(color = Color.White, radius = 7f, center = Offset(x, y))
                drawCircle(color = polygonStroke, radius = 4f, center = Offset(x, y))
            }
        }

        // 5. 5 个维度标签（使用绝对偏移定位）
        for (i in 0 until n) {
            val angle = (-90.0 + i * 360.0 / n) * PI / 180.0
            val lx = cx + (labelOffset * cos(angle)).toFloat()
            val ly = cy + (labelOffset * sin(angle)).toFloat()
            val xSign = when {
                cos(angle) > 0.3 -> 1
                cos(angle) < -0.3 -> -1
                else -> 0
            }
            val labelPx = with(LocalDensity.current) { lx.toDp() }
            val labelPy = with(LocalDensity.current) { ly.toDp() }
            // 围绕中心点偏移；标签宽度 60dp，按 xSign 对齐
            val chipWidthDp = 60.dp
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopStart
            ) {
                Column(
                    modifier = Modifier
                        .padding(
                            start = when (xSign) {
                                -1 -> labelPx - chipWidthDp
                                0 -> labelPx - chipWidthDp / 2
                                else -> labelPx
                            },
                            top = labelPy - 12.dp
                        )
                        .width(chipWidthDp),
                    horizontalAlignment = when (xSign) {
                        1 -> Alignment.End
                        -1 -> Alignment.Start
                        else -> Alignment.CenterHorizontally
                    }
                ) {
                    Text(
                        dimensions[i],
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = labelColor,
                        textAlign = TextAlign.Center
                    )
                    if (values[i] > 0f) {
                        Text(
                            "${values[i].toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = scoreColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * 综合能力等级徽章：根据平均分显示等级标签 + 配色。
 */
@Composable
private fun OverallBadge(overall: Float) {
    val (label, bg, fg) = when {
        overall >= 85 -> Triple("优秀", Color(0xFFFF6B47), Color.White)
        overall >= 70 -> Triple("良好", Color(0xFFFF9E7A), Color.White)
        overall >= 60 -> Triple("及格", Color(0xFFFFD4C2), Color(0xFF7A3A1F))
        overall > 0 -> Triple("待提升", Color(0xFFF2F2F5), Color(0xFF8A8A8A))
        else -> Triple("无数据", Color(0xFFF2F2F5), Color(0xFFB0B0B0))
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(13.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = fg
        )
    }
}

/**
 * 5 个维度芯片：每项分数 + 按比例进度条。
 */
@Composable
private fun DimensionChipsRow(values: List<Float>) {
    val dimensions = AbilityAnalyzer.DIMENSIONS
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        dimensions.forEachIndexed { i, name ->
            DimChip(
                name = name,
                score = values[i],
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 单个维度芯片：纵向排列（名称 + 分数 + 按分数比例的进度条）。
 */
@Composable
private fun DimChip(name: String, score: Float, modifier: Modifier = Modifier) {
    val scoreInt = score.toInt()
    val progressColor = when {
        score >= 85 -> BrandGradientStart
        score >= 70 -> LightPrimary
        score >= 60 -> BrandGradientEnd
        score > 0 -> Color(0xFFFFD4C2)
        else -> Color(0xFFE8E8EA)
    }
    Column(
        modifier = modifier
            .background(Color(0xFFFAFAFA), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = appOnSurfaceVariant(),
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(2.dp))
        Text(
            if (score > 0f) "$scoreInt" else "—",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (score > 0f) progressColor else Color(0xFFB0B0B0)
        )
        Spacer(Modifier.height(6.dp))
        // 进度条底色
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(Color(0xFFEDEDED), RoundedCornerShape(1.5.dp))
        ) {
            // 按分数比例填充（0-100 映射到 0-100%）
            val ratio = (score.coerceIn(0f, 100f) / 100f)
            if (ratio > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ratio)
                        .height(3.dp)
                        .background(progressColor, RoundedCornerShape(1.5.dp))
                )
            }
        }
    }
}
