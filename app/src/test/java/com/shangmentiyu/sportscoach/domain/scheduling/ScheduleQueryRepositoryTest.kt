package com.shangmentiyu.sportscoach.domain.scheduling

import android.content.Context
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import com.shangmentiyu.sportscoach.data.model.Schedule
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.data.repo.ScheduleQueryRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [ScheduleQueryRepository] 集成测试（v49 彻底重构，Robolectric + Room 内存库）。
 *
 * 覆盖范围：
 * 1. 场景3：手动排课时额度不足，保存操作应失败（抛 [ScheduleQuotaExceededException] 且不落库）
 * 2. 场景4：[fixHistoricalScheduleErrors] 正确清除超限排课并保留有效排课，清理后重排
 * 3. 日期早于购买日的排课被拒绝（[IllegalArgumentException] 且不落库）
 *
 * 运行方式：./gradlew :app:testDebugUnitTest --tests "com.shangmentiyu.sportscoach.domain.scheduling.ScheduleQueryRepositoryTest"
 */
@RunWith(RobolectricTestRunner::class)
// 显式指定 SDK 34：Robolectric 4.13 最高支持 API 34，避免按 targetSdk(35) 自动选择失败
@Config(sdk = [34])
class ScheduleQueryRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: ScheduleQueryRepository

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = ScheduleQueryRepository(
            scheduleDao = db.scheduleDao(),
            lessonDao = db.lessonDao(),
            pkgDao = db.lessonPackageDao(),
            studentDao = db.studentDao(),
            db = db
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun student(name: String = "张三", studentId: String? = "s1") =
        Student(name = name, studentId = studentId)

    private fun pkg(total: Int, used: Int, purchaseDate: String, studentName: String = "张三", studentId: String? = "s1") =
        LessonPackage(
            studentName = studentName,
            studentId = studentId,
            name = "测试课包",
            totalLessons = total,
            usedLessons = used,
            purchaseDate = purchaseDate
        )

    private fun schedule(id: String, dayOfWeek: Int, startDate: String, studentName: String = "张三", studentId: String? = "s1") =
        Schedule(
            id = id,
            studentName = studentName,
            studentId = studentId,
            dayOfWeek = dayOfWeek,
            startTime = "10:00",
            startDate = startDate,
            isLongTerm = true
        )

    // === 场景3：手动排课时，若额度不足，保存操作应失败 ===

    @Test
    fun `场景3_额度不足保存失败_抛出业务异常且不落库`() = runTest {
        db.studentDao().insert(student())
        // 2 节课时包已全部用完 → 剩余可排课时 = 0
        db.lessonPackageDao().insert(pkg(total = 2, used = 2, purchaseDate = "2025-01-01"))

        val sched = schedule(id = "new_schedule_1", dayOfWeek = 1, startDate = "2026-08-10")
        val result = runCatching { repo.saveSchedule(sched) }

        val e = result.exceptionOrNull()
        assertThat(e).isInstanceOf(ScheduleQuotaExceededException::class.java)
        assertThat(e?.message).contains("额度")
        // 保存失败：排课未写入
        assertThat(db.scheduleDao().getById("new_schedule_1")).isNull()
    }

    @Test
    fun `场景3_额度不足但有已签退占位时同样拦截`() = runTest {
        db.studentDao().insert(student())
        // 总课时 5，已签退 5 → 剩余 0
        db.lessonPackageDao().insert(pkg(total = 5, used = 0, purchaseDate = "2025-01-01"))
        repeat(5) {
            db.lessonDao().insert(
                com.shangmentiyu.sportscoach.data.model.Lesson(
                    id = "lesson_checked_$it",
                    date = "2026-08-01",
                    time = "10:0$it",
                    studentName = "张三",
                    studentId = "s1",
                    lessonType = "训练课(长期自动)",
                    status = "已签退",
                    signOutTime = "11:00"
                )
            )
        }

        val sched = schedule(id = "new_schedule_2", dayOfWeek = 2, startDate = "2026-08-11")
        val result = runCatching { repo.saveSchedule(sched) }

        assertThat(result.exceptionOrNull()).isInstanceOf(ScheduleQuotaExceededException::class.java)
        assertThat(db.scheduleDao().getById("new_schedule_2")).isNull()
    }

    @Test
    fun `场景3_仍有剩余额度时保存成功`() = runTest {
        db.studentDao().insert(student())
        db.lessonPackageDao().insert(pkg(total = 10, used = 3, purchaseDate = "2025-01-01"))

        val sched = schedule(id = "ok_schedule", dayOfWeek = 3, startDate = "2026-08-12")
        val ok = repo.saveSchedule(sched)

        assertThat(ok).isTrue()
        assertThat(db.scheduleDao().getById("ok_schedule")).isNotNull()
    }

    // === 场景1（Repository 层）：日期早于购买日期的排课被拒绝 ===

    @Test
    fun `场景1_日期早于购买日期的排课被拒绝且不落库`() = runTest {
        db.studentDao().insert(student())
        // 首次购买日期 2025-01-01
        db.lessonPackageDao().insert(pkg(total = 10, used = 0, purchaseDate = "2025-01-01"))

        // startDate 早于首次购买日期
        val sched = schedule(id = "early_schedule", dayOfWeek = 4, startDate = "2024-12-01")
        val result = runCatching { repo.saveSchedule(sched) }

        val e = result.exceptionOrNull()
        assertThat(e).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(e?.message).contains("早于")
        assertThat(db.scheduleDao().getById("early_schedule")).isNull()
    }

    // === 场景4：fixHistoricalScheduleErrors 正确清除超限排课并保留有效排课 ===

    @Test
    fun `场景4_清除购买日前排课与超限排课_保留日期靠前的有效排课并重排`() = runTest {
        // 学员 + 5 节活跃课时包（2025-01-01 购买）
        db.studentDao().insert(student())
        db.lessonPackageDao().insert(pkg(total = 5, used = 0, purchaseDate = "2025-01-01"))

        // 通道1 目标：startDate 早于购买日（2024-12-01）
        db.scheduleDao().insert(schedule(id = "s_early", dayOfWeek = 1, startDate = "2024-12-01"))
        // 有效长期模板 6 条：额度 5 → 通道2 应保留日期靠前的 5 条，删除第 6 条（最晚 2025-01-10）
        db.scheduleDao().insert(schedule(id = "s1", dayOfWeek = 1, startDate = "2025-01-05"))
        db.scheduleDao().insert(schedule(id = "s2", dayOfWeek = 2, startDate = "2025-01-06"))
        db.scheduleDao().insert(schedule(id = "s3", dayOfWeek = 3, startDate = "2025-01-07"))
        db.scheduleDao().insert(schedule(id = "s4", dayOfWeek = 4, startDate = "2025-01-08"))
        db.scheduleDao().insert(schedule(id = "s5", dayOfWeek = 5, startDate = "2025-01-09"))
        db.scheduleDao().insert(schedule(id = "s6", dayOfWeek = 1, startDate = "2025-01-10"))

        val result = repo.fixHistoricalScheduleErrors()

        // 通道1 删除 1 条（s_early）+ 通道2 删除 1 条（s6）= 共 2 条
        assertThat(result.deletedSchedules).isEqualTo(2)
        // 保留日期靠前的有效排课：s1~s5
        val remaining = db.scheduleDao().getAllOnce().map { it.id }.toSet()
        assertThat(remaining).isEqualTo(setOf("s1", "s2", "s3", "s4", "s5"))
        // 清理后重排：剩余额度 5 节 → 生成 5 条未来占位课时
        assertThat(result.regeneratedLessons).isEqualTo(5)
        val lessons = db.lessonDao().getAllOnce()
        assertThat(lessons).hasSize(5)
        lessons.forEach { lesson ->
            assertThat(lesson.lessonType).contains("长期自动")
            assertThat(lesson.studentName).isEqualTo("张三")
        }
    }

    @Test
    fun `场景4_无错误数据时不影响有效排课`() = runTest {
        db.studentDao().insert(student())
        db.lessonPackageDao().insert(pkg(total = 5, used = 0, purchaseDate = "2025-01-01"))
        // 只有 3 条有效长期模板（未超额度）
        db.scheduleDao().insert(schedule(id = "a1", dayOfWeek = 1, startDate = "2025-01-05"))
        db.scheduleDao().insert(schedule(id = "a2", dayOfWeek = 2, startDate = "2025-01-06"))
        db.scheduleDao().insert(schedule(id = "a3", dayOfWeek = 3, startDate = "2025-01-07"))

        val result = repo.fixHistoricalScheduleErrors()

        assertThat(result.deletedSchedules).isEqualTo(0)
        val remaining = db.scheduleDao().getAllOnce().map { it.id }.toSet()
        assertThat(remaining).isEqualTo(setOf("a1", "a2", "a3"))
        // 额度 5 中已有 0 条占位 → 生成 5 条
        assertThat(result.regeneratedLessons).isEqualTo(5)
    }

    @Test
    fun `场景4_重复修正幂等_不会重复生成占位`() = runTest {
        db.studentDao().insert(student())
        db.lessonPackageDao().insert(pkg(total = 5, used = 0, purchaseDate = "2025-01-01"))
        db.scheduleDao().insert(schedule(id = "s1", dayOfWeek = 1, startDate = "2025-01-05"))
        db.scheduleDao().insert(schedule(id = "s2", dayOfWeek = 2, startDate = "2025-01-06"))
        db.scheduleDao().insert(schedule(id = "s3", dayOfWeek = 3, startDate = "2025-01-07"))
        db.scheduleDao().insert(schedule(id = "s4", dayOfWeek = 4, startDate = "2025-01-08"))
        db.scheduleDao().insert(schedule(id = "s5", dayOfWeek = 5, startDate = "2025-01-09"))

        repo.fixHistoricalScheduleErrors()
        val first = db.lessonDao().getAllOnce().size
        assertThat(first).isEqualTo(5)

        // 第二次修正：占位 5 条已占用全部额度，不应再生成
        val second = repo.fixHistoricalScheduleErrors()
        assertThat(second.regeneratedLessons).isEqualTo(0)
        assertThat(db.lessonDao().getAllOnce()).hasSize(5)
    }

    // === v49 体验课 ===

    @Test
    fun `体验课_不选学员输入姓名保存成功`() = runTest {
        // 未注册体验学员：无学员记录、无课时包
        val trial = Schedule(
            id = "trial_sched_1",
            studentName = "小明（体验）",
            studentId = null,
            dayOfWeek = 1,
            startTime = "10:00",
            startDate = "2026-08-10",
            isTrial = true
        )
        val ok = repo.saveSchedule(trial)

        assertThat(ok).isTrue()
        val saved = db.scheduleDao().getById("trial_sched_1")
        assertThat(saved).isNotNull()
        assertThat(saved!!.isTrial).isTrue()
        assertThat(saved.studentId).isNull()
        assertThat(saved.studentName).isEqualTo("小明（体验）")
    }

    @Test
    fun `体验课_不消耗课时包余额`() = runTest {
        db.studentDao().insert(student())
        // 仅 1 节课时包（额度 1）
        db.lessonPackageDao().insert(pkg(total = 1, used = 0, purchaseDate = "2025-01-01"))

        // 体验课保存成功（额度不受影响）
        val trial = schedule(id = "trial_sched", dayOfWeek = 1, startDate = "2026-08-10")
            .copy(isTrial = true, studentId = null)
        val ok = repo.saveSchedule(trial)
        assertThat(ok).isTrue()

        // 常规排课仍可保存（额度未被体验课占用）
        val ok2 = repo.saveSchedule(schedule(id = "normal_sched", dayOfWeek = 2, startDate = "2026-08-11"))
        assertThat(ok2).isTrue()

        // 课时包余额未被扣减
        val pkgAfter = db.lessonPackageDao().getAllOnce().first()
        assertThat(pkgAfter.usedLessons).isEqualTo(0)
        assertThat(pkgAfter.remainingLessons).isEqualTo(1)
    }

    @Test
    fun `体验课与正常排课共存_剩余额度计算排除体验课`() = runTest {
        db.studentDao().insert(student())
        db.lessonPackageDao().insert(pkg(total = 5, used = 0, purchaseDate = "2025-01-01"))

        // 常规排课占位 3 条（isTrial=false，计入待消耗）
        repeat(3) {
            db.lessonDao().insert(
                com.shangmentiyu.sportscoach.data.model.Lesson(
                    id = "normal_placeholder_$it",
                    date = "2026-08-1$it",  // 08-10 / 08-11 / 08-12
                    time = "10:00",
                    studentName = "张三",
                    studentId = "s1",
                    lessonType = "训练课(长期自动)",
                    isTrial = false
                )
            )
        }
        // 体验课占位 2 条（isTrial=true，不计入待消耗）
        repeat(2) {
            db.lessonDao().insert(
                com.shangmentiyu.sportscoach.data.model.Lesson(
                    id = "trial_placeholder_$it",
                    date = "2026-08-2$it",  // 08-20 / 08-21
                    time = "10:00",
                    studentName = "张三",
                    studentId = null,
                    lessonType = "训练课(长期自动)",
                    isTrial = true
                )
            )
        }
        // 常规已签退 1 条（计入已消耗）
        db.lessonDao().insert(
            com.shangmentiyu.sportscoach.data.model.Lesson(
                id = "checked_out",
                date = "2026-08-01",
                time = "09:00",
                studentName = "张三",
                studentId = "s1",
                lessonType = "训练课",
                status = "已签退",
                signOutTime = "11:00",
                isTrial = false
            )
        )

        // 剩余可排课时 = 总课时 5 - 已消耗(常规已签退 1) - 待消耗(常规占位 3，体验课占位不计) = 1
        val useCase = ValidateScheduleUseCase(repo)
        assertThat(useCase.availableQuota("张三", "2026-08-10")).isEqualTo(1)
    }
}
