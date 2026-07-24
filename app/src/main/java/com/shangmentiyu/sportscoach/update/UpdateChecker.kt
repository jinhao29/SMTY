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
 */
object UpdateChecker {

    /** GitHub 用户名（仓库所有者） */
    private const val GITHUB_OWNER = "jinhao29"

    /** 仓库名 */
    private const val GITHUB_REPO = "SMTY"

    /** API 端点：获取最新 Release */
    private const val API_LATEST_RELEASE =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    /** OkHttp 客户端（带超时配置） */
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /** Gson 解析器 */
    private val gson by lazy { Gson() }

    /**
     * 查询 GitHub 最新 Release 并判断是否需要更新。
     *
     * @return [UpdateResult] 检查结果
     */
    suspend fun checkForUpdate(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(API_LATEST_RELEASE)
                .header("Authorization", "token ${BuildConfig.GITHUB_TOKEN}")
                .header("Accept", "application/vnd.github+json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateResult.Error(
                        "GitHub API 请求失败: HTTP ${response.code}"
                    )
                }
                val body = response.body?.string()
                    ?: return@withContext UpdateResult.Error("响应体为空")

                val release = gson.fromJson(body, GitHubRelease::class.java)
                    ?: return@withContext UpdateResult.Error("解析 Release JSON 失败")

                if (release.tagName.isBlank()) {
                    return@withContext UpdateResult.Error("Release 缺少 tag_name")
                }

                val apkUrl = release.assets.firstOrNull()?.downloadUrl
                if (apkUrl.isNullOrBlank()) {
                    return@withContext UpdateResult.Error("Release 缺少 APK 附件")
                }

                // 版本比较：服务器 tag 与本地 VERSION_NAME 不同则视为有新版本
                val serverVersion = release.tagName.removePrefix("v")
                val localVersion = BuildConfig.VERSION_NAME
                if (serverVersion == localVersion) {
                    UpdateResult.UpToDate
                } else {
                    UpdateResult.NewVersionAvailable(
                        tagName = release.tagName,
                        downloadUrl = apkUrl,
                        releaseNotes = release.body
                    )
                }
            }
        } catch (e: Exception) {
            UpdateResult.Error("网络异常: ${e.message ?: "未知错误"}")
        }
    }

    /**
     * 下载 APK 文件到指定目录。
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
                .header("Authorization", "token ${BuildConfig.GITHUB_TOKEN}")
                .header("Accept", "application/octet-stream")
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

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0 && onProgress != null) {
                            val percent = (downloadedBytes * 100 / totalBytes).toInt()
                            onProgress(percent)
                        }
                    }
                    fos.flush()
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
