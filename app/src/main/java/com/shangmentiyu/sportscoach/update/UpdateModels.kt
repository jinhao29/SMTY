package com.shangmentiyu.sportscoach.update

import com.google.gson.annotations.SerializedName

/**
 * GitHub Release 数据模型（Gson 解析）。
 *
 * 对应 API: https://api.github.com/repos/<user>/SMTY/releases/latest
 * 仅提取自动更新所需字段，避免解析冗余数据。
 */
data class GitHubRelease(
    /** Release 标签名，作为服务器版本号，例如 "v1.0.5" */
    @SerializedName("tag_name") val tagName: String = "",
    /** Release 标题 */
    val name: String = "",
    /** Release 说明 */
    val body: String = "",
    /** APK 附件列表 */
    val assets: List<GitHubAsset> = emptyList()
)

/**
 * GitHub Release 附件（APK 文件）。
 */
data class GitHubAsset(
    /** 文件名，例如 "app-release.apk" */
    val name: String = "",
    /** 文件大小（字节） */
    val size: Long = 0L,
    /** 下载直链（私有仓库需带 Authorization 头访问） */
    @SerializedName("browser_download_url") val downloadUrl: String = ""
)

/**
 * 版本检查结果。
 */
sealed class UpdateResult {
    /** 当前已是最新版本 */
    data object UpToDate : UpdateResult()

    /** 检测到新版本 */
    data class NewVersionAvailable(
        val tagName: String,
        val downloadUrl: String,
        val releaseNotes: String
    ) : UpdateResult()

    /** 检查失败（网络异常、Token 无效等） */
    data class Error(val message: String) : UpdateResult()
}
