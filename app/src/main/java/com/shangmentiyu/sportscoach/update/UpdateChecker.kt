package com.shangmentiyu.sportscoach.update

import android.util.Log
import com.google.gson.Gson
import com.shangmentiyu.sportscoach.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * 更新检查与下载处理器（纯逻辑层，无 Android UI 依赖）。
 *
 * === v33 优化（GitHub 自动更新稳定化） ===
 * 1. User-Agent 强制注入：所有请求（含重定向 CDN）统一携带 "SportsCoachApp/<version>"
 * 2. URL 写死为最新 Release API：https://api.github.com/repos/jinhao29/SMTY/releases/latest
 * 3. 下载改用 RandomAccessFile 实现断点续传：网络抖动重试不重新下整个包
 * 4. 异常分类：SocketTimeoutException 单独抛出给上层，弹出"网络不稳定"提示
 *
 * === ⚠️ 健身房实测签名警告（功能 5） ===
 * 在 GitHub 发布的 APK 必须使用 release 签名文件打包，绝不能用 debug 签名！
 * 否则手机端覆盖安装时会提示"解析包错误 / 应用未安装"。
 * 签名配置步骤：
 *   1. 在 app/build.gradle.kts 中配置 signingConfigs.release（storeFile / storePassword / keyAlias / keyPassword）
 *   2. 在 buildTypes.release 中引用 signingConfig = signingConfigs.getByName("release")
 *   3. 打包命令：./gradlew :app:assembleRelease
 *   4. 上传 app/build/outputs/apk/release/app-release.apk 到 GitHub Release 附件
 *   5. 务必保持签名文件不变，避免与历史版本签名冲突导致无法升级
 *
 * 异常兜底策略：
 * - HTTP 404：仓库尚未发布 Release → 静默降级为 UpToDate，仅 Log.d，绝不弹窗
 * - SocketTimeoutException（下载阶段）：抛出 DownloadException 给上层弹"网络不稳定"
 * - SocketTimeoutException（检查阶段）：静默降级为 UpToDate
 * - 其他 IO 异常：静默降级为 UpToDate，不阻塞用户
 */
object UpdateChecker {

    // === 诊断统一 Tag：在 Logcat 过滤 "AutoUpdate" 即可看到全链路日志 ===
    private const val TAG = "AutoUpdate"

    /** GitHub 用户名（仓库所有者）—— ⚠️ 请核对！ */
    private const val GITHUB_OWNER = "jinhao29"

    /** 仓库名 —— ⚠️ 请核对！ */
    private const val GITHUB_REPO = "SMTY"

    /** API 端点：获取最新 Release（写死，与需求功能 1 一致） */
    private const val API_LATEST_RELEASE =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    /**
     * User-Agent 标识（GitHub API 强制要求所有请求携带，缺失返回 403）。
     *
     * 格式规范：AppName/Version
     * 使用 BuildConfig.VERSION_NAME 让 UA 随版本升级，便于 GitHub 统计与排查。
     */
    private const val USER_AGENT = "SportsCoachApp/${BuildConfig.VERSION_NAME}"

    /**
     * OkHttp 客户端（带超时配置 + User-Agent 拦截器）。
     *
     * 关键：使用 addInterceptor + addHeader 强制注入 User-Agent，
     * 保证所有请求（含重定向后的 CDN 请求）都携带该头。
     * 同时注入 Accept 头，明确请求 GitHub API v3 JSON 响应。
     */
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)          // 自动跟随重定向到 CDN
            .followSslRedirects(true)
            .addInterceptor { chain ->
                // 为所有请求附加 User-Agent（GitHub API 强制要求）
                val req = chain.request().newBuilder()
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Accept", "application/vnd.github+json")
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    /** Gson 解析器 */
    private val gson by lazy { Gson() }

    /**
     * 下载异常（功能 4 容错降级用）。
     *
     * 调用方捕获此异常后，可读取 [userMessage] 直接弹 Toast / 通知，
     * 区分"网络不稳定"与"普通失败"两种场景。
     */
    class DownloadException(
        val userMessage: String,
        cause: Throwable? = null
    ) : Exception(userMessage, cause)

    /**
     * 从 GitHub Release tag_name 中提取数字部分作为整数版本号（处理器层：纯逻辑，可独立测试）。
     *
     * === v33+ 版本比对逻辑（与 versionCode 整数对比） ===
     * GitHub Release 的 tag_name 形如 "v33"、"v1.0.45"、"v33-beta" 等，
     * 本函数提取其中第一段连续数字作为 remoteVersionCode：
     * - "v33" → 33
     * - "v1.0.45" → 1（取首段，与 CI 注入的 VERSION_CODE=run_number 对比时
     *   仅当 tag 形如 "v<run_number>" 时才能正确触发更新）
     * - "v33-beta" → 33
     * - "latest" → 0（无法解析视为 0，不触发更新）
     *
     * 设计依据：本项目 CI 通过 `github.run_number` 注入 versionCode（每次 push 严格递增），
     * GitHub Release tag 同样使用 `v<run_number>` 格式（如 v33），
     * 因此 tag_name 的数字部分 === 远端 versionCode，可直接与本地
     * [BuildConfig.VERSION_CODE] 整数大小对比。
     *
     * === v33 数据流加固：使用 substringAfter("v").toIntOrNull() 提取整数 ===
     * 原实现使用 `Regex("\\d+").find(cleaned)`，可正确提取但写法偏复杂。
     * 改为用户明确要求的 `substringAfter("v").toIntOrNull()` 形式：
     * - "v33".substringAfter("v") = "33" → toIntOrNull() = 33 ✓
     * - "v33-beta".substringAfter("v") = "33-beta" → toIntOrNull() = null → 0（降级）
     * - "V33".substringAfter("v") = "V33"（小写 v 找不到，保留原串） → toIntOrNull() = null → 0
     *   → 因此先 toLowerCase 再 substringAfter，兼容大写 V 前缀
     * - 非常规 tag（如 "latest"）→ toIntOrNull() = null → 0（不触发更新）
     *
     * 整数比对逻辑保持不变：`remoteVersionCode > localVersionCode` 才触发更新，
     * 杜绝字符串比对带来的"明明有更新却提示无更新"问题。
     *
     * @param tagName GitHub Release tag_name（如 "v33"）
     * @return 提取出的整数版本号；无法解析返回 0
     */
    private fun extractVersionCodeFromTag(tagName: String): Int {
        // 1. 小写化以兼容 V33 / v33 两种前缀
        // 2. substringAfter("v") 取 "v" 之后的部分（找不到则返回原串）
        // 3. toIntOrNull() 尝试转为整数，失败返回 null
        // 4. ?: 0 兜底，确保异常 tag 不触发更新
        return tagName.lowercase()
            .substringAfter("v")
            .toIntOrNull()
            ?: 0
    }

    /**
     * 查询 GitHub 最新 Release 并判断是否需要更新。
     *
     * 异常兜底策略：
     * - HTTP 404：仓库尚未发布 Release → 返回 [UpdateResult.UpToDate]，仅 Log.d，绝不弹窗
     * - 网络异常（超时/无法解析主机）：→ 返回 [UpdateResult.UpToDate]，仅日志记录
     * - 其他 HTTP 错误码 / JSON 解析错误：→ 返回 [UpdateResult.Error]
     */
    suspend fun checkForUpdate(): UpdateResult = withContext(Dispatchers.IO) {
        // === 全链路诊断日志：入口埋点 ===
        // 在 Logcat 过滤 "AutoUpdate" 即可看到从入口到最终返回的完整链路
        Log.d(
            TAG,
            "========== 开始检查更新 =========="
        )
        Log.d(
            TAG,
            "开始检查更新，本地版本: ${BuildConfig.VERSION_NAME}, 本地 Code: ${BuildConfig.VERSION_CODE}"
        )
        Log.d(TAG, "请求端点: $API_LATEST_RELEASE")
        Log.d(TAG, "User-Agent: $USER_AGENT")

        try {
            val request = Request.Builder()
                .url(API_LATEST_RELEASE)
                .build()  // User-Agent / Accept 已由拦截器统一注入

            Log.d(TAG, ">> 发起 HTTP 请求...")
            httpClient.newCall(request).execute().use { response ->
                Log.d(
                    TAG,
                    "<< 收到响应：HTTP ${response.code} ${response.message}, " +
                            "url=${response.request.url}"
                )

                if (!response.isSuccessful) {
                    // === 功能 4：404 静默拦截 ===
                    // 仓库尚未发布 Release 时 GitHub 返回 404，
                    // 此时绝不弹错误窗，仅 Log.d 记录后视为"已是最新"
                    if (response.code == 404) {
                        Log.w(
                            TAG,
                            "❌ GitHub 仓库 $GITHUB_OWNER/$GITHUB_REPO 暂无 Release（HTTP 404），" +
                                    "视为已是最新版本，不弹错误窗"
                        )
                        return@withContext UpdateResult.UpToDate
                    }

                    // 其他 HTTP 错误码：返回 Error 供 UI 诊断
                    val hint = when (response.code) {
                        403 -> "\n可能原因：GitHub API 触发速率限制（未认证 60 次/小时/IP），请稍后再试。"
                        401 -> "\n可能原因：仓库为私有，需要授权才能访问。请将仓库设为 Public。"
                        500, 502, 503 -> "\n可能原因：GitHub 服务暂时不可用，请稍后再试。"
                        else -> ""
                    }
                    Log.e(TAG, "❌ HTTP 请求失败: ${response.code} ${response.message}$hint")
                    return@withContext UpdateResult.Error(
                        "GitHub API 请求失败: HTTP ${response.code} ${response.message}$hint"
                    )
                }

                val body = response.body?.string()
                if (body == null) {
                    Log.e(TAG, "❌ 响应体为空（response.body == null）")
                    return@withContext UpdateResult.Error("响应体为空")
                }
                // 打印响应体长度 + 前 500 字符，便于排查 JSON 解析问题
                Log.d(TAG, "✅ 响应体长度: ${body.length} 字符")
                Log.d(TAG, "响应体预览（前 500 字符）: ${body.take(500)}")

                val release = try {
                    gson.fromJson(body, GitHubRelease::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 解析 Release JSON 失败: ${e.message}", e)
                    return@withContext UpdateResult.Error("解析 Release JSON 失败: ${e.message}")
                }
                if (release == null) {
                    Log.e(TAG, "❌ 解析 Release JSON 返回 null（gson.fromJson 返回 null）")
                    return@withContext UpdateResult.Error("解析 Release JSON 失败")
                }

                Log.d(
                    TAG,
                    "✅ Release 解析成功: tagName=${release.tagName}, " +
                            "assetsCount=${release.assets.size}, bodyLength=${release.body.length}"
                )

                if (release.tagName.isBlank()) {
                    Log.e(TAG, "❌ Release 缺少 tag_name（tagName 为空）")
                    return@withContext UpdateResult.Error("Release 缺少 tag_name")
                }

                val apkUrl = release.assets.firstOrNull()?.downloadUrl
                if (apkUrl.isNullOrBlank()) {
                    // Release 已发布但没有 APK 附件：视为"无可用更新"，避免错误弹窗
                    Log.w(
                        TAG,
                        "❌ Release ${release.tagName} 已发布但缺少 APK 附件！" +
                                "assets=${release.assets.size}, " +
                                "请在 GitHub Release 页面上传 APK 文件后重试"
                    )
                    return@withContext UpdateResult.UpToDate
                }
                Log.d(TAG, "✅ APK 下载链接: $apkUrl")

                // === v33+ 版本比对：tag_name 数字部分 vs 本地 versionCode 整数对比 ===
                // 从 GitHub Release tag_name（如 "v33"）提取数字部分作为远端 versionCode
                // 与 Android 内部的 BuildConfig.VERSION_CODE（CI 注入的 github.run_number）
                // 进行整数大小对比：remoteVersionCode > localVersionCode 即视为有新版本
                //
                // 此逻辑兼容本地开发版（versionCode=1, versionName="0.0.1"）：
                // 只要 GitHub 上有 tag 数字 > 1 的 Release，本地编译安装的 App 也会触发更新弹窗
                val remoteVersionCode = extractVersionCodeFromTag(release.tagName)
                val localVersionCode = BuildConfig.VERSION_CODE
                val localVersionName = BuildConfig.VERSION_NAME
                Log.d(
                    TAG,
                    "版本对比：远端 tag=${release.tagName} → remoteVersionCode=$remoteVersionCode, " +
                            "本地 versionCode=$localVersionCode, versionName=$localVersionName"
                )

                // === 调试版强制更新拦截 ===
                // 背景：本地调试打包使用 versionCode=99999 / versionName=9.9.9-local，
                // 数值永远高于 GitHub 发布的 0.x，导致本地安装后检测不到云端真正的更新。
                // 规则：只要本地版本名包含 "-local" 或 versionCode==99999（即本地调试版），
                // 且 GitHub API 成功返回了 Release（能走到这里说明云端有发布），
                // 直接判定为"有新版本"，跳过数字大小比较。
                // 这样本地调试版每次检查更新都会提示，确保开发者能第一时间看到云端版本。
                val isLocalDevName = localVersionName.contains("-local")
                val isLocalDevCode = localVersionCode == 99999
                Log.d(
                    TAG,
                    "调试版判定: contains('-local')=$isLocalDevName, " +
                            "versionCode==99999=$isLocalDevCode"
                )
                if (isLocalDevName || isLocalDevCode) {
                    Log.d(
                        TAG,
                        "🚀 触发调试版强制更新逻辑！versionName=$localVersionName, " +
                                "versionCode=$localVersionCode → 直接返回 NewVersionAvailable"
                    )
                    Log.d(TAG, "========== 检查结束（调试版强制更新）==========")
                    return@withContext UpdateResult.NewVersionAvailable(
                        tagName = release.tagName,
                        downloadUrl = apkUrl,
                        releaseNotes = release.body
                    )
                }

                // === 正常版本比对 ===
                if (remoteVersionCode > localVersionCode) {
                    Log.d(
                        TAG,
                        "✅ 发现新版本：remoteVersionCode($remoteVersionCode) > " +
                                "localVersionCode($localVersionCode)"
                    )
                    Log.d(TAG, "========== 检查结束（有更新）==========")
                    UpdateResult.NewVersionAvailable(
                        tagName = release.tagName,
                        downloadUrl = apkUrl,
                        releaseNotes = release.body
                    )
                } else {
                    Log.d(
                        TAG,
                        "ℹ️ 当前已是最新：remoteVersionCode($remoteVersionCode) <= " +
                                "localVersionCode($localVersionCode)"
                    )
                    Log.d(TAG, "========== 检查结束（已是最新）==========")
                    UpdateResult.UpToDate
                }
            }
        } catch (e: SocketTimeoutException) {
            // 网络超时（检查阶段）：不弹错误窗，仅日志记录，视为"已是最新"
            Log.w(TAG, "❌ 检查更新超时（SocketTimeoutException）：${e.message}，视为当前已是最新版本", e)
            UpdateResult.UpToDate
        } catch (e: UnknownHostException) {
            // 无法解析主机（无网络/DNS 失败）：不弹错误窗，仅日志记录
            Log.w(TAG, "❌ 无法连接 GitHub（UnknownHostException，网络不可用）：${e.message}", e)
            UpdateResult.UpToDate
        } catch (e: java.io.IOException) {
            // 其他 IO 异常（连接重置等）：不弹错误窗，仅日志记录
            Log.w(TAG, "❌ 网络 IO 异常（IOException）：${e.message}", e)
            UpdateResult.UpToDate
        } catch (e: Exception) {
            // 未预期异常：返回 Error，由 UI 决定是否提示
            Log.e(TAG, "❌ 检查更新未预期异常: ${e.javaClass.simpleName}: ${e.message}", e)
            UpdateResult.Error("检查更新失败: ${e.message ?: "未知错误"}")
        }
    }

    /**
     * 下载 APK 文件到指定目录（功能 2：RandomAccessFile 断点续传）。
     *
     * === 实现要点 ===
     * 1. 使用 RandomAccessFile + setLength 支持断点续传：
     *    - 首次下载：从头写入，记录已下载字节数
     *    - 重试下载：通过 Range: bytes=<downloaded>- 头请求剩余部分
     *    - RandomAccessFile.seek(offset) 定位到断点继续写入
     * 2. 进度回调：每 1% 回调一次，避免过度刷新通知栏
     * 3. 异常分类：
     *    - SocketTimeoutException → 抛出 DownloadException("网络不稳定，下载失败，请连接更稳定的 WiFi 重试")
     *    - 其他异常 → 抛出 DownloadException("下载失败：<详情>")
     *
     * === 断点续传原理 ===
     * - 文件首次下载时，先 setLength(totalBytes) 预分配空间
     * - 每次写入后记录当前 offset 到 .resume 文件
     * - 网络中断重试时，读取 .resume 文件获得已下载 offset，
     *   通过 Range 头请求剩余字节，seek(offset) 后继续写入
     *
     * @param downloadUrl APK 下载直链
     * @param destFile 目标文件
     * @param onProgress 下载进度回调（0-100），可选
     * @throws DownloadException 下载失败，[DownloadException.userMessage] 可直接展示给用户
     */
    suspend fun downloadApk(
        downloadUrl: String,
        destFile: File,
        onProgress: ((Int) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        // 确保父目录存在
        destFile.parentFile?.mkdirs()

        // 断点续传记录文件：保存已下载字节数
        val resumeFile = File(destFile.parentFile, destFile.name + ".resume")

        try {
            // 读取断点位置（首次下载为 0）
            val existingOffset = if (resumeFile.exists() && destFile.exists()) {
                resumeFile.readText().toLongOrNull() ?: 0L
            } else {
                0L
            }

            // 构建请求：若存在断点，附加 Range 头请求剩余部分
            val requestBuilder = Request.Builder()
                .url(downloadUrl)
            if (existingOffset > 0) {
                requestBuilder.addHeader("Range", "bytes=$existingOffset-")
                Log.i(TAG, "断点续传：从 $existingOffset 字节处继续下载")
            }
            val request = requestBuilder.build()  // User-Agent 已由拦截器统一注入

            httpClient.newCall(request).execute().use { response ->
                // 206 Partial Content（断点续传成功）或 200 OK（完整下载）
                if (response.code != 200 && response.code != 206) {
                    throw DownloadException(
                        "下载失败：服务器返回 HTTP ${response.code}",
                        IllegalStateException("HTTP ${response.code}")
                    )
                }
                val body = response.body
                    ?: throw DownloadException("下载失败：服务器响应体为空")

                // 计算总字节数与起始偏移
                val totalBytes: Long
                val startOffset: Long
                if (response.code == 206) {
                    // 断点续传：Content-Range: bytes <start>-<end>/<total>
                    val contentRange = response.header("Content-Range") ?: ""
                    val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").find(contentRange)
                    if (match != null) {
                        startOffset = match.groupValues[1].toLong()
                        totalBytes = match.groupValues[3].toLong()
                    } else {
                        startOffset = existingOffset
                        totalBytes = existingOffset + body.contentLength()
                    }
                } else {
                    // 完整下载：从头开始
                    startOffset = 0L
                    totalBytes = body.contentLength()
                }

                if (totalBytes <= 0) {
                    throw DownloadException("下载失败：无法获取文件大小")
                }

                // === RandomAccessFile 断点续传写入 ===
                // setLength 预分配空间，避免写入时文件大小不匹配
                RandomAccessFile(destFile, "rw").use { raf ->
                    raf.setLength(totalBytes)
                    raf.seek(startOffset)

                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var downloadedBytes = startOffset
                    val inputStream = body.byteStream()
                    var lastReportedPercent = -1

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        raf.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        // 实时记录断点位置（每 64KB 写一次，避免频繁 IO）
                        if (downloadedBytes % 65536 == 0L) {
                            resumeFile.writeText(downloadedBytes.toString())
                        }

                        if (onProgress != null) {
                            val percent = (downloadedBytes * 100 / totalBytes).toInt()
                            // 限制进度回调频率：每 1% 才更新一次通知
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                    raf.fd.sync()  // 强制刷盘，避免数据丢失
                    // 下载完成，回调 100%
                    onProgress?.invoke(100)
                }

                // 下载完成，清理断点记录文件
                resumeFile.delete()
                Log.i(TAG, "APK 下载完成：$destFile（$totalBytes 字节）")
                true
            }
        } catch (e: SocketTimeoutException) {
            // === 功能 4：网络不稳定专用提示 ===
            // 抛出 DownloadException，由上层捕获并弹出友好提示
            Log.e(TAG, "APK 下载超时：${e.message}", e)
            throw DownloadException(
                "网络不稳定，下载失败，请连接更稳定的 WiFi 重试",
                e
            )
        } catch (e: DownloadException) {
            // 已包装过的异常，直接向上抛
            Log.e(TAG, "APK 下载失败：${e.userMessage}", e)
            throw e
        } catch (e: Exception) {
            // 其他异常：包装为 DownloadException
            Log.e(TAG, "APK 下载失败：${e.message}", e)
            throw DownloadException(
                "下载失败：${e.message ?: "未知错误"}",
                e
            )
        }
    }

    /**
     * 清理下载残留（断点文件 + 不完整 APK）。
     *
     * 调用时机：
     * - 用户主动取消下载
     * - 下载失败且不再重试时
     */
    fun cleanDownloadCache(destFile: File) {
        try {
            if (destFile.exists()) destFile.delete()
            val resumeFile = File(destFile.parentFile, destFile.name + ".resume")
            if (resumeFile.exists()) resumeFile.delete()
        } catch (e: Exception) {
            Log.w(TAG, "清理下载残留失败：${e.message}")
        }
    }
}
