package com.shangmentiyu.sportscoach.core

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 启动前避风港备份管理器（处理器层）。
 *
 * 职责：
 * - App 每次启动时检查数据库文件是否存在，存在则立即复制一份到 filesDir/PreUpdateBackup/
 * - 保留最近 [MAX_BACKUP_COUNT] 份启动前备份，超出自动清理最旧文件夹
 * - 在检测到数据库版本降级或异常时，生成"急救备份"并抛异常让 App 闪退
 *   避免继续运行破坏数据
 *
 * 设计原则：
 * - 单一职责：仅负责"启动前数据库文件复制、版本检查、急救备份"
 * - 无状态：所有方法纯函数式调用，依赖 Context 注入
 * - 失败不阻塞启动：避风港备份失败仅记录日志，不影响 App 正常启动
 *   版本检查异常则会主动抛 RuntimeException 让 App 闪退
 *
 * 备份目录结构：
 * - filesDir/PreUpdateBackup/<yyyyMMdd_HHmmss>/sports_coach_db
 * - filesDir/PreUpdateBackup/<yyyyMMdd_HHmmss>/sports_coach_db-wal
 * - filesDir/PreUpdateBackup/<yyyyMMdd_HHmmss>/sports_coach_db-shm
 * - 每个子文件夹是一次完整的启动前快照，便于整体恢复
 */
object PreUpdateBackupManager {

    private const val TAG = "PreUpdateBackup"
    private const val BACKUP_DIR_NAME = "PreUpdateBackup"
    private const val EMERGENCY_DIR_NAME = "EmergencyBackup"
    private const val MAX_BACKUP_COUNT = 3

    /**
     * App 启动时调用：如果数据库文件存在，立即复制一份到 PreUpdateBackup 目录。
     *
     * 必须在 [AppDatabase.getDatabase] 之前调用，确保即使后续数据库升级失败，
     * 也有一份"启动前"的完整数据库文件可恢复。
     *
     * 特点：
     * - 同步执行，确保备份完成后再进入数据库初始化流程
     * - 同时备份 db / wal / shm 三个文件（WAL 模式下数据可能还在 wal 文件中）
     * - 失败仅记录日志，不抛异常，不阻塞 App 启动
     *
     * @param context 应用上下文
     */
    fun backupIfDbExists(context: Context) {
        try {
            val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
            if (!dbFile.exists() || dbFile.length() == 0L) {
                Log.d(TAG, "数据库文件不存在或为空，跳过启动前备份")
                return
            }

            val backupRoot = File(context.filesDir, BACKUP_DIR_NAME).apply {
                if (!exists()) mkdirs()
            }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(Date())
            val sessionDir = File(backupRoot, timestamp).apply { mkdirs() }

            // 复制 db / wal / shm 三个文件
            dbFile.copyTo(File(sessionDir, dbFile.name), overwrite = true)
            val walFile = File(dbFile.parentFile, "${dbFile.name}-wal")
            val shmFile = File(dbFile.parentFile, "${dbFile.name}-shm")
            if (walFile.exists()) {
                walFile.copyTo(File(sessionDir, walFile.name), overwrite = true)
            }
            if (shmFile.exists()) {
                shmFile.copyTo(File(sessionDir, shmFile.name), overwrite = true)
            }

            Log.i(TAG, "已生成启动前避风港备份：${sessionDir.absolutePath}")
            cleanupOldBackups(backupRoot)
        } catch (e: Exception) {
            // 备份失败不阻塞启动，仅记录日志
            Log.w(TAG, "启动前避风港备份失败（不阻塞启动）：${e.message}", e)
        }
    }

    /**
     * 检查数据库文件版本与代码版本是否匹配。
     *
     * 如果数据库文件版本 > 代码版本（降级场景），立即生成急救备份并抛
     * [RuntimeException] 让 App 闪退，避免继续运行破坏数据。
     *
     * 必须在 [AppDatabase.getDatabase] 之前调用，确保 Room 打开数据库前
     * 就能拦截版本异常。
     *
     * @param context 应用上下文
     * @param codeVersion 当前代码声明的数据库版本（对应 [AppDatabase] 的 @Database version）
     * @throws RuntimeException 当检测到数据库文件版本 > 代码版本时抛出
     */
    fun checkVersionAndEmergencyBackup(context: Context, codeVersion: Int) {
        try {
            val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
            if (!dbFile.exists() || dbFile.length() == 0L) {
                Log.d(TAG, "数据库文件不存在或为空，跳过版本检查")
                return
            }

            // 用原生 SQLite 只读模式打开数据库文件，读取 user_version
            val dbVersion = readDbVersionViaSqlite(dbFile)
            if (dbVersion <= 0) {
                Log.d(TAG, "无法读取数据库版本（可能文件损坏），跳过版本检查")
                return
            }

            if (dbVersion > codeVersion) {
                // 检测到降级：生成急救备份
                val emergencyDir = File(context.filesDir, EMERGENCY_DIR_NAME).apply {
                    if (!exists()) mkdirs()
                }
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(Date())
                val sessionDir = File(emergencyDir, timestamp).apply { mkdirs() }
                dbFile.copyTo(File(sessionDir, dbFile.name), overwrite = true)
                val walFile = File(dbFile.parentFile, "${dbFile.name}-wal")
                val shmFile = File(dbFile.parentFile, "${dbFile.name}-shm")
                if (walFile.exists()) {
                    walFile.copyTo(File(sessionDir, walFile.name), overwrite = true)
                }
                if (shmFile.exists()) {
                    shmFile.copyTo(File(sessionDir, shmFile.name), overwrite = true)
                }

                val msg = """
                    |检测到数据库版本异常（文件版本=$dbVersion, 代码版本=$codeVersion）。
                    |为避免数据丢失，App 已自动生成急救备份到：
                    |${sessionDir.absolutePath}
                    |请联系开发者查看该目录恢复数据。
                """.trimMargin()
                Log.e(TAG, msg)
                throw RuntimeException(msg)
            }

            Log.d(TAG, "数据库版本检查通过（文件版本=$dbVersion, 代码版本=$codeVersion）")
        } catch (e: RuntimeException) {
            // 重新抛出，让 App 闪退
            throw e
        } catch (e: Exception) {
            // 其他异常（如文件权限）仅记录日志，不阻塞启动
            // 让 Room 自行处理版本不匹配
            Log.w(TAG, "版本检查异常（忽略，让 Room 自行处理）：${e.message}", e)
        }
    }

    /**
     * 用原生 SQLite 只读模式打开数据库文件，读取 user_version。
     *
     * 不依赖 Room，避免触发 Room 的版本校验逻辑。
     * 如果文件损坏或无法打开，返回 -1。
     */
    private fun readDbVersionViaSqlite(dbFile: File): Int {
        var db: SQLiteDatabase? = null
        return try {
            db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            db.version
        } catch (e: Exception) {
            Log.w(TAG, "读取数据库版本失败：${e.message}")
            -1
        } finally {
            runCatching { db?.close() }
        }
    }

    /**
     * 清理旧备份，仅保留最近 [MAX_BACKUP_COUNT] 份。
     *
     * 按子文件夹最后修改时间倒序排序，删除超出数量的最旧文件夹。
     */
    private fun cleanupOldBackups(backupRoot: File) {
        val sessions = backupRoot.listFiles { f -> f.isDirectory }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        if (sessions.size <= MAX_BACKUP_COUNT) return
        sessions.drop(MAX_BACKUP_COUNT).forEach { oldDir ->
            runCatching { oldDir.deleteRecursively() }
            Log.d(TAG, "已清理旧启动前备份：${oldDir.name}")
        }
    }

    /**
     * 获取最新一份启动前避风港备份的目录路径（用于异常恢复提示）。
     *
     * @return 最新备份目录，若不存在返回 null
     */
    fun getLatestBackupPath(context: Context): File? {
        val backupRoot = File(context.filesDir, BACKUP_DIR_NAME)
        if (!backupRoot.exists()) return null
        return backupRoot.listFiles { f -> f.isDirectory }
            ?.maxByOrNull { it.lastModified() }
    }
}
