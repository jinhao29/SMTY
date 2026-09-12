package com.shangmentiyu.sportscoach.data.internal

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.shangmentiyu.sportscoach.data.db.AppDatabase
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
        // v1.0.5：SQLCipher 的 native 库无法在 JVM（Robolectric）加载，
        // 本测试关注的是备份/恢复业务逻辑而非加密本身，故测试期关闭数据库加密。
        // 加密实效的真机验证清单见 docs/db_encryption_verification.md。
        AppDatabase.devDisableEncryptionForTesting()
        // v23.13：复位到默认模式（mode 校验测试会切换模式，防静态状态跨用例泄漏）
        ModeManager.setMode(context, ModeManager.MODE_COACHING)
    }

    @After
    fun tearDown() {
        // 关闭真实单例，避免静态 INSTANCE 跨测试泄漏
        AppDatabase.closeAndResetInstance(context)
        // 恢复默认（加密开启），避免静态开关跨测试类泄漏
        AppDatabase.devResetEncryptionForTesting()
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

    // ================================================================
    // 5. 备份加密链路（v1.0.4 补测）
    // ================================================================
    //
    // 说明：BackupCryptoTest 已覆盖加密原语（往返/错误口令/篡改/IV 随机），
    // 这里补的是**端到端**行为——加密设置真的作用到了备份包上、且导出物
    // 无法被普通工具直接读取。这是"加密是否真的生效"的最后一道防线。

    /** 写入备份口令（模拟用户在设置页配置） */
    private suspend fun setPassphrase(value: String) {
        com.shangmentiyu.sportscoach.data.repo.SettingsRepository(context)
            .setBackupPassphrase(value)
    }

    @Test
    fun `设置口令后_导出包加密_文件头非PK无法被普通解压工具打开`() = runTest {
        setPassphrase("smty-test-pass")
        AppDatabase.getDatabase(context)
            .studentDao().insert(Student(name = "加密学员", studentId = "s400", phone = "13800001111"))

        val bos = ByteArrayOutputStream()
        assertThat(BackupManager.backup(context, bos)).isTrue()
        val zipBytes = bos.toByteArray()

        // 1. ZIP 结构本身仍成立（备份包还是合法 ZIP，否则 PC 端整个读不了）
        val entries = zipEntryNames(zipBytes)
        assertThat(entries).contains("backup_manifest.json")
        assertThat(entries).contains("export_meta.json")

        // 2. 清单标记为已加密
        val manifest = ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var e = zis.nextEntry
            var json: String? = null
            while (e != null) {
                if (e.name == "backup_manifest.json") json = String(zis.readBytes(), Charsets.UTF_8)
                zis.closeEntry()
                e = zis.nextEntry
            }
            json
        }
        assertThat(manifest).contains("\"encrypted\"")
        assertThat(manifest).contains("\"salt\"")

        // 3. ★ 核心断言：数据库条目是密文（SMTB 魔数），不是可直接打开的 SQLite
        val dbEntry = ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var e = zis.nextEntry
            var bytes: ByteArray? = null
            while (e != null) {
                if (e.name == "sports_coach_db") bytes = zis.readBytes()
                zis.closeEntry()
                e = zis.nextEntry
            }
            bytes
        }
        assertThat(dbEntry).isNotNull()
        // SQLite 明文文件头固定为 "SQLite format 3\u0000"
        assertThat(String(dbEntry!!.copyOfRange(0, 15), Charsets.US_ASCII))
            .isNotEqualTo("SQLite format 3")
        // 加密载荷以 SMTB 魔数开头
        assertThat(String(dbEntry.copyOfRange(0, 4), Charsets.US_ASCII)).isEqualTo("SMTB")
    }

    @Test
    fun `错误口令_恢复失败_不产出任何数据`() = runTest {
        // 用口令 A 备份
        setPassphrase("correct-passphrase-A")
        AppDatabase.getDatabase(context)
            .studentDao().insert(Student(name = "机密学员", studentId = "s500"))
        val bos = ByteArrayOutputStream()
        assertThat(BackupManager.backup(context, bos)).isTrue()

        // 改成错误口令 B 再恢复
        setPassphrase("wrong-passphrase-B")
        val result = BackupManager.restoreDetailed(context, ByteArrayInputStream(bos.toByteArray()))

        // ★ 核心契约：恢复**没有生效**。v47 的安全不变量让本地数据完好回滚，
        //   所以 success=true（数据可用），但必须标记 rolledBack=true 以区别于"恢复成功"。
        assertThat(result.rolledBack).isTrue()
        assertThat(result.integrityOk).isTrue()  // 回滚后的库是完好的
        assertThat(result.message).contains("已自动回滚")
        // 错误口令的失败原因必须回传到消息里，让用户知道是口令问题
        assertThat(result.message).contains("解密")

        // 现有数据未被破坏，且不产生"半个库"
        AppDatabase.closeAndResetInstance(context)
        val db = AppDatabase.getDatabase(context)
        // 先执行一次真实查询强制打开连接（Robolectric 下 isOpen 是惰性的，
        // 不先查询会在未打开状态被误判为 false）
        db.studentDao().getAllIncludeDeleted().first()
        assertThat(db.isOpen).isTrue()
    }

    @Test
    fun `密文被篡改_GCM校验失败_恢复被拒`() = runTest {
        setPassphrase("tamper-test-pass")
        AppDatabase.getDatabase(context)
            .studentDao().insert(Student(name = "篡改学员", studentId = "s600"))
        val bos = ByteArrayOutputStream()
        assertThat(BackupManager.backup(context, bos)).isTrue()

        // 翻转数据库条目密文中的若干字节（模拟文件被修改/损坏）
        val original = bos.toByteArray()
        val tampered = ByteArrayOutputStream()
        ZipInputStream(ByteArrayInputStream(original)).use { zis ->
            ZipOutputStream(tampered).use { zos ->
                var e = zis.nextEntry
                while (e != null) {
                    var payload = zis.readBytes()
                    if (e.name == "sports_coach_db" && payload.size > 40) {
                        // 改密文中段：既非魔数也非 IV，确保命中的是 GCM 密文本体
                        payload[payload.size / 2] = (payload[payload.size / 2].toInt() xor 0xFF).toByte()
                    }
                    zos.putNextEntry(ZipEntry(e.name))
                    zos.write(payload)
                    zos.closeEntry()
                    e = zis.nextEntry
                }
            }
        }

        val result = BackupManager.restoreDetailed(
            context, ByteArrayInputStream(tampered.toByteArray()))

        // GCM 认证标签校验必须拦住篡改：恢复不生效，回滚保住原数据
        assertThat(result.rolledBack).isTrue()
        assertThat(result.message).contains("已自动回滚")

        // 篡改过的备份绝不能把损坏数据写进本地库
        AppDatabase.closeAndResetInstance(context)
        val db = AppDatabase.getDatabase(context)
        // 先查询强制打开连接（Robolectric 下 isOpen 惰性）
        db.studentDao().getAllIncludeDeleted().first()
        assertThat(db.isOpen).isTrue()
    }

    @Test
    fun `清除口令后_导出包恢复为明文格式_向后兼容`() = runTest {
        // 不设口令（默认状态）
        setPassphrase("")
        AppDatabase.getDatabase(context)
            .studentDao().insert(Student(name = "明文学员", studentId = "s700"))

        val bos = ByteArrayOutputStream()
        assertThat(BackupManager.backup(context, bos)).isTrue()
        val zipBytes = bos.toByteArray()

        // 未设口令 → 不写 manifest，整包保持旧格式（PC 端旧逻辑照常工作）
        val entries = zipEntryNames(zipBytes)
        assertThat(entries).doesNotContain("backup_manifest.json")
        assertThat(entries).contains("sports_coach_db")

        // 数据库条目应为明文 SQLite（旧格式），且能正常恢复
        val dbEntry = ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var e = zis.nextEntry
            var bytes: ByteArray? = null
            while (e != null) {
                if (e.name == "sports_coach_db") bytes = zis.readBytes()
                zis.closeEntry()
                e = zis.nextEntry
            }
            bytes
        }
        assertThat(dbEntry).isNotNull()
        assertThat(String(dbEntry!!.copyOfRange(0, 15), Charsets.US_ASCII))
            .isEqualTo("SQLite format 3")

        // 明文备份仍可恢复（向后兼容铁律）
        val result = BackupManager.restoreDetailed(context, ByteArrayInputStream(zipBytes))
        assertThat(result.success).isTrue()
        assertThat(result.integrityOk).isTrue()
    }

    // ================================================================
    // 7. v23.13 多租户防串库：备份 mode 校验
    // ================================================================

    /** 构造带指定 mode 标记的最小备份（明文库 + export_meta.json） */
    private fun zipWithMode(mode: String, dbName: String): ByteArray {
        val bos = ByteArrayOutputStream()
        val tmpDb = File(context.cacheDir, "mode_probe_${System.nanoTime()}.db")
        tmpDb.parentFile?.mkdirs()
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(tmpDb, null).use {
            it.version = AppDatabase.DATABASE_VERSION
        }
        ZipOutputStream(bos).use { zos ->
            zos.putNextEntry(ZipEntry(dbName))
            tmpDb.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("export_meta.json"))
            zos.write("""{"meta":{"version":"1.0","mode":"$mode"}}""".toByteArray())
            zos.closeEntry()
        }
        tmpDb.delete()
        return bos.toByteArray()
    }

    @Test
    fun `跨模式备份_拒绝恢复_提示模式不一致`() = runTest {
        // 现有数据在场（coaching 模式库）
        AppDatabase.getDatabase(context)
            .studentDao().insert(Student(name = "现役学员", studentId = "s800"))

        // 当前模式默认 coaching，备份标记却是俱乐部
        val result = BackupManager.restoreDetailed(
            context, ByteArrayInputStream(zipWithMode("club_evolve", "sports_coach_db")))

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("模式不一致")
        // 拒绝发生在触碰任何数据之前，现有数据完好
        assertThat(AppDatabase.getDatabase(context).studentDao().getByName("现役学员"))
            .isNotNull()
    }

    @Test
    fun `旧值club备份_归一化后可恢复_不误拒`() = runTest {
        // 切到俱乐部（旧值写入 → 归一化为 club_evolve）
        ModeManager.setMode(context, "club")
        assertThat(ModeManager.activeMode).isEqualTo("club_evolve")

        // 升级前导出的旧备份（mode=club）必须能恢复（向后兼容）。
        // 注意：备份 zip 的 db 条目名固定为 ZIP_ENTRY_DB（sports_coach_db），
        // 内容与落盘位置由当前 activeDbName 决定 —— 条目名不是 "sports_coach_club_db"。
        val result = BackupManager.restoreDetailed(
            context, ByteArrayInputStream(zipWithMode("club", "sports_coach_db")))

        assertThat(result.success).isTrue()
        assertThat(result.integrityOk).isTrue()

        // 复位到默认模式并清偏好，避免污染其他测试的静态状态
        ModeManager.setMode(context, ModeManager.MODE_COACHING)
        context.getSharedPreferences("mode_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }
}
