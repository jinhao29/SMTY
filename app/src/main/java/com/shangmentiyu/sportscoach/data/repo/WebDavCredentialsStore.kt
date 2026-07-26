package com.shangmentiyu.sportscoach.data.repo

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * WebDAV 配置数据类（v32 优化1 新增）。
 *
 * @param enabled 是否启用 WebDAV 云盘备份
 * @param baseUrl WebDAV 服务地址（如 https://dav.jianguoyun.com/dav/）
 * @param remoteDir 远程存放目录（如 shangmentiyu/backup）
 * @param username 用户名（应用级专用密码，坚果云场景）
 * @param password 密码（应用级专用密码）
 */
data class WebDavConfig(
    val enabled: Boolean = false,
    val baseUrl: String = "",
    val remoteDir: String = "shangmentiyu/backup",
    val username: String = "",
    val password: String = ""
)

/**
 * WebDAV 凭证加密存储（管理层 / 数据层）。
 *
 * 设计目标（v32 优化1 新增）：
 * - 使用 [EncryptedSharedPreferences] 加密存储 WebDAV 账号密码，杜绝明文落盘
 * - 主密钥通过 Android Keystore 派生（[MasterKey]），卸载 App 后密钥自动失效
 * - 同一应用签名下加密文件可读；换签名后自动重建（不影响主流程）
 * - 业务参数（enabled / baseUrl / remoteDir）与敏感字段（username / password）分区存储：
 *   业务参数走普通 SharedPreferences 便于调试，敏感字段单独走加密表
 *
 * 安全保证：
 * - 任何场景下均不返回明文日志；getUsername/getPassword 仅在协程上下文调用
 * - 配置变化时通过 [config] Flow 通知上层 UI 刷新
 */
class WebDavCredentialsStore(private val context: Context) {

    companion object {
        private const val TAG = "WebDavCredStore"

        /** 业务参数文件（非敏感，可调试可见） */
        private const val PREFS_PLAIN = "webdav_config_plain"

        /** 加密文件（账号密码等敏感字段） */
        private const val PREFS_SECURE = "webdav_config_secure"

        private const val KEY_ENABLED = "enabled"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_REMOTE_DIR = "remote_dir"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"

        /** 默认远程目录（教练无需手动配置即可使用） */
        const val DEFAULT_REMOTE_DIR = "shangmentiyu/backup"

        /** 默认常用训练动作积木（v32 优化2 预置） */
        val DEFAULT_EXERCISE_BLOCKS = listOf(
            "高抬腿", "深蹲", "折返跑", "跳绳", "立定跳远",
            "俯卧撑", "仰卧起坐", "弓步蹲", "开合跳", "平板支撑",
            "绳梯进阶", "侧滑步", "原地跑", "踢臀跑", "快速踏步"
        )

        private const val KEY_EXERCISE_BLOCKS = "exercise_blocks_json"
    }

    /** 业务参数（明文，便于调试） */
    private val plainPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_PLAIN, Context.MODE_PRIVATE)

    /** 加密参数（账号密码等敏感字段） */
    private val securePrefs: SharedPreferences? = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_SECURE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // 加密存储初始化失败（设备不支持 / Keystore 异常）时降级为 null
        // 上层调用 getUsername/getPassword 时将返回空串，WebDAV 功能不可用
        null
    }

    /** 配置变更通知流（敏感字段不放入，仅 enabled/baseUrl/remoteDir） */
    private val _configFlow = MutableStateFlow(loadPlainConfig())
    val config: Flow<WebDavConfig> = _configFlow.asStateFlow()

    /** 读取用户名（加密存储，失败返回空串） */
    fun getUsername(): String {
        return securePrefs?.getString(KEY_USERNAME, "") ?: ""
    }

    /** 读取密码（加密存储，失败返回空串） */
    fun getPassword(): String {
        return securePrefs?.getString(KEY_PASSWORD, "") ?: ""
    }

    /** 读取完整配置（含敏感字段，仅在 WebDavManager 推送时调用） */
    fun getFullConfig(): WebDavConfig {
        val plain = loadPlainConfig()
        return plain.copy(
            username = getUsername(),
            password = getPassword()
        )
    }

    /**
     * 保存完整配置（业务参数明文 + 账号密码加密）。
     *
     * @param config WebDAV 配置
     */
    fun saveConfig(config: WebDavConfig) {
        plainPrefs.edit()
            .putBoolean(KEY_ENABLED, config.enabled)
            .putString(KEY_BASE_URL, config.baseUrl)
            .putString(KEY_REMOTE_DIR, config.remoteDir)
            .apply()

        securePrefs?.edit()?.apply {
            putString(KEY_USERNAME, config.username)
            putString(KEY_PASSWORD, config.password)
        }

        // 通知配置变更（不含敏感字段）
        _configFlow.value = loadPlainConfig()
    }

    /** 仅切换启用状态（设置页开关用） */
    fun setEnabled(enabled: Boolean) {
        plainPrefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _configFlow.value = loadPlainConfig()
    }

    /** 清除所有 WebDAV 配置（设置页"清除配置"按钮） */
    fun clear() {
        plainPrefs.edit().clear().apply()
        securePrefs?.edit()?.clear()?.apply()
        _configFlow.value = WebDavConfig()
    }

    /** 读取业务参数（不含账号密码） */
    private fun loadPlainConfig(): WebDavConfig {
        return WebDavConfig(
            enabled = plainPrefs.getBoolean(KEY_ENABLED, false),
            baseUrl = plainPrefs.getString(KEY_BASE_URL, "") ?: "",
            remoteDir = plainPrefs.getString(KEY_REMOTE_DIR, DEFAULT_REMOTE_DIR) ?: DEFAULT_REMOTE_DIR,
            username = "",  // 敏感字段不放入 Flow
            password = ""
        )
    }

    // === v32 优化2：训练动作积木库（自定义常用动作） ===

    /**
     * 读取训练动作积木库（v32 优化2 新增）。
     *
     * - 首次调用返回 [DEFAULT_EXERCISE_BLOCKS] 预置列表
     * - 用户在设置页自定义后，列表持久化到普通 SharedPreferences（JSON 数组）
     * - 返回顺序与用户自定义顺序一致，便于排课 UI 渲染胶囊按钮
     */
    fun getExerciseBlocks(): List<String> {
        val json = plainPrefs.getString(KEY_EXERCISE_BLOCKS, null) ?: return DEFAULT_EXERCISE_BLOCKS
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        } catch (e: Exception) {
            DEFAULT_EXERCISE_BLOCKS
        }
    }

    /**
     * 保存训练动作积木库。
     *
     * @param blocks 自定义动作名称列表（去重并去空白项）
     */
    fun setExerciseBlocks(blocks: List<String>) {
        val cleaned = blocks
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val arr = JSONArray()
        cleaned.forEach { arr.put(it) }
        plainPrefs.edit().putString(KEY_EXERCISE_BLOCKS, arr.toString()).apply()
    }

    /** 追加单个动作到积木库（排课页"添加动作到积木库"快捷按钮） */
    fun addExerciseBlock(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val current = getExerciseBlocks().toMutableList()
        if (trimmed !in current) {
            current.add(trimmed)
            setExerciseBlocks(current)
        }
    }

    /** 删除指定动作积木（排课页长按删除） */
    fun removeExerciseBlock(name: String) {
        val current = getExerciseBlocks().toMutableList()
        if (current.remove(name)) {
            setExerciseBlocks(current)
        }
    }
}
