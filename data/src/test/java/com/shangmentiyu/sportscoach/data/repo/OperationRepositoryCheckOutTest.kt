package com.shangmentiyu.sportscoach.data.repo

import android.content.Context
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import com.shangmentiyu.sportscoach.data.model.Student
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 签退 → 课时包扣减 端到端数据流测试（v50 数据流加固）。
 *
 * 锁定以下不变量（对应审计四阶段）：
 * 1. 正常学员签退：事务内扣减最早活跃课时包 + Lesson 置为已签退 + 回填 packageId
 * 2. 体验课（isTrial=true）：仅记录签退时间，绝不消耗课时包余额
 * 3. 无可用课时包：签退失败且事务整体回滚（Lesson 保持"已签到"，不出现"签退成功但不扣课时"残缺态）
 * 4. 幂等：已签退课时再次调用直接成功返回，不重复扣减
 * 5. 旧数据（packageId 非空 ⟺ 已扣课时）：仅标记签退，不重复扣费，归属记录保留
 * 6. 多课时包：按最早购买（FIFO）精准扣减，不发生错乱
 *
 * 运行方式：./gradlew :data:testDebugUnitTest --tests "com.shangmentiyu.sportscoach.data.repo.OperationRepositoryCheckOutTest"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OperationRepositoryCheckOutTest {

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
        total: Int,
        used: Int,
        purchaseDate: String,
        studentName: String = "张三",
        studentId: String? = "s1"
    ) = LessonPackage(
        id = id,
        studentName = studentName,
        studentId = studentId,
        name = "测试课包",
        totalLessons = total,
        usedLessons = used,
        purchaseDate = purchaseDate
    )

    private fun lesson(
        id: String,
        studentName: String = "张三",
        studentId: String? = "s1",
        status: String = "已签到",
        isTrial: Boolean = false,
        packageId: String = "",
        signOutTime: String = ""
    ) = Lesson(
        id = id,
        date = "2026-08-07",
        time = "10:00",
        studentName = studentName,
        studentId = studentId,
        status = status,
        isTrial = isTrial,
        packageId = packageId,
        signOutTime = signOutTime
    )

    // === 1. 正常学员签退：必须扣减课时包 ===

    @Test
    fun `正常学员签退_扣减最早活跃课时包_状态置为已签退`() = runTest {
        db.studentDao().insert(student())
        db.lessonPackageDao().insert(pkg(id = "p1", total = 10, used = 2, purchaseDate = "2026-01-01"))
        val l = lesson(id = "l1")
        db.lessonDao().insert(l)

        val result = repo.consumeLessonForCheckOut(l)

        assertThat(result.success).isTrue()
        assertThat(result.packageId).isEqualTo("p1")
        assertThat(result.remainingAfter).isEqualTo(7) // 10 - 3
        assertThat(db.lessonPackageDao().getById("p1")!!.usedLessons).isEqualTo(3)
        val after = db.lessonDao().getById("l1")!!
        assertThat(after.status).isEqualTo("已签退")
        assertThat(after.signOutTime).isNotEmpty()
        assertThat(after.packageId).isEqualTo("p1")
    }

    // === 2. 体验课：只记录签退时间，不扣课时包 ===

    @Test
    fun `体验课签退_不扣减课时包_仅记录签退时间`() = runTest {
        db.lessonPackageDao().insert(pkg(id = "p1", total = 10, used = 0, purchaseDate = "2026-01-01"))
        val l = lesson(id = "l1", studentName = "体验学员", studentId = null, isTrial = true)
        db.lessonDao().insert(l)

        val result = repo.consumeLessonForCheckOut(l)

        assertThat(result.success).isTrue()
        assertThat(result.remainingAfter).isEqualTo(0)
        assertThat(db.lessonPackageDao().getById("p1")!!.usedLessons).isEqualTo(0) // 未扣减
        val after = db.lessonDao().getById("l1")!!
        assertThat(after.status).isEqualTo("已签退")
        assertThat(after.signOutTime).isNotEmpty()
    }

    // === 3. 无可用课时包：失败 + 事务整体回滚 ===

    @Test
    fun `无可用课时包_签退失败_事务回滚_Lesson保持已签到`() = runTest {
        db.lessonPackageDao().insert(pkg(id = "p1", total = 2, used = 2, purchaseDate = "2026-01-01"))
        val l = lesson(id = "l1")
        db.lessonDao().insert(l)

        val result = repo.consumeLessonForCheckOut(l)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("无可用课时包")
        val after = db.lessonDao().getById("l1")!!
        assertThat(after.status).isEqualTo("已签到") // 回滚：不出现"签退成功但不扣课时"
        assertThat(after.signOutTime).isEmpty()
        assertThat(db.lessonPackageDao().getById("p1")!!.usedLessons).isEqualTo(2)
    }

    // === 4. 幂等：已签退课时不重复扣减 ===

    @Test
    fun `已签退课时_幂等拦截_不重复扣减`() = runTest {
        db.lessonPackageDao().insert(pkg(id = "p1", total = 10, used = 3, purchaseDate = "2026-01-01"))
        val l = lesson(id = "l1", status = "已签退", packageId = "p1", signOutTime = "10:00")
        db.lessonDao().insert(l)

        val result = repo.consumeLessonForCheckOut(l)

        assertThat(result.success).isTrue()
        assertThat(db.lessonPackageDao().getById("p1")!!.usedLessons).isEqualTo(3) // 未重复扣减
    }

    // === 5. 旧数据（packageId 非空 ⟺ 已扣课时）：仅签退，不重复扣费 ===

    @Test
    fun `旧数据已关联packageId_仅签退不重复扣费_归属保留`() = runTest {
        db.lessonPackageDao().insert(pkg(id = "p1", total = 10, used = 5, purchaseDate = "2026-01-01"))
        val l = lesson(id = "l1", status = "已签到", packageId = "p1")
        db.lessonDao().insert(l)

        val result = repo.consumeLessonForCheckOut(l)

        assertThat(result.success).isTrue()
        assertThat(db.lessonPackageDao().getById("p1")!!.usedLessons).isEqualTo(5) // 未重复扣费
        val after = db.lessonDao().getById("l1")!!
        assertThat(after.status).isEqualTo("已签退")
        assertThat(after.packageId).isEqualTo("p1") // 扣费归属记录保留，undo 可正确恢复
    }

    // === 6. 多课时包：FIFO 精准扣减，不错乱 ===

    @Test
    fun `多课时包_按最早购买优先扣减_不串包`() = runTest {
        db.studentDao().insert(student())
        db.lessonPackageDao().insert(pkg(id = "p_late", total = 30, used = 0, purchaseDate = "2026-06-01"))
        db.lessonPackageDao().insert(pkg(id = "p_early", total = 10, used = 0, purchaseDate = "2026-01-01"))
        val l = lesson(id = "l1")
        db.lessonDao().insert(l)

        val result = repo.consumeLessonForCheckOut(l)

        assertThat(result.success).isTrue()
        assertThat(result.packageId).isEqualTo("p_early")
        assertThat(db.lessonPackageDao().getById("p_early")!!.usedLessons).isEqualTo(1)
        assertThat(db.lessonPackageDao().getById("p_late")!!.usedLessons).isEqualTo(0)
    }
}
