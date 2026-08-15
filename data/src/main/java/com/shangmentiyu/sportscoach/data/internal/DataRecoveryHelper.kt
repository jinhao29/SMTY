package com.shangmentiyu.sportscoach.data.internal

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * 数据恢复工具（处理层）：独立于 Room 框架，直接读取急救备份并验证数据完整性。
 *
 * 职责：
 * - 扫描 filesDir/EmergencyBackup/ 目录，找到最新的急救备份
 * - 将备份中的 .db / -wal / -shm 文件提取到 filesDir/RecoveryTemp/ 临时目录
 * - 使用原生 SQLite（不依赖 Room）只读打开临时数据库，执行 PRAGMA integrity_check + SELECT COUNT(*) FROM students
 * - 绝不修改原始 sports_coach_db 文件
 *
 * 设计原则：
 * - 无状态：纯静态方法，依赖 Context 注入
 * - 只读：临时数据库以 OPEN_READONLY 模式打开
 * - 安全：所有异常 try-catch，失败返回 -1 并记录日志，不阻塞 App 启动
 */
object DataRecoveryHelper {

    private const val TAG = "DataRecovery"
    private const val EMERGENCY_DIR_NAME = "EmergencyBackup"
    private const val RECOVERY_TEMP_DIR = "RecoveryTemp"
    private const val DB_NAME = "sports_coach_db"

    /**
     * 从急救备份目录中恢复数据库文件到临时目录，并用原生 SQLite 验证数据完整性。
     *
     * 不修改原始数据库文件，仅在 filesDir/RecoveryTemp/ 中操作副本。
     *
     * @param context 应用上下文
     * @return 学员数量；若急救备份不存在或损坏返回 -1
     */
    fun restoreFromEmergencyBackup(context: Context): Int {
        // 1. 查找最新的急救备份目录
        val emergencyDir = File(context.filesDir, EMERGENCY_DIR_NAME)
        if (!emergencyDir.exists()) {
            Log.e(TAG, "急救备份目录不存在：${emergencyDir.absolutePath}")
            return -1
        }

        val backupDirs = emergencyDir.listFiles { f -> f.isDirectory }
            ?.sortedByDescending { it.lastModified() }
        if (backupDirs.isNullOrEmpty()) {
            Log.e(TAG, "急救备份目录为空：${emergencyDir.absolutePath}")
            return -1
        }

        val latestBackup = backupDirs.first()
        Log.i(TAG, "找到最新急救备份：${latestBackup.absolutePath}")

        // 2. 准备临时恢复目录（清空旧内容）
        val recoveryTemp = File(context.filesDir, RECOVERY_TEMP_DIR).apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }

        // 3. 提取数据库文件（支持 ZIP 和原始文件两种格式）
        val zipFile = latestBackup.listFiles { f -> f.name.endsWith(".zip") }?.firstOrNull()
        if (zipFile != null) {
            Log.i(TAG, "检测到 ZIP 格式备份，开始解压：${zipFile.name}")
            extractZipToDir(zipFile, recoveryTemp)
        } else {
            Log.i(TAG, "检测到原始文件格式备份，开始复制")
            copyDbFiles(latestBackup, recoveryTemp)
        }

        // 4. 验证数据完整性
        val dbFile = File(recoveryTemp, DB_NAME)
        if (!dbFile.exists() || dbFile.length() == 0L) {
            Log.e(TAG, "恢复后数据库文件不存在或为空：${dbFile.absolutePath}")
            return -1
        }

        return verifyDataIntegrity(dbFile)
    }

    /**
     * 解压 ZIP 文件到目标目录。
     *
     * 仅提取 .db / -wal / -shm 文件，忽略其他条目。
     */
    private fun extractZipToDir(zipFile: File, targetDir: File) {
        try {
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    // 仅提取数据库相关文件
                    if (entry.name == DB_NAME ||
                        entry.name == "$DB_NAME-wal" ||
                        entry.name == "$DB_NAME-shm"
                    ) {
                        val targetFile = File(targetDir, entry.name)
                        FileOutputStream(targetFile).use { fos ->
                            zis.copyTo(fos)
                        }
                        Log.i(TAG, "已解压：${entry.name} (${targetFile.length()} bytes)")
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解压 ZIP 失败：${e.message}", e)
        }
    }

    /**
     * 复制原始数据库文件到目标目录（PreUpdateBackupManager 格式）。
     */
    private fun copyDbFiles(sourceDir: File, targetDir: File) {
        listOf(DB_NAME, "$DB_NAME-wal", "$DB_NAME-shm").forEach { name ->
            val src = File(sourceDir, name)
            if (src.exists()) {
                src.copyTo(File(targetDir, name), overwrite = true)
                Log.i(TAG, "已复制：$name (${src.length()} bytes)")
            }
        }
    }

    /**
     * 用原生 SQLite 只读模式打开数据库文件，验证数据完整性并统计学员数量。
     *
     * 不依赖 Room 框架，避免触发 Room 的版本校验逻辑。
     * 执行 PRAGMA integrity_check 确保数据库文件未损坏。
     *
     * @param dbFile 临时目录中的 .db 文件
     * @return 学员数量；若失败返回 -1
     */
    private fun verifyDataIntegrity(dbFile: File): Int {
        var db: SQLiteDatabase? = null
        return try {
            db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )

            // 1. 数据完整性校验
            val integrityCursor = db.rawQuery("PRAGMA integrity_check", null)
            val integrityResult = if (integrityCursor.moveToFirst()) {
                integrityCursor.getString(0)
            } else {
                "unknown"
            }
            integrityCursor.close()
            Log.i(TAG, "PRAGMA integrity_check = $integrityResult")

            if (integrityResult != "ok") {
                Log.e(TAG, "数据库完整性检查未通过：$integrityResult")
                return -1
            }

            // 2. 统计学员数量
            val cursor = db.rawQuery("SELECT COUNT(*) FROM students", null)
            val count = if (cursor.moveToFirst()) cursor.getInt(0) else -1
            cursor.close()
            Log.i(TAG, "学员数量：$count 人")

            // 3. 额外统计：课时、课时包、排课数量（便于全面评估数据完整性）
            val tables = listOf("lessons", "lesson_packages", "schedules")
            for (table in tables) {
                try {
                    val c = db.rawQuery("SELECT COUNT(*) FROM $table", null)
                    if (c.moveToFirst()) {
                        Log.i(TAG, "表 $table 记录数：${c.getInt(0)}")
                    }
                    c.close()
                } catch (e: Exception) {
                    Log.w(TAG, "表 $table 查询失败：${e.message}")
                }
            }

            count
        } catch (e: Exception) {
            Log.e(TAG, "读取数据库失败：${e.message}", e)
            -1
        } finally {
            runCatching { db?.close() }
        }
    }
}
