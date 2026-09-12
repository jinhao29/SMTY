package com.shangmentiyu.sportscoach.data.internal

import android.content.Context
import android.util.Log
import com.shangmentiyu.sportscoach.core.Standards
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import com.shangmentiyu.sportscoach.data.model.Student
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 小程序数据导出器（阶段五互通遗留 #1，处理层）。
 *
 * 生成 miniprogram/src/utils/local-store.js exportBackup v1 格式 JSON，
 * 与桌面端 data_center/miniprogram_bridge.py 完全同构（同一协议三端锚定）：
 * - students：姓名主键转自增 id；grade 编码 → 中文标签；电话单字段落 phone
 * - lesson_packages：remainingLessons 原样导出；中文状态 → 英文状态
 * - coaches / lessons / checkin_records：空数组（小程序导入端 no-op，不破坏其本地数据）
 *
 * ⚠️ mode 不同源：Android 模式 id（coaching/club_evolve）≠ 小程序模式 id
 * （shangmen/club）。导出必须按 [miniprogramModeId] 映射后填小程序侧 id，
 * 否则小程序 importBackup 的 mode 校验会拒绝。
 *
 * 往返保证：本类输出可直接被 [MiniprogramImporter.parse] 消费（单测锁定），
 * 也可被小程序「导入备份」与桌面端「导入小程序数据(JSON)」直接使用。
 */
object MiniprogramExporter {

    private const val TAG = "MpExporter"

    /** Android 模式 id → 小程序模式 id（协议不同源，导出前必须映射） */
    private val MODE_MAPPING = mapOf(
        "coaching" to "shangmen",
        "club_evolve" to "club",
    )

    /** 导出报告 */
    data class ExportResult(
        val mode: String,
        val students: Int,
        val packages: Int
    )

    /** Android 模式 id 对应的小程序模式 id；未知模式返回 null（调用方让用户手选） */
    fun miniprogramModeId(androidModeId: String): String? =
        MODE_MAPPING[androidModeId?.trim() ?: ""]

    /** 全部可选的小程序模式 id（UI 选择项） */
    fun allMiniprogramModes(): List<String> = MODE_MAPPING.values.distinct()

    /**
     * 导出为 exportBackup v1 格式 JSON 字符串（纯函数，单测锚定往返）。
     *
     * @param miniprogramMode 小程序侧模式 id（shangmen / club）
     * @param students 学员列表（通常为活跃学员）
     * @param packages 课时包列表（studentName 需能对上 students）
     */
    fun export(miniprogramMode: String,
               students: List<Student>,
               packages: List<LessonPackage>): String {
        require(miniprogramMode.isNotBlank()) { "必须指定小程序模式（shangmen / club）" }

        val now = System.currentTimeMillis()
        val studentRows = JSONArray()
        val idByName = HashMap<String, Long>()
        students.forEachIndexed { i, stu ->
            val rowId = (i + 1).toLong()
            idByName[stu.name] = rowId
            studentRows.put(JSONObject().apply {
                put("id", rowId)
                put("name", stu.name)
                put("phone", stu.phone)
                put("parent_phone", "")   // Android 无家长电话独立字段
                put("grade", Standards.gradeLabel(stu.grade))  // 编码 → 中文标签
                put("class_group", "")
                put("address", "")
                put("status", if (stu.isActive) "active" else "inactive")
                put("expire_date", "")
                put("note", "")
                put("remaining_lessons", effectiveRemaining(stu.name, packages))
                put("created_at", stampMs(stu.createdAt))
                put("updated_at", stampMs(stu.updatedAt))
                put("deleted", 0)
            })
        }

        val packageRows = JSONArray()
        packages.filter { idByName.containsKey(it.studentName) }.forEachIndexed { i, pkg ->
            packageRows.put(JSONObject().apply {
                put("id", (i + 1).toLong())
                put("student_id", idByName[pkg.studentName])
                put("total_lessons", pkg.totalLessons)
                put("remaining_lessons", pkg.remainingLessons)
                put("price", pkg.price)
                put("paid_amount", pkg.paidAmount)
                put("purchase_date", pkg.purchaseDate)
                put("expire_date", pkg.expireDate)
                put("status", packageStatusKey(pkg.status))
                put("deleted", 0)
            })
        }

        return JSONObject().apply {
            put("export_version", 1)
            put("mode", miniprogramMode)
            put("exported_at", stampMs(now))
            put("students", studentRows)
            put("coaches", JSONArray())
            put("lessons", JSONArray())
            put("lesson_packages", packageRows)
            put("checkin_records", JSONArray())
        }.toString(2)
    }

    /**
     * 从数据库导出并写入目标流（IO 线程调用）。
     *
     * 学员取活跃列表；课时包按导出学员过滤（软删学员的包不导出）。
     */
    fun execute(context: Context, miniprogramMode: String, output: java.io.OutputStream): ExportResult {
        val db = AppDatabase.getDatabase(context.applicationContext)
        val (students, packages) = runBlocking {
            val students = db.studentDao().getAll().first()
            val packages = db.lessonPackageDao().getAllOnce()
                .filter { p -> students.any { it.name == p.studentName } }
            students to packages
        }
        val json = export(miniprogramMode, students, packages)
        output.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        Log.i(TAG, "小程序数据导出：mode=$miniprogramMode，学员 ${students.size}，课时包 ${packages.size}")
        return ExportResult(miniprogramMode, students.size, packages.size)
    }

    // ------------------------------------------------------------------
    // 内部：字段映射（与桌面端 miniprogram_bridge 口径一致）
    // ------------------------------------------------------------------

    /** 学员有效剩余课时 = 活跃课时包剩余之和（与续费提醒口径一致） */
    private fun effectiveRemaining(name: String, packages: List<LessonPackage>): Int =
        packages.filter { it.studentName == name && it.status != "已退费" }
            .sumOf { it.remainingLessons }

    /** Android 中文状态 → 小程序英文状态 */
    private fun packageStatusKey(status: String): String = when (status) {
        "已用完" -> "exhausted"
        "已过期" -> "expired"
        else -> "active"
    }

    /** 毫秒 → 小程序时间戳格式（YYYY-MM-DD HH:mm:ss） */
    private fun stampMs(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(ms))
}
