package com.shangmentiyu.sportscoach.excel

import com.shangmentiyu.sportscoach.core.JsonSafe
import com.shangmentiyu.sportscoach.core.Standards
import com.shangmentiyu.sportscoach.core.Scorer
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.Student
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/**
 * Excel 导入导出工具（与桌面端兼容）
 */
object ExcelSync {

    private val TITLE_FILL_COLOR = IndexedColors.INDIGO.index
    private val HEADER_FILL_COLOR = IndexedColors.DARK_BLUE.index
    private val SCORE_FILL_COLOR = IndexedColors.DARK_TEAL.index

    /**
     * 导出单次课堂记录为可读 Excel 报告
     *
     * @param outputStream 目标输出流（由调用方负责关闭流的生命周期管理）
     */
    fun exportLessonReport(lesson: Lesson, student: Student?, outputStream: OutputStream): Boolean {
        try {
            val wb = XSSFWorkbook()
            val ws = wb.createSheet("课堂记录")

            // 标题行
            val titleStyle = createStyle(wb, bold = true, fontSize = 14, fillColor = TITLE_FILL_COLOR, fontColor = IndexedColors.WHITE.index)
            val row0 = ws.createRow(0)
            val titleCell = row0.createCell(0)
            titleCell.setCellValue("${student?.name ?: lesson.studentName} 课堂训练记录 ${lesson.date}")
            titleCell.cellStyle = titleStyle
            mergeCells(ws, 0, 0, 0, 6)

            // 学员与课时信息
            val labelStyle = createStyle(wb, bold = true, fillColor = HEADER_FILL_COLOR, fontColor = IndexedColors.WHITE.index)
            val valueStyle = createStyle(wb, align = HorizontalAlignment.LEFT)
            var rowIdx = 2

            val infoRows = listOf(
                listOf("姓名", student?.name ?: lesson.studentName, "性别", student?.gender ?: ""),
                listOf("学校", student?.school ?: "", "年级", student?.let { Standards.gradeLabel(it.grade) } ?: ""),
                listOf("日期", lesson.date, "时长", "${lesson.duration}分钟"),
                listOf("教练", lesson.coach, "地点", lesson.location),
                listOf("课时类型", lesson.lessonType, "出勤", lesson.attendance),
                listOf("训练态度", lesson.attitude, "表现评分", "${lesson.performance}/10")
            )

            for (info in infoRows) {
                val row = ws.createRow(rowIdx++)
                for ((i, text) in info.withIndex()) {
                    val cell = row.createCell(i * 2)
                    cell.setCellValue(text)
                    cell.cellStyle = if (i % 2 == 0) labelStyle else valueStyle
                }
            }

            // 下次课目标
            if (lesson.nextGoal.isNotBlank()) {
                rowIdx++
                val row = ws.createRow(rowIdx)
                val cell = row.createCell(0)
                cell.setCellValue("下次课目标: ${lesson.nextGoal}")
                mergeCells(ws, row.rowNum, row.rowNum, 0, 6)
            }

            // 教练寄语
            if (lesson.coachComment.isNotBlank()) {
                rowIdx++
                val row = ws.createRow(rowIdx)
                val cell = row.createCell(0)
                cell.setCellValue("教练寄语: ${lesson.coachComment}")
                mergeCells(ws, row.rowNum, row.rowNum, 0, 6)
            }

            // 训练内容表
            rowIdx += 2
            val exercises = parseExercises(lesson.content)
            if (exercises.isNotEmpty()) {
                val headerRow = ws.createRow(rowIdx++)
                val headers = listOf("动作名称", "组数", "次数/时长", "强度", "完成", "要点提示")
                for ((i, h) in headers.withIndex()) {
                    val cell = headerRow.createCell(i)
                    cell.setCellValue(h)
                    cell.cellStyle = labelStyle
                }
                for (item in exercises) {
                    val row = ws.createRow(rowIdx++)
                    row.createCell(0).setCellValue(item.name)
                    row.createCell(1).setCellValue(item.sets.toDouble())
                    row.createCell(2).setCellValue(item.reps)
                    row.createCell(3).setCellValue(item.intensity)
                    row.createCell(4).setCellValue(if (item.done) "✓" else "○")
                    row.createCell(5).setCellValue(item.note)
                }
            }

            // 成绩记录表
            rowIdx += 2
            val scores = parseScores(lesson.scores)
            if (scores.isNotEmpty()) {
                val headerRow = ws.createRow(rowIdx++)
                val headers = listOf("项目", "实测成绩", "得分", "等级")
                for ((i, h) in headers.withIndex()) {
                    val cell = headerRow.createCell(i)
                    cell.setCellValue(h)
                    cell.cellStyle = labelStyle
                }
                for ((name, info) in scores) {
                    val row = ws.createRow(rowIdx++)
                    row.createCell(0).setCellValue(name)
                    row.createCell(1).setCellValue(info.optString("value", ""))
                    val scoreCell = row.createCell(2)
                    scoreCell.setCellValue(info.optDouble("score", 0.0))
                    row.createCell(3).setCellValue(info.optString("grade", ""))
                }
            }

            // 课后小结
            if (lesson.summary.isNotBlank()) {
                rowIdx += 2
                val row = ws.createRow(rowIdx)
                val cell = row.createCell(0)
                cell.setCellValue("课后小结:\n${lesson.summary}")
                mergeCells(ws, row.rowNum, row.rowNum + 3, 0, 3)
            }

            // 设置列宽
            for (i in 0..6) ws.setColumnWidth(i, 4000)

            // 写入流（由调用方管理流的关闭）
            wb.write(outputStream)
            wb.close()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * 导出成绩到桌面端档案（每个学员独立文件，追加工作表）。
     *
     * @param inputStream 已有档案的输入流；null 表示档案不存在，需新建。
     *                    调用方负责在调用后关闭此流。
     * @param outputStream 目标档案输出流（由调用方负责关闭）。
     * @return 成功返回 sheetName，失败返回 null。
     */
    fun exportScoresToArchive(
        lesson: Lesson,
        student: Student?,
        inputStream: InputStream?,
        outputStream: OutputStream
    ): String? {
        try {
            val wb = if (inputStream != null) {
                XSSFWorkbook(inputStream)
            } else {
                XSSFWorkbook()
            }

            // 读取或创建 _meta 工作表
            val metaSheet = wb.getSheet("_meta") ?: wb.createSheet("_meta").also {
                wb.setSheetHidden(wb.getSheetIndex(it), true)
            }

            var meta: JSONObject = JSONObject()
            val metaCell = metaSheet.getRow(0)?.getCell(0)
            if (metaCell != null && metaCell.stringCellValue.isNotBlank()) {
                // 使用 JsonSafe 兜底：旧档案 meta 损坏时降级为空 meta，不影响本次导出
                meta = JsonSafe.parseObject(metaCell.stringCellValue) ?: JSONObject()
            }
            val records = meta.optJSONArray("records") ?: JSONArray().also { meta.put("records", it) }
            val info = meta.optJSONObject("info") ?: JSONObject().also { meta.put("info", it) }

            // 更新学员信息
            if (student != null) {
                info.put("name", student.name)
                info.put("gender", student.gender)
                info.put("grade", student.grade)
                info.put("school", student.school)
                info.put("phone", student.phone)
            }

            // 计算序号
            val seq = records.length() + 1
            val dateCompact = lesson.date.replace("-", "")

            // 工作表名（最多31字符）
            var sheetName = "第${seq}次_${dateCompact}_primary"
            if (sheetName.length > 31) sheetName = sheetName.take(31)

            // 创建成绩工作表
            val ws = wb.createSheet(sheetName)

            // 写入成绩数据
            val grade = student?.grade ?: "1"
            val stds = Standards.getStandardsByGrade(grade)
            val gender = student?.gender ?: "男"

            val headerStyle = createStyle(wb, bold = true, fillColor = HEADER_FILL_COLOR, fontColor = IndexedColors.WHITE.index)

            // 表头
            val headers = listOf("测试项目", "单位", "满分标准", "及格标准", "实测成绩", "得分", "等级")
            val headerRow = ws.createRow(0)
            for ((i, h) in headers.withIndex()) {
                val cell = headerRow.createCell(i)
                cell.setCellValue(h)
                cell.cellStyle = headerStyle
            }

            // 遍历标准
            val scores = parseScores(lesson.scores)
            var rowIdx = 1
            for (std in stds) {
                val row = ws.createRow(rowIdx++)
                row.createCell(0).setCellValue(std.name)
                row.createCell(1).setCellValue(std.unit)
                val full = if (gender == "男") std.boysFull else std.girlsFull
                val pass = if (gender == "男") std.boysPass else std.girlsPass
                row.createCell(2).setCellValue(Scorer.formatValue(full, std.unit))
                row.createCell(3).setCellValue(Scorer.formatValue(pass, std.unit))

                val scoreInfo = scores.find { it.first == std.name }?.second
                if (scoreInfo != null) {
                    row.createCell(4).setCellValue(scoreInfo.optString("value", ""))
                    row.createCell(5).setCellValue(scoreInfo.optDouble("score", 0.0))
                    row.createCell(6).setCellValue(scoreInfo.optString("grade", ""))
                }
            }

            // 追加元数据记录
            val totalScore = if (scores.isNotEmpty()) {
                scores.mapNotNull { it.second.optDouble("score", Double.NaN).takeIf { d -> !d.isNaN() } }.average()
            } else 0.0

            val recordMeta = JSONObject().apply {
                put("seq", seq)
                put("date", lesson.date)
                put("type", "primary")
                put("tag", "primary")
                put("sheet_name", sheetName)
                // 使用 JsonSafe 兜底：脏数据降级为空对象，不影响本次导出
                put("scores", JsonSafe.parseObject(lesson.scores) ?: JSONObject())
                put("total", totalScore)
                put("evaluation", lesson.summary)
            }
            records.put(recordMeta)

            // 写回 meta
            metaSheet.getRow(0)?.getCell(0)?.setCellValue(meta.toString()) ?: run {
                metaSheet.createRow(0).createCell(0).setCellValue(meta.toString())
            }

            // 设置列宽
            for (i in 0..6) ws.setColumnWidth(i, 4000)

            // 写入流（由调用方管理流的关闭）
            wb.write(outputStream)
            wb.close()
            return sheetName
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * 从档案输入流列表导入学员。
     *
     * @param inputStreams 档案文件的输入流列表（由调用方负责关闭每个流）。
     * @return 解析出的学员列表。
     */
    fun importStudentsFromExcel(inputStreams: List<InputStream>): List<Student> {
        val students = mutableListOf<Student>()
        for ((idx, fis) in inputStreams.withIndex()) {
            try {
                val wb = XSSFWorkbook(fis)
                val metaSheet = wb.getSheet("_meta") ?: continue
                val cell = metaSheet.getRow(0)?.getCell(0) ?: continue
                val metaStr = cell.stringCellValue
                if (metaStr.isBlank()) continue

                val meta = JsonSafe.parseObject(metaStr) ?: continue
                val info = meta.optJSONObject("info") ?: continue

                val name = info.optString("name").ifBlank { "学员${idx + 1}" }
                val gender = info.optString("gender", "男")
                val grade = info.optString("grade", "1")
                val school = info.optString("school", "")
                val phone = info.optString("phone", "")

                students.add(Student(name = name, gender = gender, grade = grade, school = school, phone = phone))
            } catch (e: Exception) {
                // 跳过无法解析的文件
            }
        }
        return students
    }

    // === 辅助方法 ===

    private fun createStyle(
        wb: XSSFWorkbook,
        bold: Boolean = false,
        fontSize: Int = 11,
        fillColor: Short = IndexedColors.WHITE.index,
        fontColor: Short = IndexedColors.BLACK.index,
        align: HorizontalAlignment = HorizontalAlignment.CENTER
    ): XSSFCellStyle {
        val style = wb.createCellStyle()
        val font = wb.createFont()
        font.bold = bold
        font.fontHeightInPoints = fontSize.toShort()
        font.color = fontColor
        style.setFont(font)
        style.fillForegroundColor = fillColor
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        style.alignment = align
        style.verticalAlignment = VerticalAlignment.CENTER
        style.borderTop = BorderStyle.THIN
        style.borderBottom = BorderStyle.THIN
        style.borderLeft = BorderStyle.THIN
        style.borderRight = BorderStyle.THIN
        return style
    }

    private fun mergeCells(ws: Sheet, row1: Int, row2: Int, col1: Int, col2: Int) {
        ws.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress(row1, row2, col1, col2))
    }

    private data class ExerciseData(val name: String, val sets: Int, val reps: String, val intensity: String, val done: Boolean, val note: String)

    private fun parseExercises(json: String): List<ExerciseData> {
        if (json.isBlank()) return emptyList()
        // 使用 JsonSafe 兜底：脏数据返回空列表
        val arr = JsonSafe.parseArray(json) ?: return emptyList()
        val result = mutableListOf<ExerciseData>()
        for (i in 0 until arr.length()) {
            // 单条 item 解析失败时跳过
            val obj = arr.optJSONObject(i) ?: continue
            result.add(ExerciseData(
                obj.optString("name"),
                obj.optInt("sets", 3),
                obj.optString("reps"),
                obj.optString("intensity", "中"),
                obj.optBoolean("done", false),
                obj.optString("note")
            ))
        }
        return result
    }

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
}
