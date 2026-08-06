package com.shangmentiyu.sportscoach.ui.settings.state

import com.shangmentiyu.sportscoach.core.ProgressState
import com.shangmentiyu.sportscoach.ui.settings.SettingsViewModel

/**
 * 设置页 UI 状态（架构层三，v46）。
 *
 * 聚合设置页核心展示状态为单个不可变 UiState，ViewModel 只向外暴露本状态，
 * UI 层据此渲染（单向数据流：UI → 事件 → ViewModel → UiState → UI）。
 *
 * 设计说明：签到照片统计 / 缓存管理 / 桌面连接等子模块的局部状态**刻意保持独立**
 * StateFlow——它们各自独立加载、低频更新，若强行并入主 UiState，
 * 任一字段变化都会重建整棵状态树，反而放大 Compose 重组范围。
 */
data class SettingsUiState(
    val coach: String = "",
    val todayCount: Int = 0,
    val totalCount: Int = 0,
    val autoBackupEnabled: Boolean = true,
    val floatingWindowEnabled: Boolean = false,
    // v48 新增：深色模式偏好（null=跟随系统 / true=深色 / false=亮色）
    val darkTheme: Boolean? = null,
    val statusMessage: String? = null,
    val backupInProgress: Boolean = false,
    val backupProgress: SettingsViewModel.BackupProgress = SettingsViewModel.BackupProgress.Idle,
    val needRestart: Boolean = false,
    val progressState: ProgressState = ProgressState.Idle
)
