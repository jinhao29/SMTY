package com.shangmentiyu.sportscoach.data.repo

import android.content.Context
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.model.BodyMetricHistory
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import com.shangmentiyu.sportscoach.data.model.ParentReport
import com.shangmentiyu.sportscoach.data.model.Schedule
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.data.model.StudentDietRecord
import com.shangmentiyu.sportscoach.data.model.TrainingCycle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 学员改名级联 + 课时包状态归一化 集成测试（v50 数据流加固，模块一/模块二）。
 *
 * 锁定不变量：
 * 1. renameStudentCascadeById 按 studentId 精准级联改名 7 张子表
 *    （lessons / schedules / lesson_packages / training_cycles / body_metric_history /
 *     parent_reports / student_diet_records），新补 4 张表的按 ID 改名生效
 * 2. 改名不误改其他学员的子表（studentId 精准定位）
 * 3. 课时包 usedLessons >= totalLessons 时 updatePackage 自动置 status="已用完"
 *
 * 运行方式：./gradlew :app:testDebugUnitTest --tests "com.shangmentiyu.sportscoach.data.repo.DataFlowHardeningTest"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataFlowHardeningTest {

    private lateinit var db: AppDatabase
    private lateinit var studentRepo: StudentRepository
    private lateinit var pkgRepo: LessonPackageRepository

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        studentRepo = StudentRepository(
            dao = db.studentDao(),
            db = db,
            ftsDao = null,
            auditLog = null
        )
        pkgRepo = LessonPackageRepository(
            pkgDao = db.lessonPackageDao(),
            db = null,
            scheduleDao = null
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun student(name: String, studentId: String) = Student(name = name, studentId = studentId)

    private fun lesson(id: String, name: String, studentId: String) = Lesson(
        id = id, date = "2026-08-07", time = "10:00",
        studentName = name, studentId = studentId
    )

    private fun schedule(id: String, name: String, studentId: String) = Schedule(
        id = id, studentName = name, studentId = studentId,
        dayOfWeek = 1, startTime = "10:00", startDate = "2026-08-03"
    )

    private fun pkg(id: String, name: String, studentId: String, total: Int, used: Int) = LessonPackage(
        id = id, studentName = name, studentId = studentId, name = "测试课包",
        totalLessons = total, usedLessons = used, purchaseDate = "2026-01-01"
    )

    private fun cycle(id: String, name: String, studentId: String) = TrainingCycle(
        id = id, studentName = name, studentId = studentId, name = "测试周期", startDate = "2026-08-01"
    )

    private fun metric(id: String, name: String, studentId: String) = BodyMetricHistory(
        id = id, studentName = name, studentId = studentId, date = "2026-08-07"
    )

    private fun report(id: String, name: String, studentId: String) = ParentReport(
        id = id, studentName = name, studentId = studentId,
        reportType = "周报", startDate = "2026-08-01", endDate = "2026-08-07", content = "{}"
    )

    private fun diet(name: String, studentId: String) = StudentDietRecord(
        studentName = name, studentId = studentId,
        templateId = "tpl_regular", templateName = "常规健康发育型"
    )

    // === 模块一：按 studentId 级联改名（7 张子表） ===

    @Test
    fun `改名按studentId级联_7张子表studentName同步`() = runTest {
        db.studentDao().insert(student("张三", "s1"))
        db.lessonDao().insert(lesson("l1", "张三", "s1"))
        db.scheduleDao().insert(schedule("sc1", "张三", "s1"))
        db.lessonPackageDao().insert(pkg("p1", "张三", "s1", total = 10, used = 0))
        db.trainingCycleDao().insert(cycle("c1", "张三", "s1"))
        db.bodyMetricHistoryDao().insert(metric("m1", "张三", "s1"))
        db.parentReportDao().insert(report("r1", "张三", "s1"))
        db.dietDao().insertRecord(diet("张三", "s1"))

        studentRepo.renameStudentCascadeById("s1", "张三丰")

        assertThat(db.studentDao().getByStudentId("s1")?.name).isEqualTo("张三丰")
        assertThat(db.lessonDao().getById("l1")?.studentName).isEqualTo("张三丰")
        assertThat(db.scheduleDao().getById("sc1")?.studentName).isEqualTo("张三丰")
        assertThat(db.lessonPackageDao().getById("p1")?.studentName).isEqualTo("张三丰")
        assertThat(db.trainingCycleDao().getById("c1")?.studentName).isEqualTo("张三丰")
        assertThat(db.bodyMetricHistoryDao().getByStudentOnce("张三丰").single().studentName).isEqualTo("张三丰")
        assertThat(db.parentReportDao().getById("r1")?.studentName).isEqualTo("张三丰")
        assertThat(db.dietDao().getLatestRecord("张三丰")?.studentName).isEqualTo("张三丰")
    }

    @Test
    fun `改名按studentId精准_不误改其他学员子表`() = runTest {
        db.studentDao().insert(student("张三", "s1"))
        db.studentDao().insert(student("李四", "s2"))
        db.lessonDao().insert(lesson("l1", "张三", "s1"))
        db.lessonDao().insert(lesson("l2", "李四", "s2"))
        db.trainingCycleDao().insert(cycle("c1", "张三", "s1"))
        db.trainingCycleDao().insert(cycle("c2", "李四", "s2"))

        studentRepo.renameStudentCascadeById("s1", "王五")

        // s1 的子表已改名
        assertThat(db.lessonDao().getById("l1")?.studentName).isEqualTo("王五")
        assertThat(db.trainingCycleDao().getById("c1")?.studentName).isEqualTo("王五")
        // s2（李四）的子表不被误改
        assertThat(db.lessonDao().getById("l2")?.studentName).isEqualTo("李四")
        assertThat(db.trainingCycleDao().getById("c2")?.studentName).isEqualTo("李四")
    }

    @Test
    fun `改名目标重名_抛出异常且不落库`() = runTest {
        db.studentDao().insert(student("张三", "s1"))
        db.studentDao().insert(student("李四", "s2"))

        val result = runCatching { studentRepo.renameStudentCascadeById("s1", "李四") }

        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(db.studentDao().getByStudentId("s1")?.name).isEqualTo("张三") // 主表未变
    }

    // === 模块二：课时包状态归一化 ===

    @Test
    fun `updatePackage用尽_自动置已用完`() = runTest {
        db.lessonPackageDao().insert(pkg("p1", "张三", "s1", total = 10, used = 5))

        // 模拟编辑表单：used 达到 total 但 status 仍活跃（脏状态入口）
        pkgRepo.updatePackage(pkg("p1", "张三", "s1", total = 10, used = 10))

        val after = db.lessonPackageDao().getById("p1")!!
        assertThat(after.status).isEqualTo("已用完")
        assertThat(after.remainingLessons).isEqualTo(0)
    }

    @Test
    fun `updatePackage未用尽_状态保持活跃`() = runTest {
        db.lessonPackageDao().insert(pkg("p1", "张三", "s1", total = 10, used = 5))

        pkgRepo.updatePackage(pkg("p1", "张三", "s1", total = 10, used = 8))

        assertThat(db.lessonPackageDao().getById("p1")!!.status).isEqualTo("活跃")
    }

    @Test
    fun `addPackage用尽_自动置已用完`() = runTest {
        pkgRepo.addPackage(pkg("p2", "张三", "s1", total = 5, used = 5))

        assertThat(db.lessonPackageDao().getById("p2")!!.status).isEqualTo("已用完")
    }
}
