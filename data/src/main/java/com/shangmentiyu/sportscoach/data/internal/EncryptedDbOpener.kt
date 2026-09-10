package com.shangmentiyu.sportscoach.data.internal

import android.content.Context
import android.database.Cursor
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * 裸数据库连接工具（v1.0.5）。
 *
 * ## 为什么需要它
 * 数据库中招 SQLCipher 加密后，**任何用原生 android.database.sqlite.SQLiteDatabase
 * 打开该文件的位置都会立刻报错**（SQLCipher 库文件头不是 "SQLite format 3"，
 * 原生库视为"文件已损坏"），进而被上层判为损坏 → 回滚 → 用户永远恢复不了备份。
 *
 * 本仓共有 5 处这样的裸连接调用，全部必须经由本工具打开：
 * - BackupManager.verifyIntegrity()
 * - BackupManager.rebuildFtsIndex()
 * - BackupManager.readBackupUserVersion()
 * - PreUpdateBackupManager.readDbVersionViaSqlite()
 * - DataRecoveryHelper.verifyDataIntegrity()
 *
 * ## 为什么返回值是接口而非 SQLCipher 类型
 * SQLCipher 的 `SQLiteDatabase` 与 Android 原生 `SQLiteDatabase` 是**两个不同的类**
 * （无共同父类，仅有同名方法）。为了让单元测试（JVM 无 SQLCipher native 库）
 * 也能跑通备份/恢复链路，这里用 [Handle] 把两者统一成最小接口。
 * 生产路径恒走 SQLCipher；仅当 [com.shangmentiyu.sportscoach.data.db.AppDatabase]
 * 的测试开关被显式关闭时走原生实现。
 *
 * ## 使用注意
 * - SQLCipher 参数必须与应用打开时完全一致，否则报 "file is not a database"。
 *   默认 [SQLiteDatabase.openDatabase] 已按 4.17.0 默认（AES-256 / 4000 迭代 / HMAC-SHA512）
 *   工作，与 [net.zetetic.database.sqlcipher.SupportOpenHelperFactory] 默认一致。
 * - 口令来源必须一致：都走 [DatabaseKeyManager]。
 */
internal object EncryptedDbOpener {

    @Volatile
    private var libLoaded = false

    /**
     * 是否强制走原生 SQLite（**仅单元测试**）。
     *
     * JVM 上 SQLCipher 的 native 库无法加载（Android ABI 的 ELF），
     * 测试通过 [com.shangmentiyu.sportscoach.data.db.AppDatabase.devDisableEncryptionForTesting]
     * 置位后，这里同步降级为原生实现，使既有备份/恢复测试继续可跑。
     */
    @Volatile
    @androidx.annotation.VisibleForTesting
    var useNativeForTesting: Boolean = false

    private fun ensureLib() {
        if (libLoaded) return
        synchronized(this) {
            if (libLoaded) return
            // ⚠️ native 加载失败抛的是 UnsatisfiedLinkError（继承 Error，**不是 Exception**），
            // 而本类的调用方一律 `catch (e: Exception)`（见 BackupManager.verifyIntegrity 等），
            // 漏捕会直接崩掉进程。这里统一收敛为 IllegalStateException，
            // 让上层既有的"可读错误 + 回滚"路径照常生效。
            try {
                System.loadLibrary("sqlcipher")
            } catch (e: Throwable) {
                throw IllegalStateException(
                    "数据库加密组件加载失败（当前设备 ABI 不受支持）：${e.message}", e
                )
            }
            libLoaded = true
        }
    }

    /**
     * 数据库连接的统一句柄。
     *
     * 只暴露调用方真正用到的三个能力，避免泄漏具体实现类型。
     */
    internal interface Handle : AutoCloseable {
        fun rawQuery(sql: String, args: Array<String>?): Cursor
        fun execSQL(sql: String)
        /** PRAGMA user_version（Room 用它记录 schema 版本） */
        var version: Int
    }

    /**
     * 打开数据库（读写）。
     *
     * @param context 应用上下文（用于取 Keystore 口令）
     * @param path 数据库文件绝对路径
     */
    fun openReadWrite(context: Context, path: String): Handle =
        open(context, path, readOnly = false)

    /**
     * 打开数据库（只读）。
     *
     * ⚠️ WAL 模式下只读连接无法创建 -shm 文件，打开可能失败。
     * 备份/恢复链路请优先用 [openReadWrite]（v1.0.1 的既有教训）。
     */
    fun openReadOnly(context: Context, path: String): Handle =
        open(context, path, readOnly = true)

    private fun open(context: Context, path: String, readOnly: Boolean): Handle {
        if (useNativeForTesting) {
            // 测试路径：加密被关闭，库是明文，原生 SQLite 可读
            val flags = if (readOnly) {
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            } else {
                android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
            }
            return NativeHandle(
                android.database.sqlite.SQLiteDatabase.openDatabase(path, null, flags)
            )
        }

        ensureLib()
        val passphrase = DatabaseKeyManager.getOrCreatePassphrase(context)
        return openWithPassphrase(path, passphrase, readOnly)
    }

    /**
     * 以**指定口令**打开 SQLCipher 库（用于尝试多个口令候选，如本机口令 / 备份口令）。
     *
     * @param passphrase 十六进制口令字符串（见 [DatabaseKeyManager] 类注释）
     * @throws Exception 口令不匹配时抛出（SQLCipher 报 "file is not a database"）
     */
    fun openWithPassphrase(
        path: String,
        passphrase: String,
        readOnly: Boolean = true
    ): Handle {
        ensureLib()
        val flags = if (readOnly) SQLiteDatabase.OPEN_READONLY else SQLiteDatabase.OPEN_READWRITE
        return SqlcipherHandle(
            SQLiteDatabase.openDatabase(
                path,
                passphrase.toByteArray(Charsets.UTF_8),
                null,
                flags,
                null as net.zetetic.database.DatabaseErrorHandler?,
                null as net.zetetic.database.sqlcipher.SQLiteDatabaseHook?
            )
        )
    }

    /**
     * 判断文件是否已是 SQLCipher 加密库（读前 16 字节文件头）。
     *
     * SQLCipher 加密后的文件头是随机盐，**不是** "SQLite format 3"。
     * 明文库以 "SQLite format 3\0" 开头。
     */
    fun isEncrypted(file: java.io.File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        return try {
            java.io.RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(16)
                raf.readFully(header)
                header.copyOfRange(0, 15).toString(Charsets.US_ASCII) != "SQLite format 3"
            }
        } catch (e: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------

    private class SqlcipherHandle(private val db: SQLiteDatabase) : Handle {
        override fun rawQuery(sql: String, args: Array<String>?): Cursor = db.rawQuery(sql, args)
        override fun execSQL(sql: String) = db.execSQL(sql)
        override var version: Int
            get() = db.version
            set(value) { db.version = value }
        override fun close() = db.close()
    }

    private class NativeHandle(
        private val db: android.database.sqlite.SQLiteDatabase
    ) : Handle {
        override fun rawQuery(sql: String, args: Array<String>?): Cursor = db.rawQuery(sql, args)
        override fun execSQL(sql: String) = db.execSQL(sql)
        override var version: Int
            get() = db.version
            set(value) { db.version = value }
        override fun close() = db.close()
    }
}
