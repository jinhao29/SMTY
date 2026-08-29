package com.shangmentiyu.sportscoach.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.File

/**
 * 更新包完整性校验（P2-3 修复，2026-08-28）。
 *
 * 策略：安装前比对「已下载 APK 的签名证书指纹」与「当前运行应用的签名证书指纹」，
 * 不一致即拒绝安装并删除下载文件。该策略不依赖 Release Notes 格式，
 * 即使 GitHub 账号 / Release 被接管，攻击者无法用同一签名密钥打包，
 * 投毒包也无法通过校验，从而阻断自动更新供应链攻击。
 *
 * 校验链路（双保险）：
 * 1. [UpdateCheckWorker] 下载完成后立即校验（主闸门，失败不进入待安装状态）
 * 2. [UpdateInstaller.installApk] 启动安装器前校验（兜底，防本地文件被替换）
 */
object UpdateIntegrity {

    private const val TAG = "AutoUpdate"

    /**
     * 纯逻辑：比较两组签名指纹是否一致（顺序无关）。
     *
     * 任一侧为空集合视为校验失败（fail-closed：取不到签名宁可拒绝，不可放行）。
     */
    fun signaturesMatch(current: List<String>, downloaded: List<String>): Boolean {
        if (current.isEmpty() || downloaded.isEmpty()) return false
        return current.toSet() == downloaded.toSet()
    }

    /**
     * 校验已下载的更新 APK 与当前应用签名一致。
     *
     * @param context 上下文
     * @return true 签名一致，可安装；false 签名不一致或无法提取签名（拒绝安装）
     */
    fun verifyDownloadedApk(context: Context): Boolean {
        return try {
            val apkFile = UpdateInstaller.getApkFile(context)
            if (!apkFile.exists()) return false

            val current = currentSignatureFingerprints(context)
            val downloaded = apkSignatureFingerprints(context, apkFile)
            val match = signaturesMatch(current, downloaded)
            if (match) {
                Log.i(TAG, "更新包签名校验通过（指纹 ${current.size} 项一致）")
            } else {
                Log.e(
                    TAG,
                    "更新包签名校验失败！current=${current.size} 项, downloaded=${downloaded.size} 项，" +
                            "下载源可能被篡改，已拒绝安装"
                )
            }
            match
        } catch (e: Exception) {
            // fail-closed：校验过程异常一律拒绝安装
            Log.e(TAG, "更新包签名校验异常：${e.message}", e)
            false
        }
    }

    /** 提取当前运行应用的签名指纹列表。 */
    private fun currentSignatureFingerprints(context: Context): List<String> {
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, signatureFlags())
        return extractSignatureStrings(info)
    }

    /** 提取指定 APK 文件（未安装）的签名指纹列表。 */
    private fun apkSignatureFingerprints(context: Context, apkFile: File): List<String> {
        val info = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, signatureFlags())
        return extractSignatureStrings(info)
    }

    /**
     * 签名读取 Flag：API 28+ 用 GET_SIGNING_CERTIFICATES（SigningInfo），
     * API 26/27 回退 GET_SIGNATURES（已废弃但仍可用，两侧使用同一机制故可比）。
     */
    private fun signatureFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
    }

    private fun extractSignatureStrings(info: PackageInfo?): List<String> {
        if (info == null) return emptyList()
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        return signatures?.map { it.toCharsString().orEmpty() }?.filter { it.isNotBlank() } ?: emptyList()
    }
}
