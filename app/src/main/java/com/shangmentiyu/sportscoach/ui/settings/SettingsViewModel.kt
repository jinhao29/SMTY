package com.shangmentiyu.sportscoach.ui.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.shangmentiyu.sportscoach.data.internal.AutoBackupScheduler
import com.shangmentiyu.sportscoach.data.internal.BackupFolderStore
import com.shangmentiyu.sportscoach.data.internal.BackupManager
import com.shangmentiyu.sportscoach.core.ProgressState
import com.shangmentiyu.sportscoach.data.repo.BackupRepository
import com.shangmentiyu.sportscoach.data.repo.LessonRepository
import com.shangmentiyu.sportscoach.data.repo.OperationRepository
import com.shangmentiyu.sportscoach.data.repo.SettingsRepository
import com.shangmentiyu.sportscoach.data.repo.StudentRepository
import com.shangmentiyu.sportscoach.excel.ExcelSync
import com.shangmentiyu.sportscoach.excel.ImportStrategy
import com.shangmentiyu.sportscoach.ui.settings.state.SettingsUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.Locale

class SettingsViewModel(
    private val app: Application,
    private val lessonRepo: LessonRepository,
    private val studentRepo: StudentRepository,
    private val settingsRepo: SettingsRepository,
    private val backupRepo: BackupRepository,
    // v45：运营 Repository，用于一键修正历史错误排课
    private val opRepo: OperationRepository,
    // v26 优化1：操作日志 Repository（审计溯源）
    private val auditLogRepo: com.shangmentiyu.sportscoach.data.repo.AuditLogRepository? = null,
    // v23 双端同步：PC 推送/拉取管理器（Koin 注入；缺省 null 兼容既有测试构造）
    private val lanSyncManager: com.shangmentiyu.sportscoach.app.framework.LanSyncManager? = null
) : ViewModel() {

    val coach: StateFlow<String> = settingsRepo.coach
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // === v30 全自动无感备份开关 ===
    // 默认开启：DataStore 中未配置时返回 true
    // 用户在设置页切换后，调用 setAutoBackupEnabled 立即触发 AutoBackupScheduler.reloadSettings
    val autoBackupEnabled: StateFlow<Boolean> = settingsRepo.autoBackupEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // === v23 双端同步（PC 推送/拉取） ===
    val syncEnabled: StateFlow<Boolean> = settingsRepo.syncEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val syncHost: StateFlow<String> = settingsRepo.syncHost
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val syncPort: StateFlow<String> = settingsRepo.syncPort
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_SYNC_PORT)
    val syncToken: StateFlow<String> = settingsRepo.syncToken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _syncInProgress = MutableStateFlow(false)
    val syncInProgress: StateFlow<Boolean> = _syncInProgress.asStateFlow()

    // v23.6.1：双端同步独立作用域——「立即同步/测试连接」不随设置页销毁被取消
    //（曾复现：pull 中途 viewModelScope 被 cancel，同步静默中断；同步本就是后台语义）
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun setSyncEnabled(value: Boolean) {
        viewModelScope.launch { settingsRepo.setSyncEnabled(value) }
    }

    fun setSyncHost(value: String) {
        viewModelScope.launch { settingsRepo.setSyncHost(value) }
    }

    fun setSyncPort(value: String) {
        viewModelScope.launch { settingsRepo.setSyncPort(value) }
    }

    fun setSyncToken(value: String) {
        viewModelScope.launch { settingsRepo.setSyncToken(value) }
    }

    /**
     * 一键双向同步：先推送整库备份到 PC（自动合并），再拉取 PC 学员汇总（UPDATE_PART 合并）。
     * 两个方向独立成败，聚合消息经 statusMessage 反馈。
     */
    fun syncNow() {
        val mgr = lanSyncManager
        if (mgr == null) {
            updateStatus("同步服务不可用")
            return
        }
        if (_syncInProgress.value) {
            updateStatus("正在同步中，请稍候")
            return
        }
        syncScope.launch {
            _syncInProgress.value = true
            try {
                val r = mgr.syncNow()
                updateStatus(r.message.replace("\n", "　|　"))
            } catch (e: Exception) {
                // v23.6.1 诊断：堆栈前 3 行随 Toast 显示（vivo 屏蔽 logcat）
                val stack = e.stackTraceToString().lineSequence()
                    .filter { it.trim().isNotEmpty() }.take(3).joinToString(" ← ")
                updateStatus("同步异常：${e.message}｜$stack")
            } finally {
                _syncInProgress.value = false
            }
        }
    }    /**
     * v23.6 测试连接：/health 握手探测 PC 端同步服务（空地址自动回退 127.0.0.1 → USB）。
     * 结果经 statusMessage 反馈（成功含连接方式与延迟）。
     */
    fun testConnection() {
        val mgr = lanSyncManager
        if (mgr == null) {
            updateStatus("同步服务不可用")
            return
        }
        syncScope.launch {
            updateStatus("正在测试连接…")
            try {
                val r = mgr.pingDesktop()
                updateStatus(r.message)
            } catch (e: Exception) {
                updateStatus("测试连接异常：${e.message ?: "未知错误"}")
            }
        }
    }

    val floatingWindowEnabled: StateFlow<Boolean> = settingsRepo.floatingWindowEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // === v48 终极打磨：深色模式偏好（null=跟随系统 / true=深色 / false=亮色） ===
    val darkTheme: StateFlow<Boolean?> = settingsRepo.darkTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * 设置深色模式偏好。
     * - null：跟随系统（默认）
     * - true：强制深色
     * - false：强制亮色
     * 写入 DataStore 持久化，SportsCoachTheme 即时响应切换。
     */
    fun setDarkTheme(enabled: Boolean) {
        safeLaunch {
            // 开关语义：开 = 强制深色；关 = 恢复跟随系统
            settingsRepo.setDarkTheme(if (enabled) true else null)
            _statusMessage.value = if (enabled) "已切换深色模式" else "已恢复跟随系统"
        }
    }

    /**
     * 设置自动备份开关。
     * - 立即写入 DataStore 持久化
     * - 调用 [AutoBackupScheduler.reloadSettings] 使设置立即生效
     *   - 关闭时取消所有 pending 防抖任务
     *   - 开启时等待下次数据变更自然触发
     */
    fun setAutoBackupEnabled(enabled: Boolean) {
        safeLaunch {
            settingsRepo.setAutoBackupEnabled(enabled)
            AutoBackupScheduler.reloadSettings()
            _statusMessage.value = if (enabled) "已开启自动备份" else "已关闭自动备份"
        }
    }

    // === v1.0.2+ 固定备份文件夹（手动/自动/恢复联动） ===
    // SAF 目录树 Uri 选一次持久化；手动备份直存、恢复自动取最新、自动备份同步写入。
    // 选公共目录（如 Download）→ 卸载应用备份不丢（此前私有目录备份随卸载蒸发）。

    /** 固定备份文件夹 Uri（未设置 null） */
    val backupDirUri: StateFlow<String?> = settingsRepo.backupDirUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 文件夹展示名（"content://…/primary:Download" → "Download"） */
    val backupFolderLabel: StateFlow<String> = settingsRepo.backupDirUri
        .map { uri ->
            uri?.let { Uri.parse(it).lastPathSegment?.substringAfter(':', "") } ?: ""
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /** 刷新计数：备份完成/文件夹变更后 +1，驱动 latestBackup 重新扫描文件夹 */
    private val backupRefreshTick = MutableStateFlow(0)

    /** 文件夹内最新备份（文件名, 最后修改时间 ms）；null = 暂无备份 */
    val latestBackup: StateFlow<Pair<String, Long>?> = combine(
        settingsRepo.backupDirUri, backupRefreshTick
    ) { uri, _ -> uri }
        .map { uri ->
            withContext(Dispatchers.IO) {
                BackupFolderStore.listBackups(app, uri).firstOrNull()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 用户通过 SAF 选定固定备份文件夹后调用：持久化授权 + 保存 + 立即执行首次备份 */
    fun onBackupFolderPicked(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                app.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            settingsRepo.setBackupDirUri(uri.toString())
            _statusMessage.value = "备份文件夹已设置，开始备份…"
            backupToDefaultFolder(treeUriOverride = uri.toString())
        }
    }

    /** 手动备份到固定文件夹：自动命名 → 复用 [backupData]（含桌面端推送联动）。
     *  [treeUriOverride]：首次选完文件夹时直接传入，避开 DataStore 流未传播的竞态。 */
    fun backupToDefaultFolder(treeUriOverride: String? = null) {
        viewModelScope.launch {
            // 直接从 DataStore 挂起读取：backupDirUri 是 WhileSubscribed 惰性流，
            // 无订阅者时 .value 恒为初始 null，不能在这里读（17:06 真机 trace 定位）
            val treeUri = treeUriOverride ?: settingsRepo.getBackupDirUri()
            if (treeUri == null) {
                _statusMessage.value = "尚未设置备份文件夹，请先点击备份选择文件夹"
                return@launch
            }
            val fileUri = withContext(Dispatchers.IO) {
                val folder = BackupFolderStore.resolveFolder(app, treeUri)
                    ?: return@withContext null
                BackupFolderStore.createZipFile(app, folder, generateBackupFileName())
            }
            if (fileUri == null) {
                _statusMessage.value = "备份文件夹不可用，请在设置中重新选择"
                return@launch
            }
            backupData(fileUri)
        }
    }

    /** 从固定文件夹自动恢复最新一份备份（无需手动选文件） */
    fun restoreLatestFromFolder() {
        viewModelScope.launch {
            // 同 backupToDefaultFolder：惰性 StateFlow 无人订阅时 .value 恒 null，直接读 DataStore
            val treeUri = settingsRepo.getBackupDirUri()
            val latestName = withContext(Dispatchers.IO) {
                BackupFolderStore.listBackups(app, treeUri).firstOrNull()?.first
            }
            if (latestName == null) {
                _statusMessage.value = "备份文件夹里还没有备份文件"
                return@launch
            }
            val fileUri = withContext(Dispatchers.IO) {
                BackupFolderStore.resolveFolder(app, treeUri)
                    ?.findFile(latestName)?.uri
            }
            if (fileUri == null) {
                _statusMessage.value = "找不到备份文件，请重新选择文件夹"
                return@launch
            }
            restoreData(fileUri)
        }
    }

    fun setFloatingWindowEnabled(enabled: Boolean) {
        safeLaunch {
            settingsRepo.setFloatingWindowEnabled(enabled)
            val intent = Intent(app, com.shangmentiyu.sportscoach.service.FloatingWindowService::class.java)
            if (enabled) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    app.startForegroundService(intent)
                } else {
                    app.startService(intent)
                }
            } else {
                app.stopService(intent)
            }
        }
    }

    /**
     * === v45：一键修正历史错误排课 ===
     *
     * 调用 [OperationRepository.fixHistoricalScheduleErrors] 清理历史 Bug 产生的错误排课
     * （早于购买日期 / 超过剩余课时），并在剩余额度内重新生成正确排课。
     * 完成后通过 [_statusMessage] 向 UI 反馈修正统计。
     */
    fun fixHistoricalScheduleErrors() {
        safeLaunch {
            try {
                // === v54：先修复旧版自动排课在排课阶段错误扣减的课时包余额 ===
                // 旧版自动排课在排课时即增加 usedLessons + 设置 Lesson.packageId，
                // 导致排课与消课未分离。先回退余额，后续排课修正才能基于正确的剩余额度计算。
                val balanceFix = withContext(Dispatchers.IO) {
                    opRepo.fixPrematureBalanceDeduction()
                }
                val result = withContext(Dispatchers.IO) {
                    opRepo.fixHistoricalScheduleErrors()
                }
                _statusMessage.value = buildString {
                    append("已成功修正 ${result.fixedStudents} 位学员的错误排课数据")
                    append("（删除排课 ${result.deletedSchedules} 条、")
                    append("占位课时 ${result.deletedPlaceholders} 条，")
                    append("重新生成 ${result.regeneratedLessons} 条）")
                    if (balanceFix.fixedPackageCount > 0 || balanceFix.fixedLessonCount > 0) {
                        append("\n已回退 ${balanceFix.fixedPackageCount} 个课时包的提前扣费，")
                        append("清除 ${balanceFix.fixedLessonCount} 条占位课时的扣费归属")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsVM",
                    "修正历史排课失败：${e.message}", e)
                _statusMessage.value = "修正失败：${e.message ?: "未知异常"}"
            }
        }
    }

    val todayCount = lessonRepo.getTodayCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val totalCount = lessonRepo.getTotalCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // === v32：清理无效课表结果（null=无待展示结果，非负=清理数量，供设置页弹窗） ===
    private val _cleanupResult = MutableStateFlow<Int?>(null)
    val cleanupResult: StateFlow<Int?> = _cleanupResult.asStateFlow()
    fun clearCleanupResult() { _cleanupResult.value = null }

    /**
     * === v32：清理无效课表（仅设置页手动触发） ===
     *
     * 仅删除已过期（date < 今天）且从未签到（status = '待签到'）的占位课时，
     * 不删除未来课表，不删除已签到/已签退的真实记录。
     * 清理数量通过 [_cleanupResult] 供 UI 弹窗展示。
     */
    fun cleanupInvalidLessons() {
        safeLaunch {
            try {
                val deleted = withContext(Dispatchers.IO) { opRepo.clearExpiredUnsignedLessons() }
                _cleanupResult.value = deleted
            } catch (e: Exception) {
                android.util.Log.e("SettingsVM", "清理无效课表失败：${e.message}", e)
                _statusMessage.value = "清理失败：${e.message ?: "未知异常"}"
            }
        }
    }

    /**
     * === v24 优化4：全局协程异常捕获 ===
     *
     * 应用级异常处理器：拦截 Excel 导出 / 备份 / 恢复过程中可能出现的
     * IO 异常、JSON 解析异常、SQLiteDatabaseLockedException 等，避免 App 闪退。
     * - 异常落盘：通过 [com.shangmentiyu.sportscoach.app.framework.CrashHandler.writeLog]
     * - UI 反馈：通过 [_statusMessage] 推送轻量提示
     */
    private val appExceptionHandler =
        com.shangmentiyu.sportscoach.app.framework.CoroutineExt.createAppExceptionHandler(
            toastSink = _statusMessage,
            contextTag = "SettingsViewModel"
        )

    /**
     * 安全启动协程：自动挂载 [appExceptionHandler]，未捕获异常不会导致 App 崩溃。
     * 闭包签名兼容 [viewModelScope.launch]，便于直接替换。
     */
    private fun safeLaunch(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
        viewModelScope.launch(appExceptionHandler) {
            try {
                block(this)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            }
        }
    }

    /** 备份/恢复操作是否进行中（UI 据此禁用按钮、显示加载动画） */
    private val _backupInProgress = MutableStateFlow(false)
    val backupInProgress: StateFlow<Boolean> = _backupInProgress.asStateFlow()

    /**
     * 备份/恢复进度状态（UI 据此显示进度文案与百分比）。
     *
     * BackupManager.onProgress 在 IO 线程被调用，这里通过 emit 安全切到 StateFlow。
     */
    sealed class BackupProgress {
        data object Idle : BackupProgress()
        data class Working(val phase: String, val current: Int, val total: Int, val message: String) : BackupProgress()
        data class Done(val message: String) : BackupProgress()
    }

    private val _backupProgress = MutableStateFlow<BackupProgress>(BackupProgress.Idle)
    val backupProgress: StateFlow<BackupProgress> = _backupProgress.asStateFlow()

    /** UI 消费了备份进度事件后调用，重置为 Idle */
    fun consumeBackupProgress() {
        _backupProgress.value = BackupProgress.Idle
    }

    /** 恢复成功后置为 true，UI 据此弹出"重启应用"确认对话框 */
    private val _needRestart = MutableStateFlow(false)
    val needRestart: StateFlow<Boolean> = _needRestart.asStateFlow()

    /**
     * Excel 导出/导入进度状态（UI 据此显示进度条与文字提示）。
     *
     * 关键：Excel 操作（XSSFWorkbook 构造 + wb.write）是 CPU/IO 密集型，
     * 必须通过 [withContext]([Dispatchers.IO]) 切到 IO 线程，否则主线程阻塞 3s+ 必 ANR。
     */
    sealed class ExportProgress {
        data object Idle : ExportProgress()
        data class Working(val current: Int, val total: Int, val message: String) : ExportProgress()
        data class Done(val successCount: Int, val failCount: Int, val message: String) : ExportProgress()
        data class Failed(val message: String) : ExportProgress()
    }

    private val _exportProgress = MutableStateFlow<ExportProgress>(ExportProgress.Idle)
    val exportProgress: StateFlow<ExportProgress> = _exportProgress.asStateFlow()

    /** UI 消费了导出进度事件后调用，重置为 Idle */
    fun consumeExportProgress() {
        _exportProgress.value = ExportProgress.Idle
    }

    /**
     * v37 任务5：统一的 IO 操作加载状态（防抖蒙层专用）。
     *
     * 组合 backupInProgress 与 exportProgress.isWorking()：
     * - 任一为 true → isLoading = true，蒙层显示，禁用所有导出/备份按钮
     * - 全部为 false → isLoading = false，蒙层隐藏
     *
     * UI 通过 collectAsState() 订阅，在 isLoading=true 时弹出占满半屏的加载蒙层。
     *
     * 注意：声明位置必须在 [_exportProgress] 之后，否则 Kotlin 属性初始化顺序
     * 会导致 combine() 收到 null → 运行时 NPE。
     */
    val isLoading: StateFlow<Boolean> = combine(_backupInProgress, _exportProgress) { backup, export ->
        backup || (export is ExportProgress.Working)
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    /**
     * === v24 优化3：统一进度状态（与 ProgressDialog 配合）===
     *
     * 桥接 [ExportProgress] 与 [BackupProgress]：在两者变化时同步更新此 StateFlow，
     * 使 UI 层只需订阅一个 [progressState] 即可展示统一的 [com.shangmentiyu.sportscoach.ui.theme.ProgressDialog]。
     *
     * - 进行中：弹出 ProgressDialog，禁用返回，显示进度文案/百分比
     * - 完成：显示"完成"文案 1.5s 后自动消失（UI 层用 LaunchedEffect 控制）
     * - 失败：以完成态展示错误文案（文案本身已含错误信息）
     *
     * === v34 修复 NPE ===
     * 之前问题：`_progressState` 声明在 `init` 块之后，但 `init` 块内启动的协程
     *   会在 `_progressState` 初始化前被调度执行 → `_progressState.value = ...` 抛 NPE：
     *   "Attempt to invoke interface method 'void kotlinx.coroutines.flow.MutableStateFlow.setValue'"
     * 修复：将 `_progressState` 与 `progressState` 的声明移到 `init` 块之前，
     *   保证 `init` 块执行时 `_progressState` 已完成初始化。
     * 参考 Kotlin 属性初始化顺序：主构造函数 → 属性初始化（按声明顺序）→ init 块
     */
    private val _progressState = MutableStateFlow(ProgressState.Idle)
    val progressState: StateFlow<ProgressState> = _progressState.asStateFlow()

    // === v46 架构层三：单一 UiState 聚合（UI 只订阅这一个状态） ===
    // 嵌套 combine 规避 vararg combine 要求同类型限制；核心展示态聚合为 SettingsUiState，
    // 子模块局部状态（照片统计/缓存/桌面连接）保持独立 StateFlow。
    val uiState: StateFlow<SettingsUiState> = combine(
        combine(coach, todayCount, totalCount) { c, today, total -> Triple(c, today, total) },
        combine(autoBackupEnabled, _statusMessage, _progressState) { a, s, p -> Triple(a, s, p) },
        combine(_backupInProgress, _backupProgress, _needRestart) { i, p, n -> Triple(i, p, n) },
        floatingWindowEnabled,
        darkTheme
    ) { stats, flags, backup, floating, theme ->
        SettingsUiState(
            coach = stats.first,
            todayCount = stats.second,
            totalCount = stats.third,
            autoBackupEnabled = flags.first,
            floatingWindowEnabled = floating,
            darkTheme = theme,
            statusMessage = flags.second,
            progressState = flags.third,
            backupInProgress = backup.first,
            backupProgress = backup.second,
            needRestart = backup.third
        )
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    /** UI 消费了进度事件后调用，重置为 Idle（关闭 ProgressDialog） */
    fun consumeProgressState() {
        _progressState.value = ProgressState.Idle
    }

    /**
     * === v24 优化3：自动收集 ExportProgress / BackupProgress 变化并桥接到 progressState ===
     *
     * 通过 init block 在 viewModelScope 中订阅两个内部 StateFlow，
     * 任一变化时同步映射到统一 [progressState]，UI 层只需订阅一个 StateFlow 即可。
     *
     * === v34 修复 NPE：必须放在 _progressState 声明之后 ===
     * Kotlin 的 init 块在主构造函数后、按声明顺序执行的属性初始化之后执行。
     * 但 init 块内启动的协程是异步的，可能被立即调度执行。
     * 如果 _progressState 还没初始化（声明在 init 之后），协程就会读到 null。
     */
    init {
        safeLaunch {
            _exportProgress.collect { p ->
                syncProgressFromExport(p)
            }
        }
        safeLaunch {
            _backupProgress.collect { p ->
                syncProgressFromBackup(p)
            }
        }
    }

    /** 内部工具：将 ExportProgress 同步映射到统一 ProgressState */
    private fun syncProgressFromExport(p: ExportProgress) {
        _progressState.value = when (p) {
            is ExportProgress.Working -> ProgressState.workingCounted(
                current = p.current - 1,
                total = p.total,
                messagePrefix = p.message.substringBefore("（").trim()
            )
            is ExportProgress.Done -> ProgressState.done(p.message)
            is ExportProgress.Failed -> ProgressState.done(p.message)
            ExportProgress.Idle -> ProgressState.Idle
        }
    }

    /** 内部工具：将 BackupProgress 同步映射到统一 ProgressState */
    private fun syncProgressFromBackup(p: BackupProgress) {
        _progressState.value = when (p) {
            is BackupProgress.Working -> {
                if (p.total > 0) {
                    ProgressState.workingCounted(p.current - 1, p.total, p.message)
                } else {
                    ProgressState.working(p.message, -1f)
                }
            }
            is BackupProgress.Done -> ProgressState.done(p.message)
            BackupProgress.Idle -> ProgressState.Idle
        }
    }

    /** 自动保存防抖任务：连续输入时只保留最后一次写库 */
    private var saveJob: Job? = null

    /** 防抖延迟（毫秒）：用户停止输入 500ms 后才真正写库 */
    private val saveDebounceMs = 500L

    /** 最新待保存的教练姓名（用户最近一次输入，尚未写库） */
    private var pendingCoach: String? = null

    /**
     * 独立保存作用域：onCleared 时 viewModelScope 已取消，
     * 用此作用域保证最后一次防抖未触发的改动也能写入数据库。
     */
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 更新教练姓名：立即同步内存 StateFlow（UI 即时响应），
     * 数据库写入采用 500ms 防抖，连续输入只保留最后一次写库。
     */
    fun setCoach(name: String) {
        pendingCoach = name  // 记录最新待保存值
        saveJob?.cancel()
        saveJob = saveScope.launch {
            delay(saveDebounceMs)
            settingsRepo.setCoach(name)
            pendingCoach = null  // 已写库，清除待保存标记
        }
    }

    /**
     * 强制立即写库（页面退出等场景）：取消未触发的防抖任务，同步最新值。
     * 优先使用 pendingCoach（用户最近输入），避免 onCleared 时 StateFlow 尚未更新导致数据丢失。
     */
    fun flushSave() {
        val toSave = pendingCoach ?: coach.value
        saveJob?.cancel()
        saveJob = null
        saveScope.launch {
            settingsRepo.setCoach(toSave)
            pendingCoach = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        // ViewModel 销毁时确保最后一次改动已写库：
        // 使用 NonCancellable 保证写库协程在 scope.cancel() 后仍能完成
        saveJob?.cancel()
        val toSave = pendingCoach ?: coach.value
        saveScope.launch(NonCancellable) {
            settingsRepo.setCoach(toSave)
        }
        // 取消 saveScope 防止内存泄漏，NonCancellable 协程不受影响
        saveScope.cancel()
    }

    /**
     * 导出今日课堂记录到用户通过 SAF 选择的目录。
     *
     * 性能优化（v16）：
     * - 所有 IO + Excel 写入切到 [Dispatchers.IO]，避免主线程 ANR
     * - 通过 [exportProgress] StateFlow 实时反馈进度给 UI
     * - 使用 [lessonRepo.getTodayLessons] 直接命中索引查询，不再全表加载
     */
    fun exportTodayRecords(treeUri: Uri) {
        // === v50 防叠加：导出进行中忽略重复触发，防止多个导出任务并发写文件 ===
        if (_exportProgress.value is ExportProgress.Working) {
            _statusMessage.value = "正在导出中，请稍候"
            return
        }
        safeLaunch {
            // 线程安全：使用 [java.time.LocalDate] 替代 [SimpleDateFormat]
            val today = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()))

            // 1. 读取今日课时（SQL 直查命中 idx_lessons_date 索引）
            _exportProgress.value = ExportProgress.Working(0, 0, "正在读取今日记录…")
            val lessons = withContext(Dispatchers.IO) {
                lessonRepo.getTodayLessons().first()
            }

            if (lessons.isEmpty()) {
                _exportProgress.value = ExportProgress.Failed("今日无课堂记录")
                return@safeLaunch
            }

            val treeDir = withContext(Dispatchers.IO) {
                DocumentFile.fromTreeUri(app, treeUri)
            } ?: run {
                _exportProgress.value = ExportProgress.Failed("无法访问所选目录")
                return@safeLaunch
            }

            var success = 0
            var fail = 0
            for ((idx, lesson) in lessons.withIndex()) {
                _exportProgress.value = ExportProgress.Working(
                    current = idx + 1,
                    total = lessons.size,
                    message = "正在导出 ${lesson.studentName} 的记录（${idx + 1}/${lessons.size}）"
                )
                // 关键：Excel 写入是 CPU 密集型，必须 IO 线程
                val ok = withContext(Dispatchers.IO) {
                    try {
                        val student = studentRepo.getByName(lesson.studentName)
                        // === v50：文件名追加毫秒时间戳，杜绝同日同学员多条课时记录互相覆盖 ===
                        // 原实现 "${name}_${date}_课堂记录.xlsx" 无时间戳，循环导出时
                        // 后一条会 findFile→delete 掉前一条，最终只剩最后一条，数据丢失
                        val fileName = "${lesson.studentName}_${lesson.date}_" +
                            "${System.currentTimeMillis()}_课堂记录.xlsx"
                            .replace("/", "_").replace("\\", "_")
                        val docFile = treeDir.createFile(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            fileName
                        ) ?: return@withContext false
                        app.contentResolver.openOutputStream(docFile.uri)?.use { os ->
                            ExcelSync.exportLessonReport(lesson, student, os)
                        } ?: false
                    } catch (e: Exception) { false }
                }
                if (ok) success++ else fail++
            }
            val msg = "导出完成: 成功${success}条" + if (fail > 0) "，失败${fail}条" else ""
            _exportProgress.value = ExportProgress.Done(success, fail, msg)
            _statusMessage.value = msg
        }
    }

    /**
     * 导出成绩档案到用户通过 SAF 选择的目录。
     * 每个学员独立文件，若档案已存在则读取旧档案追加工作表后整体回写。
     *
     * 性能优化（v16）：全部切 IO 线程 + 进度 Flow，避免大数据量导出时 ANR。
     */
    fun exportScoresArchive(treeUri: Uri) {
        // === v50 防叠加：导出进行中忽略重复触发 ===
        if (_exportProgress.value is ExportProgress.Working) {
            _statusMessage.value = "正在导出中，请稍候"
            return
        }
        safeLaunch {
            _exportProgress.value = ExportProgress.Working(0, 0, "正在读取成绩记录…")
            val lessons = withContext(Dispatchers.IO) {
                lessonRepo.getAllLessons().first()
                    .filter { it.scores.isNotBlank() && it.scores != "{}" }
            }

            if (lessons.isEmpty()) {
                _exportProgress.value = ExportProgress.Failed("无成绩记录可导出")
                return@safeLaunch
            }

            val treeDir = withContext(Dispatchers.IO) {
                DocumentFile.fromTreeUri(app, treeUri)
            } ?: run {
                _exportProgress.value = ExportProgress.Failed("无法访问所选目录")
                return@safeLaunch
            }

            var success = 0
            var fail = 0
            for ((idx, lesson) in lessons.withIndex()) {
                _exportProgress.value = ExportProgress.Working(
                    current = idx + 1,
                    total = lessons.size,
                    message = "正在导出 ${lesson.studentName} 的成绩档案（${idx + 1}/${lessons.size}）"
                )
                val ok = withContext(Dispatchers.IO) {
                    try {
                        val student = studentRepo.getByName(lesson.studentName)
                        val name = (student?.name ?: lesson.studentName)
                            .replace("/", "_").replace("\\", "_")
                        val fileName = "$name.xlsx"

                        val existing = treeDir.findFile(fileName)
                        val inputStream: InputStream? = existing?.uri?.let { app.contentResolver.openInputStream(it) }

                        if (existing == null) {
                            treeDir.createFile(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                fileName
                            )
                        }
                        val target = treeDir.findFile(fileName)
                        if (target == null) {
                            inputStream?.close()
                            return@withContext false
                        }

                        val result = app.contentResolver.openOutputStream(target.uri)?.use { os ->
                            ExcelSync.exportScoresToArchive(lesson, student, inputStream, os) != null
                        } ?: false
                        inputStream?.close()
                        result
                    } catch (e: Exception) { false }
                }
                if (ok) success++ else fail++
            }
            val msg = "档案导出完成: 成功${success}条" + if (fail > 0) "，失败${fail}条" else ""
            _exportProgress.value = ExportProgress.Done(success, fail, msg)
            _statusMessage.value = msg
        }
    }

    /**
     * 从用户通过 SAF 选择的目录导入学员。
     * 遍历目录下所有 .xlsx 文件，解析 _meta 工作表。
     *
     * 性能优化（v16）：文件遍历 + Excel 解析切 IO 线程 + 进度 Flow。
     */
    fun importStudents(treeUri: Uri) {
        // 兼容旧调用：默认使用 APPEND 策略（追加，同名跳过）
        importStudentsWithStrategy(treeUri, ImportStrategy.APPEND)
    }

    /**
     * === v25 优化4：按指定策略从 Excel 档案导入学员 ===
     *
     * 流程：
     * 1. 遍历 SAF 目录下的所有 .xlsx 文件
     * 2. 解析 _meta 工作表得到 List<Student>
     * 3. 调用 [StudentRepository.importStudentsWithStrategy] 按策略处理同名学员
     * 4. 通过 [ExportProgress.Done] 反馈导入结果统计（新增/跳过/更新/失败）
     *
     * @param treeUri 用户通过 SAF 选择的目录 Uri
     * @param strategy 导入策略（APPEND / OVERWRITE / UPDATE_PART）
     */
    fun importStudentsWithStrategy(treeUri: Uri, strategy: ImportStrategy) {
        // === v50 防叠加：导入进行中忽略重复触发，防止并发解析/写库 ===
        if (_exportProgress.value is ExportProgress.Working) {
            _statusMessage.value = "正在导入中，请稍候"
            return
        }
        safeLaunch {
            _exportProgress.value = ExportProgress.Working(0, 0, "正在扫描档案文件…")
            val treeDir = withContext(Dispatchers.IO) {
                DocumentFile.fromTreeUri(app, treeUri)
            } ?: run {
                _exportProgress.value = ExportProgress.Failed("无法访问所选目录")
                return@safeLaunch
            }

            val xlsxFiles = withContext(Dispatchers.IO) {
                treeDir.listFiles().filter {
                    it.isFile && it.name?.endsWith(".xlsx") == true && !it.name!!.startsWith("~$")
                }
            }

            if (xlsxFiles.isEmpty()) {
                _exportProgress.value = ExportProgress.Failed("未找到可导入的学员档案")
                return@safeLaunch
            }

            _exportProgress.value = ExportProgress.Working(0, xlsxFiles.size, "正在解析档案…")
            val streams = mutableListOf<InputStream>()
            var fail = 0
            for ((idx, file) in xlsxFiles.withIndex()) {
                _exportProgress.value = ExportProgress.Working(
                    current = idx + 1,
                    total = xlsxFiles.size,
                    message = "正在解析 ${file.name}（${idx + 1}/${xlsxFiles.size}）"
                )
                val s = withContext(Dispatchers.IO) {
                    runCatching { app.contentResolver.openInputStream(file.uri) }.getOrNull()
                }
                if (s != null) streams.add(s) else fail++
            }

            val students = withContext(Dispatchers.IO) {
                ExcelSync.importStudentsFromExcel(streams)
            }
            streams.forEach { runCatching { it.close() } }

            if (students.isEmpty()) {
                _exportProgress.value = ExportProgress.Failed("未找到可导入的学员")
                return@safeLaunch
            }

            // v25 优化4：按用户选择的策略处理同名学员
            val result = studentRepo.importStudentsWithStrategy(students, strategy)
            val msg = result.toUserMessage()
            _exportProgress.value = ExportProgress.Done(result.added, fail, msg)
            _statusMessage.value = msg
        }
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    /** 更新状态消息（用于更新检查等场景） */
    fun updateStatus(message: String) {
        _statusMessage.value = message
    }

    // ==================== 数据备份与恢复 ====================

    /**
     * 执行整库备份到用户通过 SAF 选择的目标文件 Uri。
     *
     * 流程：
     * 1. 标记进行中状态（UI 禁用按钮）
     * 2. 调用 [BackupRepository.backup] 执行备份，传入进度回调
     * 3. 通过 [statusMessage] 反馈结果给 UI，通过 [backupProgress] 反馈阶段进度
     *
     * @param targetUri 用户通过 SAF CreateDocument 选择的目标文件 Uri
     */
    fun backupData(targetUri: Uri) {
        // === v50 防叠加：备份进行中忽略重复触发 ===
        if (_backupInProgress.value) {
            _statusMessage.value = "正在备份中，请稍候"
            return
        }
        safeLaunch {
            _backupInProgress.value = true
            _backupProgress.value = BackupProgress.Working("prepare", 0, 0, "正在准备备份…")
            try {
                val result = backupRepo.backup(targetUri) { phase, current, total, msg ->
                    _backupProgress.value = BackupProgress.Working(phase, current, total, msg)
                }
                _statusMessage.value = result.message
                _backupProgress.value = BackupProgress.Done(result.message)

                // === v23 双端同步：开启「桌面同步」时，手动备份成功即自动推送到 PC 合并 ===
                // 独立 runCatching：推送失败不影响备份成功状态
                if (result.success) {
                    runCatching {
                        if (settingsRepo.syncEnabled.first()) {
                            lanSyncManager?.let { mgr ->
                                val push = mgr.pushBackupUri(
                                    app, targetUri,
                                    name = "smty_backup_${System.currentTimeMillis() / 1000}.smty_backup")
                                _statusMessage.value = "${result.message}；${push.message}"
                            }
                        }
                    }
                }
            } finally {
                _backupInProgress.value = false
                backupRefreshTick.value++  // 刷新固定文件夹内"最新备份"展示
            }
        }
    }

    /**
     * 从用户通过 SAF 选择的备份文件恢复整库数据。
     *
     * 流程（v47 起安全备份由 BackupManager.restoreDetailed 内部保证）：
     * 1. 标记进行中状态（UI 禁用按钮）
     * 2. 调用 [BackupRepository.restore] 执行恢复
     *    - BackupManager 恢复前自动创建安全备份；恢复失败自动回滚到恢复前数据
     * 3. 成功时标记 [needRestart]，UI 据此引导用户重启应用
     * 4. 失败时展示错误消息（此时恢复前数据已被自动回滚，或双失败时保留在缓存目录）
     *
     * 警告：恢复会覆盖当前所有学员/课时包/排课/签到数据。
     *
     * @param sourceUri 用户通过 SAF OpenDocument 选择的备份文件 Uri
     */
    fun restoreData(sourceUri: Uri) {
        // === v50 防叠加：恢复进行中忽略重复触发（恢复会覆盖整库，绝不允许并发） ===
        if (_backupInProgress.value) {
            _statusMessage.value = "正在恢复中，请稍候"
            return
        }
        safeLaunch {
            _backupInProgress.value = true
            try {
                _backupProgress.value = BackupProgress.Working("restore", 0, 0, "正在恢复数据…")
                val result = backupRepo.restore(sourceUri) { phase, current, total, msg ->
                    _backupProgress.value = BackupProgress.Working(phase, current, total, msg)
                }
                _statusMessage.value = result.message
                if (result.success && result.needRestart) {
                    // === v46 修复：恢复成功立即自动重启（根治"恢复后无法添加学员"）===
                    // 数据库已被 closeAndResetInstance 关闭，各 Repository 注入的旧 db 引用永久失效；
                    // 若继续运行（用户按返回键/不点重启），此后所有写操作（添加学员/排课/签到等）
                    // 必然失败。数据已替换，重启加载新数据是唯一正确路径——不再依赖用户点击确认。
                    _needRestart.value = true  // 兜底：若自动重启异常，UI 仍弹"立即重启"确认框
                    _backupProgress.value = BackupProgress.Done(result.message)
                    restartApp()
                } else {
                    // 恢复失败：BackupManager 已尝试自动回滚，本地数据状态见消息说明
                    _backupProgress.value = BackupProgress.Done(result.message)
                    _statusMessage.value = result.message
                }
            } finally {
                _backupInProgress.value = false
            }
        }
    }

    /**
     * 重启应用以加载恢复后的新数据。
     *
     * 通过 Intent 重启 MainActivity，结束当前任务栈，确保所有旧 ViewModel 失效、
     * 数据库重新打开、StateFlow 重新订阅，避免脏读。
     */
    fun restartApp() {
        val intent = app.packageManager.getLaunchIntentForPackage(app.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        app.startActivity(intent)
        // 杀掉当前进程，彻底清理旧 ViewModel 与数据库连接
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    /** 生成默认备份文件名（UI 在 SAF CreateDocument 时使用） */
    fun generateBackupFileName(): String = BackupManager.generateBackupFileName()

    /** UI 消费了 needRestart 事件后调用，重置标记 */
    fun consumeNeedRestart() {
        _needRestart.value = false
    }

    // ==================== 签到照片存储空间管理 ====================

    /** 一年毫秒数：用于"清理一年前签到照片"判定阈值 */
    private val ONE_YEAR_MS = 365L * 24 * 60 * 60 * 1000

    /** 签到照片目录名（与 BackupManager.PHOTOS_DIR_NAME 保持一致） */
    private val signPhotosDirName = "SignPhotos"

    /** 签到照片目录大小（字节），UI 据此展示 MB/GB */
    private val _signPhotosSize = MutableStateFlow(0L)
    val signPhotosSize: StateFlow<Long> = _signPhotosSize.asStateFlow()

    /** 签到照片总数 */
    private val _signPhotosCount = MutableStateFlow(0)
    val signPhotosCount: StateFlow<Int> = _signPhotosCount.asStateFlow()

    /** 一年前可清理的照片数量（预扫描，用于二次确认弹窗展示） */
    private val _cleanableCount = MutableStateFlow(0)
    val cleanableCount: StateFlow<Int> = _cleanableCount.asStateFlow()

    /**
     * 扫描签到照片目录：统计目录大小、文件总数、可清理数（一年前）。
     *
     * 性能：目录遍历在 [Dispatchers.IO] 执行，避免主线程卡顿。
     * 异常兜底：目录不存在或权限失败时返回 0，不阻塞 UI。
     */
    fun scanSignPhotos() {
        safeLaunch {
            try {
                BackupManager.migrateLegacySignPhotosDir(app.filesDir)
                val dir = File(app.filesDir, signPhotosDirName)
                if (!dir.exists()) {
                    _signPhotosSize.value = 0
                    _signPhotosCount.value = 0
                    _cleanableCount.value = 0
                    return@safeLaunch
                }
                var totalSize = 0L
                var totalCount = 0
                var cleanable = 0
                val threshold = System.currentTimeMillis() - ONE_YEAR_MS
                dir.walkTopDown().forEach { f ->
                    if (f.isFile) {
                        totalSize += f.length()
                        totalCount++
                        if (f.lastModified() < threshold) cleanable++
                    }
                }
                _signPhotosSize.value = totalSize
                _signPhotosCount.value = totalCount
                _cleanableCount.value = cleanable
            } catch (e: Exception) {
                // 静默失败：存储统计不阻塞 UI
            }
        }
    }

    /**
     * 清理一年前的签到照片。
     *
     * 调用时机：用户在二次确认对话框点击"确认清理"后调用。
     * 流程：
     * 1. 遍历 SignPhotos 目录，删除 lastModified < (now - 1 年) 的文件
     * 2. 重新扫描目录大小与文件数
     * 3. 通过 [statusMessage] 反馈清理结果
     *
     * 安全保证：
     * - 仅删除 filesDir/SignPhotos/ 下的文件，不会跨目录
     * - 仅按文件修改时间判定，不依赖文件名解析（兼容历史数据）
     * - 异常路径不中断，已删除的文件不回滚
     */
    fun cleanOldSignPhotos() {
        safeLaunch {
            try {
                val dir = File(app.filesDir, signPhotosDirName)
                if (!dir.exists()) {
                    _statusMessage.value = "签到照片目录不存在"
                    return@safeLaunch
                }
                val threshold = System.currentTimeMillis() - ONE_YEAR_MS
                var deleted = 0
                var freedBytes = 0L
                dir.walkTopDown().forEach { f ->
                    if (f.isFile && f.lastModified() < threshold) {
                        val size = f.length()
                        if (f.delete()) {
                            deleted++
                            freedBytes += size
                        }
                    }
                }
                // 清理后重新扫描
                scanSignPhotos()
                val freed = formatBytes(freedBytes)
                _statusMessage.value = "已清理 $deleted 张一年前的签到照片，释放 $freed"
            } catch (e: Exception) {
                _statusMessage.value = "清理失败：${e.message}"
            }
        }
    }

    /**
     * 将字节数格式化为人类可读字符串（B/KB/MB/GB）。
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIdx = 0
        while (size >= 1024 && unitIdx < units.size - 1) {
            size /= 1024
            unitIdx++
        }
        return if (unitIdx == 0) "${bytes.toInt()} ${units[unitIdx]}"
        else String.format(Locale.getDefault(), "%.2f %s", size, units[unitIdx])
    }

    // ==================== v29 优化3：缓存管理（孤立照片 + 临时缓存） ====================

    /**
     * === v29 优化3：孤立照片判定阈值（6 个月） ===
     *
     * 业务规则：lessons 表中 photoPath / signOutPhotoPath / contentImages 引用的照片
     * 视为"有效引用"，无论时间多久都不清理；未被任何 lesson 引用且文件 lastModified
     * 超过此阈值的照片视为"孤立照片"，可一键清理。
     *
     * 与 [ONE_YEAR_MS] 区别：[ONE_YEAR_MS] 用于"按时间清理一年前所有照片"（含被引用的），
     * 本阈值用于"按引用关系清理未被引用的孤立文件"，更安全（不会删除还在用的照片）。
     */
    private val ORPHAN_THRESHOLD_MS = 180L * 24 * 60 * 60 * 1000  // 180 天

    /** 孤立照片数量（预扫描，UI 二次确认弹窗展示） */
    private val _orphanPhotoCount = MutableStateFlow(0)
    val orphanPhotoCount: StateFlow<Int> = _orphanPhotoCount.asStateFlow()

    /** 孤立照片占用空间（字节，用于二次确认弹窗展示可释放空间） */
    private val _orphanPhotoSize = MutableStateFlow(0L)
    val orphanPhotoSize: StateFlow<Long> = _orphanPhotoSize.asStateFlow()

    /** 应用缓存目录大小（字节，UI 展示 cacheDir 占用） */
    private val _cacheSize = MutableStateFlow(0L)
    val cacheSize: StateFlow<Long> = _cacheSize.asStateFlow()

    /** 应用缓存目录临时文件数量 */
    private val _cacheFileCount = MutableStateFlow(0)
    val cacheFileCount: StateFlow<Int> = _cacheFileCount.asStateFlow()

    /**
     * === v29 优化3：扫描孤立照片 ===
     *
     * 流程：
     * 1. 查询 lessons 表全部记录，收集所有"被引用"的照片路径作为白名单
     *    （photoPath、signOutPhotoPath、contentImages JSON 数组中的每一项）
     * 2. 遍历 filesDir/SignPhotos/ 目录下的所有文件
     * 3. 文件不在白名单中 + lastModified < (now - 180 天) → 标记为孤立
     * 4. 累加孤立数量与占用空间，通过 StateFlow 反馈给 UI
     *
     * 设计权衡：
     * - 用"被引用关系"判定而非"按时间一刀切"：避免删除学员最近还在用的照片
     * - 同时叠加 6 个月时间阈值：避免删除教练刚拍但 lesson 尚未落库的临时文件
     *   （极端场景：教练拍完照 App 闪退，lesson 未创建，照片孤立但很新，不应清理）
     *
     * 性能：lessons 全量查询 + 文件遍历均在 [Dispatchers.IO] 执行，避免阻塞主线程。
     * 异常兜底：失败时静默返回 0，不阻塞 UI 展示。
     */
    fun scanOrphanPhotos() {
        safeLaunch {
            try {
                val db = com.shangmentiyu.sportscoach.data.db.AppDatabase.getDatabase(app)
                val photosDir = File(app.filesDir, signPhotosDirName)
                if (!photosDir.exists()) {
                    _orphanPhotoCount.value = 0
                    _orphanPhotoSize.value = 0L
                    return@safeLaunch
                }

                withContext(Dispatchers.IO) {
                    // 1. 收集所有被引用的照片绝对路径（白名单）
                    val allLessons = db.lessonDao().getAllOnce()
                    val referencedSet = HashSet<String>(allLessons.size * 2)
                    for (lesson in allLessons) {
                        if (lesson.photoPath.isNotBlank()) referencedSet.add(lesson.photoPath)
                        if (lesson.signOutPhotoPath.isNotBlank()) {
                            referencedSet.add(lesson.signOutPhotoPath)
                        }
                        // 解析 contentImages JSON 数组（课后反馈训练内容图片）
                        if (lesson.contentImages.isNotBlank()
                            && lesson.contentImages != "[]"
                            && lesson.contentImages != "null"
                        ) {
                            try {
                                val arr = org.json.JSONArray(lesson.contentImages)
                                for (i in 0 until arr.length()) {
                                    val p = arr.optString(i, "")
                                    if (p.isNotBlank()) referencedSet.add(p)
                                }
                            } catch (_: Exception) {
                                // 单条 JSON 解析失败不影响整体扫描
                            }
                        }
                    }

                    // 2. 规范化白名单：提取文件绝对路径，兼容相对路径与绝对路径
                    val normalizedReferenced = referencedSet.mapTo(HashSet()) { normalizePhotoPath(it) }

                    // 3. 遍历照片目录，统计孤立文件
                    val threshold = System.currentTimeMillis() - ORPHAN_THRESHOLD_MS
                    var orphanCount = 0
                    var orphanSize = 0L
                    photosDir.walkTopDown().forEach { f ->
                        if (!f.isFile) return@forEach
                        val normalized = normalizePhotoPath(f.absolutePath)
                        val isReferenced = normalized in normalizedReferenced
                        if (!isReferenced && f.lastModified() < threshold) {
                            orphanCount++
                            orphanSize += f.length()
                        }
                    }

                    _orphanPhotoCount.value = orphanCount
                    _orphanPhotoSize.value = orphanSize
                }
            } catch (_: Exception) {
                // 静默失败：扫描不阻塞 UI
                _orphanPhotoCount.value = 0
                _orphanPhotoSize.value = 0L
            }
        }
    }

    /**
     * === v29 优化3：归一化照片路径 ===
     *
     * 统一比较口径：lesson 中存储的路径可能是相对路径（如 "SignPhotos/xxx.jpg"）
     * 或绝对路径（如 "/data/.../SignPhotos/xxx.jpg"），文件系统扫描得到的是绝对路径。
     *
     * 归一化策略：取文件名 + 文件长度作为唯一标识，避免路径前缀差异导致误判。
     * 若无法解析（如 lesson 中存的是 SAF Uri），保留原始字符串作为兜底。
     */
    private fun normalizePhotoPath(path: String): String {
        return try {
            val file = File(path)
            "${file.name}#${file.length()}"
        } catch (_: Exception) {
            path
        }
    }

    /**
     * === v29 优化3：清理孤立照片 ===
     *
     * 调用时机：用户在二次确认弹窗点击"确认清理"后调用。
     *
     * 流程：
     * 1. 重新扫描白名单（避免清理期间数据变化导致误删）
     * 2. 遍历 filesDir/SignPhotos/，删除未被引用 + 超过 6 个月的孤立文件
     * 3. 重新扫描孤立照片与签到照片目录统计，反馈清理结果
     *
     * 安全保证：
     * - 二次扫描白名单确保最新数据（清理期间教练可能新建了 lesson）
     * - 删除前双重校验：未被引用 + 超过阈值
     * - 异常不中断：已删除的文件不回滚
     * - 仅删除 filesDir/SignPhotos/ 下文件，不跨目录
     */
    fun cleanOrphanPhotos() {
        safeLaunch {
            try {
                val db = com.shangmentiyu.sportscoach.data.db.AppDatabase.getDatabase(app)
                val photosDir = File(app.filesDir, signPhotosDirName)
                if (!photosDir.exists()) {
                    _statusMessage.value = "签到照片目录不存在"
                    return@safeLaunch
                }

                var deleted = 0
                var freedBytes = 0L
                withContext(Dispatchers.IO) {
                    // 1. 重新收集白名单（最新数据）
                    val allLessons = db.lessonDao().getAllOnce()
                    val referencedSet = HashSet<String>(allLessons.size * 2)
                    for (lesson in allLessons) {
                        if (lesson.photoPath.isNotBlank()) referencedSet.add(lesson.photoPath)
                        if (lesson.signOutPhotoPath.isNotBlank()) {
                            referencedSet.add(lesson.signOutPhotoPath)
                        }
                        if (lesson.contentImages.isNotBlank()
                            && lesson.contentImages != "[]"
                            && lesson.contentImages != "null"
                        ) {
                            try {
                                val arr = org.json.JSONArray(lesson.contentImages)
                                for (i in 0 until arr.length()) {
                                    val p = arr.optString(i, "")
                                    if (p.isNotBlank()) referencedSet.add(p)
                                }
                            } catch (_: Exception) { }
                        }
                    }
                    val normalizedReferenced = referencedSet.mapTo(HashSet()) { normalizePhotoPath(it) }

                    // 2. 删除孤立 + 超过阈值的文件
                    val threshold = System.currentTimeMillis() - ORPHAN_THRESHOLD_MS
                    photosDir.walkTopDown().forEach { f ->
                        if (!f.isFile) return@forEach
                        val normalized = normalizePhotoPath(f.absolutePath)
                        if (normalized !in normalizedReferenced && f.lastModified() < threshold) {
                            val size = f.length()
                            if (f.delete()) {
                                deleted++
                                freedBytes += size
                            }
                        }
                    }
                }

                // 3. 清理后重新扫描，UI 状态最新
                scanOrphanPhotos()
                scanSignPhotos()
                val freed = formatBytes(freedBytes)
                _statusMessage.value = "已清理 $deleted 个孤立照片，释放 $freed"
                // v26 优化1：记录清理日志（审计溯源）
                auditLogRepo?.log(
                    action = "清理孤立照片",
                    summary = "清理 $deleted 个孤立照片，释放 $freed"
                )
            } catch (e: Exception) {
                _statusMessage.value = "清理失败：${e.message}"
            }
        }
    }

    /**
     * === v29 优化3：扫描应用缓存目录大小 ===
     *
     * 遍历 app.cacheDir 目录，统计全部临时文件大小与数量。
     * 用于 UI 展示"缓存占用"与可清理数量，让用户决定是否一键清理。
     *
     * 注意：cacheDir 中的文件包括系统临时文件、Glide 图片缓存、
     * BackupRepository 的安全备份等，全部统计为可清理（清理后系统会自动重建）。
     */
    fun scanCacheSize() {
        safeLaunch {
            try {
                val cacheDir = app.cacheDir
                if (!cacheDir.exists()) {
                    _cacheSize.value = 0L
                    _cacheFileCount.value = 0
                    return@safeLaunch
                }
                var totalSize = 0L
                var totalFiles = 0
                cacheDir.walkTopDown().forEach { f ->
                    if (f.isFile) {
                        totalSize += f.length()
                        totalFiles++
                    }
                }
                _cacheSize.value = totalSize
                _cacheFileCount.value = totalFiles
            } catch (_: Exception) {
                // 静默失败：扫描不阻塞 UI
                _cacheSize.value = 0L
                _cacheFileCount.value = 0
            }
        }
    }

    /**
     * === v29 优化3：清理应用缓存目录 ===
     *
     * 删除 app.cacheDir 下的全部临时文件，但保留目录结构（避免系统重建目录失败）。
     *
     * 调用时机：用户在二次确认弹窗点击"确认清理缓存"后调用。
     *
     * 安全保证：
     * - 仅删除文件，保留目录（防止系统 / 第三方 SDK 因目录缺失而异常）
     * - 不删除 cacheDir 根目录本身
     * - 异常不中断：已删除的文件不回滚
     *
     * 注意：安全备份文件（smty_safety_*.smty_backup）也会被清理——这些文件仅在
     * 数据恢复失败时用于手动回滚，正常恢复成功后已无意义；用户主动清理缓存视为
     * 确认放弃回滚保险。
     */
    fun cleanCacheFiles() {
        safeLaunch {
            try {
                val cacheDir = app.cacheDir
                if (!cacheDir.exists()) {
                    _statusMessage.value = "缓存目录不存在"
                    return@safeLaunch
                }
                var deleted = 0
                var freedBytes = 0L
                withContext(Dispatchers.IO) {
                    cacheDir.walkTopDown().forEach { f ->
                        // 跳过根目录本身，仅删除子文件
                        if (f.isFile) {
                            val size = f.length()
                            if (f.delete()) {
                                deleted++
                                freedBytes += size
                            }
                        }
                    }
                }
                // 清理后重新扫描
                scanCacheSize()
                val freed = formatBytes(freedBytes)
                _statusMessage.value = "已清理 $deleted 个临时缓存文件，释放 $freed"
                // v26 优化1：记录清理日志
                auditLogRepo?.log(
                    action = "清理缓存文件",
                    summary = "清理 $deleted 个临时缓存文件，释放 $freed"
                )
            } catch (e: Exception) {
                _statusMessage.value = "缓存清理失败：${e.message}"
            }
        }
    }

    // ==================== v32 优化3：UDP 设备自动发现 ====================

    /**
     * 桌面端连接状态（绿色指示灯）。
     *
     * - null：未检测到桌面端
     * - 非 null：包含桌面端 IP 与最后心跳时间
     *
     * 数据来源：[com.shangmentiyu.sportscoach.app.framework.UdpDesktopDiscoveryService] 收到广播后写入
     */
    private val _desktopConnection = MutableStateFlow<DesktopConnection?>(null)
    val desktopConnection: StateFlow<DesktopConnection?> = _desktopConnection.asStateFlow()

    /** 桌面端连接信息数据类 */
    data class DesktopConnection(
        val host: String,
        val port: Int,
        val lastSeenAt: Long  // 收到广播的时间戳（毫秒）
    ) {
        /** 是否在 60 秒内有过广播（超过视为离线） */
        val isAlive: Boolean
            get() = System.currentTimeMillis() - lastSeenAt < 60_000L
    }

    /** 进入设置页时刷新一次桌面端连接状态 */
    fun refreshDesktopConnection() {
        val prefs = app.getSharedPreferences("desktop_discovery", Context.MODE_PRIVATE)
        val host = prefs.getString("host", "") ?: ""
        if (host.isBlank()) {
            _desktopConnection.value = null
            return
        }
        _desktopConnection.value = DesktopConnection(
            host = host,
            port = prefs.getInt("port", 0),
            lastSeenAt = prefs.getLong("last_seen_at", 0L)
        )
    }
}
