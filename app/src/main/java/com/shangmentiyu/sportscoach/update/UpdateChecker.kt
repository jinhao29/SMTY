package com.shangmentiyu.sportscoach.update

import com.google.gson.Gson
import com.shangmentiyu.sportscoach.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * 更新检查与下载处理器（纯逻辑层，无 Android UI 依赖）。
 *
 * 职责：
 * 1. 通过 GitHub API 查询最新 Release
 * 2. 比较版本号判断是否需要更新
 * 3. 下载 APK 文件到本地目录
 *
 * 使用 OkHttp + Gson，所有网络操作在 IO 线程执行。
 *
 * 设计说明：
 * - 仓库已改为 public，无需任何认证 Token
 * - 仅需 User-Agent 头（GitHub API 强制要求，缺失返回 403）
 * - 下载链路为公开的 objects.githubusercontent.com，匿名访问即可
 */
object UpdateChecker {

    /** GitHub 用户名（仓库所有者） */
    private const val GITHUB_OWNER = "jinhao29"

    /** 仓库名 */
    private const val GITHUB_REPO = "SMTY"

    /** API 端点：获取最新 Release */
    private const val API_LATEST_RELEASE =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    /** User-Agent 标识（GitHub API 要求所有请求携带，缺失返回 403） */
    private const val USER_AGENT = "SMTY-Android-App/${BuildConfig.VERSION_NAME}"

    /** OkHttp 客户端（带超时配置 + User-Agent 拦截器） */
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)          // 自动跟随重定向到 CDN
            .followSslRedirects(true)
            .addInterceptor { chain ->
                // 为所有请求附加 User-Agent（GitHub API 强制要求）
                val req = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    /** Gson 解析器 */
    private val gson by lazy { Gson() }

    /**
     * 语义化版本比较（处理器层：纯逻辑，无副作用，可独立测试）。
     *
     * 将版本字符串按 "." 分割为数字组件，逐组件比较大小。
     * 支持不等长版本号（如 "1.0" vs "1.0.1"），缺失位视为 0。
     *
     * @param v1 版本字符串1（如 "1.0.5"）
     * @param v2 版本字符串2（如 "1.0.10"）
     * @return 正数= v1 更新；负数= v2 更新；0= 相同
     */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val a = parts1.getOrElse(i) { 0 }
            val b = parts2.getOrElse(i) { 0 }
            if (a != b) return a - b
        }
        return 0
    }

    /**
     * 查询 GitHub 最新 Release 并判断是否需要更新。
     *
     * @return [UpdateResult] 检查结果
     */
    suspend fun checkForUpdate(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(API_LATEST_RELEASE)
                .header("Accept", "application/vnd.github+json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateResult.Error(
                        "GitHub API 请求失败: HTTP ${response.code} ${response.message}"
                    )
                }
                val body = response.body?.string()
                    ?: return@withContext UpdateResult.Error("响应体为空")

                val release = try {
                    gson.fromJson(body, GitHubRelease::class.java)
                } catch (e: Exception) {
                    return@withContext UpdateResult.Error("解析 Release JSON 失败: ${e.message}")
                } ?: return@withContext UpdateResult.Error("解析 Release JSON 失败")

                if (release.tagName.isBlank()) {
                    return@withContext UpdateResult.Error("Release 缺少 tag_name")
                }

                val apkUrl = release.assets.firstOrNull()?.downloadUrl
                if (apkUrl.isNullOrBlank()) {
                    return@withContext UpdateResult.Error("Release 缺少 APK 附件")
                }

                // 版本比较：语义化版本比较（serverVersion > localVersion 才提示更新）
                val serverVersion = release.tagName.removePrefix("v").trim()
                val localVersion = BuildConfig.VERSION_NAME.trim()
                if (compareVersions(serverVersion, localVersion) > 0) {
                    UpdateResult.NewVersionAvailable(
                        tagName = release.tagName,
                        downloadUrl = apkUrl,
                        releaseNotes = release.body
                    )
                } else {
                    UpdateResult.UpToDate
                }
            }
        } catch (e: Exception) {
            UpdateResult.Error("网络异常: ${e.message ?: "未知错误"}")
        }
    }

    /**
     * 下载 APK 文件到指定目录。
     *
     * 公开仓库的下载链路无需认证，OkHttp 自动跟随重定向到 CDN。
     *
     * @param downloadUrl APK 下载直链
     * @param destFile 目标文件
     * @param onProgress 下载进度回调（0-100），可选
     * @return 下载成功返回 true，失败返回 false
     */
    suspend fun downloadApk(
        downloadUrl: String,
        destFile: File,
        onProgress: ((Int) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body ?: return@withContext false
                val totalBytes = body.contentLength()

                // 确保父目录存在
                destFile.parentFile?.mkdirs()
                // 若存在旧文件则先删除
                if (destFile.exists()) destFile.delete()

                FileOutputStream(destFile).use { fos ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var downloadedBytes = 0L
                    val inputStream = body.byteStream()
                    var lastReportedPercent = -1

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0 && onProgress != null) {
                            val percent = (downloadedBytes * 100 / totalBytes).toInt()
                            // 限制进度回调频率：每 5% 才更新一次通知，避免过度刷新
                            if (percent != lastReportedPercent && percent % 5 == 0) {
                                lastReportedPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                    fos.flush()
                    // 下载完成，回调 100%
                    onProgress?.invoke(100)
                }
                true
            }
        } catch (e: Exception) {
            // 下载失败时删除不完整文件
            if (destFile.exists()) destFile.delete()
            false
        }
    }
}
