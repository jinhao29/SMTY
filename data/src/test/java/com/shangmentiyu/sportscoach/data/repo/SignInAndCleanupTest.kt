package com.shangmentiyu.sportscoach.data.repo

import android.content.Context
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.Student
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * === v32：签到分离 + 无效课表清理 的最小自检 ===
 *
 * 锁定三个不变量：
 * 1. 签到翻转今日占位课时（status 待签到 → 已签到），不新建重复课时
 * 2. 重复签到被唯一索引 + 应用层双重防线拦截（alreadySigned=true）
 * 3. 清理仅删除「过期 + 待签到」占位，保留已签到与未来课表
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SignInAndCleanupTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: OperationRepository

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
    private fun today(): String = LocalDate.now().format(fmt)
    private fun datePlus(days: Long): String = LocalDate.now().plusDays(days).format(fmt)

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

    private fun lesson(
        id: String,
        date: String,
        time: String = "10:00",
        status: String = "待签到",
        signOutTime: String = ""
    ) = Lesson(
        id = id,
        date = date,
        time = time,
        studentName = "张三",
        studentId = "s1",
        status = status,
        signOutTime = signOutTime
    )

    // === 1. 签到翻转占位，不新建 ===

    @Test
    fun `签到翻转今日占位课时_不新建重复课时`() = runTest {
        db.studentDao().insert(Student(name = "张三", studentId = "s1"))
        db.lessonDao().insert(lesson(id = "l1", date = today(), status = "待签到"))

        val result = repo.signIn("张三", "s1", operator = "教练A")

        assertThat(result.success).isTrue()
        val lessons = db.lessonDao().getByStudentDualOnce("s1", "张三")
        assertThat(lessons.size).isEqualTo(1) // 未新建
        assertThat(lessons[0].status).isEqualTo("已签到") // 已翻转
        assertThat(db.signInDao().countByLessonAndType("l1", "签到")).isEqualTo(1)
    }

    // === 2. 重复签到被拦截 ===

    @Test
    fun `重复签到被拦截_不产生第二条签到记录`() = runTest {
        db.studentDao().insert(Student(name = "张三", studentId = "s1"))
        db.lessonDao().insert(lesson(id = "l1", date = today(), status = "待签到"))

        val first = repo.signIn("张三", "s1", operator = "教练A")
        val second = repo.signIn("张三", "s1", operator = "教练A")

        assertThat(first.success).isTrue()
        assertThat(second.success).isFalse()
        assertThat(second.alreadySigned).isTrue()
        assertThat(db.signInDao().countByLessonAndType("l1", "签到")).isEqualTo(1)
    }

    // === 3. 清理仅删过期待签到 ===

    @Test
    fun `清理仅删过期待签到_保留已签到与未来课表`() = runTest {
        db.lessonDao().insert(lesson(id = "expired_pending", date = datePlus(-3), time = "10:00", status = "待签到"))
        db.lessonDao().insert(lesson(id = "expired_signed", date = datePlus(-3), time = "11:00", status = "已签到"))
        db.lessonDao().insert(lesson(id = "future_pending", date = datePlus(3), time = "10:00", status = "待签到"))

        val deleted = repo.clearExpiredUnsignedLessons()

        assertThat(deleted).isEqualTo(1)
        val remaining = db.lessonDao().getAllOnce()
        assertThat(remaining.map { it.id }).containsExactly("expired_signed", "future_pending")
    }
}
