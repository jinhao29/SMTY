package com.shangmentiyu.sportscoach.data.repo

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.model.PcSyncState
import com.shangmentiyu.sportscoach.data.model.Student
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 删除学员（物理删除）+ 同步墓碑 回归测试（2026-09-10 硬删除定版）。
 *
 * 锁定不变量：
 * 1. deleteStudent 后 students 表物理无行（含 IncludeDeleted 查询）
 * 2. 删除同时写入 pc_sync_state 墓碑（appliedPcLessons = TOMBSTONE）
 * 3. PC→手机 students.xlsx 同步（importStudentsBlockingUpdatePart）对墓碑
 *    命中的名字跳过新增 —— 已删除学员不因 PC 端仍有档案而复活
 * 4. addStudentBlocking 清除墓碑，重新添加同名学员后同步导入恢复正常
 *
 * 运行方式：./gradlew :data:testDebugUnitTest --tests "com.shangmentiyu.sportscoach.data.repo.StudentDeleteTombstoneTest"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StudentDeleteTombstoneTest {

    private lateinit var db: AppDatabase
    private lateinit var studentRepo: StudentRepository

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
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun student(name: String) = Student(
        name = name, gender = "男", grade = "", school = "", phone = "",
        age = 10, heightCm = 140, weightKg = 35f, bmi = 17.9f,
        isActive = true,
        studentId = "test-" + name.hashCode().toString()
    )

    private suspend fun addStudent(name: String) {
        studentRepo.addStudentBlocking(name, gender = "男", grade = "", school = "", phone = "")
    }

    @Test
    fun deleteStudent_removesRowPhysically_andWritesTombstone() = runTest {
        addStudent("左其心")
        assertThat(db.studentDao().getByNameIncludeDeleted("左其心")).isNotNull()

        studentRepo.deleteStudent("左其心")

        // 物理删除：IncludeDeleted 也查不到（软删除时代此查询会返回 isActive=0 的行）
        assertThat(db.studentDao().getByNameIncludeDeleted("左其心")).isNull()
        assertThat(db.studentDao().getAllIncludeDeleted().first()).isEmpty()
        // 墓碑已写入
        val state = db.pcSyncStateDao().getBlocking("左其心")
        assertThat(state).isNotNull()
        assertThat(state!!.appliedPcLessons).isEqualTo(PcSyncState.TOMBSTONE)
    }

    @Test
    fun pcSyncImport_doesNotResurrectTombstonedStudent() = runTest {
        addStudent("左其心")
        studentRepo.deleteStudent("左其心")

        // PC 端仍有该学员档案，下次 students.xlsx 同步推送过来
        val result = studentRepo.importStudentsBlockingUpdatePart(listOf(student("左其心")))

        assertThat(result.added).isEqualTo(0)
        assertThat(result.skipped).isEqualTo(1)
        assertThat(db.studentDao().getByNameIncludeDeleted("左其心")).isNull()
    }

    @Test
    fun pcSyncImport_stillAddsNonTombstonedStudent() = runTest {
        // 对照组：无墓碑的学员正常导入
        val result = studentRepo.importStudentsBlockingUpdatePart(listOf(student("正常学员")))
        assertThat(result.added).isEqualTo(1)
        assertThat(db.studentDao().getByNameIncludeDeleted("正常学员")).isNotNull()
    }

    @Test
    fun reAddingSameNameStudent_clearsTombstone() = runTest {
        addStudent("左其心")
        studentRepo.deleteStudent("左其心")
        assertThat(db.pcSyncStateDao().getBlocking("左其心")!!.appliedPcLessons)
            .isEqualTo(PcSyncState.TOMBSTONE)

        // 用户重新添加同名学员 → 墓碑清除 → PC 同步档案可以正常导入
        addStudent("左其心")
        assertThat(db.pcSyncStateDao().getBlocking("左其心")).isNull()

        val result = studentRepo.importStudentsBlockingUpdatePart(
            listOf(student("左其心").copy(updatedAt = Long.MAX_VALUE))
        )
        assertThat(result.overwritten).isEqualTo(1)
    }
}
