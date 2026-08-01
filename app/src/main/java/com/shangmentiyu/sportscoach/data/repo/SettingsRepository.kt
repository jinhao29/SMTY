package com.shangmentiyu.sportscoach.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * 应用设置仓储（管理层）。
 *
 * 持久化字段分三组：
 * - 教练信息：[coach] 当前登录教练姓名，用于签到记录归属
 * - 桌面同步配置（v21 引入）：
 *   - [syncEnabled] 是否启用备份自动推送到 PC 端
 *   - [syncHost] PC 端 IP 地址（如 "192.168.1.100"）
 *   - [syncPort] PC 端接收服务端口（如 8765）
 *   - [syncToken] 简单鉴权 token（避免局域网内误投递），客户端与服务端需一致
 * - 照片加密种子（v22 引入）：
 *   - [userPrivateKey] 用户私钥种子，用于跨设备照片解密
 *     （详见 [com.shangmentiyu.sportscoach.core.PhotoCrypto]）
 *
 * 所有字段使用 DataStore 持久化，应用卸载后自动清除。
 */
class SettingsRepository(private val context: Context) {
    companion object {
        private val KEY_COACH = stringPreferencesKey("coach")

        // === 桌面同步配置（v21 新增） ===
        private val KEY_SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
        private val KEY_SYNC_HOST = stringPreferencesKey("sync_host")
        private val KEY_SYNC_PORT = stringPreferencesKey("sync_port")
        private val KEY_SYNC_TOKEN = stringPreferencesKey("sync_token")

        // === 照片加密种子（v22 新增） ===
        // 用于派生 PhotoCrypto 的 AES 密钥，确保跨设备迁移后仍能解密老照片
        private val KEY_USER_PRIVATE_KEY = stringPreferencesKey("user_private_key")

        /** 默认同步端口（与桌面端 backup_receiver.py 默认端口一致） */
        const val DEFAULT_SYNC_PORT = "8765"

        // === 全自动无感备份开关（v30 新增） ===
        // 默认开启：用户在设置页可手动关闭
        // 关闭后 AutoBackupScheduler 不会触发任何自动备份，已 pending 的防抖任务会被取消
        private val KEY_AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
    }

    val coach: Flow<String> = context.dataStore.data.map { it[KEY_COACH] ?: "" }

    suspend fun setCoach(value: String) {
        context.dataStore.edit { it[KEY_COACH] = value }
    }

    // === 照片加密种子（v22 新增） ===

    /**
     * 用户私钥种子（Flow 形式）。
     *
     * - 用作 [com.shangmentiyu.sportscoach.core.PhotoCrypto] 派生 AES 密钥的种子
     * - 未配置时返回空串，PhotoCrypto 将回退到旧的 EncryptedFile 方案
     * - 用户应在「设置页」配置一个长度 ≥ 8 的随机字符串作为私钥
     * - 跨设备迁移时：用户在新手机输入相同私钥即可解密旧照片
     */
    val userPrivateKey: Flow<String> =
        context.dataStore.data.map { it[KEY_USER_PRIVATE_KEY] ?: "" }

    /** 同步读取用户私钥（供 PhotoCrypto 在非协程上下文中使用，内部使用 runBlocking） */
    suspend fun getUserPrivateKey(): String =
        context.dataStore.data.map { it[KEY_USER_PRIVATE_KEY] ?: "" }.first()

    /**
     * 同步阻塞读取用户私钥。
     *
     * 仅供 [com.shangmentiyu.sportscoach.core.PhotoCrypto] 等无法在协程上下文中
     * 调用挂起函数的场景使用。读取频繁时建议由调用方缓存结果。
     */
    fun getUserPrivateKeyBlocking(): String = runBlocking {
        context.dataStore.data.map { it[KEY_USER_PRIVATE_KEY] ?: "" }.first()
    }

    suspend fun setUserPrivateKey(value: String) {
        context.dataStore.edit { it[KEY_USER_PRIVATE_KEY] = value.trim() }
    }

    // === 桌面同步配置（v21 新增） ===

    /** 是否启用备份自动推送到 PC 端 */
    val syncEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SYNC_ENABLED] ?: false }

    /** PC 端 IP 地址（如 "192.168.1.100"）；未配置返回空串 */
    val syncHost: Flow<String> =
        context.dataStore.data.map { it[KEY_SYNC_HOST] ?: "" }

    /** PC 端接收服务端口；未配置返回默认端口 [DEFAULT_SYNC_PORT] */
    val syncPort: Flow<String> =
        context.dataStore.data.map { it[KEY_SYNC_PORT] ?: DEFAULT_SYNC_PORT }

    /** 简单鉴权 token；未配置返回空串（同时为空表示不校验） */
    val syncToken: Flow<String> =
        context.dataStore.data.map { it[KEY_SYNC_TOKEN] ?: "" }

    suspend fun setSyncEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_SYNC_ENABLED] = value }
    }

    suspend fun setSyncHost(value: String) {
        context.dataStore.edit { it[KEY_SYNC_HOST] = value.trim() }
    }

    suspend fun setSyncPort(value: String) {
        context.dataStore.edit { it[KEY_SYNC_PORT] = value.trim() }
    }

    suspend fun setSyncToken(value: String) {
        context.dataStore.edit { it[KEY_SYNC_TOKEN] = value.trim() }
    }

    // === 全自动无感备份开关（v30 新增） ===

    /**
     * 自动备份启用开关（Flow 形式）。
     *
     * - 默认开启：DataStore 中未配置时返回 true
     * - 监听本 Flow 的调用方（[com.shangmentiyu.sportscoach.core.AutoBackupScheduler]）
     *   在用户切换开关时通过 [reloadSettings] 重新读取
     * - 关闭时已 pending 的防抖任务会被立即取消
     */
    val autoBackupEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_AUTO_BACKUP_ENABLED] ?: true }

    /** 同步读取自动备份开关（供 AutoBackupScheduler 初次启动时使用） */
    suspend fun getAutoBackupEnabled(): Boolean =
        context.dataStore.data.map { it[KEY_AUTO_BACKUP_ENABLED] ?: true }.first()

    /**
     * 设置自动备份开关。
     *
     * 调用方应在切换后调用 [AutoBackupScheduler.reloadSettings] 使设置立即生效。
     */
    suspend fun setAutoBackupEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_BACKUP_ENABLED] = value }
    }
}

