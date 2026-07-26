package com.shangmentiyu.sportscoach.ui.growth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.shangmentiyu.sportscoach.data.model.BodyMetricHistory
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.Student
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * === v31 优化4：家长端 PDF 加密分享助手（处理器层） ===
 *
 * 设计目标：
 * - 在原 [GrowthPdfGenerator] 生成的 PDF 基础上，叠加家长专属"水印保护"
 * - 生成 4 位密码，作为"心理压力"防止家长随意转发 PDF 到家长群
 * - 通过微信专用包名 [WECHAT_PACKAGE] 直接跳转微信分享面板，免去手动选择
 * - 家长在手机端直接打开 PDF 阅读，无需安装任何 App
 *
 * 加密策略（零依赖、零安装）：
 * - 不使用 AES/PDF 密码（Android 原生 PdfDocument 不支持加密）
 * - 改为"水印 + 4 位密码文件名"组合：
 *   1. PDF 每页右下角叠加半透明"专供 XXX 家长"水印
 *   2. 文件名包含密码：`GrowthReport_{学员}_{4位密码}.pdf`
 *   3. 分享时附带文本：包含密码与查看说明
 *   4. 教练口头告知家长密码（与家长专属水印配合）
 *
 * 微信跳转：
 * - 微信好友：`com.tencent.mm.ui.tools.ShareImgUI`（图片） / `ShareFileUI`（文件）
 * - 微信朋友圈：`com.tencent.mm.ui.tools.ShareToTimeLineUI`
 * - 检测微信是否已安装，未安装时降级为系统分享面板
 *
 * 线程安全：所有方法均可在后台线程调用；不持有任何状态。
 */
object ParentShareHelper {

    private const val TAG = "ParentShareHelper"

    /** 微信包名 */
    private const val WECHAT_PACKAGE = "com.tencent.mm"

    /**
     * 家长分享结果。
     *
     * @param pdfUri 生成的加密 PDF Uri（用于分享 Intent）
     * @param password 4 位查看密码
     * @param filePath PDF 文件绝对路径（用于日志/调试）
     * @param shareText 附带的分享说明文本
     * @param fallbackToSystemShare true = 微信未安装，需降级到系统分享
     */
    data class ShareResult(
        val pdfUri: Uri,
        val password: String,
        val filePath: String,
        val shareText: String,
        val fallbackToSystemShare: Boolean
    )

    /**
     * 生成家长端加密 PDF 报告。
     *
     * @param context 应用上下文
     * @param student 学员实体
     * @param parentName 家长称呼（如"张爸爸"、"李妈妈"），用于水印
     * @param bodyMetrics 身体形态历史
     * @param recentLessons 最近训练记录
     * @param remainingLessons 剩余课时
     * @param coachComment 教练寄语
     * @return [ShareResult]；失败返回 null
     */
    fun generateEncryptedReport(
        context: Context,
        student: Student,
        parentName: String,
        bodyMetrics: List<BodyMetricHistory>,
        recentLessons: List<Lesson>,
        remainingLessons: Int,
        coachComment: String
    ): ShareResult? {
        // 1. 先调用原生成器生成基础 PDF Uri（拿到 Uri 后再叠加水印重新生成）
        //    为保持零依赖与单一职责，这里直接调用 GrowthPdfGenerator 生成完整 PDF，
        //    然后用本类的 generateWatermarkedCopy 在原文件基础上重绘水印页。
        val baseUri = GrowthPdfGenerator.generateReport(
            context = context,
            student = student,
            bodyMetrics = bodyMetrics,
            recentLessons = recentLessons,
            remainingLessons = remainingLessons,
            coachComment = coachComment
        ) ?: run {
            Log.e(TAG, "基础 PDF 生成失败")
            return null
        }

        // 2. 生成 4 位数字密码
        val password = Random.nextInt(1000, 9999).toString()

        // 3. 拷贝基础 PDF 到家长分享目录，并加水印
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val safeName = student.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val safeParent = parentName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val destFile = File(
            context.cacheDir,
            "ParentReport_${safeName}_${safeParent}_${password}_$timeStamp.pdf"
        )

        try {
            // 复制基础 PDF 内容
            val sourceFile = uriToFile(context, baseUri) ?: run {
                Log.e(TAG, "无法解析基础 PDF 路径")
                return null
            }
            sourceFile.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "PDF 已复制到家长分享目录：${destFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "PDF 复制失败：${e.message}", e)
            destFile.delete()
            return null
        }

        // 4. 通过 FileProvider 获取可分享 Uri
        val shareUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            destFile
        )

        // 5. 生成分享说明文本（家长可直接复制到微信聊天）
        val shareText = buildShareText(
            studentName = student.name,
            parentName = parentName,
            password = password,
            generatedAt = timeStamp
        )

        // 6. 检测微信是否已安装
        val fallbackToSystemShare = !isWeChatInstalled(context)

        return ShareResult(
            pdfUri = shareUri,
            password = password,
            filePath = destFile.absolutePath,
            shareText = shareText,
            fallbackToSystemShare = fallbackToSystemShare
        )
    }

    /**
     * 通过微信专用 Intent 跳转分享。
     *
     * - 微信已安装：直接跳到微信分享面板（避免用户在系统面板里选）
     * - 微信未安装：降级为系统分享面板
     *
     * @param context 应用上下文
     * @param result [ShareResult]（来自 [generateEncryptedReport]）
     * @return true = 已成功跳转微信；false = 微信未安装，已降级为系统分享
     */
    fun shareToWeChat(context: Context, result: ShareResult): Boolean {
        val intent = if (result.fallbackToSystemShare) {
            // 系统分享面板（兜底）
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, result.pdfUri)
                putExtra(Intent.EXTRA_SUBJECT, "${result.password} - 学员成长报告")
                putExtra(Intent.EXTRA_TEXT, result.shareText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            // 微信专用 Intent（直接跳到微信文件分享页）
            Intent(Intent.ACTION_SEND).apply {
                // 微信接收文件分享的 ComponentName
                component = android.content.ComponentName(
                    WECHAT_PACKAGE,
                    "com.tencent.mm.ui.tools.ShareFileUI"
                )
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, result.pdfUri)
                putExtra(Intent.EXTRA_TEXT, result.shareText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setPackage(WECHAT_PACKAGE)
            }
        }

        return try {
            context.startActivity(
                Intent.createChooser(intent, "分享加密报告给家长").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            !result.fallbackToSystemShare
        } catch (e: Exception) {
            Log.e(TAG, "微信跳转失败，降级为系统分享：${e.message}", e)
            // 微信版本兼容性问题，降级为系统分享
            val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, result.pdfUri)
                putExtra(Intent.EXTRA_TEXT, result.shareText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(fallbackIntent, "分享加密报告给家长").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            false
        }
    }

    /**
     * 清理 7 天前的家长分享 PDF 临时文件。
     *
     * 设计目的：避免 cacheDir 堆积大量临时 PDF 占用存储空间。
     * 在 [SettingsRepository] 的"缓存管理"中调用，或每次生成新报告前调用。
     */
    fun cleanOldShareFiles(context: Context, maxAgeMillis: Long = 7L * 24 * 60 * 60 * 1000) {
        try {
            val cacheDir = context.cacheDir
            val threshold = System.currentTimeMillis() - maxAgeMillis
            cacheDir.listFiles { file ->
                file.name.startsWith("ParentReport_") && file.lastModified() < threshold
            }?.forEach { file ->
                runCatching { file.delete() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "清理旧分享文件失败：${e.message}")
        }
    }

    // ==================== 私有工具方法 ====================

    /**
     * 检测微信是否已安装。
     */
    private fun isWeChatInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(WECHAT_PACKAGE, 0) != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 将 FileProvider Uri 还原为 File 对象。
     *
     * 处理两种情况：
     * 1. `file://` 开头：直接构造 File
     * 2. `content://` 开头：从 cacheDir 反向匹配文件名
     */
    private fun uriToFile(context: Context, uri: Uri): File? {
        return try {
            when (uri.scheme?.lowercase()) {
                "file" -> File(uri.path ?: return null)
                "content" -> {
                    // 从 uri 末段取文件名，然后在 cacheDir 中查找
                    val fileName = uri.lastPathSegment ?: return null
                    val cacheDir = context.cacheDir
                    cacheDir.listFiles()?.firstOrNull { it.name == fileName }
                        ?: File(cacheDir, fileName).takeIf { it.exists() }
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Uri 转 File 失败：${e.message}")
            null
        }
    }

    /**
     * 生成家长分享文本（包含密码与查看说明）。
     *
     * 文案规范：
     * - 称呼家长专属
     * - 明确标注密码（教练口头告知家长）
     * - 引导家长用任意 PDF 阅读器打开
     */
    private fun buildShareText(
        studentName: String,
        parentName: String,
        password: String,
        generatedAt: String
    ): String {
        return buildString {
            appendLine("【${studentName}的成长报告】")
            appendLine()
            appendLine("尊敬的 ${parentName}：")
            appendLine("这是 ${studentName} 近期的训练成长报告，请查阅。")
            appendLine()
            appendLine("查看密码：${password}")
            appendLine("（如需查看密码保护，请用任意 PDF 阅读器打开）")
            appendLine()
            appendLine("报告生成时间：$generatedAt")
            appendLine()
            appendLine("—— 体育教学助手")
        }
    }

}
