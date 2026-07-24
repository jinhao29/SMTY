package com.shangmentiyu.sportscoach.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 更新进度总线（全局单例）。
 *
 * 设计目的：
 * WorkManager 后台 Worker 无法直接与 UI 通信（Worker 在独立进程上下文执行），
 * 通过全局 StateFlow 让 UI 层订阅下载进度，实现"Worker 写入 → UI 实时刷新"。
 *
 * 使用方式：
 * - Worker 在 doWork 中调用 [emit] 推送进度
 * - UI 通过 [progress] 订阅并渲染进度浮层
 *
 * 注意：StateFlow 会保留最新值，UI 重建后（如旋转屏）仍能立即拿到当前进度。
 */
object UpdateProgressBus {

    /**
     * 更新进度状态。
     *
     * @property percent 下载进度 0-100
     * @property version 目标版本号
     * @property message 可读消息（用于失败时展示原因）
     */
    sealed class UpdateProgress {
        /** 空闲：无下载任务 */
        data object Idle : UpdateProgress()
        /** 下载中：UI 据此显示进度浮层 */
        data class Downloading(val percent: Int, val version: String) : UpdateProgress()
        /** 下载完成：UI 据此显示"准备安装"提示，并触发安装流程 */
        data class Done(val version: String) : UpdateProgress()
        /** 下载失败：UI 据此显示错误消息 */
        data class Failed(val message: String) : UpdateProgress()
    }

    private val _progress = MutableStateFlow<UpdateProgress>(UpdateProgress.Idle)
    val progress: StateFlow<UpdateProgress> = _progress.asStateFlow()

    /** Worker 调用此方法推送进度（线程安全） */
    fun emit(p: UpdateProgress) {
        _progress.value = p
    }

    /** 重置为 Idle（UI 消费完进度事件后调用） */
    fun reset() {
        _progress.value = UpdateProgress.Idle
    }
}
