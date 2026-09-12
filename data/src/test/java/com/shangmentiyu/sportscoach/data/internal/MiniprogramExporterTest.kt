package com.shangmentiyu.sportscoach.data.internal

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 小程序数据导出器测试（阶段五互通遗留 #1）。
 *
 * 锚定策略：
 * 1. 导出 → [MiniprogramImporter.parse] 往返：字段无损（同协议自洽）；
 * 2. fixture 与桌面端 test_miniprogram_parity.py::FIXTURE_STUDENT 对齐：
 *    Android 实体 → 导出 JSON 的字段值应与桌面端桥导出的协议字段一致。
 * Robolectric 仅提供 org.json 运行时，不涉及数据库。
 */
@RunWith(RobolectricTestRunner::class)
class MiniprogramExporterTest {

    /** 与桌面端 FIXTURE_STUDENT 语义对应的 Android 实体（五年级学员，10 次卡剩 3） */
    private val student = com.shangmentiyu.sportscoach.data.model.Student(
        name = "锚定测试学员",
        grade = "5",
        phone = "13900000001",
    )
    private val pkg = com.shangmentiyu.sportscoach.data.model.LessonPackage(
        id = "pkg00001",
        studentName = "锚定测试学员",
        name = "10次卡",
        totalLessons = 10,
        usedLessons = 7,
        price = 1200.0,
        paidAmount = -1.0,
        purchaseDate = "2026-09-01",
        expireDate = "",
        status = "活跃",
    )

    @Test
    fun `导出往返_被导入器无损解析`() {
        val json = MiniprogramExporter.export("shangmen", listOf(student), listOf(pkg))
        val plan = MiniprogramImporter.parse(json)

        assertThat(plan.mode).isEqualTo("shangmen")
        assertThat(plan.students).hasSize(1)
        val parsed = plan.students[0]
        assertThat(parsed.name).isEqualTo("锚定测试学员")
        assertThat(parsed.grade).isEqualTo("5")           // 中文标签 → 编码往返无损
        assertThat(parsed.phone).isEqualTo("13900000001")
        assertThat(parsed.isActive).isTrue()

        assertThat(plan.packages).hasSize(1)
        val parsedPkg = plan.packages[0]
        assertThat(parsedPkg.studentName).isEqualTo("锚定测试学员")
        assertThat(parsedPkg.totalLessons).isEqualTo(10)
        assertThat(parsedPkg.usedLessons).isEqualTo(7)
        assertThat(parsedPkg.price).isEqualTo(1200.0)
        assertThat(parsedPkg.status).isEqualTo("活跃")
    }

    @Test
    fun `导出结构与协议锚定一致`() {
        val json = MiniprogramExporter.export("club", listOf(student), listOf(pkg))
        val root = org.json.JSONObject(json)

        assertThat(root.optInt("export_version")).isEqualTo(1)
        assertThat(root.optString("mode")).isEqualTo("club")
        // 五表齐全（coaches/lessons/checkins 为空数组 → 小程序导入端 no-op）
        for (table in listOf("students", "coaches", "lessons", "lesson_packages", "checkin_records")) {
            assertThat(root.optJSONArray(table)).isNotNull()
        }
        assertThat(root.optJSONArray("coaches")!!.length()).isEqualTo(0)

        val row = root.optJSONArray("students")!!.getJSONObject(0)
        // 桌面端桥导出的学生字段全集逐字段对齐
        for (field in listOf("id", "name", "phone", "parent_phone", "grade", "class_group",
                             "address", "status", "expire_date", "note", "remaining_lessons",
                             "created_at", "updated_at", "deleted")) {
            assertThat(row.has(field)).isTrue()
        }
        // 剩余课时 = 包剩余（10-7=3），与桌面端口径一致
        assertThat(row.optInt("remaining_lessons")).isEqualTo(3)
        assertThat(row.optString("grade")).isEqualTo("五年级")  // 编码 → 中文标签
        assertThat(row.optString("status")).isEqualTo("active")
    }

    @Test
    fun `模式映射_coaching到shangmen_club_evolve到club`() {
        assertThat(MiniprogramExporter.miniprogramModeId("coaching")).isEqualTo("shangmen")
        assertThat(MiniprogramExporter.miniprogramModeId("club_evolve")).isEqualTo("club")
        assertThat(MiniprogramExporter.miniprogramModeId("未知模式")).isNull()
        assertThat(MiniprogramExporter.allMiniprogramModes()).containsExactly("shangmen", "club")
    }

    @Test
    fun `空模式拒绝导出`() {
        val e = runCatching { MiniprogramExporter.export("", listOf(student), emptyList()) }
            .exceptionOrNull()
        assertThat(e).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `无主课时包不导出`() {
        val deleted = student.copy(name = "软删学员", isActive = false)
        // studentName 不在导出列表中的包（无主）必须过滤，避免小程序端悬空引用
        val orphanPkg = pkg.copy(id = "pkg00002", studentName = "不存在学员")
        val json = MiniprogramExporter.export("shangmen", listOf(student, deleted),
                                              listOf(pkg, orphanPkg))
        val root = org.json.JSONObject(json)
        assertThat(root.optJSONArray("students")!!.length()).isEqualTo(2)  // 软删学员仍导出（inactive）
        assertThat(root.optJSONArray("lesson_packages")!!.length()).isEqualTo(1)
    }
}
