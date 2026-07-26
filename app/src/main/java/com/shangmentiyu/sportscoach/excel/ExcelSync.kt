package com.shangmentiyu.sportscoach.excel

import com.shangmentiyu.sportscoach.core.JsonSafe
import com.shangmentiyu.sportscoach.core.Standards
import com.shangmentiyu.sportscoach.core.Scorer
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.Student
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/**
 * Excel 导入导出工具（与桌面端兼容）
 *
 * 性能优化（v22 防止 OOM）：
 * - 写入路径（[exportLessonReport] / [exportScoresToArchive]）改用 [SXSSFWorkbook]
 *   流式写入，设置 100 行窗口，超出窗口的行自动刷盘并释放内存，
 *   避免大量学员/成绩场景下一次性构建全量 DOM 导致 OOM。
 * - 读取路径（[importStudentsFromExcel]）仍使用 [XSSFWorkbook]，因 SXSSF 不支持读取。
 * - 写入完成后必须调用 [SXSSFWorkbook.dispose] 清理 POI 在 java.io.tmpdir
 *   生成的临时文件，避免磁盘膨胀。
 */
/**
 * === v25 优化4：Excel 导入策略（同名学员处理方式） ===
 *
 * 由用户在导入前选择，决定如何处理 Excel 档案中与本地已存在学员同名的记录：
 *
 * - [APPEND]      追加：同名则跳过，仅新增本地不存在的学员
 * - [OVERWRITE]   覆盖：同名则覆盖原有全部资料（含历史排课与课时包会被一并清除）
 * - [UPDATE_PART] 更新部分：同名则更新身高/体重等身体形态指标，
 *                 保留历史排课与课时包（适用于"学员资料变更但课包记录不可丢"的场景）
 */
enum class ImportStrategy {
    APPEND,
    OVERWRITE,
    UPDATE_PART
}

object ExcelSync {

    /** SXSSF 流式写入窗口：保留在内存中的行数，超出后自动刷盘 */
    private const val SXSSF_WINDOW_SIZE = 100

    private val TITLE_FILL_COLOR = IndexedColors.INDIGO.index
    private val HEADER_FILL_COLOR = IndexedColors.DARK_BLUE.index
    private val SCORE_FILL_COLOR = IndexedColors.DARK_TEAL.index

    /**
     * 导出单次课堂记录为可读 Excel 报告
     *
     * 性能：使用 [SXSSFWorkbook] 流式写入，100 行窗口防 OOM。
     *
     * @param outputStream 目标输出流（由调用方负责关闭流的生命周期管理）
     */
    fun exportLessonReport(lesson: Lesson, student: Student?, outputStream: OutputStream): Boolean {
        var wb: SXSSFWorkbook? = null
        try {
            wb = SXSSFWorkbook(SXSSF_WINDOW_SIZE)
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
                listOf("学校", student?.school ?: "", "年级", student?.let { Standards.gradeFullLabel(it.grade) } ?: ""),
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
            wb.dispose()  // 清理 SXSSF 在 tmpdir 生成的临时文件
            wb.close()    // 同时关闭底层的 XSSFWorkbook
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            // 异常路径也必须清理临时文件，避免磁盘膨胀
            runCatching { wb?.dispose() }
            runCatching { wb?.close() }
            return false
        }
    }

    /**
     * 导出成绩到桌面端档案（每个学员独立文件，追加工作表）。
     *
     * 性能：读阶段必须使用 [XSSFWorkbook]（SXSSF 不支持读取），写阶段用
     * [SXSSFWorkbook] 包装底层 XSSFWorkbook，新增的成绩工作表走流式写入。
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
        var xssf: XSSFWorkbook? = null
        var sxssf: SXSSFWorkbook? = null
        try {
            xssf = if (inputStream != null) {
                XSSFWorkbook(inputStream)
            } else {
                XSSFWorkbook()
            }

            // 读取或创建 _meta 工作表（读阶段，必须 XSSF）
            val metaSheet = xssf.getSheet("_meta") ?: xssf.createSheet("_meta").also {
                xssf.setSheetHidden(xssf.getSheetIndex(it), true)
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

            // 写阶段：用 SXSSF 包装底层 XSSFWorkbook，新增成绩工作表走流式写入
            sxssf = SXSSFWorkbook(xssf, SXSSF_WINDOW_SIZE)

            // 创建成绩工作表
            val ws = sxssf.createSheet(sheetName)

            // 写入成绩数据
            val grade = student?.grade ?: "1"
            val stds = Standards.getStandardsByGrade(grade)
            val gender = student?.gender ?: "男"

            val headerStyle = createStyle(sxssf, bold = true, fillColor = HEADER_FILL_COLOR, fontColor = IndexedColors.WHITE.index)

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

            // 写回 meta（_meta 仍在 XSSF 模型中，可读可写）
            metaSheet.getRow(0)?.getCell(0)?.setCellValue(meta.toString()) ?: run {
                metaSheet.createRow(0).createCell(0).setCellValue(meta.toString())
            }

            // 设置列宽
            for (i in 0..6) ws.setColumnWidth(i, 4000)

            // 写入流（由调用方管理流的关闭）
            sxssf.write(outputStream)
            sxssf.dispose()  // 清理 SXSSF 临时文件
            sxssf.close()    // 关闭 SXSSF 同时关闭底层 XSSFWorkbook
            return sheetName
        } catch (e: Exception) {
            e.printStackTrace()
            // 异常路径清理：先 SXSSF 再 XSSF
            runCatching { sxssf?.dispose() }
            runCatching { sxssf?.close() }
            runCatching { xssf?.close() }
            return null
        }
    }

    /**
     * 从档案输入流列表导入学员。
     *
     * 仅读取 _meta 工作表，使用 [XSSFWorkbook] 即可（SXSSF 不支持读取）。
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

    /**
     * === v26 优化5：智能列映射（表头模糊匹配）===
     *
     * 从普通 Excel 表格（含表头行）导入学员信息。
     *
     * 适用场景：教练从其他系统或手工编辑的 Excel 表，表头可能是
     * "姓名/性别/年级/学校/电话/身高/体重/年龄/备注"，
     * 也可能写成"名字/性别/班级/学校名称/手机/身高cm/体重kg/岁/说明" 等近义表头。
     *
     * 模糊列映射规则（基于关键字包含匹配）：
     * - 姓名：包含"姓"或"名"或"name"
     * - 性别：包含"性"或"别"或"gender"
     * - 年级：包含"年"或"级"或"grade"
     * - 学校：包含"学"或"校"或"school"
     * - 电话：包含"电"或"话"或"手"或"机"或"phone"
     * - 身高：包含"高"或"height"
     * - 体重：包含"体"或"重"或"weight"
     * - 年龄：包含"龄"或"age"
     * - 备注：包含"备"或"注"或"remark"
     *
     * 即使表头稍微不准（如"身高" 写成 "高度"），也能顺利导入，降低用户心理压力。
     *
     * @param inputStreams Excel 文件输入流列表（由调用方负责关闭每个流）
     * @return 解析出的学员列表
     */
    fun importStudentsFromTabularExcel(inputStreams: List<InputStream>): List<Student> {
        val students = mutableListOf<Student>()
        for ((idx, fis) in inputStreams.withIndex()) {
            try {
                val wb = XSSFWorkbook(fis)
                // 遍历所有工作表，跳过 _meta 等元数据表
                for (sheetIndex in 0 until wb.numberOfSheets) {
                    val sheet = wb.getSheetAt(sheetIndex)
                    val sheetName = sheet.sheetName
                    if (sheetName == "_meta" || sheetName.startsWith("_")) continue

                    val headerRow = sheet.getRow(0) ?: continue
                    // 构建列索引映射：列名 -> 列号
                    val colMap = buildColumnMapping(headerRow)
                    if (colMap.isEmpty()) continue  // 没有可识别的表头

                    // 从第 2 行开始读取学员数据
                    val lastRow = sheet.lastRowNum
                    for (rowIdx in 1..lastRow) {
                        val row = sheet.getRow(rowIdx) ?: continue
                        val student = parseStudentRow(row, colMap, idx, rowIdx)
                        if (student != null) students.add(student)
                    }
                }
            } catch (e: Exception) {
                // 跳过无法解析的文件
            }
        }
        return students
    }

    /**
     * 构建模糊列映射表：根据表头关键字识别每列对应的字段。
     *
     * @param headerRow 表头行
     * @return 字段名 -> 列号 的映射（如 "name" -> 0, "heightCm" -> 5）
     */
    private fun buildColumnMapping(headerRow: Row): Map<String, Int> {
        val mapping = mutableMapOf<String, Int>()
        for (colIdx in 0 until headerRow.lastCellNum) {
            val cell = headerRow.getCell(colIdx) ?: continue
            val headerText = cell.stringCellValue?.trim() ?: continue
            if (headerText.isBlank()) continue

            // 按优先级匹配关键字（先匹配到的字段优先）
            val lowerHeader = headerText.lowercase()
            when {
                // 姓名（必须包含"姓"或"名"，但不能同时是"班级"）
                (headerText.contains("姓") || lowerHeader.contains("name") ||
                    (headerText.contains("名") && !headerText.contains("班"))) ->
                    if (mapping["name"] == null) mapping["name"] = colIdx
                // 性别
                headerText.contains("性") || lowerHeader.contains("gender") ->
                    if (mapping["gender"] == null) mapping["gender"] = colIdx
                // 学校（必须先于"年级"判断，因都含"学"字；优先匹配"校"字）
                headerText.contains("校") || lowerHeader.contains("school") ->
                    if (mapping["school"] == null) mapping["school"] = colIdx
                // 年级
                headerText.contains("年") || headerText.contains("级") || lowerHeader.contains("grade") ->
                    if (mapping["grade"] == null) mapping["grade"] = colIdx
                // 电话
                headerText.contains("电") || headerText.contains("话") ||
                    headerText.contains("手") || lowerHeader.contains("phone") ->
                    if (mapping["phone"] == null) mapping["phone"] = colIdx
                // 身高（含"高"字，如"身高"、"高度"、"身长"）
                headerText.contains("高") || lowerHeader.contains("height") ->
                    if (mapping["heightCm"] == null) mapping["heightCm"] = colIdx
                // 体重（含"体"或"重"字，如"体重"、"重量"）
                headerText.contains("体") || headerText.contains("重") || lowerHeader.contains("weight") ->
                    if (mapping["weightKg"] == null) mapping["weightKg"] = colIdx
                // 年龄
                headerText.contains("龄") || lowerHeader.contains("age") ->
                    if (mapping["age"] == null) mapping["age"] = colIdx
                // 备注
                headerText.contains("备") || headerText.contains("注") || lowerHeader.contains("remark") ->
                    if (mapping["note"] == null) mapping["note"] = colIdx
            }
        }
        return mapping
    }

    /**
     * 解析单行学员数据（基于模糊列映射）。
     *
     * @param row 当前行
     * @param colMap 字段名 -> 列号 的映射（来自 [buildColumnMapping]）
     * @param fileIdx 文件序号（用于默认姓名生成）
     * @param rowIdx 行序号（用于默认姓名生成）
     * @return 学员对象，如本行无任何有效数据则返回 null
     */
    private fun parseStudentRow(
        row: Row,
        colMap: Map<String, Int>,
        fileIdx: Int,
        rowIdx: Int
    ): Student? {
        // 姓名：必须存在且非空，否则跳过该行
        val nameCol = colMap["name"] ?: return null
        val nameCell = row.getCell(nameCol)
        val name = nameCell?.stringCellValue?.trim() ?: ""
        if (name.isBlank()) return null

        // 性别：默认"男"
        val gender = colMap["gender"]?.let { col ->
            row.getCell(col)?.stringCellValue?.trim()?.ifBlank { "男" } ?: "男"
        } ?: "男"

        // 年级：从字符串提取数字（支持"高一"、"7年级"、"3"等），默认"1"
        val grade = colMap["grade"]?.let { col ->
            val raw = row.getCell(col)?.stringCellValue?.trim() ?: ""
            parseGradeFromString(raw)
        } ?: "1"

        // 学校
        val school = colMap["school"]?.let { col ->
            row.getCell(col)?.stringCellValue?.trim() ?: ""
        } ?: ""

        // 电话
        val phone = colMap["phone"]?.let { col ->
            row.getCell(col)?.stringCellValue?.trim() ?: ""
        } ?: ""

        // 身高（数字类型，单位 cm）
        val heightCm = colMap["heightCm"]?.let { col ->
            getCellAsDouble(row, col).toInt()
        } ?: 0

        // 体重（数字类型，单位 kg）
        val weightKg = colMap["weightKg"]?.let { col ->
            getCellAsDouble(row, col).toFloat()
        } ?: 0f

        // 年龄
        val age = colMap["age"]?.let { col ->
            getCellAsDouble(row, col).toInt()
        } ?: 0

        // BMI 自动计算
        val bmi = if (heightCm > 0 && weightKg > 0f) {
            val h = heightCm / 100.0
            (weightKg / (h * h)).toFloat()
        } else 0f

        return Student(
            name = name,
            gender = gender,
            grade = grade,
            school = school,
            phone = phone,
            age = age,
            heightCm = heightCm,
            weightKg = weightKg,
            bmi = bmi
        )
    }

    /**
     * 从字符串中提取年级编码（1-13）。
     *
     * 支持格式：
     * - 纯数字："3" -> "3"
     * - "X年级"："7年级" -> "7"
     * - "小X" / "初X" / "高X"："小3" -> "3"，"初2" -> "8"，"高1" -> "10"
     * - "高一" / "初一" -> "10" / "7"
     * - "中考" -> "13"
     *
     * @return 1-13 的字符串，无法识别返回 "1"
     */
    private fun parseGradeFromString(raw: String): String {
        if (raw.isBlank()) return "1"
        // 处理"中考"
        if (raw.contains("中考")) return "13"

        // 中文数字映射
        val chineseMap = mapOf(
            "一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5,
            "六" to 6, "七" to 7, "八" to 8, "九" to 9, "十" to 10,
            "十一" to 11, "十二" to 12
        )

        // 匹配"小X"：1-6
        val smallMatch = Regex("小([一二三四五六])").find(raw)
        if (smallMatch != null) {
            val num = chineseMap[smallMatch.groupValues[1]] ?: 1
            return num.coerceIn(1, 6).toString()
        }
        // 匹配"初X"：7-9
        val midMatch = Regex("初([一二三四五])").find(raw)
        if (midMatch != null) {
            val num = chineseMap[midMatch.groupValues[1]] ?: 1
            return (num + 6).coerceIn(7, 9).toString()
        }
        // 匹配"高X"：10-12
        val highMatch = Regex("高([一二三])").find(raw)
        if (highMatch != null) {
            val num = chineseMap[highMatch.groupValues[1]] ?: 1
            return (num + 9).coerceIn(10, 12).toString()
        }

        // 匹配纯数字 + "年级"
        val gradeMatch = Regex("(\\d+)\\s*年?级?").find(raw)
        if (gradeMatch != null) {
            val num = gradeMatch.groupValues[1].toIntOrNull() ?: 1
            return num.coerceIn(1, 13).toString()
        }

        // 直接是纯数字
        val num = raw.trim().toIntOrNull()
        if (num != null) return num.coerceIn(1, 13).toString()

        return "1"
    }

    /**
     * 安全读取单元格的数值（支持数字、字符串、公式）。
     *
     * Excel 单元格可能是 NUMERIC、STRING 或 FORMULA 类型：
     * - NUMERIC：直接返回数值
     * - STRING：尝试解析为数字，失败返回 0
     * - FORMULA：取公式计算结果
     *
     * @return 单元格数值（无法解析返回 0.0）
     */
    private fun getCellAsDouble(row: Row, colIdx: Int): Double {
        return try {
            val cell = row.getCell(colIdx) ?: return 0.0
            when (cell.cellType) {
                CellType.NUMERIC -> cell.numericCellValue
                CellType.STRING -> cell.stringCellValue.trim().toDoubleOrNull() ?: 0.0
                CellType.FORMULA -> cell.numericCellValue
                else -> 0.0
            }
        } catch (_: Exception) {
            0.0
        }
    }

    // === 辅助方法 ===

    /**
     * 创建单元格样式。
     *
     * 参数类型使用 [Workbook] 父接口（XSSF/SXSSF 均实现），
     * 兼容传统 XSSFWorkbook 写入与 SXSSFWorkbook 流式写入两种场景。
     * 返回类型保持 [XSSFCellStyle]，因 SXSSF 的 createCellStyle 实际返回 XSSF 实例。
     */
    private fun createStyle(
        wb: Workbook,
        bold: Boolean = false,
        fontSize: Int = 11,
        fillColor: Short = IndexedColors.WHITE.index,
        fontColor: Short = IndexedColors.BLACK.index,
        align: HorizontalAlignment = HorizontalAlignment.CENTER
    ): XSSFCellStyle {
        val style = wb.createCellStyle() as XSSFCellStyle
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
