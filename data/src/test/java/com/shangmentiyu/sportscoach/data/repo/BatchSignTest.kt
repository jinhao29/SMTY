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
 * 小班课批量签到/签退最小自检（同 date+time+location 分组，经 [OperationRepository] 门面调用）。
 *
 * 锁定三个不变量（对应需求三种场景）：
 * 1. 正常场景：同组学员全部可操作 → 全部成功（签到翻转状态；签退扣减各自课时包）
 * 2. 部分已操作：状态不符学员自动跳过并正确计数，不报错
 * 3. 全部已操作：全部跳过，幂等不重复扣减课时包
 *
 * 运行方式：./gradlew :data:testDebugUnitTest --tests "com.shangmentiyu.sportscoach.data.repo.BatchSignTest"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BatchSignTest {

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
            signInDao = db.signInDao(),
            consumptionRepo = LessonConsumptionRepository(
                db.lessonDao(), db.lessonPackageDao(), db.signInDao(), db
            ),
            batchScheduleRepo = BatchScheduleRepository(
                db.lessonDao(), db.lessonPackageDao(), db
            ),
            archiveRepo = LessonArchiveRepository(db.lessonDao(), null, db)
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun lesson(
        id: String,
        studentName: String,
        studentId: String,
        status: String = "待签到",
        signOutTime: String = "",
        location: String = "体能馆"
    ) = Lesson(
        id = id,
        date = "2026-08-20",
        time = "10:00",
        location = location,
        studentName = studentName,
        studentId = studentId,
        status = status,
        signOutTime = signOutTime
    )

    private fun pkg(id: String, studentName: String, studentId: String, used: Int = 0) =
        LessonPackage(
            id = id,
            studentName = studentName,
            studentId = studentId,
            name = "小班课包",
            totalLessons = 10,
            usedLessons = used,
            purchaseDate = "2026-01-01"
        )

    // === 批量签到 ===

    @Test
    fun `批量签到正常场景_全部待签到_全部成功翻转已签到`() = runTest {
        db.lessonDao().insert(lesson("l1", "张三", "s1"))
        db.lessonDao().insert(lesson("l2", "李四", "s2"))
        db.lessonDao().insert(lesson("l3", "王五", "s3"))

        val r = repo.batchSignIn(listOf("l1", "l2", "l3"))

        assertThat(r.successCount).isEqualTo(3)
        assertThat(r.skippedCount).isEqualTo(0)
        assertThat(r.checkedOutCount).isEqualTo(0)
        assertThat(r.failedCount).isEqualTo(0)
        assertThat(r.total).isEqualTo(3)
        listOf("l1", "l2", "l3").forEach {
            assertThat(db.lessonDao().getById(it)!!.status).isEqualTo("已签到")
            assertThat(db.signInDao().countByLessonAndType(it, "签到")).isEqualTo(1)
        }
    }

    @Test
    fun `批量签到部分已操作_待签到成功_已签到已签退跳过`() = runTest {
        db.lessonDao().insert(lesson("l1", "张三", "s1"))
        db.lessonDao().insert(lesson("l2", "李四", "s2", status = "已签到"))
        db.lessonDao().insert(lesson("l3", "王五", "s3", status = "已签退", signOutTime = "11:00"))

        val r = repo.batchSignIn(listOf("l1", "l2", "l3"))

        assertThat(r.successCount).isEqualTo(1)
        assertThat(r.skippedCount).isEqualTo(1)
        assertThat(r.checkedOutCount).isEqualTo(1)
        assertThat(r.failedCount).isEqualTo(0)
        assertThat(db.lessonDao().getById("l1")!!.status).isEqualTo("已签到")
        assertThat(db.signInDao().countByLessonAndType("l2", "签到")).isEqualTo(0)
        assertThat(db.signInDao().countByLessonAndType("l3", "签到")).isEqualTo(0)
    }

    @Test
    fun `批量签到全部已操作_全部跳过不报错`() = runTest {
        db.lessonDao().insert(lesson("l1", "张三", "s1", status = "已签退", signOutTime = "11:00"))
        db.lessonDao().insert(lesson("l2", "李四", "s2", status = "已签退", signOutTime = "11:00"))
        db.lessonDao().insert(lesson("l3", "王五", "s3", status = "已签退", signOutTime = "11:00"))

        val r = repo.batchSignIn(listOf("l1", "l2", "l3"))

        assertThat(r.successCount).isEqualTo(0)
        assertThat(r.checkedOutCount).isEqualTo(3)
        assertThat(r.failedCount).isEqualTo(0)
    }

    @Test
    fun `批量签到含不存在ID_计入失败不影响他人`() = runTest {
        db.lessonDao().insert(lesson("l1", "张三", "s1"))
        db.lessonDao().insert(lesson("l2", "李四", "s2"))

        val r = repo.batchSignIn(listOf("l1", "l2", "ghost"))

        assertThat(r.successCount).isEqualTo(2)
        assertThat(r.failedCount).isEqualTo(1)
        assertThat(r.total).isEqualTo(3)
    }

    // === 批量签退 ===

    @Test
    fun `批量签退正常场景_全部已签到_签退并扣减各自课时包`() = runTest {
        db.studentDao().insert(Student(name = "张三", studentId = "s1"))
        db.studentDao().insert(Student(name = "李四", studentId = "s2"))
        db.studentDao().insert(Student(name = "王五", studentId = "s3"))
        db.lessonPackageDao().insert(pkg("p1", "张三", "s1"))
        db.lessonPackageDao().insert(pkg("p2", "李四", "s2"))
        db.lessonPackageDao().insert(pkg("p3", "王五", "s3"))
        db.lessonDao().insert(lesson("l1", "张三", "s1", status = "已签到"))
        db.lessonDao().insert(lesson("l2", "李四", "s2", status = "已签到"))
        db.lessonDao().insert(lesson("l3", "王五", "s3", status = "已签到"))

        val r = repo.batchSignOut(listOf("l1", "l2", "l3"))

        assertThat(r.successCount).isEqualTo(3)
        assertThat(r.failedCount).isEqualTo(0)
        listOf("l1" to "p1", "l2" to "p2", "l3" to "p3").forEach { (l, p) ->
            val after = db.lessonDao().getById(l)!!
            assertThat(after.status).isEqualTo("已签退")
            assertThat(after.signOutTime).isNotEmpty()
            assertThat(after.packageId).isEqualTo(p)
            assertThat(db.lessonPackageDao().getById(p)!!.usedLessons).isEqualTo(1)
        }
    }

    @Test
    fun `批量签退部分已操作_已签到签退_待签到已签退跳过`() = runTest {
        db.studentDao().insert(Student(name = "张三", studentId = "s1"))
        db.studentDao().insert(Student(name = "王五", studentId = "s3"))
        db.lessonPackageDao().insert(pkg("p1", "张三", "s1"))
        db.lessonPackageDao().insert(pkg("p3", "王五", "s3"))
        db.lessonDao().insert(lesson("l1", "张三", "s1", status = "已签到"))
        db.lessonDao().insert(lesson("l2", "李四", "s2")) // 待签到（未签到）
        db.lessonDao().insert(lesson("l3", "王五", "s3", status = "已签退", signOutTime = "11:00"))

        val r = repo.batchSignOut(listOf("l1", "l2", "l3"))

        assertThat(r.successCount).isEqualTo(1)
        assertThat(r.skippedCount).isEqualTo(1)
        assertThat(r.checkedOutCount).isEqualTo(1)
        assertThat(db.lessonPackageDao().getById("p1")!!.usedLessons).isEqualTo(1)
        assertThat(db.lessonPackageDao().getById("p3")!!.usedLessons).isEqualTo(0) // 未重复扣减
    }

    @Test
    fun `批量签退全部已操作_全部跳过_幂等不扣减课时包`() = runTest {
        db.lessonPackageDao().insert(pkg("p1", "张三", "s1"))
        db.lessonPackageDao().insert(pkg("p2", "李四", "s2"))
        db.lessonDao().insert(lesson("l1", "张三", "s1", status = "已签退", signOutTime = "11:00"))
        db.lessonDao().insert(lesson("l2", "李四", "s2", status = "已签退", signOutTime = "11:00"))

        val r = repo.batchSignOut(listOf("l1", "l2"))

        assertThat(r.successCount).isEqualTo(0)
        assertThat(r.checkedOutCount).isEqualTo(2)
        assertThat(db.lessonPackageDao().getById("p1")!!.usedLessons).isEqualTo(0)
        assertThat(db.lessonPackageDao().getById("p2")!!.usedLessons).isEqualTo(0)
    }

    // === 小班课分组查询 ===

    @Test
    fun `小班课分组查询_同日期时间地点聚合_按学员名排序`() = runTest {
        db.lessonDao().insert(lesson("l1", "王五", "s3"))
        db.lessonDao().insert(lesson("l2", "张三", "s1"))
        db.lessonDao().insert(lesson("l3", "李四", "s2"))
        db.lessonDao().insert(lesson("l4", "赵六", "s4", location = "游泳馆")) // 不同地点，不属同组

        val group = db.lessonDao().getLessonsByDateTimeLocation("2026-08-20", "10:00", "体能馆")

        assertThat(group.map { it.id }).containsExactly("l2", "l3", "l1").inOrder()
    }
}
