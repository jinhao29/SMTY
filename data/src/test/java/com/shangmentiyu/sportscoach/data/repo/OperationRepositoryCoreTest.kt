package com.shangmentiyu.sportscoach.data.repo

import android.content.Context
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import com.shangmentiyu.sportscoach.data.model.Student
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * OperationRepository 核心业务逻辑测试（v51 质量债补测）。
 *
 * 覆盖三大核心域（消课签退已由 OperationRepositoryCheckOutTest 覆盖）：
 * 1. 余额计算：getRemainingSummary 活跃包累加 + 排除已用完/已过期；
 *    getEffectiveRemainingLessons 按日期生效过滤（购买日之前=0、过期日之后=0）
 * 2. 撤销签到：undoCheckIn 恢复 usedLessons、"已用完"状态复活、
 *    无关联包仅删记录、学员不匹配拒绝、usedLessons=0 不减为负
 * 3. 批量排课：batchAutoSchedule 只占位不扣余额、状态"待签到"、
 *    重复日期跳过、全重复不报错
 *
 * 运行方式：./gradlew :data:testDebugUnitTest --tests "com.shangmentiyu.sportscoach.data.repo.OperationRepositoryCoreTest"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OperationRepositoryCoreTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: OperationRepository

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = OperationRepository(
            pkgDao = db.lessonPackageDao(),
            coachDao = db.coachDao(),
            lessonDao = db.lessonDao(),
            studentDao = db.studentDao(),
            archivedLessonDao = null,
            db = db,
            scheduleRepo = ScheduleRepository(db.scheduleDao(), db.lessonDao()),
            scheduleQueryRepo = ScheduleQueryRepository(
                db.scheduleDao(), db.lessonDao(), db.lessonPackageDao(), db.studentDao(), db
            ),
            trainingCycleRepo = TrainingCycleRepository(db.trainingCycleDao()),
            stageSummaryRepo = StageSummaryRepository(),
            signInDao = db.signInDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun student(name: String = "张三", studentId: String? = "s1") =
        Student(name = name, studentId = studentId)

    private fun pkg(
        id: String,
        name: String = "测试课包",
        total: Int,
        used: Int,
        purchaseDate: String,
        expireDate: String = "",
        status: String = "活跃",
        studentName: String = "张三",
        studentId: String? = "s1"
    ) = LessonPackage(
        id = id,
        studentName = studentName,
        studentId = studentId,
        name = name,
        totalLessons = total,
        usedLessons = used,
        purchaseDate = purchaseDate,
        expireDate = expireDate,
        status = status
    )

    private fun lesson(
        id: String,
        date: String = "2026-08-10",
        time: String = "10:00",
        studentName: String = "张三",
        studentId: String? = "s1",
        status: String = "已签到",
        packageId: String = ""
    ) = Lesson(
        id = id,
        date = date,
        time = time,
        studentName = studentName,
        studentId = studentId,
        status = status,
        isTrial = false,
        packageId = packageId
    )

    // ================================================================
    // 1. 余额计算
    // ================================================================

    @Test
    fun `余额汇总_仅累加活跃包_排除已用完与已过期`() = runTest {
        db.studentDao().insert(student())
        db.lessonPackageDao().insert(pkg(id = "p_early", name = "早包", total = 10, used = 2, purchaseDate = "2026-01-01"))
        db.lessonPackageDao().insert(pkg(id = "p_late", name = "晚包", total = 5, used = 0, purchaseDate = "2026-05-01"))
        // 已用完：remainingLessons=0，即使 status 仍为"活跃"也排除
        db.lessonPackageDao().insert(pkg(id = "p_used_up", total = 4, used = 4, purchaseDate = "2026-02-01"))
        // 已过期：expireDate 早于今天
        db.lessonPackageDao().insert(pkg(id = "p_expired", total = 8, used = 0, purchaseDate = "2025-01-01", expireDate = "2025-06-01"))
        // 已退费：status 非"活跃"
        db.lessonPackageDao().insert(pkg(id = "p_refunded", total = 6, used = 1, purchaseDate = "2026-03-01", status = "已退费"))

        val summary = repo.getRemainingSummary("张三")

        assertThat(summary.studentName).isEqualTo("张三")
        assertThat(summary.totalRemaining).isEqualTo(13) // (10-2) + (5-0)
        assertThat(summary.activePackageName).isEqualTo("早包") // 最早购买的活跃包
    }

    @Test
    fun `余额汇总_无任何课时包_返回零余额`() = runTest {
        val summary = repo.getRemainingSummary("李四")

        assertThat(summary.totalRemaining).isEqualTo(0)
        assertThat(summary.activePackageName).isEmpty()
    }

    @Test
    fun `按日期有效余额_购买日期之前为0_之后正常`() = runTest {
        db.studentDao().insert(student())
        db.lessonPackageDao().insert(pkg(id = "p1", total = 10, used = 0, purchaseDate = "2026-03-01"))

        // 排课日期早于购买日期：课包尚未生效
        assertThat(repo.getEffectiveRemainingLessons("张三", "2026-02-21")).isEqualTo(0)
        // 排课日期等于购买日期：生效
        assertThat(repo.getEffectiveRemainingLessons("张三", "2026-03-01")).isEqualTo(10)
        // 排课日期晚于购买日期：生效
        assertThat(repo.getEffectiveRemainingLessons("张三", "2026-03-25")).isEqualTo(10)
    }

    @Test
    fun `按日期有效余额_过期日期之后为0`() = runTest {
        db.studentDao().insert(student())
        // expireDate 必须晚于今天，否则包整体被判已过期（getActivePackagesByStudent 过滤）
        db.lessonPackageDao().insert(
            pkg(id = "p1", total = 10, used = 0, purchaseDate = "2026-01-01", expireDate = "2026-12-31")
        )

        assertThat(repo.getEffectiveRemainingLessons("张三", "2026-12-30")).isEqualTo(10)
        assertThat(repo.getEffectiveRemainingLessons("张三", "2027-01-02")).isEqualTo(0)
    }

    // ================================================================
    // 2. 撤销签到（undoCheckIn）
    // ================================================================

    @Test
    fun `撤销签到_恢复课时包usedLessons_已用完状态复活为活跃`() = runTest {
        db.lessonPackageDao().insert(pkg(id = "p1", total = 2, used = 2, purchaseDate = "2026-01-01"))
        val l = lesson(id = "l1", packageId = "p1")
        db.lessonDao().insert(l)

        val result = repo.undoCheckIn("l1", "张三")

        assertThat(result.success).isTrue()
        assertThat(result.restoredPackageId).isEqualTo("p1")
        assertThat(result.remainingAfter).isEqualTo(1)
        assertThat(db.lessonDao().getById("l1")).isNull() // Lesson 已物理删除
        val after = db.lessonPackageDao().getById("p1")!!
        assertThat(after.usedLessons).isEqualTo(1)
        assertThat(after.status).isEqualTo("活跃") // 原"已用完"复活
    }

    @Test
    fun `撤销签到_长期占位课时无关联包_仅删除Lesson`() = runTest {
        val l = lesson(id = "l1", packageId = "")
        db.lessonDao().insert(l)

        val result = repo.undoCheckIn("l1", "张三")

        assertThat(result.success).isTrue()
        assertThat(result.message).contains("未扣减课时")
        assertThat(db.lessonDao().getById("l1")).isNull()
    }

    @Test
    fun `撤销签到_学员不匹配_拒绝执行`() = runTest {
        val l = lesson(id = "l1", packageId = "p1")
        db.lessonDao().insert(l)

        val result = repo.undoCheckIn("l1", "王五")

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("学员不匹配")
        assertThat(db.lessonDao().getById("l1")).isNotNull() // 记录未被删除
    }

    @Test
    fun `撤销签到_课时包used为0_不出现负数`() = runTest {
        db.lessonPackageDao().insert(pkg(id = "p1", total = 5, used = 0, purchaseDate = "2026-01-01"))
        // 旧数据异常态：Lesson 关联了包但包 used=0
        val l = lesson(id = "l1", packageId = "p1")
        db.lessonDao().insert(l)

        val result = repo.undoCheckIn("l1", "张三")

        assertThat(result.success).isTrue()
        assertThat(db.lessonPackageDao().getById("p1")!!.usedLessons).isEqualTo(0)
    }

    @Test
    fun `撤销签到_记录不存在_返回失败`() = runTest {
        val result = repo.undoCheckIn("not_exist", "张三")

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("不存在")
    }

    // ================================================================
    // 3. 批量排课（batchAutoSchedule）
    // ================================================================

    @Test
    fun `批量排课_仅占位不扣余额_状态待签到`() = runTest {
        db.studentDao().insert(student())
        db.lessonPackageDao().insert(pkg(id = "p1", total = 10, used = 0, purchaseDate = "2026-01-01"))
        val dates = listOf(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 8))

        val result = repo.batchAutoSchedule(
            lessonDates = dates,
            studentName = "张三",
            studentId = "s1",
            coachName = "王教练",
            startTime = "10:00",
            durationMinutes = 60,
            location = "一号场地",
            lessonType = "训练课"
        )

        assertThat(result.success).isTrue()
        assertThat(result.createdCount).isEqualTo(3)
        assertThat(result.skippedCount).isEqualTo(0)

        // 排课与消课分离：不扣余额
        assertThat(db.lessonPackageDao().getById("p1")!!.usedLessons).isEqualTo(0)

        val lessons = db.lessonDao().getByStudent("张三").first()
        assertThat(lessons).hasSize(3)
        lessons.forEach { l ->
            assertThat(l.status).isEqualTo("待签到")
            assertThat(l.packageId).isEmpty() // packageId 留空，签退时统一扣费
            assertThat(l.time).isEqualTo("10:00")
            assertThat(l.coach).isEqualTo("王教练")
        }
    }

    @Test
    fun `批量排课_重复日期跳过_只插入新增日期`() = runTest {
        db.studentDao().insert(student())
        // 已有 9月1日 10:00 的排课
        db.lessonDao().insert(lesson(id = "exist1", date = "2026-09-01", time = "10:00"))
        val dates = listOf(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3))

        val result = repo.batchAutoSchedule(
            lessonDates = dates,
            studentName = "张三",
            studentId = "s1",
            coachName = "王教练",
            startTime = "10:00",
            durationMinutes = 60,
            location = "一号场地",
            lessonType = "训练课"
        )

        assertThat(result.success).isTrue()
        assertThat(result.createdCount).isEqualTo(1)
        assertThat(result.skippedCount).isEqualTo(1)
        assertThat(result.message).contains("跳过")
        assertThat(db.lessonDao().getByStudent("张三").first()).hasSize(2)
    }

    @Test
    fun `批量排课_全部重复_成功返回且不报错`() = runTest {
        db.studentDao().insert(student())
        db.lessonDao().insert(lesson(id = "exist1", date = "2026-09-01", time = "10:00"))
        db.lessonDao().insert(lesson(id = "exist2", date = "2026-09-03", time = "10:00"))
        val dates = listOf(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3))

        val result = repo.batchAutoSchedule(
            lessonDates = dates,
            studentName = "张三",
            studentId = "s1",
            coachName = "王教练",
            startTime = "10:00",
            durationMinutes = 60,
            location = "一号场地",
            lessonType = "训练课"
        )

        assertThat(result.success).isTrue()
        assertThat(result.createdCount).isEqualTo(0)
        assertThat(result.skippedCount).isEqualTo(2)
        assertThat(result.message).contains("均已排课")
        assertThat(db.lessonDao().getByStudent("张三").first()).hasSize(2) // 无新增
    }

    @Test
    fun `批量排课_同时段不同时间_不算重复`() = runTest {
        db.studentDao().insert(student())
        // 9月1日已有 14:00 的课，10:00 不冲突
        db.lessonDao().insert(lesson(id = "exist1", date = "2026-09-01", time = "14:00"))
        val dates = listOf(LocalDate.of(2026, 9, 1))

        val result = repo.batchAutoSchedule(
            lessonDates = dates,
            studentName = "张三",
            studentId = "s1",
            coachName = "王教练",
            startTime = "10:00",
            durationMinutes = 60,
            location = "一号场地",
            lessonType = "训练课"
        )

        assertThat(result.success).isTrue()
        assertThat(result.createdCount).isEqualTo(1)
        assertThat(result.skippedCount).isEqualTo(0)
    }

    @Test
    fun `批量排课_空日期列表_直接失败`() = runTest {
        val result = repo.batchAutoSchedule(
            lessonDates = emptyList(),
            studentName = "张三",
            studentId = "s1",
            coachName = "王教练",
            startTime = "10:00",
            durationMinutes = 60,
            location = "一号场地",
            lessonType = "训练课"
        )

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("为空")
    }
}
