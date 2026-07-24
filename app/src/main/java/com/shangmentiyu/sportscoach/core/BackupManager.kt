package com.shangmentiyu.sportscoach.core

import android.content.Context
import android.net.Uri
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 数据备份与恢复管理器（处理器层）。
 *
 * 职责：
 * - 纯文件操作，无业务逻辑，无数据库依赖
 * - 负责数据库文件（sports_coach_db + wal + shm）与签到照片目录的打包/解包
 * - 备份格式：ZIP 压缩包（.smty_backup），包含 db 文件 + photos 目录
 *
 * 数据安全策略：
 * - 备份前调用 [AppDatabase.closeAndResetInstance] 强制 WAL checkpoint，确保数据完整
 * - 恢复前同样关闭数据库，释放文件锁，避免覆盖失败导致数据损坏
 * - 恢复后调用方必须重启 App，让 ViewModel 重新初始化
 *
 * 与桌面端 Excel 导出的区别：
 * - ExcelSync 导出的是可读的成绩报告（单次课堂/成绩档案），仅含学员+成绩
 * - BackupManager 导出的是整库二进制备份（含学员/课时包/排课/签到/照片/训练周期等全部数据）
 *
 * @see AppDatabase.closeAndResetInstance
 */
object BackupManager {

    /** 备份文件扩展名（用于文件过滤器与默认文件名） */
    const val BACKUP_EXTENSION = "smty_backup"

    /** 备份 ZIP 内数据库文件条目名 */
    private const val ZIP_ENTRY_DB = "sports_coach_db"

    /** 备份 ZIP 内 WAL 日志条目名 */
    private const val ZIP_ENTRY_DB_WAL = "sports_coach_db-wal"

    /** 备份 ZIP 内共享内存条目名 */
    private const val ZIP_ENTRY_DB_SHM = "sports_coach_db-shm"

    /** 备份 ZIP 内签到照片目录条目前缀 */
    private const val ZIP_ENTRY_PHOTOS_DIR = "SignPhotos/"

    /** 签到照片在 App 内部存储的目录名（与 PhotoCrypto 约定一致） */
    private const val PHOTOS_DIR_NAME = "SignPhotos"

    /**
     * 生成默认备份文件名：smty_backup_YYYYMMDD_HHmmss
     *
     * @return 形如 "smty_backup_20260725_143022"
     */
    fun generateBackupFileName(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "smty_backup_${sdf.format(Date())}"
    }

    /**
     * 执行完整备份：数据库 + 签到照片 → ZIP 文件。
     *
     * 流程：
     * 1. 关闭数据库单例（强制 WAL checkpoint，确保数据完整刷盘）
     * 2. 收集数据库文件（db / wal / shm，存在哪个备份哪个）
     * 3. 收集签到照片目录下所有文件
     * 4. 打包成 ZIP 写入 [outputStream]
     * 5. 重新打开数据库连接（恢复 App 正常运行）
     *
     * @param context 上下文（用于定位数据库文件路径与照片目录）
     * @param outputStream 备份文件的输出流（由调用方负责关闭）
     * @return true=备份成功；false=备份失败（IO 异常等）
     */
    fun backup(context: Context, outputStream: OutputStream): Boolean {
        // 1. 关闭数据库，触发 WAL checkpoint，确保所有学员/课程数据写入主 db 文件
        AppDatabase.closeAndResetInstance(context)

        try {
            ZipOutputStream(outputStream).use { zos ->
                // 2. 写入数据库文件
                val dbFiles = listOf(
                    ZIP_ENTRY_DB to File(context.getDatabasePath(AppDatabase.DATABASE_NAME).absolutePath),
                    ZIP_ENTRY_DB_WAL to File(context.getDatabasePath(AppDatabase.DATABASE_NAME + "-wal").absolutePath),
                    ZIP_ENTRY_DB_SHM to File(context.getDatabasePath(AppDatabase.DATABASE_NAME + "-shm").absolutePath)
                )
                for ((entryName, file) in dbFiles) {
                    if (file.exists()) {
                        putFileEntry(zos, entryName, file)
                    }
                }

                // 3. 写入签到照片目录
                val photosDir = File(context.filesDir, PHOTOS_DIR_NAME)
                if (photosDir.exists() && photosDir.isDirectory) {
                    photosDir.listFiles()?.forEach { photoFile ->
                        if (photoFile.isFile) {
                            putFileEntry(zos, ZIP_ENTRY_PHOTOS_DIR + photoFile.name, photoFile)
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            return false
        } finally {
            // 5. 无论备份成功与否，都重新打开数据库连接，恢复 App 正常运行
            AppDatabase.getDatabase(context)
        }
    }

    /**
     * 执行完整恢复：从 ZIP 文件还原数据库 + 签到照片。
     *
     * 流程：
     * 1. 关闭数据库单例（释放文件锁，避免覆盖失败）
     * 2. 清空当前数据库文件与照片目录（避免残留旧数据混入）
     * 3. 从 ZIP 解包数据库文件到原位置
     * 4. 从 ZIP 解包签到照片到原位置
     *
     * 注意：调用方必须在恢复成功后重启 App，让 ViewModel 重新初始化，
     * 否则旧的 Dao 引用会指向已关闭的数据库，导致 NPE 或脏读。
     *
     * @param context 上下文（用于定位数据库文件路径与照片目录）
     * @param inputStream 备份文件的输入流（由调用方负责关闭）
     * @return true=恢复成功；false=恢复失败（IO 异常、备份格式错误等）
     */
    fun restore(context: Context, inputStream: InputStream): Boolean {
        // 1. 关闭数据库，释放文件锁
        AppDatabase.closeAndResetInstance(context)

        try {
            // 2. 清空当前数据库文件（避免恢复后残留旧数据）
            listOf("", "-wal", "-shm").forEach { suffix ->
                val file = context.getDatabasePath(AppDatabase.DATABASE_NAME + suffix)
                if (file.exists()) {
                    file.delete()
                }
            }

            // 3. 清空当前签到照片目录
            val photosDir = File(context.filesDir, PHOTOS_DIR_NAME)
            if (photosDir.exists()) {
                photosDir.listFiles()?.forEach { it.delete() }
            }

            // 4. 从 ZIP 解包
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    when {
                        // 数据库文件
                        entry.name == ZIP_ENTRY_DB -> {
                            val target = context.getDatabasePath(AppDatabase.DATABASE_NAME)
                            target.parentFile?.mkdirs()
                            extractFile(zis, target)
                        }
                        // WAL 日志
                        entry.name == ZIP_ENTRY_DB_WAL -> {
                            val target = context.getDatabasePath(AppDatabase.DATABASE_NAME + "-wal")
                            extractFile(zis, target)
                        }
                        // 共享内存
                        entry.name == ZIP_ENTRY_DB_SHM -> {
                            val target = context.getDatabasePath(AppDatabase.DATABASE_NAME + "-shm")
                            extractFile(zis, target)
                        }
                        // 签到照片
                        entry.name.startsWith(ZIP_ENTRY_PHOTOS_DIR) -> {
                            val photoName = entry.name.removePrefix(ZIP_ENTRY_PHOTOS_DIR)
                            if (photoName.isNotBlank()) {
                                val target = File(context.filesDir, "$PHOTOS_DIR_NAME/$photoName")
                                target.parentFile?.mkdirs()
                                extractFile(zis, target)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            return true
        } catch (e: Exception) {
            return false
        }
        // 注意：恢复成功后不在此处重开数据库，由调用方负责重启 App
    }

    /**
     * 将单个文件写入 ZIP 条目。
     *
     * @param zos ZIP 输出流
     * @param entryName 条目名
     * @param file 源文件
     */
    private fun putFileEntry(zos: ZipOutputStream, entryName: String, file: File) {
        zos.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var len = fis.read(buffer)
            while (len > 0) {
                zos.write(buffer, 0, len)
                len = fis.read(buffer)
            }
        }
        zos.closeEntry()
    }

    /**
     * 从 ZIP 输入流解包单个文件到目标位置。
     *
     * @param zis ZIP 输入流（已定位到条目）
     * @param target 目标文件
     */
    private fun extractFile(zis: ZipInputStream, target: File) {
        FileOutputStream(target).use { fos ->
            val buffer = ByteArray(8192)
            var len = zis.read(buffer)
            while (len > 0) {
                fos.write(buffer, 0, len)
                len = zis.read(buffer)
            }
        }
    }

    /**
     * 通过 SAF Uri 创建备份输出流（用于写入用户选择的位置）。
     *
     * @param context 上下文
     * @param uri 用户通过 SAF 选择的目标文件 Uri
     * @return 输出流，调用方负责关闭
     */
    fun openBackupOutputStream(context: Context, uri: Uri): OutputStream? {
        return context.contentResolver.openOutputStream(uri)
    }

    /**
     * 通过 SAF Uri 创建备份输入流（用于读取用户选择的备份文件）。
     *
     * @param context 上下文
     * @param uri 用户通过 SAF 选择的源文件 Uri
     * @return 输入流，调用方负责关闭
     */
    fun openBackupInputStream(context: Context, uri: Uri): InputStream? {
        return context.contentResolver.openInputStream(uri)
    }
}
