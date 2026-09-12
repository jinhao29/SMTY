package com.shangmentiyu.sportscoach.data.internal

import android.content.Context
import android.util.Log
import com.shangmentiyu.sportscoach.core.Standards
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import com.shangmentiyu.sportscoach.data.model.Student
import org.json.JSONException
import org.json.JSONObject

/**
 * 小程序数据导入器（阶段五互通，处理层：纯解析 + 落库执行）。
 *
 * 与桌面端 data_center/miniprogram_bridge.py 使用同一份数据协议
 * （miniprogram/src/utils/local-store.js exportBackup 格式），跨端锚定测试锁定：
 * - 桌面端：test_miniprogram_parity.py
 * - 本端：MiniprogramImporterTest（同一 fixture 字段一一对应）
 *
 * 映射规则：
 * - students 非软删行 → [Student]（姓名主键；grade 中文标签 → 编码；
 *   parent_phone 优先于 phone 落电话字段，与桌面端口径一致）
 * - lesson_packages → [LessonPackage]（remaining_lessons 反算 usedLessons；
 *   英文状态 → 中文状态；student_id 经 students 表解析为姓名）
 * - coaches / lessons / checkin_records：Android 无对应实体，跳过并计入报告
 *
 * 兼容性：mode 仅作信息展示不强制校验（Android 端模式 id 与小程序不同源）。
 */
object MiniprogramImporter {

    private const val TAG = "MpImporter"

    /** 解析结果（未落库） */
    data class ImportPlan(
        val mode: String,
        val exportedAt: String,
        val students: List<Student>,
        val packages: List<LessonPackage>,
        /** 软删行数（跳过） */
        val skippedDeleted: Int,
        /** 桌面/手机端无对应实体、被跳过的非空表名 */
        val skippedTables: List<String>
    )

    /** 导入执行结果 */
    data class ImportResult(
        val createdStudents: Int,
        val skippedExistingStudents: Int,
        val createdPackages: Int,
        val skippedDeleted: Int,
        val skippedTables: List<String>
    )

    /**
     * 解析小程序备份 JSON。
     *
     * @throws IllegalArgumentException 结构非法（版本/mode 缺失、JSON 损坏）时抛出，
     *         message 用户可直接展示
     */
    fun parse(json: String): ImportPlan {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw IllegalArgumentException("文件不是有效的小程序备份 JSON", e)
        }
        val version = root.optInt("export_version", 0)
        if (version != 1) {
            throw IllegalArgumentException("不支持的备份版本：$version（期望 1）")
        }
        val mode = root.optString("mode", "")
        if (mode.isBlank()) {
            throw IllegalArgumentException("备份缺少 mode 字段，无法确认数据模式")
        }

        // students：先建 id→姓名 映射（lesson_packages.student_id 依赖）
        val nameById = HashMap<Long, String>()
        val rawStudents = root.optJSONArray("students")
        if (rawStudents != null) {
            for (i in 0 until rawStudents.length()) {
                val row = rawStudents.optJSONObject(i) ?: continue
                val name = row.optString("name", "").trim()
                if (name.isNotEmpty()) nameById[row.optLong("id", -1L)] = name
            }
        }

        val now = System.currentTimeMillis()
        val students = ArrayList<Student>()
        var skippedDeleted = 0
        if (rawStudents != null) {
            for (i in 0 until rawStudents.length()) {
                val row = rawStudents.optJSONObject(i) ?: continue
                if (row.optInt("deleted", 0) != 0) {
                    skippedDeleted++
                    continue
                }
                val name = row.optString("name", "").trim()
                if (name.isEmpty()) continue
                // 电话字段：家长联系方式优先（与桌面端导入口径一致）
                val phone = row.optString("parent_phone", "").ifBlank {
                    row.optString("phone", "")
                }
                students.add(
                    Student(
                        name = name,
                        grade = gradeCodeFromLabel(row.optString("grade", "")) ?: "1",
                        phone = phone,
                        isActive = row.optString("status", "active") == "active",
                        createdAt = parseStampMs(row.optString("created_at", "")) ?: now,
                        updatedAt = parseStampMs(row.optString("updated_at", "")) ?: now
                    )
                )
            }
        }

        // lesson_packages → LessonPackage
        val packages = ArrayList<LessonPackage>()
        val rawPackages = root.optJSONArray("lesson_packages")
        if (rawPackages != null) {
            for (i in 0 until rawPackages.length()) {
                val row = rawPackages.optJSONObject(i) ?: continue
                if (row.optInt("deleted", 0) != 0) continue
                val studentName = nameById[row.optLong("student_id", Long.MIN_VALUE)] ?: continue
                val total = row.optInt("total_lessons", 0)
                val remaining = row.optInt("remaining_lessons", 0).coerceAtLeast(0)
                packages.add(
                    LessonPackage(
                        studentName = studentName,
                        name = "小程序导入",
                        totalLessons = total,
                        usedLessons = (total - remaining).coerceAtLeast(0),
                        price = row.optDouble("price", 0.0),
                        paidAmount = row.optDouble("paid_amount", -1.0),
                        purchaseDate = row.optString("purchase_date", "")
                            .takeIf { it.isNotBlank() }
                            ?: exportedDate(root),
                        expireDate = row.optString("expire_date", ""),
                        status = packageStatusLabel(row.optString("status", "active"))
                    )
                )
            }
        }

        val skippedTables = mutableListOf<String>()
        for (t in listOf("coaches", "lessons", "checkin_records")) {
            if (root.optJSONArray(t)?.length() ?: 0 > 0) skippedTables.add(t)
        }
        return ImportPlan(
            mode = mode,
            exportedAt = root.optString("exported_at", ""),
            students = students,
            packages = packages,
            skippedDeleted = skippedDeleted,
            skippedTables = skippedTables
        )
    }

    /**
     * 执行导入（学员按姓名去重：已存在跳过，新建其余；课时包随新学员写入）。
     *
     * 阻塞调用，须在 IO 线程执行（沿用阻塞 DAO 模式，见 StudentDao v23.6.1 注释）。
     */
    fun execute(context: Context, plan: ImportPlan): ImportResult {
        val db = AppDatabase.getDatabase(context.applicationContext)
        val studentDao = db.studentDao()
        val packageDao = db.lessonPackageDao()
        var created = 0
        var skipped = 0
        var createdPackages = 0
        for (student in plan.students) {
            if (studentDao.getByNameIncludeDeletedBlocking(student.name) != null) {
                skipped++
                continue
            }
            studentDao.insertBlocking(student)
            created++
            plan.packages.filter { it.studentName == student.name }.forEach { pkg ->
                packageDao.insertBlocking(pkg)
                createdPackages++
            }
        }
        Log.i(TAG, "小程序数据导入：新建 $created，跳过 $skipped，课时包 $createdPackages")
        return ImportResult(created, skipped, createdPackages,
            plan.skippedDeleted, plan.skippedTables)
    }

    // ------------------------------------------------------------------
    // 内部：字段映射（与桌面端 miniprogram_bridge 口径一致）
    // ------------------------------------------------------------------

    /** 小程序 grade 中文标签 → Android 年级编码；无法识别返回 null（调用方回退 "1"） */
    fun gradeCodeFromLabel(label: String): String? {
        val t = label.trim()
        if (t.isEmpty()) return null
        Standards.gradeCodeFromLabel(t)?.let { return it }
        // 简写匹配："五年级" → "小学五年级"（去掉学段前缀后比对）
        for ((code, name) in Standards.GRADE_OPTIONS) {
            val shortName = name.removePrefix("小学").removePrefix("初中").removePrefix("高中")
            if (shortName == t) return code
        }
        return null
    }

    /** 小程序套餐状态（英文）→ Android 中文状态 */
    private fun packageStatusLabel(status: String): String = when (status) {
        "exhausted" -> "已用完"
        "expired" -> "已过期"
        else -> "活跃"
    }

    /** "YYYY-MM-DD HH:mm:ss" → 毫秒；解析失败返回 null（调用方回退当前时间） */
    private fun parseStampMs(stamp: String): Long? = runCatching {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
        fmt.timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
        fmt.parse(stamp.trim())?.time
    }.getOrNull()

    /** exported_at 的日期部分，兜底今天 */
    private fun exportedDate(root: JSONObject): String {
        val exported = root.optString("exported_at", "")
        if (exported.length >= 10) return exported.take(10)
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
            .format(java.util.Date())
    }
}
