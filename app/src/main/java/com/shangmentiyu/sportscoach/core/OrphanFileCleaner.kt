package com.shangmentiyu.sportscoach.core

import android.content.Context
import android.util.Log
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import org.json.JSONArray
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 本地孤立文件清理（处理器层）：扫描并清理 App 沙盒内的孤立文件。

 * 设计目标（第3轮新增）：
 * - 解决长期使用后 App 沙盒累积的孤立文件占用存储空间问题
 * - 三类清理目标：
 *   1) 签到照片孤立文件：`filesDir/sign_photos/` 中存在但无任何 Lesson 引用的照片
 *   2) 临时缓存过期文件：`cacheDir/sign_photos_tmp/` 与 `cacheDir` 下 `.tmp` 后缀文件中超过 7 天的临时文件
 *   3) 接收目录残留文件：`filesDir/received/` 中超过 30 天的旧文件
 *
 * 安全策略：
 * - 默认 dryRun=true，仅扫描统计不删除，由 UI 确认后再执行实际清理
 * - 删除照片前必须确认无任何 Lesson.photoPath / signOutPhotoPath / contentImages 引用
 * - 删除临时文件前必须确认文件最后修改时间超过保留阈值
 * - 不删除 .nomedia 文件（防止系统相册扫描）
 * - 删除失败的单个文件不影响其他文件清理（容错）
 *
 * 与 [BackupManager] 的协作：
 * - 备份恢复（restore）操作可能解压出未在数据库中登记的临时文件
 * - 备份失败时残留的 .part 文件也由本类清理
 *
 * 调用时机：
 * - 设置页「清理缓存」按钮（用户主动触发）
 * - App 启动后空闲时段（可选扩展，通过 WorkManager 调度）
 *
 * 性能策略：
 * - 一次性 getAllOnce 全部 lessons，在内存中构建「被引用文件集合」
 * - 单次扫描 + 单次比对，避免对每个文件都查数据库
 */
object OrphanFileCleaner {

    private const val TAG = "OrphanFileCleaner"

    /** 签到照片目录名（与 [PhotoCrypto] 一致） */
    private const val PHOTO_DIR_NAME = "sign_photos"

    /** 临时照片目录名（与 [SignPhotoCard] 一致） */
    private const val PHOTO_TMP_DIR_NAME = "sign_photos_tmp"

    /** Socket 接收文件目录名（与 [SocketTransferManager] 一致） */
    private const val RECEIVED_DIR_NAME = "received"

    /** 临时文件保留天数（超过此天数才清理） */
    private const val TMP_RETENTION_DAYS = 7L

    /** 接收文件保留天数 */
    private const val RECEIVED_RETENTION_DAYS = 30L

    /**
     * 清理结果报告（数据类）。
     *
     * @param orphanPhotos 孤立签到照片文件列表（绝对路径）
     * @param staleTempFiles 过期临时文件列表（绝对路径）
     * @param staleReceivedFiles 过期接收文件列表（绝对路径）
     * @param totalSizeBytes 可回收总字节数
     * @param deletedCount 实际删除的文件数（dryRun=true 时为 0）
     * @param deletedBytes 实际回收的字节数（dryRun=true 时为 0）
     * @param dryRun 是否仅扫描未删除
     */
    data class CleanReport(
        val orphanPhotos: List<String>,
        val staleTempFiles: List<String>,
        val staleReceivedFiles: List<String>,
        val totalSizeBytes: Long,
        val deletedCount: Int,
        val deletedBytes: Long,
        val dryRun: Boolean
    ) {
        /** 可读的汇总文本，UI 可直接展示 */
        fun summaryText(): String = buildString {
            append("扫描结果：\n")
            append("• 孤立签到照片：${orphanPhotos.size} 个\n")
            append("• 过期临时文件：${staleTempFiles.size} 个\n")
            append("• 过期接收文件：${staleReceivedFiles.size} 个\n")
            append("• 可回收空间：${formatSize(totalSizeBytes)}\n")
            if (!dryRun) {
                append("\n实际清理：${deletedCount} 个文件，回收 ${formatSize(deletedBytes)}")
            }
        }
    }

    /**
     * 扫描并清理 App 沙盒内的孤立文件。
     *
     * @param context 上下文
     * @param db AppDatabase 实例（用于读取 Lesson 引用的照片路径）
     * @param dryRun true=仅扫描统计不删除；false=扫描后实际删除
     * @return [CleanReport] 清理结果报告
     */
    suspend fun scanAndClean(context: Context, db: AppDatabase, dryRun: Boolean = true): CleanReport {
        val referencedPhotos = collectReferencedPhotoPaths(db)
        val photoDir = File(context.filesDir, PHOTO_DIR_NAME)
        val tmpPhotoDir = File(context.cacheDir, PHOTO_TMP_DIR_NAME)
        val receivedDir = File(context.filesDir, RECEIVED_DIR_NAME)

        val orphanPhotos = scanOrphanPhotos(photoDir, referencedPhotos)
        val staleTempFiles = scanStaleFiles(tmpPhotoDir, TMP_RETENTION_DAYS)
        val staleTempFilesExtra = scanStaleFilesByPattern(
            context.cacheDir, TMP_RETENTION_DAYS, ".tmp", ".part"
        )
        val allStaleTemp = staleTempFiles + staleTempFilesExtra
        val staleReceived = scanStaleFiles(receivedDir, RECEIVED_RETENTION_DAYS)

        val allOrphans = orphanPhotos + allStaleTemp + staleReceived
        val totalSize = allOrphans.sumOf { runCatching { File(it).length() }.getOrDefault(0L) }

        var deletedCount = 0
        var deletedBytes = 0L
        if (!dryRun) {
            for (path in allOrphans) {
                val file = File(path)
                val size = runCatching { file.length() }.getOrDefault(0L)
                if (file.delete()) {
                    deletedCount++
                    deletedBytes += size
                }
            }
            Log.i(TAG, "清理完成：删除 $deletedCount 个文件，回收 ${formatSize(deletedBytes)}")
        }

        return CleanReport(
            orphanPhotos = orphanPhotos,
            staleTempFiles = allStaleTemp,
            staleReceivedFiles = staleReceived,
            totalSizeBytes = totalSize,
            deletedCount = deletedCount,
            deletedBytes = deletedBytes,
            dryRun = dryRun
        )
    }

    // ============================================================
    // 内部扫描逻辑
    // ============================================================

    /**
     * 收集数据库中所有 Lesson 引用的照片路径（绝对路径归一化）。
     * 包含 photoPath / signOutPhotoPath / contentImages(JSON 数组)。
     */
    private suspend fun collectReferencedPhotoPaths(db: AppDatabase): Set<String> {
        val allLessons = db.lessonDao().getAllOnce()
        val allArchived = runCatching { db.archivedLessonDao().getAllOnce() }.getOrDefault(emptyList())
        val referenced = mutableSetOf<String>()

        // 分别遍历 Lesson 与 ArchivedLesson（两者无共同父类，不能合并为单一 for 循环）
        for (lesson in allLessons) {
            addLessonPhotoPaths(lesson.photoPath, lesson.signOutPhotoPath, lesson.contentImages, referenced)
        }
        for (archived in allArchived) {
            addLessonPhotoPaths(archived.photoPath, archived.signOutPhotoPath, archived.contentImages, referenced)
        }
        return referenced
    }

    /** 提取单条课时记录中引用的所有照片路径（签到/签退/内容图片） */
    private fun addLessonPhotoPaths(
        photoPath: String,
        signOutPhotoPath: String,
        contentImages: String,
        out: MutableSet<String>
    ) {
        if (photoPath.isNotBlank()) out.add(File(photoPath).absolutePath)
        if (signOutPhotoPath.isNotBlank()) out.add(File(signOutPhotoPath).absolutePath)
        if (contentImages.isNotBlank() && contentImages != "[]") {
            runCatching {
                val arr = JSONArray(contentImages)
                for (i in 0 until arr.length()) {
                    val p = arr.optString(i)
                    if (p.isNotBlank()) out.add(File(p).absolutePath)
                }
            }
        }
    }

    /**
     * 扫描签到照片目录中无引用的孤立文件。
     * 跳过 .nomedia 文件与子目录。
     */
    private fun scanOrphanPhotos(photoDir: File, referenced: Set<String>): List<String> {
        if (!photoDir.exists() || !photoDir.isDirectory) return emptyList()
        val result = mutableListOf<String>()
        photoDir.listFiles()?.forEach { f ->
            if (!f.isFile) return@forEach
            if (f.name == ".nomedia") return@forEach
            if (f.absolutePath !in referenced) {
                result.add(f.absolutePath)
            }
        }
        return result
    }

    /**
     * 扫描指定目录中超过保留阈值的文件。
     */
    private fun scanStaleFiles(dir: File, retentionDays: Long): List<String> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val threshold = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays)
        val result = mutableListOf<String>()
        dir.listFiles()?.forEach { f ->
            if (!f.isFile) return@forEach
            if (f.name == ".nomedia") return@forEach
            if (f.lastModified() < threshold) {
                result.add(f.absolutePath)
            }
        }
        return result
    }

    /**
     * 扫描指定目录下（不递归）符合后缀且超过保留阈值的文件。
     * 用于清理 cacheDir 下的 *.tmp / *.part 残留文件。
     */
    private fun scanStaleFilesByPattern(
        dir: File, retentionDays: Long, vararg suffixes: String
    ): List<String> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val threshold = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays)
        val result = mutableListOf<String>()
        dir.listFiles()?.forEach { f ->
            if (!f.isFile) return@forEach
            val name = f.name.lowercase()
            if (suffixes.any { name.endsWith(it) } && f.lastModified() < threshold) {
                result.add(f.absolutePath)
            }
        }
        return result
    }

    /** 字节数转人类可读字符串（B/KB/MB/GB） */
    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        val gb = mb / 1024.0
        return "%.2f GB".format(gb)
    }
}
