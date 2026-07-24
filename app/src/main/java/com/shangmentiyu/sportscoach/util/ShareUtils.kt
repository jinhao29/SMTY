package com.shangmentiyu.sportscoach.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.shangmentiyu.sportscoach.core.JsonSafe
import com.shangmentiyu.sportscoach.core.Standards
import com.shangmentiyu.sportscoach.data.model.ExerciseItem
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.excel.ExcelSync
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * 分享工具：支持文本、图片、Excel 文件三种方式分享到微信等社交应用。
 */
object ShareUtils {

    /**
     * 分享纯文本到其他应用（微信、QQ、短信等）。
     * 微信支持纯文本分享，会以卡片形式发送给好友。
     */
    fun shareText(context: Context, text: String, title: String = "分享课堂小结") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_TITLE, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, title)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /**
     * 分享图片到其他应用。
     * 微信支持图片分享，家长可直观看到格式化报告。
     */
    fun shareImage(context: Context, bitmap: Bitmap, title: String = "分享课堂报告") {
        val cacheDir = File(context.cacheDir, "shared_images")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val imageFile = File(cacheDir, "lesson_report_${System.currentTimeMillis()}.png")

        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            imageFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, title)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /**
     * 分享 Excel 文件到其他应用。
     */
    fun shareExcelFile(context: Context, file: File, title: String = "分享课堂记录") {
        if (!file.exists()) return

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, title)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /**
     * 生成并分享 Excel 课堂报告。
     * 返回 true 表示分享成功启动。
     */
    fun shareLessonReportExcel(
        context: Context,
        lesson: Lesson,
        student: Student?
    ): Boolean {
        val cacheDir = File(context.cacheDir, "shared_reports")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val name = (student?.name ?: lesson.studentName)
            .replace("/", "_").replace("\\", "_")
        val fileName = "${name}_${lesson.date}_课堂记录.xlsx"
        val file = File(cacheDir, fileName)

        val ok = java.io.FileOutputStream(file).use { fos ->
            ExcelSync.exportLessonReport(lesson, student, fos)
        }
        if (!ok) return false

        shareExcelFile(context, file, "分享课堂记录 - $name")
        return true
    }

    /**
     * 将课堂小结渲染为图片（家长友好格式）。
     * 深色科技风配色，与 App 主题一致。
     */
    fun renderLessonReportImage(
        lesson: Lesson,
        student: Student?
    ): Bitmap {
        val width = 1080
        // 根据内容动态计算高度
        val exercises = parseExercises(lesson.content)
        val scores = parseScores(lesson.scores)
        val lineHeight = 56
        val padding = 60

        // 计算各部分高度
        var height = padding * 2 + 180 // 标题区
        height += 60 // 学员信息行
        height += 60 // 课时信息行
        if (exercises.isNotEmpty()) {
            height += 80 // 小标题
            height += exercises.size * lineHeight
        }
        if (scores.isNotEmpty()) {
            height += 80 // 小标题
            height += scores.size * lineHeight
        }
        height += 80 // 评价区
        if (lesson.nextGoal.isNotBlank()) height += 60
        // 教练寄语高度（需估算行数）
        if (lesson.coachComment.isNotBlank()) {
            val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 24f }
            val commentLines = wrapText(lesson.coachComment, measurePaint, width - padding * 2)
            height += 60 // 小标题
            height += commentLines.size * lineHeight
        }
        height += 100 // 底部留白

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#1A1A2E")) // 深色背景

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var y = padding

        // === 标题 ===
        val gradeLabel = student?.let { Standards.gradeLabel(it.grade) } ?: ""
        paint.color = Color.WHITE
        paint.textSize = 42f
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.LEFT
        val title = "${lesson.studentName} · $gradeLabel"
        canvas.drawText(title, padding.toFloat(), y.toFloat(), paint)

        // 日期（右上角）
        paint.textSize = 28f
        paint.typeface = Typeface.DEFAULT
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(lesson.date, (width - padding).toFloat(), y.toFloat(), paint)
        y += 60

        // 副标题
        paint.color = Color.parseColor("#818CF8")
        paint.textSize = 26f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("课堂训练报告", padding.toFloat(), y.toFloat(), paint)
        y += 50

        // 分割线
        paint.color = Color.parseColor("#4338CA")
        paint.strokeWidth = 3f
        canvas.drawLine(padding.toFloat(), y.toFloat(), (width - padding).toFloat(), y.toFloat(), paint)
        y += 40

        // === 学员信息 ===
        paint.color = Color.parseColor("#E0E0E0")
        paint.textSize = 26f
        val schoolText = student?.school?.ifBlank { "" } ?: ""
        val genderText = student?.gender ?: ""
        val infoLine = buildString {
            if (genderText.isNotEmpty()) append("$genderText · ")
            append(gradeText(student?.grade))
            if (schoolText.isNotEmpty()) append(" · $schoolText")
        }
        canvas.drawText(infoLine, padding.toFloat(), y.toFloat(), paint)
        y += 50

        // === 课时信息 ===
        paint.color = Color.parseColor("#A5B4FC")
        val lessonInfo = "${lesson.duration}分钟 · ${lesson.lessonType} · ${lesson.attendance}"
        canvas.drawText(lessonInfo, padding.toFloat(), y.toFloat(), paint)
        if (lesson.location.isNotBlank()) {
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(lesson.location, (width - padding).toFloat(), y.toFloat(), paint)
            paint.textAlign = Paint.Align.LEFT
        }
        y += 60

        // === 训练内容 ===
        if (exercises.isNotEmpty()) {
            y += 20
            paint.color = Color.WHITE
            paint.textSize = 32f
            paint.typeface = Typeface.DEFAULT_BOLD
            val doneCount = exercises.count { it.done }
            canvas.drawText("训练内容（$doneCount/${exercises.size}完成）", padding.toFloat(), y.toFloat(), paint)
            y += 50

            paint.textSize = 24f
            paint.typeface = Typeface.DEFAULT
            for (item in exercises) {
                val mark = if (item.done) "✓" else "○"
                val markColor = if (item.done) Color.parseColor("#10B981") else Color.parseColor("#6B7280")
                paint.color = markColor
                canvas.drawText(mark, padding.toFloat(), y.toFloat(), paint)

                paint.color = Color.parseColor("#E5E7EB")
                val text = "${item.name}  ${item.sets}组×${item.reps}（${item.intensity}）"
                canvas.drawText(text, (padding + 40).toFloat(), y.toFloat(), paint)
                y += lineHeight
            }
            y += 20
        }

        // === 成绩 ===
        if (scores.isNotEmpty()) {
            y += 20
            paint.color = Color.WHITE
            paint.textSize = 32f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("体测成绩", padding.toFloat(), y.toFloat(), paint)
            y += 50

            paint.textSize = 24f
            paint.typeface = Typeface.DEFAULT
            for ((name, info) in scores) {
                val score = info.optDouble("score", 0.0)
                val grade = info.optString("grade", "")

                paint.color = Color.parseColor("#E5E7EB")
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(name, padding.toFloat(), y.toFloat(), paint)

                val gradeColor = when (grade) {
                    "优秀" -> Color.parseColor("#10B981")
                    "良好" -> Color.parseColor("#3B82F6")
                    "及格" -> Color.parseColor("#F59E0B")
                    else -> Color.parseColor("#EF4444")
                }
                paint.color = gradeColor
                paint.textAlign = Paint.Align.RIGHT
                val scoreText = "${String.format("%.1f", score)}分  $grade"
                canvas.drawText(scoreText, (width - padding).toFloat(), y.toFloat(), paint)
                y += lineHeight
            }
            y += 20
        }

        // === 课堂评价 ===
        y += 20
        paint.color = Color.WHITE
        paint.textSize = 32f
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("课堂评价", padding.toFloat(), y.toFloat(), paint)
        y += 50

        paint.color = Color.parseColor("#E5E7EB")
        paint.textSize = 26f
        paint.typeface = Typeface.DEFAULT
        val evalText = "训练态度：${lesson.attitude}  整体表现：${lesson.performance}/10"
        canvas.drawText(evalText, padding.toFloat(), y.toFloat(), paint)
        y += lineHeight

        if (lesson.nextGoal.isNotBlank()) {
            paint.color = Color.parseColor("#A5B4FC")
            canvas.drawText("下次课目标：${lesson.nextGoal}", padding.toFloat(), y.toFloat(), paint)
            y += lineHeight
        }

        // 教练寄语（自由编辑内容）
        if (lesson.coachComment.isNotBlank()) {
            y += 10
            paint.color = Color.WHITE
            paint.textSize = 28f
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("教练寄语", padding.toFloat(), y.toFloat(), paint)
            y += 44

            paint.color = Color.parseColor("#E5E7EB")
            paint.textSize = 24f
            paint.typeface = Typeface.DEFAULT
            // 自动换行处理
            val commentLines = wrapText(lesson.coachComment, paint, width - padding * 2)
            for (line in commentLines) {
                canvas.drawText(line, padding.toFloat(), y.toFloat(), paint)
                y += lineHeight
            }
        }

        // === 底部 ===
        y += 30
        paint.color = Color.parseColor("#6B7280")
        paint.textSize = 22f
        paint.textAlign = Paint.Align.CENTER
        val coachName = lesson.coach.ifBlank { "体育教学助手" }
        canvas.drawText("—— $coachName ——", (width / 2).toFloat(), y.toFloat(), paint)

        return bitmap
    }

    /** 年级代码转中文描述。 */
    private fun gradeText(grade: String?): String {
        if (grade.isNullOrBlank()) return ""
        return try {
            Standards.gradeLabel(grade)
        } catch (e: Exception) {
            ""
        }
    }

    /** 解析训练内容 JSON。 */
    private fun parseExercises(json: String): List<ExerciseItem> {
        if (json.isBlank()) return emptyList()
        // 使用 JsonSafe 兜底：脏数据返回空列表
        val arr = JsonSafe.parseArray(json) ?: return emptyList()
        val result = mutableListOf<ExerciseItem>()
        for (i in 0 until arr.length()) {
            // 单条 item 解析失败时跳过
            val obj = arr.optJSONObject(i) ?: continue
            result.add(ExerciseItem(
                name = obj.optString("name"),
                sets = obj.optInt("sets", 3),
                reps = obj.optString("reps"),
                intensity = obj.optString("intensity", "中"),
                done = obj.optBoolean("done", false),
                note = obj.optString("note")
            ))
        }
        return result
    }

    /** 解析成绩 JSON。 */
    private fun parseScores(json: String): List<Pair<String, JSONObject>> {
        if (json.isBlank() || json == "{}") return emptyList()
        // 使用 JsonSafe 兜底：脏数据返回空列表
        val obj = JsonSafe.parseObject(json) ?: return emptyList()
        val result = mutableListOf<Pair<String, JSONObject>>()
        for (key in obj.keys()) {
            // 单条项目解析失败时跳过
            val item = obj.optJSONObject(key) ?: continue
            result.add(key to item)
        }
        return result
    }

    /**
     * 按像素宽度自动换行文本。
     * @param text 原始文本
     * @param paint 已配置 textSize 的画笔
     * @param maxWidth 最大可用宽度（像素）
     * @return 换行后的行列表
     */
    private fun wrapText(text: String, paint: Paint, maxWidth: Int): List<String> {
        if (text.isBlank()) return emptyList()
        val lines = mutableListOf<String>()
        // 先按显式换行符拆分
        for (paragraph in text.split("\n")) {
            if (paragraph.isBlank()) {
                lines.add("")
                continue
            }
            val current = StringBuilder()
            for (ch in paragraph) {
                current.append(ch)
                if (paint.measureText(current.toString()) > maxWidth) {
                    // 当前字符加入后超宽，回退一个字符
                    current.deleteCharAt(current.length - 1)
                    lines.add(current.toString())
                    current.clear()
                    current.append(ch)
                }
            }
            if (current.isNotEmpty()) lines.add(current.toString())
        }
        return lines
    }
}
