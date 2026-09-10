package com.shangmentiyu.sportscoach.data.internal

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 明文库 → SQLCipher 加密库的一次性迁移（v1.0.5）。
 *
 * ## 为什么必须迁移
 * v1.0.5 之前，数据库文件是**明文 SQLite**（文件头 "SQLite format 3"）。
 * 启用 SQLCipher 后，Room 用口令打开同一路径的文件：明文库没有正确的密钥头，
 * SQLCipher 会判定"文件不是数据库"，Room 随之报 `IllegalStateException`。
 * 不迁移 = 所有老用户升级后 App 打不开 → 直接闪退。
 *
 * ## 迁移策略（安全优先，绝不冒险删数据）
 * 1. 检测目标文件是否已是加密库（读文件头）。已加密 → 什么都不做，直接返回。
 * 2. 明文库存在 → 先做**全量文件级备份**（db/wal/shm 三件套）到 filesDir/PlainMigration/。
 * 3. 用 SQLCipher 在**临时路径**建新加密库，执行 `ATTACH` 明文库 + `sqlcipher_export()` 全量导入。
 *    注意：导入在临时路径上做，明文库全程只读，失败不影响原数据。
 * 4. 校验：新库能打开 + `PRAGMA integrity_check` = ok + 关键表行数与明文库一致。
 * 5. 全部通过 → 删除明文 db/wal/shm，把临时加密库 rename 到正式路径。
 *    任一步失败 → 删除临时文件，保留明文库原样，返回失败（App 可继续用旧版本打开）。
 *
 * ## 迁移标记
 * 用 SharedPreferences 记录已完成，避免每次启动重复检测文件头（成本极低，但没必要）。
 * 注意：标记**只是快捷路径**，真正的判据永远是文件头——标记存在但文件仍是明文时仍会迁移。
 */
internal object PlainDbMigrator {

    private const val TAG = "PlainDbMigrator"
    private const val PREFS_NAME = "smty_plain_migration"
    private const val BACKUP_DIR = "PlainMigration"
    private const val TMP_SUFFIX = ".migrating"

    /** 需要一起处理的三件套后缀 */
    private val SIDECAR_SUFFIXES = listOf("", "-wal", "-shm")

    /**
     * 执行迁移（幂等，可在每次 getDatabase 前调用）。
     *
     * @return true = 已是加密库或迁移成功；false = 迁移失败（调用方应提示用户）
     */
    fun migrateIfNeeded(context: Context, dbName: String): Boolean {
        val appContext = context.applicationContext
        val dbFile = appContext.getDatabasePath(dbName)

        if (!dbFile.exists() || dbFile.length() == 0L) {
            // 全新安装：无明文库，Room 会直接建加密库
            return true
        }
        if (EncryptedDbOpener.isEncrypted(dbFile)) {
            markDone(appContext, dbName)
            return true
        }

        Log.i(TAG, "检测到明文数据库，开始迁移到 SQLCipher：$dbName")
        return try {
            doMigrate(appContext, dbFile)
        } catch (e: Throwable) {
            // ⚠️ 必须捕获 Throwable 而非 Exception：SQLCipher 的 native 库加载失败会抛
            // UnsatisfiedLinkError（属于 Error 而非 Exception），若漏捕会直接崩溃。
            // 这里把一切失败都收敛为"返回 false"，由调用方（AppDatabase）给出可读提示，
            // 同时保证明文库原样保留 —— 这是数据安全的底线。
            Log.e(TAG, "数据库加密迁移失败，已保留明文库原样：${e.message}", e)
            // 清掉可能残留的半成品临时库，避免下次迁移误判
            SIDECAR_SUFFIXES.forEach { suffix ->
                File(dbFile.path + TMP_SUFFIX + suffix)
                    .takeIf { it.exists() }?.delete()
            }
            false
        }
    }

    /** 是否已迁移过（UI 可据此决定是否提示"本次启动正在加密数据库"） */
    fun isDone(context: Context, dbName: String): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(dbName, false)

    // ------------------------------------------------------------------

    private fun doMigrate(context: Context, plainDb: File): Boolean {
        val dbName = plainDb.name
        val tmpFile = File(plainDb.parentFile, "$dbName$TMP_SUFFIX")
        // 清掉上次失败残留
        SIDECAR_SUFFIXES.forEach { File(tmpFile.path + it).takeIf { f -> f.exists() }?.delete() }

        // 1) 全量文件级备份（迁移前最后一道保险）
        val backupDir = File(context.filesDir, BACKUP_DIR).apply { mkdirs() }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val sessionDir = File(backupDir, stamp).apply { mkdirs() }
        SIDECAR_SUFFIXES.forEach { suffix ->
            val src = File(plainDb.path + suffix)
            if (src.exists()) src.copyTo(File(sessionDir, src.name), overwrite = true)
        }
        Log.i(TAG, "迁移前明文库已备份到：${sessionDir.absolutePath}")

        // 2) 在临时路径建加密库并全量导入
        val passphrase = DatabaseKeyManager.getOrCreatePassphraseBytes(context)
        // ATTACH 走 SQL 层，只认字符串口令——用同一口令的十六进制形态
        val passphraseText = DatabaseKeyManager.getOrCreatePassphrase(context)
        var encrypted: net.zetetic.database.sqlcipher.SQLiteDatabase? = null
        try {
            encrypted = net.zetetic.database.sqlcipher.SQLiteDatabase.openOrCreateDatabase(
                tmpFile.absolutePath, passphrase, null, null
            )
            // ATTACH 明文库（key '' 表示无加密），再 sqlcipher_export 到主库
            // 路径中的单引号需转义，避免注入/语法错误
            val attachPath = plainDb.absolutePath.replace("'", "''")
            encrypted.rawExecSQL("ATTACH DATABASE '$attachPath' AS plaintext KEY '';")
            encrypted.rawExecSQL("SELECT sqlcipher_export('main', 'plaintext');")
            encrypted.rawExecSQL("DETACH DATABASE plaintext;")
        } finally {
            runCatching { encrypted?.close() }
        }

        // 3) 校验：能打开 + integrity ok + 关键表行数一致
        val check = verifyMigration(context, tmpFile, plainDb)
        if (!check.first) {
            Log.e(TAG, "迁移校验未通过：${check.second}，已放弃本次迁移并保留明文库")
            SIDECAR_SUFFIXES.forEach { File(tmpFile.path + it).takeIf { f -> f.exists() }?.delete() }
            return false
        }

        // 4) 校验通过 → 换名上线（先删明文三件套，再把临时库改名）
        SIDECAR_SUFFIXES.forEach { suffix ->
            File(plainDb.path + suffix).takeIf { it.exists() }?.delete()
        }
        if (!tmpFile.renameTo(plainDb)) {
            // rename 失败：尝试复制（rename 跨目录/占用失败时）
            tmpFile.copyTo(plainDb, overwrite = true)
            SIDECAR_SUFFIXES.forEach { suffix ->
                File(tmpFile.path + suffix).takeIf { it.exists() }?.delete()
            }
        }
        SIDECAR_SUFFIXES.drop(1).forEach { suffix ->
            File(tmpFile.path + suffix).takeIf { it.exists() }?.delete()
        }

        markDone(context, dbName)
        Log.i(TAG, "数据库加密迁移完成：$dbName（明文备份留存于 ${sessionDir.absolutePath}）")
        return true
    }

    /**
     * 校验迁移结果。
     *
     * 行数比对用**原生 SQLite**读明文库（它本来就是明文，原生库能开），
     * 加密库用 SQLCipher 开。这样避免在 SQLCipher 侧反复处理无密钥库的边界情况。
     *
     * @return Pair(是否通过, 说明)
     */
    private fun verifyMigration(
        context: Context,
        encryptedFile: File,
        plainDb: File
    ): Pair<Boolean, String> {
        var enc: EncryptedDbOpener.Handle? = null
        var plain: android.database.sqlite.SQLiteDatabase? = null
        try {
            // 加密库：integrity_check
            enc = EncryptedDbOpener.openReadWrite(context, encryptedFile.absolutePath)
            enc.rawQuery("PRAGMA integrity_check;", null).use { c ->
                val result = if (c.moveToFirst()) c.getString(0) else "unknown"
                if (result != "ok") return false to "加密库 integrity_check=$result"
            }

            // 行数比对（students 是关键表；空库也允许）
            val encCount = tableCount(enc, "students")
            if (encCount < 0) return false to "加密库缺少 students 表"

            // 明文库：原生 SQLite 打开（它此时仍是明文，原生库完全能读）
            // migration 尚未删除明文文件，此处是迁移后的比对快照
            plain = android.database.sqlite.SQLiteDatabase.openDatabase(
                plainDb.absolutePath, null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            val plainCount = try {
                plain.rawQuery("SELECT COUNT(*) FROM students;", null).use { c ->
                    if (c.moveToFirst()) c.getInt(0) else -1
                }
            } catch (e: Exception) {
                -1
            }
            if (plainCount != encCount) {
                return false to "students 行数不一致（明文=$plainCount, 加密=$encCount）"
            }
            return true to "ok（students=$encCount）"
        } catch (e: Exception) {
            return false to "校验异常：${e.message}"
        } finally {
            runCatching { plain?.close() }
            runCatching { enc?.close() }
        }
    }

    private fun tableCount(
        db: EncryptedDbOpener.Handle,
        table: String
    ): Int = try {
        db.rawQuery("SELECT COUNT(*) FROM `$table`;", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else -1
        }
    } catch (e: Exception) {
        -1
    }

    private fun markDone(context: Context, dbName: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(dbName, true).apply()
    }
}
