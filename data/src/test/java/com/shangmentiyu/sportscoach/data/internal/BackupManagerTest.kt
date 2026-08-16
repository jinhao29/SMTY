package com.shangmentiyu.sportscoach.data.internal

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.shangmentiyu.sportscoach.data.db.AppDatabase
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * BackupManager 备份/恢复核心流程测试（v51 质量债补测）。
 *
 * 覆盖：
 * 1. generateBackupFileName 默认文件名格式
 * 2. migrateLegacySignPhotosDir 旧照片目录迁移 + 幂等
 * 3. 备份→恢复完整往返：ZIP 条目完整（db + export_meta.json）、
 *    恢复后学员/课时包数据真实还原、完整性校验通过
 * 4. v48 跨版本防御：备份库 user_version > 当前版本时拒绝恢复
 * 5. v47 安全不变量：损坏备份恢复失败后自动回滚，现有数据不丢
 *
 * 运行方式：./gradlew :data:testDebugUnitTest --tests "com.shangmentiyu.sportscoach.data.internal.BackupManagerTest"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @After
    fun tearDown() {
        // 关闭真实单例，避免静态 INSTANCE 跨测试泄漏
        AppDatabase.closeAndResetInstance(context)
    }

    private fun zipEntryNames(bytes: ByteArray): List<String> =
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            generateSequence { zis.nextEntry }.map { it.name }.toList()
        }

    // ================================================================
    // 1. 工具函数
    // ================================================================

    @Test
    fun `默认备份文件名_符合smty_backup_日期_时间格式`() {
        val name = BackupManager.generateBackupFileName()

        assertThat(name).matches("smty_backup_\\d{8}_\\d{6}")
        assertThat(name).startsWith(BackupManager.BACKUP_EXTENSION)
    }

    @Test
    fun `旧照片目录迁移_文件搬移且幂等`() {
        val legacy = File(context.filesDir, "sign_photos")
        legacy.mkdirs()
        File(legacy, "a.jpg").writeText("photo-a")
        File(legacy, "b.jpg").writeText("photo-b")

        BackupManager.migrateLegacySignPhotosDir(context.filesDir)

        val target = File(context.filesDir, "SignPhotos")
        assertThat(File(target, "a.jpg").exists()).isTrue()
        assertThat(File(target, "b.jpg").exists()).isTrue()
        assertThat(legacy.exists()).isFalse() // 全部复制成功后旧目录删除

        // 幂等：旧目录已不存在时直接返回，不影响新目录
        BackupManager.migrateLegacySignPhotosDir(context.filesDir)
        assertThat(File(target, "a.jpg").exists()).isTrue()
    }

    @Test
    fun `旧照片目录迁移_目标同名文件不覆盖`() {
        val legacy = File(context.filesDir, "sign_photos")
        legacy.mkdirs()
        File(legacy, "a.jpg").writeText("legacy-content")
        val target = File(context.filesDir, "SignPhotos")
        target.mkdirs()
        File(target, "a.jpg").writeText("new-content")

        BackupManager.migrateLegacySignPhotosDir(context.filesDir)

        assertThat(File(target, "a.jpg").readText()).isEqualTo("new-content")
    }

    // ================================================================
    // 2. 备份 → 恢复完整往返
    // ================================================================

    @Test
    fun `备份恢复完整往返_数据真实还原_完整性校验通过`() = runTest {
        // 1. 造数据（真实单例数据库，文件落在 Robolectric 沙箱 databases 目录）
        val db = AppDatabase.getDatabase(context)
        db.studentDao().insert(Student(name = "备份学员", studentId = "s100"))
        db.lessonPackageDao().insert(
            LessonPackage(
                id = "p_backup",
                studentName = "备份学员",
                studentId = "s100",
                name = "10次卡",
                totalLessons = 10,
                usedLessons = 3,
                purchaseDate = "2026-01-01"
            )
        )

        // 2. 备份
        val bos = ByteArrayOutputStream()
        val backupOk = BackupManager.backup(context, bos)
        assertThat(backupOk).isTrue()

        val zipBytes = bos.toByteArray()
        assertThat(zipBytes.size).isGreaterThan(0)
        val entries = zipEntryNames(zipBytes)
        assertThat(entries).contains("sports_coach_db")
        assertThat(entries).contains("export_meta.json") // 桌面端易读副本

        // 3. 破坏现有数据（模拟误删学员）
        val db2 = AppDatabase.getDatabase(context)
        db2.studentDao().deleteByName("备份学员")
        assertThat(db2.studentDao().getByName("备份学员")).isNull()

        // 4. 从备份恢复
        val result = BackupManager.restoreDetailed(context, ByteArrayInputStream(zipBytes))
        assertThat(result.success).isTrue()
        assertThat(result.integrityOk).isTrue()
        assertThat(result.needRestart).isTrue()

        // 5. 重开数据库验证数据还原
        AppDatabase.closeAndResetInstance(context)
        val db3 = AppDatabase.getDatabase(context)
        assertThat(db3.studentDao().getByName("备份学员")).isNotNull()
        val pkg = db3.lessonPackageDao().getById("p_backup")
        assertThat(pkg).isNotNull()
        assertThat(pkg!!.usedLessons).isEqualTo(3)
    }

    // ================================================================
    // 3. v48 跨版本防御
    // ================================================================

    @Test
    fun `高版本备份_拒绝恢复_不触碰现有数据`() = runTest {
        // 现有数据在场
        AppDatabase.getDatabase(context)
            .studentDao().insert(Student(name = "现役学员", studentId = "s200"))

        // 构造 user_version 高于当前版本的备份 ZIP
        val tmpDb = File(context.cacheDir, "high_version.db")
        tmpDb.parentFile?.mkdirs()
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(tmpDb, null).use { sqlDb ->
            sqlDb.version = AppDatabase.DATABASE_VERSION + 1
        }
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            zos.putNextEntry(ZipEntry("sports_coach_db"))
            tmpDb.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
        }
        tmpDb.delete()

        val result = BackupManager.restoreDetailed(context, ByteArrayInputStream(bos.toByteArray()))

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("更高版本")
        assertThat(result.integrityReport).contains("user_version")

        // 拒绝发生在触碰数据之前：现有数据必须原样保留
        AppDatabase.closeAndResetInstance(context)
        assertThat(
            AppDatabase.getDatabase(context).studentDao().getByName("现役学员")
        ).isNotNull()
    }

    // ================================================================
    // 4. v47 安全不变量：损坏备份自动回滚
    // ================================================================

    @Test
    fun `损坏备份_恢复失败自动回滚_现有数据不丢`() = runTest {
        // 现有数据在场（触发恢复前安全备份路径）
        AppDatabase.getDatabase(context)
            .studentDao().insert(Student(name = "重要学员", studentId = "s300"))

        val garbage = "这不是一个合法的ZIP备份文件".toByteArray()
        val result = BackupManager.restoreDetailed(context, ByteArrayInputStream(garbage))

        // v47 设计：恢复失败但安全备份回滚成功 → success=true + 提示已回滚
        assertThat(result.success).isTrue()
        assertThat(result.message).contains("已自动回滚")

        // 现有数据完整保留
        AppDatabase.closeAndResetInstance(context)
        assertThat(
            AppDatabase.getDatabase(context).studentDao().getByName("重要学员")
        ).isNotNull()
    }

    @Test
    fun `无数据库条目的ZIP_恢复失败_提示格式不正确`() = runTest {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            zos.putNextEntry(ZipEntry("some_other_file.txt"))
            zos.write("hello".toByteArray())
            zos.closeEntry()
        }

        val result = BackupManager.restoreDetailed(context, ByteArrayInputStream(bos.toByteArray()))

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("未找到数据库文件")
    }
}
