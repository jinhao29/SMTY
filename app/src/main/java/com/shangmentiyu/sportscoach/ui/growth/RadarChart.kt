package com.shangmentiyu.sportscoach.ui.growth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appSecondary

/**
 * 银河星空风格五维雷达图。
 *
 * @param values 五维数值（0-100），顺序与 labels 对应
 * @param labels 五维标签
 * @param modifier 尺寸修饰（建议正方形）
 */
@Composable
fun RadarChart(
    values: List<Float>,
    labels: List<String> = listOf("速度", "力量", "耐力", "柔韧", "灵敏"),
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primaryColor = appPrimary()
    val secondaryColor = appSecondary()
    val labelStyle = TextStyle(color = onSurface.copy(alpha = 0.85f), fontSize = 11.sp)

    Box(modifier = modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(240.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f * 0.72f
            val sides = labels.size
            val angleStep = (2 * Math.PI / sides).toFloat()

            // === 1. 绘制同心网格（5层） ===
            val gridColor = onSurface.copy(alpha = 0.12f)
            for (layer in 1..5) {
                val r = radius * layer / 5f
                val path = Path()
                for (i in 0 until sides) {
                    val angle = -Math.PI / 2 + i * angleStep
                    val x = center.x + r * Math.cos(angle).toFloat()
                    val y = center.y + r * Math.sin(angle).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path = path, color = gridColor, style = Stroke(width = 1f))
            }

            // === 2. 绘制放射轴线 ===
            for (i in 0 until sides) {
                val angle = -Math.PI / 2 + i * angleStep
                val x = center.x + radius * Math.cos(angle).toFloat()
                val y = center.y + radius * Math.sin(angle).toFloat()
                drawLine(
                    color = onSurface.copy(alpha = 0.15f),
                    start = center,
                    end = Offset(x, y),
                    strokeWidth = 1f
                )
            }

            // === 3. 绘刻度数字（20/40/60/80/100） ===
            val scaleStyle = TextStyle(color = onSurface.copy(alpha = 0.35f), fontSize = 9.sp)
            for (layer in 1..5) {
                val value = layer * 20
                val r = radius * layer / 5f
                val text = value.toString()
                val measured = textMeasurer.measure(AnnotatedString(text), scaleStyle)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(center.x + 2f, center.y - r - measured.size.height.toFloat())
                )
            }

            // === 4. 绘制数据多边形 ===
            val dataPath = Path()
            val vertexPoints = mutableListOf<Offset>()
            for (i in values.indices) {
                val v = values[i].coerceIn(0f, 100f)
                val r = radius * v / 100f
                val angle = -Math.PI / 2 + i * angleStep
                val x = center.x + r * Math.cos(angle).toFloat()
                val y = center.y + r * Math.sin(angle).toFloat()
                vertexPoints.add(Offset(x, y))
                if (i == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
            }
            dataPath.close()

            // 渐变填充（珊瑚橙）
            drawPath(
                path = dataPath,
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.45f), secondaryColor.copy(alpha = 0.25f)),
                    center = center,
                    radius = radius
                )
            )
            // 边线
            drawPath(
                path = dataPath,
                color = primaryColor,
                style = Stroke(width = 2f)
            )

            // === 5. 顶点圆点 ===
            for (point in vertexPoints) {
                drawCircle(
                    color = primaryColor,
                    radius = 4f,
                    center = point
                )
                drawCircle(
                    color = Color.White,
                    radius = 1.5f,
                    center = point
                )
            }

            // === 6. 绘制标签（外圈） ===
            for (i in labels.indices) {
                val angle = -Math.PI / 2 + i * angleStep
                val labelR = radius + 26f
                val x = center.x + labelR * Math.cos(angle).toFloat()
                val y = center.y + labelR * Math.sin(angle).toFloat()
                val measured = textMeasurer.measure(AnnotatedString(labels[i]), labelStyle)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        x - measured.size.width / 2f,
                        y - measured.size.height / 2f
                    )
                )
                // 标签下方显示数值
                val valueText = "${values[i].toInt()}"
                val valueStyle = TextStyle(color = primaryColor, fontSize = 10.sp)
                val valueMeasured = textMeasurer.measure(AnnotatedString(valueText), valueStyle)
                drawText(
                    textLayoutResult = valueMeasured,
                    topLeft = Offset(
                        x - valueMeasured.size.width / 2f,
                        y + measured.size.height / 2f + 2f
                    )
                )
            }
        }
    }
}
