package com.shangmentiyu.sportscoach.ui.growth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.shangmentiyu.sportscoach.data.model.BodyMetricHistory
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.Student
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * === v28 优化2：学员成长 PDF 报告生成器（数据层 + 处理器层） ===
 *
 * 使用 AndroidX [PdfDocument]（API 19+ 无依赖）在纯本地离线生成 A4 尺寸 PDF，
 * 不依赖 iText 等第三方库，安装包零增量。
 *
 * 报告内容（按页面顺序）：
 * 1. 报告标题 + 生成时间
 * 2. 学员基础信息（姓名/性别/年级/学校/年龄）
 * 3. 身体形态卡片（身高/体重/BMI）+ 历史趋势折线图（嵌入 Bitmap）
 * 4. 最近 5 次训练成绩列表
 * 5. 剩余课时与教练寄语
 *
 * 输出文件：cacheDir/GrowthReport_{studentName}_{timestamp}.pdf
 * 通过 [FileProvider] + [androidx.core.app.ShareCompat] 分享给微信/钉钉。
 *
 * 线程安全：所有方法均可在后台线程调用；不持有任何状态。
 */
object GrowthPdfGenerator {

    private const val TAG = "GrowthPdfGenerator"

    // A4 尺寸（PDF Point 单位，1pt = 1/72 inch）
    // A4 = 210mm × 297mm = 595pt × 842pt
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842

    // 边距
    private const val MARGIN_X = 40f
    private const val MARGIN_TOP = 50f
    private const val MARGIN_BOTTOM = 40f

    /** 珊瑚橙主题色（与 App 一致） */
    private const val COLOR_PRIMARY = 0xFFFF6B47.toInt()
    private const val COLOR_TEXT = 0xFF1F2937.toInt()
    private const val COLOR_TEXT_LIGHT = 0xFF6B7280.toInt()
    private const val COLOR_DIVIDER = 0xFFE5E7EB.toInt()
    private const val COLOR_CARD_BG = 0xFFF9FAFB.toInt()
    private const val COLOR_BLUE = 0xFF3B82F6.toInt()
    private const val COLOR_ORANGE = 0xFFFF6B47.toInt()
    private const val COLOR_PURPLE = 0xFF8B5CF6.toInt()
    private const val COLOR_GREEN = 0xFF10B981.toInt()

    /**
     * 生成学员成长 PDF 报告。
     *
     * @param context 应用上下文（用于 FileProvider 与缓存目录）
     * @param student 学员实体
     * @param bodyMetrics 身体形态历史（按日期升序）
     * @param recentLessons 最近训练记录（按日期降序，最多 5 条）
     * @param remainingLessons 剩余课时数（-1 表示无课时包）
     * @param coachComment 教练寄语（可空，PDF 中以灰色块呈现）
     * @return 生成的 PDF 文件 Uri；失败返回 null
     */
    fun generateReport(
        context: Context,
        student: Student,
        bodyMetrics: List<BodyMetricHistory>,
        recentLessons: List<Lesson>,
        remainingLessons: Int,
        coachComment: String
    ): Uri? {
        val document = PdfDocument()
        try {
            // 第一页：基础信息 + 身体形态 + 折线图
            val page1 = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
            )
            val canvas1 = page1.canvas
            drawHeader(canvas1, student)
            drawBasicInfo(canvas1, student, currentY = 130f)
            drawBodyMetrics(canvas1, student, currentY = 240f)

            // 折线图：取最近 6 次身体形态记录
            val chartBitmap = GrowthChartRenderer.renderBmiChart(bodyMetrics, maxPoints = 6)
            if (chartBitmap != null) {
                drawChart(canvas1, chartBitmap, currentY = 360f)
            } else {
                drawNoDataHint(canvas1, currentY = 380f, hint = "暂无身体形态测量记录")
            }
            document.finishPage(page1)

            // 第二页：最近训练成绩 + 剩余课时 + 教练寄语
            val page2 = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 2).create()
            )
            val canvas2 = page2.canvas
            drawPageTitle(canvas2, "训练记录与寄语")
            drawRecentLessons(canvas2, recentLessons, currentY = 130f)
            drawRemainingLessons(canvas2, remainingLessons, currentY = 560f)
            drawCoachComment(canvas2, coachComment, currentY = 640f)
            document.finishPage(page2)

            // 写入文件
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(Date())
            val safeName = student.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val file = File(
                context.cacheDir,
                "GrowthReport_${safeName}_$timeStamp.pdf"
            )
            FileOutputStream(file).use { fos ->
                document.writeTo(fos)
            }
            Log.i(TAG, "PDF 生成成功：${file.absolutePath}")

            // 通过 FileProvider 获取可分享的 Uri
            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
        } catch (e: Exception) {
            Log.e(TAG, "PDF 生成失败：${e.message}", e)
            return null
        } finally {
            document.close()
        }
    }

    // ==================== 绘制各区块 ====================

    /** 绘制报告标题与生成时间 */
    private fun drawHeader(canvas: Canvas, student: Student) {
        // 顶部珊瑚橙横条
        val barPaint = Paint().apply { color = COLOR_PRIMARY }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 8f, barPaint)

        // 主标题
        val titlePaint = Paint().apply {
            color = COLOR_TEXT
            textSize = 24f
            isAntiAlias = true
            isFakeBoldText = true
        }
        canvas.drawText(
            "${student.name} · 学员成长报告",
            MARGIN_X,
            40f,
            titlePaint
        )

        // 副标题：生成时间
        val subPaint = Paint().apply {
            color = COLOR_TEXT_LIGHT
            textSize = 11f
            isAntiAlias = true
        }
        val dateStr = SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.getDefault()).format(Date())
        canvas.drawText("生成时间：$dateStr", MARGIN_X, 60f, subPaint)

        // 分隔线
        val dividerPaint = Paint().apply { color = COLOR_DIVIDER }
        canvas.drawRect(MARGIN_X, 80f, PAGE_WIDTH - MARGIN_X, 81f, dividerPaint)
    }

    /** 绘制学员基础信息卡片 */
    private fun drawBasicInfo(canvas: Canvas, student: Student, currentY: Float) {
        val sectionTitlePaint = Paint().apply {
            color = COLOR_PRIMARY
            textSize = 15f
            isAntiAlias = true
            isFakeBoldText = true
        }
        canvas.drawText("一、学员基础信息", MARGIN_X, currentY, sectionTitlePaint)

        // 信息卡背景
        val cardPaint = Paint().apply { color = COLOR_CARD_BG }
        val cardRect = RectF(
            MARGIN_X, currentY + 10f,
            PAGE_WIDTH - MARGIN_X, currentY + 90f
        )
        canvas.drawRoundRect(cardRect, 8f, 8f, cardPaint)

        // 信息项
        val labelPaint = Paint().apply {
            color = COLOR_TEXT_LIGHT
            textSize = 11f
            isAntiAlias = true
        }
        val valuePaint = Paint().apply {
            color = COLOR_TEXT
            textSize = 13f
            isAntiAlias = true
        }

        val infoY = currentY + 35f
        val colWidth = (PAGE_WIDTH - 2 * MARGIN_X) / 2

        // 第一行
        canvas.drawText("姓名：", MARGIN_X + 16f, infoY, labelPaint)
        canvas.drawText(student.name, MARGIN_X + 56f, infoY, valuePaint)
        canvas.drawText("性别：", MARGIN_X + colWidth, infoY, labelPaint)
        canvas.drawText(student.gender.ifBlank { "未填写" }, MARGIN_X + colWidth + 40f, infoY, valuePaint)

        // 第二行
        val infoY2 = currentY + 60f
        canvas.drawText("年级：", MARGIN_X + 16f, infoY2, labelPaint)
        canvas.drawText(student.grade.ifBlank { "未填写" }, MARGIN_X + 56f, infoY2, valuePaint)
        canvas.drawText("年龄：", MARGIN_X + colWidth, infoY2, labelPaint)
        canvas.drawText(
            if (student.age > 0) "${student.age} 岁" else "未填写",
            MARGIN_X + colWidth + 40f,
            infoY2,
            valuePaint
        )
    }

    /** 绘制身体形态快照（当前值） */
    private fun drawBodyMetrics(canvas: Canvas, student: Student, currentY: Float) {
        val sectionTitlePaint = Paint().apply {
            color = COLOR_PRIMARY
            textSize = 15f
            isAntiAlias = true
            isFakeBoldText = true
        }
        canvas.drawText("二、身体形态", MARGIN_X, currentY, sectionTitlePaint)

        // 三个数据卡：身高 / 体重 / BMI
        val cardW = (PAGE_WIDTH - 2 * MARGIN_X - 16f) / 3
        val cardH = 70f
        val cards = listOf(
            Triple("身高", "${student.heightCm} cm", COLOR_BLUE),
            Triple("体重", "${student.weightKg} kg", COLOR_ORANGE),
            Triple("BMI", "%.1f".format(student.bmi), COLOR_PURPLE)
        )
        cards.forEachIndexed { index, (label, value, color) ->
            val x = MARGIN_X + index * (cardW + 8f)
            val rect = RectF(x, currentY + 10f, x + cardW, currentY + 10f + cardH)
            // 卡片背景
            val bgPaint = Paint().apply { this.color = Color.WHITE }
            canvas.drawRoundRect(rect, 8f, 8f, bgPaint)
            // 顶部色条
            val barPaint = Paint().apply { this.color = color }
            canvas.drawRect(x, currentY + 10f, x + cardW, currentY + 14f, barPaint)
            // 标签
            val labelP = Paint().apply {
                this.color = COLOR_TEXT_LIGHT
                textSize = 10f
                isAntiAlias = true
            }
            canvas.drawText(label, x + 12f, currentY + 32f, labelP)
            // 数值
            val valueP = Paint().apply {
                this.color = color
                textSize = 20f
                isAntiAlias = true
                isFakeBoldText = true
            }
            canvas.drawText(value, x + 12f, currentY + 62f, valueP)
        }
    }

    /** 绘制折线图 Bitmap */
    private fun drawChart(canvas: Canvas, bitmap: Bitmap, currentY: Float) {
        val sectionTitlePaint = Paint().apply {
            color = COLOR_PRIMARY
            textSize = 15f
            isAntiAlias = true
            isFakeBoldText = true
        }
        canvas.drawText("三、近 6 次身体形态趋势", MARGIN_X, currentY, sectionTitlePaint)

        // 等比缩放 Bitmap 到 PDF 区域
        val availableW = PAGE_WIDTH - 2 * MARGIN_X
        val availableH = 200f
        val scale = minOf(
            availableW / bitmap.width,
            availableH / bitmap.height
        )
        val targetW = bitmap.width * scale
        val targetH = bitmap.height * scale
        val left = MARGIN_X + (availableW - targetW) / 2
        val top = currentY + 10f

        // 卡片背景（白底 + 圆角）
        val bgPaint = Paint().apply { color = Color.WHITE }
        val bgRect = RectF(left - 4f, top - 4f, left + targetW + 4f, top + targetH + 4f)
        canvas.drawRoundRect(bgRect, 8f, 8f, bgPaint)

        val srcRect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        val dstRect = RectF(left, top, left + targetW, top + targetH)
        canvas.drawBitmap(bitmap, null, dstRect, null)
    }

    /** 绘制无数据提示（数据稀疏时占位） */
    private fun drawNoDataHint(canvas: Canvas, currentY: Float, hint: String) {
        val paint = Paint().apply {
            color = COLOR_TEXT_LIGHT
            textSize = 12f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            hint,
            PAGE_WIDTH / 2f,
            currentY,
            paint
        )
    }

    /** 绘制第二页标题 */
    private fun drawPageTitle(canvas: Canvas, title: String) {
        val barPaint = Paint().apply { color = COLOR_PRIMARY }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 8f, barPaint)

        val titlePaint = Paint().apply {
            color = COLOR_TEXT
            textSize = 22f
            isAntiAlias = true
            isFakeBoldText = true
        }
        canvas.drawText(title, MARGIN_X, 40f, titlePaint)

        val dividerPaint = Paint().apply { color = COLOR_DIVIDER }
        canvas.drawRect(MARGIN_X, 80f, PAGE_WIDTH - MARGIN_X, 81f, dividerPaint)
    }

    /** 绘制最近 5 次训练记录 */
    private fun drawRecentLessons(canvas: Canvas, lessons: List<Lesson>, currentY: Float) {
        val sectionTitlePaint = Paint().apply {
            color = COLOR_PRIMARY
            textSize = 15f
            isAntiAlias = true
            isFakeBoldText = true
        }
        canvas.drawText("四、最近 5 次训练记录", MARGIN_X, currentY, sectionTitlePaint)

        if (lessons.isEmpty()) {
            val emptyPaint = Paint().apply {
                color = COLOR_TEXT_LIGHT
                textSize = 12f
                isAntiAlias = true
            }
            canvas.drawText("暂无训练记录", MARGIN_X + 16f, currentY + 40f, emptyPaint)
            return
        }

        // 表头
        val headerY = currentY + 30f
        val headerPaint = Paint().apply {
            color = COLOR_TEXT_LIGHT
            textSize = 10f
            isAntiAlias = true
            isFakeBoldText = true
        }
        // 列：日期 | 类型 | 时长 | 表现 | 寄语
        canvas.drawText("日期", MARGIN_X + 12f, headerY, headerPaint)
        canvas.drawText("类型", MARGIN_X + 110f, headerY, headerPaint)
        canvas.drawText("时长", MARGIN_X + 200f, headerY, headerPaint)
        canvas.drawText("表现", MARGIN_X + 280f, headerY, headerPaint)
        canvas.drawText("寄语", MARGIN_X + 360f, headerY, headerPaint)

        // 分隔线
        val dividerPaint = Paint().apply { color = COLOR_DIVIDER }
        canvas.drawRect(MARGIN_X, headerY + 6f, PAGE_WIDTH - MARGIN_X, headerY + 7f, dividerPaint)

        // 数据行
        val rowPaint = Paint().apply {
            color = COLOR_TEXT
            textSize = 11f
            isAntiAlias = true
        }
        val recent5 = lessons.take(5)
        recent5.forEachIndexed { index, lesson ->
            val rowY = headerY + 30f + index * 60f

            // 交替背景（提升可读性）
            if (index % 2 == 0) {
                val bgPaint = Paint().apply { color = COLOR_CARD_BG }
                val bgRect = RectF(
                    MARGIN_X, rowY - 18f,
                    PAGE_WIDTH - MARGIN_X, rowY + 36f
                )
                canvas.drawRect(bgRect, bgPaint)
            }

            canvas.drawText(
                "${lesson.date.takeLast(5)} ${lesson.time}",
                MARGIN_X + 12f, rowY, rowPaint
            )
            canvas.drawText(
                lesson.lessonType.ifBlank { "训练课" }.take(6),
                MARGIN_X + 110f, rowY, rowPaint
            )
            canvas.drawText(
                if (lesson.duration > 0) "${lesson.duration}分钟" else "-",
                MARGIN_X + 200f, rowY, rowPaint
            )
            canvas.drawText(
                if (lesson.performance > 0) "${lesson.performance}/10" else "-",
                MARGIN_X + 280f, rowY, rowPaint
            )
            // 寄语（最多 30 字符）
            val comment = lesson.coachComment.ifBlank { "-" }.take(30)
            canvas.drawText(comment, MARGIN_X + 360f, rowY, rowPaint)
        }
    }

    /** 绘制剩余课时卡片 */
    private fun drawRemainingLessons(canvas: Canvas, remaining: Int, currentY: Float) {
        val sectionTitlePaint = Paint().apply {
            color = COLOR_PRIMARY
            textSize = 15f
            isAntiAlias = true
            isFakeBoldText = true
        }
        canvas.drawText("五、剩余课时", MARGIN_X, currentY, sectionTitlePaint)

        // 卡片背景
        val cardPaint = Paint().apply { color = COLOR_CARD_BG }
        val cardRect = RectF(
            MARGIN_X, currentY + 10f,
            PAGE_WIDTH - MARGIN_X, currentY + 60f
        )
        canvas.drawRoundRect(cardRect, 8f, 8f, cardPaint)

        val labelPaint = Paint().apply {
            color = COLOR_TEXT_LIGHT
            textSize = 12f
            isAntiAlias = true
        }
        canvas.drawText("当前剩余课时：", MARGIN_X + 16f, currentY + 40f, labelPaint)

        val valuePaint = Paint().apply {
            color = if (remaining > 0) COLOR_GREEN else COLOR_TEXT_LIGHT
            textSize = 22f
            isAntiAlias = true
            isFakeBoldText = true
        }
        val displayValue = if (remaining >= 0) "$remaining 节" else "未购课"
        canvas.drawText(displayValue, MARGIN_X + 130f, currentY + 42f, valuePaint)
    }

    /** 绘制教练寄语 */
    private fun drawCoachComment(canvas: Canvas, comment: String, currentY: Float) {
        val sectionTitlePaint = Paint().apply {
            color = COLOR_PRIMARY
            textSize = 15f
            isAntiAlias = true
            isFakeBoldText = true
        }
        canvas.drawText("六、教练寄语", MARGIN_X, currentY, sectionTitlePaint)

        // 卡片背景（白底圆角）
        val cardPaint = Paint().apply { color = Color.WHITE }
        val cardRect = RectF(
            MARGIN_X, currentY + 10f,
            PAGE_WIDTH - MARGIN_X, currentY + 140f
        )
        canvas.drawRoundRect(cardRect, 8f, 8f, cardPaint)

        // 边框（细灰线，与 UI 风格保持一致）
        val borderPaint = Paint().apply {
            color = COLOR_DIVIDER
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRoundRect(cardRect, 8f, 8f, borderPaint)

        // 寄语文本（自动换行）
        val textPaint = Paint().apply {
            color = COLOR_TEXT
            textSize = 12f
            isAntiAlias = true
        }
        val displayComment = comment.ifBlank { "暂无寄语，期待下次训练后由教练填写。" }
        val maxWidth = PAGE_WIDTH - 2 * MARGIN_X - 32f
        val lines = breakTextIntoLines(displayComment, textPaint, maxWidth)
        var lineY = currentY + 35f
        val lineHeight = 18f
        // 最多显示 6 行
        lines.take(6).forEach { line ->
            canvas.drawText(line, MARGIN_X + 16f, lineY, textPaint)
            lineY += lineHeight
        }
    }

    /**
     * 文本自动换行：按字符宽度切分多行。
     *
     * 使用 [Paint.breakText] 逐字符测量，兼容中英文混排。
     */
    private fun breakTextIntoLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (char in text) {
            current.append(char)
            if (paint.measureText(current.toString()) > maxWidth) {
                // 当前字符已超宽，回退一个字符并换行
                current.deleteCharAt(current.length - 1)
                result.add(current.toString())
                current.clear()
                current.append(char)
            }
            if (char == '\n') {
                // 显式换行符
                current.deleteCharAt(current.length - 1)
                result.add(current.toString())
                current.clear()
            }
        }
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        return result
    }
}
