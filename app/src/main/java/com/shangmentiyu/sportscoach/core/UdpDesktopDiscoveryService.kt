package com.shangmentiyu.sportscoach.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.shangmentiyu.sportscoach.MainActivity
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.charset.StandardCharsets

/**
 * UDP 局域网设备自动发现服务（前台 Service / 协调层）。
 *
 * 设计目标（v32 优化3 新增）：
 * - 监听桌面端定期广播的"在线心跳"报文，实现零配置自动发现
 * - 收到广播后写入 SharedPreferences（host/port/last_seen_at），供 UI 顶部状态栏读取
 * - 教练打开 App 即可在顶部看到 "已连接：电脑端 192.168.x.x" 绿色指示灯
 * - 彻底省去教练手动输入 IP 地址的烦恼
 *
 * 协议约定（与桌面端 desktop_beacon.py 配合）：
 * - 端口：UDP 9112（与 UdpPlanListenerService 的 9111 区分，避免报文混淆）
 * - 报文格式：JSON {"type":"desktop_online","host":"192.168.1.100","port":8080,"timestamp":172...}
 * - 广播周期：桌面端每 15 秒广播一次
 * - 离线判定：Android 端超过 60 秒未收到广播视为离线（[isAlive] 判定）
 *
 * 生命周期：
 * - 由 [com.shangmentiyu.sportscoach.SportsCoachApp] 在 App 启动时启动
 * - 前台 Service 保证长期运行不被系统杀死（Android 8.0+ 要求）
 * - START_STICKY 保证被杀后自动重启
 *
 * 与 [UdpPlanListenerService] 的区别：
 * - UdpPlanListenerService 监听"新截图到达"事件，单次触发 + 通知
 * - 本 Service 监听"桌面端在线心跳"，持续更新连接状态，不弹通知
 */
class UdpDesktopDiscoveryService : Service() {

    companion object {
        private const val TAG = "UdpDiscovery"

        /** UDP 监听端口（与桌面端 desktop_beacon.UDP_PORT 一致，区别于 9111） */
        private const val UDP_PORT = 9112

        /** 前台 Service 通知 ID */
        private const val FOREGROUND_NOTIFICATION_ID = 3003

        /** 通知渠道：前台服务常驻 */
        private const val CHANNEL_ID_FOREGROUND = "desktop_discovery_channel"

        /** SharedPreferences 文件名（与 SettingsViewModel 读取的一致） */
        private const val PREFS_NAME = "desktop_discovery"

        /** 接收缓冲区大小（字节） */
        private const val BUFFER_SIZE = 1024

        /**
         * 启动 Service 的便捷方法。
         * - Android 8.0+ 调用 startForegroundService
         * - 低版本调用 startService
         */
        fun start(context: Context) {
            val intent = Intent(context, UdpDesktopDiscoveryService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // 部分厂商 ROM 限制后台启动 Service，仅记录不崩溃
                Log.w(TAG, "启动 UdpDesktopDiscoveryService 失败：${e.message}")
            }
        }

        /** 停止 Service */
        fun stop(context: Context) {
            context.stopService(Intent(context, UdpDesktopDiscoveryService::class.java))
        }
    }

    /** UDP socket（在后台线程中阻塞接收） */
    @Volatile
    private var socket: DatagramSocket? = null

    /** 后台接收线程 */
    @Volatile
    private var receiveThread: Thread? = null

    /** 控制线程退出标志 */
    @Volatile
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "UdpDesktopDiscoveryService onCreate")

        createNotificationChannel()

        // 启动前台通知（Android 8.0+ 要求前台 Service 启动后 5 秒内调用 startForeground）
        startForeground(FOREGROUND_NOTIFICATION_ID, buildForegroundNotification())

        // 启动 UDP 接收线程
        startReceiving()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY：Service 被系统杀死后会自动重启，保证长期监听
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        receiveThread?.interrupt()
        Log.i(TAG, "UdpDesktopDiscoveryService onDestroy")
    }

    /**
     * 启动 UDP 接收线程。
     *
     * - 绑定 UDP 9112 端口
     * - 阻塞接收广播报文，解析 JSON 后写入 SharedPreferences
     * - socket 绑定失败时线程退出（端口被占用等），Service 自身仍保留前台通知
     */
    private fun startReceiving() {
        isRunning = true
        receiveThread = Thread {
            try {
                val sock = DatagramSocket(UDP_PORT)
                socket = sock
                Log.i(TAG, "开始监听 UDP $UDP_PORT 端口，等待桌面端广播")

                val buffer = ByteArray(BUFFER_SIZE)
                while (isRunning && !Thread.currentThread().isInterrupted) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        sock.receive(packet)  // 阻塞接收
                        val json = String(packet.data, 0, packet.length, StandardCharsets.UTF_8)
                        handleBroadcast(json)
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.w(TAG, "接收广播异常：${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "UDP socket 绑定失败（端口 $UDP_PORT 可能被占用）", e)
            }
        }.apply {
            isDaemon = true
            name = "UdpDiscoveryReceiver"
            start()
        }
    }

    /**
     * 处理收到的广播报文。
     *
     * - 解析 JSON：{type, host, port, timestamp}
     * - 仅处理 type == "desktop_online" 的报文（防误判）
     * - 写入 SharedPreferences：host / port / last_seen_at
     *
     * @param jsonStr 收到的 JSON 字符串
     */
    private fun handleBroadcast(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val type = json.optString("type", "")
            if (type != "desktop_online") return

            val host = json.optString("host", "")
            val port = json.optInt("port", 0)
            if (host.isBlank() || port <= 0) return

            val now = System.currentTimeMillis()
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString("host", host)
                .putInt("port", port)
                .putLong("last_seen_at", now)
                .apply()

            Log.d(TAG, "收到桌面端心跳：$host:$port")
        } catch (e: Exception) {
            Log.w(TAG, "广播报文解析失败：${e.message}")
        }
    }

    /** 创建通知渠道（Android 8.0+ 必需） */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_FOREGROUND,
                "局域网设备发现",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "监听桌面端在线状态，显示绿色指示灯"
                setShowBadge(false)
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    /** 构建前台 Service 必需的通知 */
    private fun buildForegroundNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID_FOREGROUND)
            .setContentTitle("上梅体育")
            .setContentText("正在监听桌面端连接…")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }
}
