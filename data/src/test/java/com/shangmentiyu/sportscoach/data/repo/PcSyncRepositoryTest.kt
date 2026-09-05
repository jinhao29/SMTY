package com.shangmentiyu.sportscoach.data.repo

import android.content.Context
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.LessonPackage
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
 * PC 数据对账仓储集成测试（v35 双端数据真统一）。
 *
 * 覆盖：课时包总量对账落库（多包差值新建 / 安全锁拒绝）、PC 消课差值折算
 * （幂等 + 剩余截断 + 无活跃包延迟补齐）、收费记录镜像幂等 upsert。
 *
 * 运行：./gradlew :data:testDebugUnitTest --tests "com.shangmentiyu.sportscoach.data.repo.PcSyncRepositoryTest"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PcSyncRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: PcSyncRepository
    private val today = "2026-09-06"

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = PcSyncRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun pkg(name: String, total: Int, used: Int = 0, createdAt: Long = 0) =
        LessonPackage(
            studentName = name, name = "10次卡", totalLessons = total, usedLessons = used,
            purchaseDate = "2026-01-01", createdAt = createdAt
        )

    private suspend fun insertLesson(name: String, date: String, signedOut: Boolean = true) {
        db.lessonDao().insert(
            Lesson(
                id = "$name-$date-${System.nanoTime()}", date = date, time = "10:00",
                studentName = name,
                signOutTime = if (signedOut) "11:00" else "",
                status = if (signedOut) "已签退" else "已签到"
            )
        )
    }

    // === 课时包总量对账 ===

    @Test
    fun `多包差值新建 PC 同步包`() = runTest {
        db.lessonPackageDao().insert(pkg("张三", 20))
        db.lessonPackageDao().insert(pkg("张三", 20, createdAt = 2))

        val report = repo.applyPcDataBlocking(
            packages = listOf(PcPackage("张三", totalLessons = 45, usedLessons = 3)),
            lessons = emptyList(), fees = emptyList(), today = today
        )

        assertThat(report.pkgAdded).isEqualTo(1)
        val pkgs = db.lessonPackageDao().getAllByStudentBlocking("张三")
        assertThat(pkgs).hasSize(3)
        assertThat(pkgs.sumOf { it.totalLessons }).isEqualTo(45)  // 与 PC 收敛
    }

    @Test
    fun `pc 空数据不覆盖手机现值`() = runTest {
        db.lessonPackageDao().insert(pkg("张三", 20, used = 5))

        repo.applyPcDataBlocking(
            packages = listOf(PcPackage("张三", 0, 0)),
            lessons = emptyList(), fees = emptyList(), today = today
        )

        val pkg = db.lessonPackageDao().getAllByStudentBlocking("张三").first()
        assertThat(pkg.totalLessons).isEqualTo(20)
        assertThat(pkg.usedLessons).isEqualTo(5)
    }

    @Test
    fun `多包缩减被安全锁拒绝`() = runTest {
        db.lessonPackageDao().insert(pkg("张三", 20))
        db.lessonPackageDao().insert(pkg("张三", 20, createdAt = 2))

        val report = repo.applyPcDataBlocking(
            packages = listOf(PcPackage("张三", 10, 5)),
            lessons = emptyList(), fees = emptyList(), today = today
        )

        assertThat(report.pkgSkipped).isNotEmpty()
        assertThat(db.lessonPackageDao().getAllByStudentBlocking("张三").sumOf { it.totalLessons })
            .isEqualTo(40)
    }

    // === PC 消课差值折算 ===

    @Test
    fun `pc 独录消课折算进活跃包且幂等`() = runTest {
        db.lessonPackageDao().insert(pkg("张三", 30, used = 2))
        repeat(3) { insertLesson("张三", "2026-09-0${it + 1}") }

        val first = repo.applyPcDataBlocking(
            packages = listOf(PcPackage("张三", 30, usedLessons = 8)),  // PC 已上 8，手机 3 行 → 折算 5
            lessons = emptyList(), fees = emptyList(), today = today
        )
        assertThat(first.consumedUnits).isEqualTo(5)
        assertThat(db.lessonPackageDao().getAllByStudentBlocking("张三").first().usedLessons)
            .isEqualTo(7)  // 2 + 5

        // 重复同步：不再折算
        val second = repo.applyPcDataBlocking(
            packages = listOf(PcPackage("张三", 30, usedLessons = 8)),
            lessons = emptyList(), fees = emptyList(), today = today
        )
        assertThat(second.consumedUnits).isEqualTo(0)
        assertThat(db.lessonPackageDao().getAllByStudentBlocking("张三").first().usedLessons)
            .isEqualTo(7)
    }

    @Test
    fun `折算按剩余课时截断且进度只记实加`() = runTest {
        db.lessonPackageDao().insert(pkg("张三", 4, used = 3))  // 剩余 1
        repeat(2) { insertLesson("张三", "2026-09-0${it + 1}") }

        val report = repo.applyPcDataBlocking(
            packages = listOf(PcPackage("张三", 4, usedLessons = 10)),  // PC 独录 8 节，只装得下 1
            lessons = emptyList(), fees = emptyList(), today = today
        )

        assertThat(report.consumedUnits).isEqualTo(1)
        val pkg = db.lessonPackageDao().getAllByStudentBlocking("张三").first()
        assertThat(pkg.usedLessons).isEqualTo(4)   // 3 + 1，绝不超过 total
        assertThat(db.pcSyncStateDao().getBlocking("张三")?.appliedPcLessons).isEqualTo(1)
    }

    @Test
    fun `无活跃包时延迟折算`() = runTest {
        db.lessonPackageDao().insert(pkg("张三", 10, used = 0).copy(status = "已用完"))
        repeat(1) { insertLesson("张三", "2026-09-01") }

        val report = repo.applyPcDataBlocking(
            packages = listOf(PcPackage("张三", 10, usedLessons = 5)),  // PC 独录 4 节
            lessons = emptyList(), fees = emptyList(), today = today
        )

        assertThat(report.consumedUnits).isEqualTo(0)
        assertThat(db.pcSyncStateDao().getBlocking("张三")).isNull()  // 进度未推进

        // 教练在手机续包（活跃）后下个周期补齐
        db.lessonPackageDao().insert(pkg("张三", 10, createdAt = 9))
        repo.applyPcDataBlocking(
            packages = listOf(PcPackage("张三", 10, usedLessons = 5)),
            lessons = emptyList(), fees = emptyList(), today = today
        )
        assertThat(db.pcSyncStateDao().getBlocking("张三")?.appliedPcLessons).isEqualTo(4)
        assertThat(db.lessonPackageDao().getAllByStudentBlocking("张三")
            .first { it.status == "活跃" }.usedLessons).isEqualTo(4)
    }

    // === 收费记录镜像 ===

    @Test
    fun `收费记录幂等镜像且删除不传播`() = runTest {
        val fees = listOf(
            PcFee("张三", "2026-09-01", 800.0, 10.0, "微信", ""),
            PcFee("李四", "2026-09-02", 1600.0, 20.0, "支付宝", "续费"),
        )
        repo.applyPcDataBlocking(packages = emptyList(), lessons = emptyList(), fees = fees, today = today)
        repo.applyPcDataBlocking(packages = emptyList(), lessons = emptyList(), fees = fees, today = today)

        val all = db.feeRecordDao().getAll().first()
        assertThat(all).hasSize(2)  // 重复推送不放大

        // PC 端删除李四的记录（下次同步 fees 少一条）→ 手机保留（删除不传播）
        repo.applyPcDataBlocking(
            packages = emptyList(), lessons = emptyList(),
            fees = fees.take(1), today = today)
        assertThat(db.feeRecordDao().getAll().first()).hasSize(2)
    }

    @Test
    fun `同一学员同日多笔收费不因主键碰撞丢失`() = runTest {
        val fees = listOf(
            PcFee("张三", "2026-09-01", 800.0, 10.0, "微信", ""),
            PcFee("张三", "2026-09-01", 400.0, 5.0, "现金", ""),
        )
        repo.applyPcDataBlocking(packages = emptyList(), lessons = emptyList(), fees = fees, today = today)
        assertThat(db.feeRecordDao().getAll().first()).hasSize(2)
    }
}
