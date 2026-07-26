package com.shangmentiyu.sportscoach.core

import android.content.Context
import android.util.Log
import com.shangmentiyu.sportscoach.data.model.PlanImage
import com.shangmentiyu.sportscoach.data.repo.PlanImageRepository
import com.shangmentiyu.sportscoach.data.repo.StudentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 局域网训练计划图片接收器（处理器层）。
 *
 * 职责：
 * - 通过 HTTP 从电脑端下载训练计划截图
 * - 保存到 App filesDir/ImportedPlans/ 目录
 * - 解析文件名提取学员姓名
 * - 调用 [PlanImageRepository] 写入数据库，完成与学员的关联
 *
 * 协议：
 * - 电脑端：lan_plan_sender.py 启动 HTTP 服务（默认 8080 端口）
 * - 文件名格式：{学员姓名}_{YYYYMMDD}_plan.png
 * - Android 端通过 http://{host}:{port}/{filename} 下载
 *
 * 与 UdpPlanListenerService 配合：
 * - UdpPlanListenerService 收到 UDP 广播后，弹出 Notification 提示用户
 * - 用户在设置页点击"同步电脑端截图"按钮，触发本类的 [downloadAndImport]
 *
 * === v25 新增 ===
 */
object LanImageReceiver {

    private const val TAG = "LanImageReceiver"

    /** 接收图片的本地目录名（位于 filesDir 下） */
    private const val IMPORTED_DIR_NAME = "ImportedPlans"

    /** HTTP 连接超时（秒） */
    private const val HTTP_CONNECT_TIMEOUT_SEC = 5L

    /** HTTP 读取超时（秒） */
    private const val HTTP_READ_TIMEOUT_SEC = 30L

    /** 下载结果：携带成功标志 + 可读消息 + 关联的学员姓名 */
    data class DownloadResult(
        val success: Boolean,
        val localPath: String?,
        val studentName: String?,
        val matchedStudentId: String?,
        val message: String
    )

    /**
     * 下载并导入一张训练计划截图。
     *
     * 流程：
     * 1. 创建 filesDir/ImportedPlans/ 目录（若不存在）
     * 2. 通过 OkHttp 发起 GET 请求下载图片
     * 3. 保存到本地文件
     * 4. 解析文件名提取学员姓名
     * 5. 调用 [StudentRepository.getByName] 确认学员存在
     * 6. 调用 [PlanImageRepository.insert] 写入数据库
     *
     * @param context 上下文（用于定位 filesDir）
     * @param host 电脑端局域网 IP
     * @param port 电脑端 HTTP 服务端口（默认 8080）
     * @param filename 文件名（同时用于 URL 路径与本地保存名）
     * @param planImageRepo 训练计划图片仓储
     * @param studentRepo 学员仓储（用于按姓名匹配）
     * @return [DownloadResult]
     */
    suspend fun downloadAndImport(
        context: Context,
        host: String,
        port: Int,
        filename: String,
        planImageRepo: PlanImageRepository,
        studentRepo: StudentRepository
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            // 1. 准备本地目录
            val importedDir = File(context.filesDir, IMPORTED_DIR_NAME)
            if (!importedDir.exists()) {
                importedDir.mkdirs()
            }
            val localFile = File(importedDir, filename)

            // 2. 构建下载 URL
            val url = "http://$host:$port/${filename.removePrefix("/")}"
            Log.i(TAG, "开始下载训练计划截图：$url")

            // 3. OkHttp 下载
            val client = OkHttpClient.Builder()
                .connectTimeout(HTTP_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(HTTP_READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder().url(url).get().build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext DownloadResult(
                        success = false,
                        localPath = null,
                        studentName = null,
                        matchedStudentId = null,
                        message = "下载失败：HTTP ${response.code}"
                    )
                }
                val body = response.body
                    ?: return@withContext DownloadResult(
                        success = false,
                        localPath = null,
                        studentName = null,
                        matchedStudentId = null,
                        message = "下载失败：响应体为空"
                    )

                // 4. 写入本地文件
                localFile.outputStream().use { out ->
                    body.byteStream().copyTo(out)
                }
                Log.i(TAG, "图片已保存：${localFile.absolutePath}（${localFile.length()} 字节）")
            }

            // 5. 解析文件名提取学员姓名
            val studentName = parseStudentNameFromFilename(filename)
            if (studentName.isNullOrBlank()) {
                return@withContext DownloadResult(
                    success = false,
                    localPath = localFile.absolutePath,
                    studentName = null,
                    matchedStudentId = null,
                    message = "文件名解析失败：无法识别学员姓名"
                )
            }

            // 6. 按姓名查询学员（含已软删除的，避免历史数据无法关联）
            val student = studentRepo.getByNameIncludeDeleted(studentName)
            val studentId = student?.studentId

            // 7. 写入数据库
            val planImage = PlanImage(
                id = UUID.randomUUID().toString().take(8),
                studentName = studentName,
                imagePath = localFile.absolutePath,
                sourceHost = host,
                originalFilename = filename
            )
            planImageRepo.insert(planImage)

            val matchMsg = if (student != null) {
                "已关联学员：$studentName"
            } else {
                "已保存图片，但未找到姓名为「$studentName」的学员（可在学员详情手动关联）"
            }

            DownloadResult(
                success = true,
                localPath = localFile.absolutePath,
                studentName = studentName,
                matchedStudentId = studentId,
                message = "下载成功：$filename\n$matchMsg"
            )
        } catch (e: Exception) {
            Log.e(TAG, "下载训练计划截图失败", e)
            DownloadResult(
                success = false,
                localPath = null,
                studentName = null,
                matchedStudentId = null,
                message = "下载失败：${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    /**
     * 从文件名解析学员姓名。
     *
     * 文件名约定（与桌面端 lan_plan_sender.py 一致）：
     * - 格式：{学员姓名}_{YYYYMMDD}_plan.png
     * - 示例：陈书楠_20260726_plan.png → "陈书楠"
     *
     * 解析规则：
     * - 去掉扩展名
     * - 按下划线分割
     * - 第一段为学员姓名（保留中文、英文、数字）
     *
     * @param filename 文件名（含或不含路径）
     * @return 学员姓名，解析失败返回 null
     */
    fun parseStudentNameFromFilename(filename: String): String? {
        // 取最后一段路径（避免 Windows/Unix 路径分隔符差异）
        val name = filename.substringAfterLast('/').substringAfterLast('\\')
        // 去掉扩展名
        val noExt = name.substringBeforeLast('.')
        // 按下划线分割，取第一段
        val parts = noExt.split('_')
        if (parts.isEmpty()) return null
        val studentName = parts[0].trim()
        return if (studentName.isNotEmpty()) studentName else null
    }
}
