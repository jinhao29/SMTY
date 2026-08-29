package com.shangmentiyu.sportscoach.domain.usecase

import android.content.Context
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.model.Lesson
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
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 忘记签退提醒用例最小自检。
 *
 * 锁定两个不变量：
 * 1. 检测：date < today 且 status='已签到' 且 signOutTime 为空的记录按学员汇总
 *    （今日 / 已签退 / 待签到 / v24 遗留"status=已签到但有签退时间"均不计入）
 * 2. 处理完成：所有记录签退后 → shouldShow 变为 false
 *
 * 运行方式：./gradlew :data:testDebugUnitTest --tests "com.shangmentiyu.sportscoach.domain.usecase.UnsignedOutReminderUseCaseTest"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnsignedOutReminderUseCaseTest {

    private lateinit var db: AppDatabase
    private lateinit var getReminder: GetUnsignedOutReminderUseCase

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
    private fun today(): String = LocalDate.now().format(fmt)
    private fun daysAgo(n: Long): String = LocalDate.now().minusDays(n).format(fmt)

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        getReminder = GetUnsignedOutReminderUseCase(db.lessonDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun state() = getReminder(today()).first()

    private fun lesson(id: String, studentName: String, date: String) = Lesson(
        id = id,
        date = date,
        time = "10:00",
        studentName = studentName,
        status = "已签到"
    )

    @Test
    fun `无未签退记录_提醒不显示`() = runTest {
        val s = state()

        assertThat(s.shouldShow).isFalse()
        assertThat(s.totalLessonCount).isEqualTo(0)
        assertThat(s.students).isEmpty()
    }

    @Test
    fun `过去日期已签到未签退_按学员汇总显示提醒_排除不符记录`() = runTest {
        db.lessonDao().insert(lesson("l1", "张三", daysAgo(3)))
        db.lessonDao().insert(lesson("l2", "张三", daysAgo(1)))
        db.lessonDao().insert(lesson("l3", "李四", daysAgo(2)))
        // 以下均不应计入
        db.lessonDao().insert(lesson("l4", "今日学员", today()))
        db.lessonDao().insert(
            lesson("l5", "已签退学员", daysAgo(5)).copy(status = "已签退", signOutTime = "11:00")
        )
        db.lessonDao().insert(lesson("l6", "待签到学员", daysAgo(5)).copy(status = "待签到"))
        // v24 遗留：status 停留"已签到"但实际已签退（signOutTime 非空）
        db.lessonDao().insert(lesson("l7", "遗留学员", daysAgo(4)).copy(signOutTime = "12:00"))

        val s = state()

        assertThat(s.shouldShow).isTrue()
        assertThat(s.totalLessonCount).isEqualTo(3)
        assertThat(s.students).hasSize(2)
        assertThat(s.students[0].studentName).isEqualTo("张三")
        assertThat(s.students[0].lessonCount).isEqualTo(2)
        assertThat(s.students[0].latestDate).isEqualTo(daysAgo(1))
        assertThat(s.students[1].studentName).isEqualTo("李四")
        assertThat(s.students[1].lessonCount).isEqualTo(1)
        assertThat(s.students[1].latestDate).isEqualTo(daysAgo(2))
    }

    @Test
    fun `所有记录签退处理后_提醒不再显示`() = runTest {
        val l1 = lesson("l1", "张三", daysAgo(1))
        db.lessonDao().insert(l1)

        val s1 = state()
        assertThat(s1.shouldShow).isTrue()

        db.lessonDao().update(l1.copy(status = "已签退", signOutTime = "11:00"))

        val s2 = state()
        assertThat(s2.shouldShow).isFalse()
        assertThat(s2.totalLessonCount).isEqualTo(0)
    }
}
