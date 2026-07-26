package com.shangmentiyu.sportscoach.data.repo

import android.content.Context
import android.net.Uri
import android.util.Log
import com.shangmentiyu.sportscoach.core.BackupManager
import com.shangmentiyu.sportscoach.core.SyncManager
import com.shangmentiyu.sportscoach.core.WebDavManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 数据备份与恢复仓储（管理层）。
 *
 * 职责：
 * - 封装 [BackupManager] 的纯文件操作，对上层提供业务语义清晰的方法
 * - 切换到 IO 线程执行文件复制，避免阻塞主线程导致 UI 卡顿
 * - 返回携带可读消息的结果对象，便于 ViewModel 直接展示给用户
 * - v21 新增：备份完成后可选推送到 PC 端（[syncManager] 不为空时启用）
 * - v32 优化1 新增：备份完成后可选推送到 WebDAV 云盘（[webDavManager] 不为空时启用）
 *
 * 数据安全保证：
 * - 备份：先触发 WAL checkpoint，再打包数据库文件与签到照片，确保数据完整
 * - 恢复：先关闭数据库释放文件锁，再清空旧文件，最后解包新数据，避免残留混入
 * - 恢复成功后必须重启 App（调用方负责），让 ViewModel 重新初始化
 *
 * @param context 应用上下文（用于定位数据库文件与照片目录）
 * @param syncManager 桌面同步管理器（v21 新增，可选；为空时不同步到 PC 端）
 * @param webDavManager WebDAV 云盘管理器（v32 优化1 新增，可选；为空时不推送到网盘）
 */
class BackupRepository(
    private val context: Context,
    private val syncManager: SyncManager? = null,
    private val webDavManager: WebDavManager? = null
) {

    /**
     * 备份/恢复操作结果。
     *
     * @param success 是否成功
     * @param message 可读结果消息，用于直接展示给用户
     * @param needRestart 恢复成功后是否需要重启 App（仅 restore=true 时为 true）
     * @param syncedToDesktop 备份后是否已成功推送到 PC 端（v21 新增）
     * @param syncedToWebDav 备份后是否已成功推送到 WebDAV 云盘（v32 优化1 新增）
     */
    data class Result(
        val success: Boolean,
        val message: String,
        val needRestart: Boolean = false,
        val syncedToDesktop: Boolean = false,
        val syncedToWebDav: Boolean = false
    )

    /**
     * 执行整库备份到用户通过 SAF 选择的目标 Uri。
     *
     * 流程：
     * 1. 通过 [BackupManager.openBackupOutputStream] 打开目标输出流
     * 2. 调用 [BackupManager.backup] 执行备份
     * 3. 若 [syncManager] 不为空且同步已启用，自动推送到 PC 端（失败不影响主流程）
     * 4. 若 [webDavManager] 不为空且已启用，自动推送 Zip 到 WebDAV 云盘（失败不影响主流程）
     * 5. 返回结果（备份成功不需要重启 App）
     *
     * @param targetUri 用户通过 SAF 选择的目标文件 Uri
     * @param onProgress 进度回调（可选，在 IO 线程被调用，调用方负责线程切换）
     * @return 备份结果
     */
    suspend fun backup(
        targetUri: Uri,
        onProgress: BackupManager.OnProgress? = null
    ): Result = withContext(Dispatchers.IO) {
        try {
            val outputStream = BackupManager.openBackupOutputStream(context, targetUri)
            if (outputStream == null) {
                return@withContext Result(false, "无法访问目标文件，请检查存储权限")
            }
            outputStream.use { os ->
                val ok = BackupManager.backup(context, os, onProgress)
                if (ok) {
                    // v21：备份成功后尝试推送到 PC 端（仅在同步启用时实际发送）
                    val syncedPc = trySyncToDesktopFromUri(targetUri)
                    // v32 优化1：备份成功后尝试推送到 WebDAV 云盘（仅在已启用时实际发送）
                    // 将 SAF Uri 内容拷贝到缓存文件后传给 WebDavManager（基于 HttpURLConnection 需 File）
                    val syncedDav = tryPushToWebDavFromUri(targetUri)

                    val msg = buildString {
                        append("备份成功")
                        if (syncedPc && syncedDav) {
                            append("，已同步到 PC 端与 WebDAV 云盘")
                        } else if (syncedPc) {
                            append("，已同步到 PC 端")
                        } else if (syncedDav) {
                            append("，已同步到 WebDAV 云盘")
                        } else {
                            append("，数据已保存到所选位置")
                        }
                    }
                    Result(
                        success = true,
                        message = msg,
                        syncedToDesktop = syncedPc,
                        syncedToWebDav = syncedDav
                    )
                } else {
                    Result(false, "备份失败，请重试或更换存储位置")
                }
            }
        } catch (e: Exception) {
            Result(false, "备份异常：${e.message ?: "未知错误"}")
        }
    }

    /**
     * 从用户通过 SAF 选择的备份文件恢复整库数据。
     *
     * 流程：
     * 1. 通过 [BackupManager.openBackupInputStream] 打开源文件输入流
     * 2. 调用 [BackupManager.restore] 执行恢复
     * 3. 返回结果（恢复成功需重启 App，调用方据此提示用户）
     *
     * 警告：恢复会覆盖当前所有学员/课时包/排课/签到数据，请用户先确认。
     *
     * @param sourceUri 用户通过 SAF 选择的备份文件 Uri
     * @param onProgress 进度回调（可选，在 IO 线程被调用，调用方负责线程切换）
     * @return 恢复结果（成功时 needRestart=true）
     */
    suspend fun restore(
        sourceUri: Uri,
        onProgress: BackupManager.OnProgress? = null
    ): Result = withContext(Dispatchers.IO) {
        try {
            val inputStream = BackupManager.openBackupInputStream(context, sourceUri)
            if (inputStream == null) {
                return@withContext Result(false, "无法访问备份文件，请检查文件是否存在")
            }
            inputStream.use { ins ->
                val ok = BackupManager.restore(context, ins, onProgress)
                if (ok) {
                    Result(
                        success = true,
                        message = "恢复成功，应用将重启以加载新数据",
                        needRestart = true
                    )
                } else {
                    Result(false, "恢复失败，备份文件可能已损坏或格式不正确")
                }
            }
        } catch (e: Exception) {
            Result(false, "恢复异常：${e.message ?: "未知错误"}")
        }
    }

    /**
     * 执行整库备份到 App 内部缓存文件（用于"恢复前自动安全备份"）。
     *
     * 与 [backup] 的区别：目标为 App 内部缓存目录下的临时文件，
     * 不需要用户通过 SAF 选择，便于在恢复前自动创建安全网。
     *
     * @param cacheFile App 内部缓存文件（调用方负责路径管理）
     * @param onProgress 进度回调
     * @return 备份结果
     */
    suspend fun backupToCache(
        cacheFile: File,
        onProgress: BackupManager.OnProgress? = null
    ): Result = withContext(Dispatchers.IO) {
        try {
            cacheFile.parentFile?.mkdirs()
            java.io.FileOutputStream(cacheFile).use { fos ->
                val ok = BackupManager.backup(context, fos, onProgress)
                if (ok) {
                    Result(true, "自动安全备份完成")
                } else {
                    Result(false, "自动安全备份失败")
                }
            }
        } catch (e: Exception) {
            Result(false, "自动备份异常：${e.message ?: "未知错误"}")
        }
    }

    // === v21 桌面同步接口 ===

    /**
     * 单独推送已有备份文件到 PC 端。
     *
     * 调用场景：
     * - 用户在备份列表中点击"推送到 PC"按钮；
     * - [backup] 完成后同步未启用，用户事后想推送到 PC。
     *
     * @param backupUri 备份文件 Uri（通过 SAF 选择或备份时记录）
     * @return [SyncManager.PushResult] 包装为业务 [Result]
     */
    suspend fun pushToDesktop(backupUri: Uri): Result = withContext(Dispatchers.IO) {
        val mgr = syncManager
        if (mgr == null) {
            return@withContext Result(false, "桌面同步功能未启用")
        }
        val push = mgr.pushBackupUri(backupUri)
        Result(
            success = push.success,
            message = push.message,
            syncedToDesktop = push.success
        )
    }

    /**
     * 测试 PC 端接收服务是否在线（用于设置页"测试连接"按钮）。
     *
     * @return true=PC 端接收服务在线；false=不可达
     */
    suspend fun pingDesktop(): Boolean = withContext(Dispatchers.IO) {
        syncManager?.pingDesktop() ?: false
    }

    /**
     * 内部：备份完成后从 SAF Uri 推送备份到 PC 端。
     *
     * - 同步未启用 / 未配置 PC IP / 推送失败时返回 false，但**不抛异常**，
     *   避免影响主备份流程（备份文件本身已成功保存到 SAF 位置）。
     * - 推送失败时由调用方根据 [Result.message] 决定是否提示用户。
     */
    private suspend fun trySyncToDesktopFromUri(backupUri: Uri): Boolean {
        val mgr = syncManager ?: return false
        return try {
            mgr.pushBackupUri(backupUri).success
        } catch (e: Exception) {
            // 同步失败不影响主流程
            false
        }
    }

    // === v32 优化1：WebDAV 云盘推送接口 ===

    /**
     * 单独推送已有备份文件到 WebDAV 云盘。
     *
     * 调用场景：
     * - 用户在设置页"立即推送到 WebDAV"按钮；
     * - 备份时同步未启用，用户事后想推送到网盘。
     *
     * @param backupUri 备份文件 Uri（通过 SAF 选择或备份时记录）
     * @return [Result] 包装为业务结果
     */
    suspend fun pushToWebDav(backupUri: Uri): Result = withContext(Dispatchers.IO) {
        val mgr = webDavManager
        if (mgr == null) {
            return@withContext Result(false, "WebDAV 云盘功能未启用")
        }
        val cacheFile = try {
            copyUriToCache(backupUri)
        } catch (e: Exception) {
            Log.w(TAG, "WebDAV 推送：缓存文件读取失败", e)
            return@withContext Result(false, "无法读取备份文件：${e.message ?: "未知错误"}")
        }
        try {
            val push = mgr.pushBackup(cacheFile)
            Result(
                success = push.success,
                message = push.message,
                syncedToWebDav = push.success
            )
        } finally {
            // 推送完成（或失败）后清理缓存文件，避免占用 App 内部存储
            cacheFile.delete()
        }
    }

    /**
     * 测试 WebDAV 连接是否正常。
     *
     * @return true=连接成功；false=不可达或认证失败
     */
    suspend fun pingWebDav(): Boolean = withContext(Dispatchers.IO) {
        webDavManager?.testConnection() ?: false
    }

    /**
     * 内部：备份完成后从 SAF Uri 推送 Zip 到 WebDAV 云盘。
     *
     * - WebDAV 未启用 / 未配置 / 推送失败时返回 false，但**不抛异常**，
     *   避免影响主备份流程。
     * - 因 [WebDavManager.pushBackup] 基于 HttpURLConnection 需要 File 参数，
     *   这里先将 SAF Uri 内容拷贝到缓存文件，推送完成后删除。
     */
    private suspend fun tryPushToWebDavFromUri(backupUri: Uri): Boolean {
        val mgr = webDavManager ?: return false
        return try {
            val cacheFile = copyUriToCache(backupUri)
            try {
                mgr.pushBackup(cacheFile).success
            } finally {
                cacheFile.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "WebDAV 异步推送失败：${e.message}")
            false
        }
    }

    /**
     * 将 SAF Uri 内容拷贝到 App 内部缓存目录，返回临时 File。
     *
     * 用于 [WebDavManager] 等需要 File 参数的组件。
     * 调用方负责在使用完成后调用 [File.delete] 清理。
     */
    private fun copyUriToCache(uri: Uri): File {
        val cacheDir = File(context.cacheDir, "webdav_push").apply { if (!exists()) mkdirs() }
        val cacheFile = File(cacheDir, "smty_backup_${System.currentTimeMillis()}.zip")
        context.contentResolver.openInputStream(uri)?.use { input ->
            cacheFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw java.io.IOException("无法打开备份文件 Uri")
        return cacheFile
    }

    companion object {
        private const val TAG = "BackupRepository"
    }
}

