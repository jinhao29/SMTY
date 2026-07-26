package com.shangmentiyu.sportscoach.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
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
 * UDP 局域网广播监听服务（前台 Service）。
 *
 * 职责：
 * - 在后台持续监听 UDP 9111 端口，接收电脑端 lan_plan_sender.py 发送的广播
 * - 解析广播 JSON：{host, port, filename, student_name, date, timestamp}
 * - 收到广播后弹出 Android 系统 Notification，提示用户"有新的训练计划截图可同步"
 * - 用户点击 Notification 跳转到 MainActivity（设置页"同步电脑端截图"按钮）
 *
 * 生命周期：
 * - 由 [com.shangmentiyu.sportscoach.SportsCoachApp] 在 App 启动时启动
 * - 前台 Service 保证长期运行不被系统杀死（Android 8.0+ 要求）
 * - 用户可在设置页手动启停
 *
 * 与 LanImageReceiver 配合：
 * - 本 Service 仅负责"通知用户有新截图"
 * - 实际下载由用户在设置页点击"同步电脑端截图"按钮后，调用 LanImageReceiver.downloadAndImport
 *
 * === v25 新增 ===
 */
class UdpPlanListenerService : Service() {

    companion object {
        private const val TAG = "UdpPlanListener"

        /** UDP 监听端口（与桌面端 lan_plan_sender.UDP_PORT 一致） */
        private const val UDP_PORT = 9111

        /** 前台 Service 通知 ID */
        private const val FOREGROUND_NOTIFICATION_ID = 3001

        /** 新截图到达通知 ID */
        private const val PLAN_ARRIVED_NOTIFICATION_ID = 3002

        /** 通知渠道：前台服务常驻 */
        private const val CHANNEL_ID_FOREGROUND = "lan_plan_listening_channel"

        /** 通知渠道：新截图到达 */
        private const val CHANNEL_ID_PLAN_ARRIVED = "plan_arrived_channel"

        /** 接收缓冲区大小（字节） */
        private const val BUFFER_SIZE = 4096

        /**
         * 启动 Service 的便捷方法。
         * - Android 8.0+ 调用 startForegroundService
         * - 低版本调用 startService
         */
        fun start(context: Context) {
            val intent = Intent(context, UdpPlanListenerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** 停止 Service */
        fun stop(context: Context) {
            context.stopService(Intent(context, UdpPlanListenerService::class.java))
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
        Log.i(TAG, "UdpPlanListenerService onCreate")

        // 创建通知渠道
        createNotificationChannels()

        // 启动前台通知（Android 8.0+ 要求前台 Service 启动后 5 秒内调用 startForeground）
        startForeground(
            FOREGROUND_NOTIFICATION_ID,
            buildForegroundNotification()
        )

        // 启动 UDP 接收线程
        startReceiving()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY：Service 被系统杀死后会自动重启，保证长期监听
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "UdpPlanListenerService onDestroy")
        stopReceiving()
    }

    // ===== UDP 接收逻辑 =====

    private fun startReceiving() {
        if (isRunning) return
        isRunning = true

        receiveThread = Thread({
            try {
                val sock = DatagramSocket(UDP_PORT)
                socket = sock
                Log.i(TAG, "UDP 监听已启动，端口 $UDP_PORT")

                val buffer = ByteArray(BUFFER_SIZE)
                while (isRunning && !Thread.currentThread().isInterrupted) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        sock.receive(packet)  // 阻塞等待

                        val data = String(
                            packet.data, 0, packet.length,
                            StandardCharsets.UTF_8
                        )
                        Log.i(TAG, "收到 UDP 广播：$data")

                        handleBroadcast(data, packet.address)
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.w(TAG, "UDP 接收异常：${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "UDP 监听启动失败", e)
            } finally {
                socket?.let {
                    try {
                        it.close()
                    } catch (_: Exception) {
                    }
                }
                socket = null
            }
        }, "UdpPlanListener").apply {
            isDaemon = true
            start()
        }
    }

    private fun stopReceiving() {
        isRunning = false
        socket?.let {
            try {
                it.close()  // 触发 receive() 抛出异常，退出循环
            } catch (_: Exception) {
            }
        }
        socket = null
        receiveThread?.let {
            try {
                it.interrupt()
            } catch (_: Exception) {
            }
        }
        receiveThread = null
    }

    /**
     * 处理收到的 UDP 广播数据。
     *
     * 解析 JSON：{host, port, filename, student_name, date, timestamp}
     * 弹出"新截图到达"通知。
     */
    private fun handleBroadcast(data: String, sender: InetAddress?) {
        try {
            val json = JSONObject(data)

            // 校验来源（仅响应 shangmentiyu_desktop）
            val source = json.optString("source", "")
            if (source != "shangmentiyu_desktop") {
                Log.w(TAG, "忽略非本应用广播：source=$source")
                return
            }

            val host = json.optString("host", sender?.hostAddress ?: "")
            val port = json.optInt("port", 8080)
            val filename = json.optString("filename", "")
            val studentName = json.optString("student_name", "")
            val date = json.optString("date", "")

            if (host.isBlank() || filename.isBlank()) {
                Log.w(TAG, "广播数据不完整：host=$host, filename=$filename")
                return
            }

            // 保存待同步信息到 SharedPreferences（设置页读取后触发下载）
            savePendingBroadcast(host, port, filename, studentName, date)

            // 弹出通知
            showPlanArrivedNotification(studentName, date, host, filename)

        } catch (e: Exception) {
            Log.e(TAG, "解析 UDP 广播失败：$data", e)
        }
    }

    /**
     * 将待同步的广播信息保存到 SharedPreferences。
     *
     * 设置页"同步电脑端截图"按钮会读取此信息并触发下载。
     * 多条广播按时间戳排序，仅保留最新的一条（避免堆积）。
     */
    private fun savePendingBroadcast(
        host: String,
        port: Int,
        filename: String,
        studentName: String,
        date: String
    ) {
        val prefs = getSharedPreferences("lan_plan_pending", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("host", host)
            .putInt("port", port)
            .putString("filename", filename)
            .putString("student_name", studentName)
            .putString("date", date)
            .putLong("received_at", System.currentTimeMillis())
            .apply()

        Log.i(TAG, "已保存待同步截图信息：$studentName / $filename")
    }

    // ===== 通知 =====

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            // 前台服务常驻渠道
            val fgChannel = NotificationChannel(
                CHANNEL_ID_FOREGROUND,
                "截图同步监听",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "后台监听电脑端训练计划截图推送"
                setShowBadge(false)
            }
            manager.createNotificationChannel(fgChannel)

            // 新截图到达渠道
            val arrivedChannel = NotificationChannel(
                CHANNEL_ID_PLAN_ARRIVED,
                "新训练计划截图",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "收到电脑端推送的训练计划截图时提醒"
                enableVibration(true)
            }
            manager.createNotificationChannel(arrivedChannel)
        }
    }

    /**
     * 前台 Service 常驻通知（低重要性，不打扰用户）。
     */
    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID_FOREGROUND)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("训练计划同步监听中")
            .setContentText("正在监听电脑端截图推送")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    /**
     * 新截图到达通知（点击后跳转 MainActivity）。
     */
    private fun showPlanArrivedNotification(
        studentName: String,
        date: String,
        host: String,
        filename: String
    ) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // 点击跳转 MainActivity（设置页"同步电脑端截图"按钮读取 pending 信息）
        val clickIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("navigate_to", "settings")
            putExtra("lan_plan_pending", true)
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentIntent = PendingIntent.getActivity(
            this, PLAN_ARRIVED_NOTIFICATION_ID, clickIntent, pendingFlags
        )

        val title = if (studentName.isNotBlank()) {
            "收到 $studentName 的训练计划截图"
        } else {
            "收到新的训练计划截图"
        }
        val content = "来自电脑端 $host，点击立即同步到手机"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID_PLAN_ARRIVED)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "学员：$studentName\n" +
                    "日期：$date\n" +
                    "来源：$host\n" +
                    "点击通知立即同步到手机"
            ))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)

        manager.notify(PLAN_ARRIVED_NOTIFICATION_ID, builder.build())
        Log.i(TAG, "已弹出截图到达通知：$title")
    }
}
