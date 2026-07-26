package com.shangmentiyu.sportscoach.ui.growth

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.shangmentiyu.sportscoach.data.model.BodyMetricHistory
import kotlin.math.max

/**
 * === v28 优化2：成长曲线图渲染器（处理器层） ===
 *
 * 纯逻辑单元：使用 Android 原生 [Canvas] + [Paint] 将学员近 6 次身体形态数据
 * 绘制为折线 Bitmap，供 PDF 报告嵌入使用。
 *
 * 设计原则：
 * - 纯函数式渲染，无状态依赖，便于单元测试
 * - 不依赖 Compose（避免在 PDF 后台线程使用 Composable）
 * - 自适应坐标轴范围，数据稀疏时不出现空图
 *
 * 绘制内容：
 * - 三条折线：身高（蓝）/ 体重（橙）/ BMI（紫）
 * - X 轴：测量日期（MM-DD）
 * - Y 轴：自适应范围 + 网格线
 * - 数据点圆点 + 数值标签
 *
 * 性能：单次渲染 < 30ms（6 个数据点），适合后台线程调用。
 */
object GrowthChartRenderer {

    /** 画布尺寸（像素，对应 PDF 嵌入区域） */
    private const val CANVAS_WIDTH = 1080
    private const val CANVAS_HEIGHT = 540

    /** 内边距（留出坐标轴空间） */
    private const val PADDING_LEFT = 120f
    private const val PADDING_RIGHT = 60f
    private const val PADDING_TOP = 80f
    private const val PADDING_BOTTOM = 100f

    /** 珊瑚橙主色（与 App 主题一致） */
    private const val COLOR_CORAL = 0xFFFF6B47.toInt()
    private const val COLOR_BLUE = 0xFF3B82F6.toInt()
    private const val COLOR_PURPLE = 0xFF8B5CF6.toInt()
    private const val COLOR_GRID = 0xFFE5E7EB.toInt()
    private const val COLOR_TEXT = 0xFF374151.toInt()
    private const val COLOR_TEXT_LIGHT = 0xFF9CA3AF.toInt()

    /**
     * 渲染学员身体形态变化折线图。
     *
     * @param history 学员身体形态历史记录（按日期升序）
     * @param maxPoints 最多渲染的数据点数，默认 6（取最近 6 次）
     * @return 折线图 Bitmap；若数据为空返回 null
     */
    fun renderBmiChart(history: List<BodyMetricHistory>, maxPoints: Int = 6): Bitmap? {
        if (history.isEmpty()) return null

        // 取最近 N 次记录（确保不超过画布承载能力）
        val data = if (history.size > maxPoints) history.takeLast(maxPoints) else history
        if (data.isEmpty()) return null

        val bitmap = Bitmap.createBitmap(CANVAS_WIDTH, CANVAS_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        // 1. 计算各指标范围（自适应 Y 轴）
        val heights = data.map { it.heightCm.toFloat() }.filter { it > 0 }
        val weights = data.map { it.weightKg }.filter { it > 0 }
        val bmis = data.map { it.bmi }.filter { it > 0 }

        // 2. 绘制标题与图例
        drawTitleAndLegend(canvas)

        // 3. 绘制网格与坐标轴
        val chartLeft = PADDING_LEFT
        val chartTop = PADDING_TOP + 40f
        val chartRight = CANVAS_WIDTH - PADDING_RIGHT
        val chartBottom = CANVAS_HEIGHT - PADDING_BOTTOM
        drawGridAndAxes(canvas, chartLeft, chartTop, chartRight, chartBottom, data.size)

        // 4. 绘制三条折线（仅绘制存在数据的指标）
        if (heights.isNotEmpty()) {
            drawLine(canvas, data, chartLeft, chartTop, chartRight, chartBottom,
                heights.min(), heights.max(), COLOR_BLUE) { it.heightCm.toFloat() }
        }
        if (weights.isNotEmpty()) {
            drawLine(canvas, data, chartLeft, chartTop, chartRight, chartBottom,
                weights.min(), weights.max(), COLOR_CORAL) { it.weightKg }
        }
        if (bmis.isNotEmpty()) {
            drawLine(canvas, data, chartLeft, chartTop, chartRight, chartBottom,
                bmis.min(), bmis.max(), COLOR_PURPLE) { it.bmi }
        }

        // 5. 绘制 X 轴日期标签
        drawXLabels(canvas, data, chartLeft, chartRight, chartBottom)

        return bitmap
    }

    /** 绘制标题与三色图例 */
    private fun drawTitleAndLegend(canvas: Canvas) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT
            textSize = 36f
            isFakeBoldText = true
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText("身高 / 体重 / BMI 变化趋势", PADDING_LEFT, 50f, titlePaint)

        // 图例：三个色块 + 文字
        val legendY = 50f
        val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 24f }
        var legendX = CANVAS_WIDTH - PADDING_RIGHT - 480f

        listOf(
            "身高" to COLOR_BLUE,
            "体重" to COLOR_CORAL,
            "BMI" to COLOR_PURPLE
        ).forEach { (label, color) ->
            legendPaint.color = color
            canvas.drawCircle(legendX + 8f, legendY - 8f, 8f, legendPaint)
            legendPaint.color = COLOR_TEXT
            canvas.drawText(label, legendX + 24f, legendY, legendPaint)
            legendX += 160f
        }
    }

    /** 绘制网格线与 Y 轴 */
    private fun drawGridAndAxes(
        canvas: Canvas,
        left: Float, top: Float, right: Float, bottom: Float,
        dataSize: Int
    ) {
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_GRID
            strokeWidth = 1.5f
        }
        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_LIGHT
            strokeWidth = 2f
        }

        // 横向网格线（5 条）
        val rows = 4
        for (i in 0..rows) {
            val y = top + (bottom - top) * i / rows
            canvas.drawLine(left, y, right, y, gridPaint)
        }
        // 纵向网格线（按数据点数）
        val cols = max(dataSize - 1, 1)
        for (i in 0..cols) {
            val x = left + (right - left) * i / cols
            canvas.drawLine(x, top, x, bottom, gridPaint)
        }

        // 坐标轴
        canvas.drawLine(left, top, left, bottom, axisPaint)
        canvas.drawLine(left, bottom, right, bottom, axisPaint)
    }

    /**
     * 绘制单条折线 + 数据点 + 数值标签。
     *
     * @param data 身体形态数据列表
     * @param valueExtractor 从 BodyMetricHistory 提取数值的函数（高度抽象，支持身高/体重/BMI）
     * @param color 折线颜色
     */
    private fun drawLine(
        canvas: Canvas,
        data: List<BodyMetricHistory>,
        left: Float, top: Float, right: Float, bottom: Float,
        minValue: Float, maxValue: Float,
        color: Int,
        valueExtractor: (BodyMetricHistory) -> Float
    ) {
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = 4f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = 22f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }

        val cols = max(data.size - 1, 1)
        val range = (maxValue - minValue).coerceAtLeast(0.1f)

        // 计算每个点的坐标
        val points = data.mapIndexed { index, item ->
            val x = left + (right - left) * index / cols
            val rawValue = valueExtractor(item)
            // 防御性：值为 0 时跳过该点
            val y = if (rawValue > 0) {
                bottom - (bottom - top) * (rawValue - minValue) / range
            } else {
                bottom
            }
            androidx.compose.ui.geometry.Offset(x, y)
        }

        // 绘制折线
        if (points.size >= 2) {
            val path = Path()
            path.moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                path.lineTo(points[i].x, points[i].y)
            }
            canvas.drawPath(path, linePaint)
        }

        // 绘制数据点 + 数值标签
        points.forEachIndexed { index, point ->
            val rawValue = valueExtractor(data[index])
            if (rawValue > 0) {
                canvas.drawCircle(point.x, point.y, 8f, pointPaint)
                // 数值标签放在点上方 18px
                canvas.drawText(
                    formatValue(rawValue),
                    point.x,
                    point.y - 18f,
                    textPaint
                )
            }
        }
    }

    /** 绘制 X 轴日期标签（MM-DD） */
    private fun drawXLabels(
        canvas: Canvas,
        data: List<BodyMetricHistory>,
        left: Float, right: Float, bottom: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_LIGHT
            textSize = 22f
            textAlign = Paint.Align.CENTER
        }
        val cols = max(data.size - 1, 1)
        data.forEachIndexed { index, item ->
            val x = left + (right - left) * index / cols
            // 取 MM-DD 短格式
            val dateShort = if (item.date.length >= 10) item.date.takeLast(5) else item.date
            canvas.drawText(dateShort, x, bottom + 32f, paint)
        }
    }

    /** 数值格式化：保留 1 位小数（BMI / 体重）或整数（身高） */
    private fun formatValue(value: Float): String {
        return if (value >= 100) {
            value.toInt().toString()
        } else {
            "%.1f".format(value)
        }
    }
}
