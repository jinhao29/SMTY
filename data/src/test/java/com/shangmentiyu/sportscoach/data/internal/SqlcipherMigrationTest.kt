package com.shangmentiyu.sportscoach.data.internal

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * 数据库加密（SQLCipher）迁移链路测试（v1.0.5）。
 *
 * ## ⚠️ 为什么这里测不到"真正的加密"
 * SQLCipher 的核心是 **native 库（libsqlcipher.so）**。Robolectric 跑在桌面 JVM 上，
 * AAR 里带的是 Android ABI 的 ELF（arm64-v8a / x86_64 等），**JVM 无法加载**，
 * `System.loadLibrary("sqlcipher")` 必然抛 UnsatisfiedLinkError。
 *
 * 因此本测试分两类：
 * - **JVM 可测**（本文件主体）：文件头判定、迁移决策分支、加密库识别、路径清理逻辑
 * - **必须真机测**（[nativeCryptoRequiresRealDevice] 标记跳过）：真实加密/解密、口令错误拒绝
 *
 * 真机验证清单见 `docs/db_encryption_verification.md`。
 *
 * 运行：./gradlew :data:testDebugUnitTest --tests "*SqlcipherMigrationTest"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SqlcipherMigrationTest {

    private lateinit var context: Context
    private lateinit var dbDir: File
    private val dbName = "sports_coach_db"

    /** native 库能否加载（JVM 上恒为 false，真机仪器测试上为 true） */
    private val nativeAvailable: Boolean by lazy {
        runCatching { System.loadLibrary("sqlcipher") }.isSuccess
    }

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        dbDir = context.getDatabasePath(dbName).parentFile!!
        dbDir.mkdirs()
        deleteDbFiles()
        File(context.filesDir, "PlainMigration").deleteRecursively()
        DatabaseKeyManager.reset(context)
        // 本测试直接调 PlainDbMigrator / EncryptedDbOpener，不经过 AppDatabase，
        // 故不关闭加密开关；但需保证开关为默认值，避免受其他测试类影响。
        com.shangmentiyu.sportscoach.data.db.AppDatabase.devResetEncryptionForTesting()
    }

    @After
    fun tearDown() {
        deleteDbFiles()
        File(dbDir, "$dbName.migrating").takeIf { it.exists() }?.delete()
    }

    private fun deleteDbFiles() {
        listOf("", "-wal", "-shm").forEach { s ->
            File(dbDir, dbName + s).takeIf { it.exists() }?.delete()
        }
    }

    /** 用 Android 原生 SQLite 造一个明文库（等价于 v1.0.5 之前的真实库文件） */
    private fun createPlainDb(students: List<String> = listOf("张三", "李四")): File {
        val f = File(dbDir, dbName)
        val db = SQLiteDatabase.openOrCreateDatabase(f, null)
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS students (id INTEGER PRIMARY KEY, name TEXT)")
            students.forEachIndexed { i, name ->
                db.execSQL("INSERT INTO students (id, name) VALUES (${i + 1}, '$name')")
            }
            db.version = 35
        } finally {
            db.close()
        }
        return f
    }

    // ================================================================
    // A. 文件头判定（JVM 可测，这是迁移决策的唯一依据）
    // ================================================================

    @Test
    fun `明文SQLite文件_被判定为非加密`() {
        val plain = createPlainDb()

        assertThat(EncryptedDbOpener.isEncrypted(plain)).isFalse()
    }

    @Test
    fun `随机盐文件头_被判定为已加密`() {
        // SQLCipher 加密库以 16 字节随机盐开头，绝非 "SQLite format 3"
        val enc = File(dbDir, "enc.db")
        enc.writeBytes(ByteArray(96) { (it * 7 + 3).toByte() })

        assertThat(EncryptedDbOpener.isEncrypted(enc)).isTrue()
    }

    @Test
    fun `文件不存在或过短_不判定为加密`() {
        assertThat(EncryptedDbOpener.isEncrypted(File(dbDir, "nope.db"))).isFalse()

        val tiny = File(dbDir, "tiny.db").apply { writeBytes(ByteArray(8)) }
        assertThat(EncryptedDbOpener.isEncrypted(tiny)).isFalse()
    }

    @Test
    fun `恰好奇数长度文件头_不越界不崩溃`() {
        // 边界：短于 16 字节一律视为"非加密"，不能抛异常
        for (len in intArrayOf(0, 1, 15)) {
            val f = File(dbDir, "edge_$len.db").apply { writeBytes(ByteArray(len)) }
            assertThat(EncryptedDbOpener.isEncrypted(f)).isFalse()
        }
    }

    // ================================================================
    // B. 迁移决策分支（JVM 可测的部分）
    // ================================================================

    @Test
    fun `全新安装无数据库文件_迁移直接通过`() {
        val result = PlainDbMigrator.migrateIfNeeded(context, dbName)

        assertThat(result).isTrue()
        assertThat(File(dbDir, dbName).exists()).isFalse()
    }

    @Test
    fun `零长度数据库文件_不触发迁移`() {
        File(dbDir, dbName).writeBytes(ByteArray(0))

        val result = PlainDbMigrator.migrateIfNeeded(context, dbName)

        assertThat(result).isTrue()
        // 空文件保持原样，交给 Room 自己建库
        assertThat(File(dbDir, dbName).length()).isEqualTo(0L)
    }

    @Test
    fun `明文库迁移失败时_保留明文库原样并返回false`() {
        // JVM 上 native 库不可用 → 迁移必然失败。
        // 这正是要验证的**安全底线**：失败时绝不破坏原有明文数据。
        val plainDb = createPlainDb(listOf("不能被破坏"))
        val before = plainDb.readBytes()
        assumeTrue("JVM 无 SQLCipher native 库，本用例验证失败路径", !nativeAvailable)

        val result = PlainDbMigrator.migrateIfNeeded(context, dbName)

        assertThat(result).isFalse()
        // ★ 核心安全断言：失败后明文库内容一字未改
        assertThat(plainDb.exists()).isTrue()
        assertThat(plainDb.readBytes()).isEqualTo(before)
        assertThat(EncryptedDbOpener.isEncrypted(plainDb)).isFalse()
        // 不留下临时文件垃圾
        assertThat(File(dbDir, "$dbName.migrating").exists()).isFalse()
    }

    // ================================================================
    // C. 加密库识别（真实加密产物，必须真机）
    // ================================================================

    @Test
    fun `迁移后_文件头变为SQLCipher盐且数据完整`() {
        assumeTrue("需要 SQLCipher native 库，请在真机/仪器测试中运行", nativeAvailable)

        val plainDb = createPlainDb(listOf("张三", "李四"))
        assertThat(EncryptedDbOpener.isEncrypted(plainDb)).isFalse()

        val migrated = PlainDbMigrator.migrateIfNeeded(context, dbName)
        assertThat(migrated).isTrue()
        assertThat(EncryptedDbOpener.isEncrypted(plainDb)).isTrue()

        EncryptedDbOpener.openReadWrite(context, plainDb.absolutePath).use { db ->
            db.rawQuery("SELECT COUNT(*) FROM students;", null).use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getInt(0)).isEqualTo(2)
            }
            db.rawQuery("SELECT name FROM students WHERE id = 1;", null).use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getString(0)).isEqualTo("张三")
            }
        }
    }

    @Test
    fun `迁移会保留明文库的文件级备份`() {
        assumeTrue("需要 SQLCipher native 库，请在真机/仪器测试中运行", nativeAvailable)

        createPlainDb(listOf("备份验证"))
        PlainDbMigrator.migrateIfNeeded(context, dbName)

        val backupRoot = File(context.filesDir, "PlainMigration")
        assertThat(backupRoot.exists()).isTrue()
        val sessions = backupRoot.listFiles { f -> f.isDirectory }
        assertThat(sessions).isNotNull()
        assertThat(sessions!!.isNotEmpty()).isTrue()
        // 备份是**明文**快照——有意的：这是最后一道人工兜底
        val backedUp = File(sessions.first(), dbName)
        assertThat(backedUp.exists()).isTrue()
        assertThat(EncryptedDbOpener.isEncrypted(backedUp)).isFalse()
        assertThat(backedUp.length()).isGreaterThan(0L)
    }

    @Test
    fun `迁移后user_version保持不变`() {
        assumeTrue("需要 SQLCipher native 库，请在真机/仪器测试中运行", nativeAvailable)

        createPlainDb()
        PlainDbMigrator.migrateIfNeeded(context, dbName)

        EncryptedDbOpener.openReadWrite(context, File(dbDir, dbName).absolutePath).use { db ->
            // sqlcipher_export 会复制 user_version；Room 依赖它判断 schema 版本
            assertThat(db.version).isEqualTo(35)
        }
    }

    @Test
    fun `已是加密库_重复调用迁移不改变文件`() {
        assumeTrue("需要 SQLCipher native 库，请在真机/仪器测试中运行", nativeAvailable)

        val plainDb = createPlainDb(listOf("A"))
        PlainDbMigrator.migrateIfNeeded(context, dbName)
        val afterFirst = plainDb.readBytes()

        val second = PlainDbMigrator.migrateIfNeeded(context, dbName)

        assertThat(second).isTrue()
        assertThat(plainDb.readBytes()).isEqualTo(afterFirst)
        assertThat(PlainDbMigrator.isDone(context, dbName)).isTrue()
    }

    // ================================================================
    // D. 加密有效性：无密钥读不出（安全性的直接证据，必须真机）
    // ================================================================

    @Test
    fun `加密库_用原生SQLite打开失败`() {
        assumeTrue("需要 SQLCipher native 库，请在真机/仪器测试中运行", nativeAvailable)

        val plainDb = createPlainDb(listOf("机密学员"))
        PlainDbMigrator.migrateIfNeeded(context, dbName)
        assertThat(EncryptedDbOpener.isEncrypted(plainDb)).isTrue()

        val threw = try {
            SQLiteDatabase.openDatabase(
                plainDb.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            ).use { db ->
                db.rawQuery("SELECT COUNT(*) FROM students;", null).use { it.moveToFirst() }
            }
            false
        } catch (e: Exception) {
            true
        }

        // ★ 核心安全断言：没有密钥就读不出数据
        assertThat(threw).isTrue()
    }

    @Test
    fun `加密库_文件内不含明文业务数据`() {
        assumeTrue("需要 SQLCipher native 库，请在真机/仪器测试中运行", nativeAvailable)

        val plainDb = createPlainDb(listOf("13800138000"))
        PlainDbMigrator.migrateIfNeeded(context, dbName)

        val asLatin = String(plainDb.readBytes(), Charsets.ISO_8859_1)
        assertThat(asLatin.contains("13800138000")).isFalse()
    }
}
