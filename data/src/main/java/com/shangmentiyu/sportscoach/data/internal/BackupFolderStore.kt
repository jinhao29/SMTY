package com.shangmentiyu.sportscoach.data.internal

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * 固定备份文件夹操作助手（v1.0.2+ 手动/自动/恢复联动）。
 *
 * 设计：
 * - 用户通过 SAF OpenDocumentTree 选一次公共目录（建议 Download），持久化授权后
 *   Uri 存 DataStore（[SettingsRepository.backupDirUri]）
 * - 手动备份 / 自动备份统一写入该文件夹，卸载应用后备份不丢
 * - 恢复时自动选取文件夹内最新备份（[PICK_LATEST] 纯函数，可单测）
 *
 * 命名约定：
 * - 手动备份：smty_backup_yyyyMMdd_HHmmss.zip（[BackupManager.generateBackupFileName]）
 * - 自动备份：AutoBackup_yyyyMMdd_HHmmss.zip（滚动保留若干份）
 * - 两类前缀都在 [BACKUP_PREFIXES] 内，恢复时统一参与"取最新"
 */
object BackupFolderStore {

    /** 参与恢复候选的备份文件前缀（手动 + 自动） */
    val BACKUP_PREFIXES = listOf("smty_backup_", "AutoBackup_")

    /** 判断文件名是否为可恢复的备份文件（纯函数） */
    fun isBackupFileName(name: String?): Boolean {
        if (name == null) return false
        return BACKUP_PREFIXES.any { name.startsWith(it) } && name.endsWith(".zip")
    }

    /**
     * 从候选 (文件名, 最后修改时间) 列表中选最新一份（纯函数，可单测）。
     * 返回 null 表示没有可用备份。
     */
    fun pickLatest(candidates: List<Pair<String, Long>>): Pair<String, Long>? =
        candidates.filter { isBackupFileName(it.first) }.maxByOrNull { it.second }

    /** 解析已持久化的备份文件夹；未设置或已失效（被删/授权丢失）返回 null */
    fun resolveFolder(context: Context, treeUriString: String?): DocumentFile? {
        if (treeUriString.isNullOrBlank()) return null
        return runCatching {
            val folder = DocumentFile.fromTreeUri(context, Uri.parse(treeUriString))
            if (folder != null && folder.exists() && folder.canWrite()) folder else null
        }.getOrNull()
    }

    /** 在文件夹内创建空 zip 占位文件，返回其文档 Uri；失败返回 null */
    fun createZipFile(context: Context, folder: DocumentFile, displayName: String): Uri? {
        return runCatching {
            val mime = "application/zip"
            val doc = folder.createFile(mime, displayName) ?: return null
            doc.uri
        }.getOrNull()
    }

    /** 列出文件夹内全部备份候选（文件名 → lastModified），按时间降序 */
    fun listBackups(context: Context, treeUriString: String?): List<Pair<String, Long>> {
        val folder = resolveFolder(context, treeUriString) ?: return emptyList()
        return runCatching {
            folder.listFiles()
                .filter { it.isFile && isBackupFileName(it.name) }
                .map { (it.name ?: "") to it.lastModified() }
                .sortedByDescending { it.second }
        }.getOrDefault(emptyList())
    }

    /** 在文件夹内按前缀+保留份数滚动清理旧备份（时间新→旧，超出 [keep] 的删除） */
    fun pruneOldBackups(context: Context, treeUriString: String?, keep: Int) {
        val folder = resolveFolder(context, treeUriString) ?: return
        runCatching {
            folder.listFiles()
                .filter { it.isFile && isBackupFileName(it.name) }
                .sortedByDescending { it.lastModified() }
                .drop(keep)
                .forEach { it.delete() }
        }
    }
}
