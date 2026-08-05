package com.shangmentiyu.sportscoach.ui.script

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangmentiyu.sportscoach.data.repo.ScriptRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 话术管理 ViewModel（协调层）。
 *
 * 协调 [ScriptRepository] 完成话术的增删改查，
 * 暴露 [scripts] 状态流供 UI 订阅。
 *
 * 所有数据库/文件操作均在 viewModelScope 内执行，避免阻塞主线程。
 */
class ScriptViewModel(
    private val repo: ScriptRepository
) : ViewModel() {

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private val appExceptionHandler =
        com.shangmentiyu.sportscoach.core.CoroutineExt.createAppExceptionHandler(_toast, "ScriptViewModel")

    /** 所有话术列表（按更新时间倒序） */
    val scripts: StateFlow<List<ScriptRepository.ScriptItem>> = repo.scripts

    /** 当前编辑的话术（null 表示新建） */
    private val _current = MutableStateFlow<ScriptRepository.ScriptItem?>(null)
    val current: StateFlow<ScriptRepository.ScriptItem?> = _current.asStateFlow()

    /** 加载指定 ID 的话术到 current（用于编辑页） */
    fun loadCurrent(id: String?) {
        viewModelScope.launch(appExceptionHandler) {
            _current.value = if (id.isNullOrBlank()) null else repo.getById(id)
        }
    }

    /** 新增话术，返回新 ID（通过 [current] 状态返回） */
    fun add(name: String, content: String) {
        viewModelScope.launch(appExceptionHandler) {
            val id = repo.add(name, content)
            _current.value = repo.getById(id)
            _toast.value = "已保存"
        }
    }

    /** 更新话术 */
    fun update(id: String, name: String, content: String) {
        viewModelScope.launch(appExceptionHandler) {
            repo.update(id, name, content)
            _current.value = repo.getById(id)
            _toast.value = "已保存"
        }
    }

    /** 删除话术 */
    fun delete(id: String) {
        viewModelScope.launch(appExceptionHandler) {
            repo.delete(id)
            _toast.value = "已删除"
        }
    }

    /** 清除 toast 状态 */
    fun consumeToast() {
        _toast.value = null
    }

    /** 清除 current 状态（退出编辑页时重置） */
    fun clearCurrent() {
        _current.value = null
    }
}
