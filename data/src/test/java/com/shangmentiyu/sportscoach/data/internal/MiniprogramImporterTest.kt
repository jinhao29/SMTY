package com.shangmentiyu.sportscoach.data.internal

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 小程序数据导入器锚定测试（阶段五互通）。
 *
 * fixture 与桌面端 test_miniprogram_parity.py::FIXTURE_STUDENT 字段一一对应
 * （勿单端改动）：锁小程序 local-store.js 导出格式 → Android 实体的字段映射。
 * 任一端协议改动未同步，本测试与桌面端锚定测试将同时失败。
 *
 * Robolectric 仅提供 org.json 运行时（local unit test 的 android.jar 是 stub），
 * 不涉及数据库加载。
 */
@RunWith(RobolectricTestRunner::class)
class MiniprogramImporterTest {

    /** 与桌面端 FIXTURE_STUDENT 对应（同一学员，字段值一致） */
    private val fixtureJson = """
    {
      "export_version": 1,
      "mode": "shangmen",
      "exported_at": "2026-09-12 10:00:00",
      "students": [
        {"id": 1, "name": "锚定测试学员", "phone": "13800000001", "parent_phone": "13900000001",
         "grade": "五年级", "class_group": "五年级2班", "address": "某小区某栋",
         "status": "active", "expire_date": "2026-12-31", "note": "跨端锚定",
         "remaining_lessons": 7,
         "created_at": "2026-09-01 10:00:00", "updated_at": "2026-09-12 10:00:00",
         "deleted": 0},
        {"id": 2, "name": "软删学员", "phone": "", "status": "active",
         "remaining_lessons": 0, "deleted": 1}
      ],
      "coaches": [],
      "lessons": [],
      "lesson_packages": [
        {"id": 1, "student_id": 1, "total_lessons": 10, "remaining_lessons": 3,
         "price": 1200.0, "paid_amount": -1, "purchase_date": "2026-09-01",
         "expire_date": "", "status": "active", "deleted": 0}
      ],
      "checkin_records": []
    }
    """.trimIndent()

    @Test
    fun `解析锚定fixture_字段映射正确`() {
        val plan = MiniprogramImporter.parse(fixtureJson)

        assertThat(plan.mode).isEqualTo("shangmen")
        assertThat(plan.skippedDeleted).isEqualTo(1)
        assertThat(plan.skippedTables).isEmpty()

        // 学生：软删行跳过，家长联系方式优先落电话字段，中文年级转编码
        assertThat(plan.students).hasSize(1)
        val stu = plan.students[0]
        assertThat(stu.name).isEqualTo("锚定测试学员")
        assertThat(stu.phone).isEqualTo("13900000001") // parent_phone 优先，与桌面端一致
        assertThat(stu.grade).isEqualTo("5")            // 五年级 → 编码 5
        assertThat(stu.isActive).isTrue()

        // 课时包：remaining 反算 used（10 总 - 3 余 = 7 已用），状态转中文
        assertThat(plan.packages).hasSize(1)
        val pkg = plan.packages[0]
        assertThat(pkg.studentName).isEqualTo("锚定测试学员")
        assertThat(pkg.totalLessons).isEqualTo(10)
        assertThat(pkg.usedLessons).isEqualTo(7)
        assertThat(pkg.price).isEqualTo(1200.0)
        assertThat(pkg.status).isEqualTo("活跃")
        assertThat(pkg.purchaseDate).isEqualTo("2026-09-01")
    }

    @Test
    fun `解析非法输入_抛可读异常`() {
        val e1 = runCatching { MiniprogramImporter.parse("{invalid json") }
            .exceptionOrNull()
        assertThat(e1).isInstanceOf(IllegalArgumentException::class.java)

        val e2 = runCatching {
            MiniprogramImporter.parse("""{"export_version": 2, "mode": "shangmen"}""")
        }.exceptionOrNull()
        assertThat(e2?.message).contains("版本")

        val e3 = runCatching {
            MiniprogramImporter.parse("""{"export_version": 1}""")
        }.exceptionOrNull()
        assertThat(e3?.message).contains("mode")
    }

    @Test
    fun `年级简写映射_兼容小程序标签`() {
        assertThat(MiniprogramImporter.gradeCodeFromLabel("初一")).isEqualTo("7")
        assertThat(MiniprogramImporter.gradeCodeFromLabel("初三")).isEqualTo("9")
        assertThat(MiniprogramImporter.gradeCodeFromLabel("中考")).isEqualTo("13")
        assertThat(MiniprogramImporter.gradeCodeFromLabel("一年级")).isEqualTo("1")
        assertThat(MiniprogramImporter.gradeCodeFromLabel("小学五年级")).isEqualTo("5")
        assertThat(MiniprogramImporter.gradeCodeFromLabel("")).isNull()
        assertThat(MiniprogramImporter.gradeCodeFromLabel("未知年级")).isNull()
    }

    @Test
    fun `非空跳过表被报告`() {
        val withLessons = fixtureJson.replace(
            "\"lessons\": []",
            "\"lessons\": [{\"id\": 1, \"student_ids\": [1]}]"
        )
        val plan = MiniprogramImporter.parse(withLessons)
        assertThat(plan.skippedTables).containsExactly("lessons")
    }
}
