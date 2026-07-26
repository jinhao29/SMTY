package com.shangmentiyu.sportscoach.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * APK 安装处理器（数据/系统交互层）。
 *
 * 职责：
 * 1. 通过 FileProvider 获取 APK 的 content:// URI（Android 7.0+ 要求）
 * 2. 创建安装 Intent 并授权读取
 * 3. 引导用户开启"安装未知来源"权限（Android 8.0+）
 */
object UpdateInstaller {

    /** APK 下载后的文件名 */
    private const val APK_FILE_NAME = "update.apk"

    /**
     * 获取 APK 下载目标文件（externalCacheDir/update.apk）。
     */
    fun getApkFile(context: Context): File {
        return File(context.externalCacheDir, APK_FILE_NAME)
    }

    /**
     * 触发系统安装界面。
     *
     * 流程：
     * 1. 检查 APK 文件是否存在
     * 2. 通过 FileProvider 获取 content:// URI
     * 3. 创建 ACTION_VIEW Intent，设置 FLAG_GRANT_READ_URI_PERMISSION
     * 4. 若系统要求"安装未知来源"权限，先引导用户开启
     *
     * @param context 上下文
     * @return true 表示成功启动安装界面；false 表示需要先开启未知来源权限
     */
    fun installApk(context: Context): Boolean {
        val apkFile = getApkFile(context)
        if (!apkFile.exists()) return false

        // Android 8.0+ 检查安装未知来源权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            // 引导用户前往"安装未知应用"设置页
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return false
        }

        // 通过 FileProvider 获取 content:// URI
        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            apkFile
        )

        // 创建安装 Intent
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(installIntent)
        return true
    }

    /**
     * 创建安装 Intent 的 PendingIntent（用于通知栏点击触发安装）。
     */
    fun createInstallPendingIntent(context: Context): PendingIntent? {
        val apkFile = getApkFile(context)
        if (!apkFile.exists()) return null

        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, 0, installIntent, flags)
    }

    /**
     * 创建"打开 App 主界面"的 PendingIntent（v33 功能 2 新增）。
     *
     * 用于下载中通知点击：
     * - 避免通知栏"死按钮"（点击无反应）
     * - 点击直接打开 App，让教练查看下载进度浮层
     *
     * @return 打开 MainActivity 的 PendingIntent
     */
    fun createOpenAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, com.shangmentiyu.sportscoach.MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, 1, intent, flags)
    }
}
