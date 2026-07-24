package com.shangmentiyu.sportscoach.data.repo

import android.content.Context
import android.net.Uri
import com.shangmentiyu.sportscoach.core.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 数据备份与恢复仓储（管理层）。
 *
 * 职责：
 * - 封装 [BackupManager] 的纯文件操作，对上层提供业务语义清晰的方法
 * - 切换到 IO 线程执行文件复制，避免阻塞主线程导致 UI 卡顿
 * - 返回携带可读消息的结果对象，便于 ViewModel 直接展示给用户
 *
 * 数据安全保证：
 * - 备份：先触发 WAL checkpoint，再打包数据库文件与签到照片，确保数据完整
 * - 恢复：先关闭数据库释放文件锁，再清空旧文件，最后解包新数据，避免残留混入
 * - 恢复成功后必须重启 App（调用方负责），让 ViewModel 重新初始化
 *
 * @param context 应用上下文（用于定位数据库文件与照片目录）
 */
class BackupRepository(private val context: Context) {

    /**
     * 备份/恢复操作结果。
     *
     * @param success 是否成功
     * @param message 可读结果消息，用于直接展示给用户
     * @param needRestart 恢复成功后是否需要重启 App（仅 restore=true 时为 true）
     */
    data class Result(
        val success: Boolean,
        val message: String,
        val needRestart: Boolean = false
    )

    /**
     * 执行整库备份到用户通过 SAF 选择的目标 Uri。
     *
     * 流程：
     * 1. 通过 [BackupManager.openBackupOutputStream] 打开目标输出流
     * 2. 调用 [BackupManager.backup] 执行备份
     * 3. 返回结果（备份成功不需要重启 App）
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
                    Result(true, "备份成功，数据已保存到所选位置")
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
        cacheFile: java.io.File,
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
}
