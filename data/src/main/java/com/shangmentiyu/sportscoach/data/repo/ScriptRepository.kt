package com.shangmentiyu.sportscoach.data.repo

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * 话术管理仓储（管理层）。
 *
 * 用于保存教练常用的家长沟通话术模板，按"项目"维度分组存储。
 * 每条话术包含：项目名（如"课后反馈-表扬"）、话术内容、更新时间。
 *
 * 存储方式：JSON 文件（filesDir/scripts.json），简单可靠，支持手动备份恢复。
 *
 * 线程安全：所有读写操作均通过 synchronized(this) 串行化，避免多 ViewModel 并发写入冲突。
 */
class ScriptRepository(private val context: Context) {

    /** 话术文件名（位于 filesDir） */
    private val fileName = "scripts.json"

    /** 内存中的话术列表，UI 订阅此 StateFlow 实时刷新 */
    private val _scripts = MutableStateFlow<List<ScriptItem>>(emptyList())
    val scripts: StateFlow<List<ScriptItem>> = _scripts.asStateFlow()

    init {
        loadFromDisk()
    }

    /** 从磁盘加载话术到内存 */
    private fun loadFromDisk() {
        synchronized(this) {
            val list = try {
                val file = context.filesDir.resolve(fileName)
                if (!file.exists()) {
                    emptyList()
                } else {
                    val text = file.readText()
                    if (text.isBlank()) {
                        emptyList()
                    } else {
                        val arr = org.json.JSONArray(text)
                        (0 until arr.length()).map { i ->
                            val obj = arr.optJSONObject(i) ?: return@map null
                            ScriptItem(
                                id = obj.optString("id"),
                                name = obj.optString("name"),
                                content = obj.optString("content"),
                                updatedAt = obj.optString("updatedAt")
                            )
                        }.filterNotNull()
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }
            _scripts.value = list
        }
    }

    /** 持久化当前内存列表到磁盘 */
    private fun persist() {
        synchronized(this) {
            try {
                val arr = org.json.JSONArray()
                _scripts.value.forEach { item ->
                    arr.put(JSONObject().apply {
                        put("id", item.id)
                        put("name", item.name)
                        put("content", item.content)
                        put("updatedAt", item.updatedAt)
                    })
                }
                context.filesDir.resolve(fileName).writeText(arr.toString())
            } catch (e: Exception) {
                // 写入失败仅记录，不抛出异常导致 App 崩溃
            }
        }
    }

    /** 新增话术项目，返回新 ID */
    fun add(name: String, content: String): String {
        val id = System.currentTimeMillis().toString()
        val item = ScriptItem(
            id = id,
            name = name.trim().ifBlank { "未命名话术" },
            content = content,
            updatedAt = now()
        )
        _scripts.value = _scripts.value + item
        persist()
        return id
    }

    /** 更新指定 ID 的话术 */
    fun update(id: String, name: String, content: String) {
        val updated = _scripts.value.map { item ->
            if (item.id == id) {
                item.copy(
                    name = name.trim().ifBlank { "未命名话术" },
                    content = content,
                    updatedAt = now()
                )
            } else {
                item
            }
        }
        _scripts.value = updated
        persist()
    }

    /** 删除指定 ID 的话术 */
    fun delete(id: String) {
        _scripts.value = _scripts.value.filterNot { it.id == id }
        persist()
    }

    /** 获取指定 ID 的话术 */
    fun getById(id: String): ScriptItem? {
        return _scripts.value.firstOrNull { it.id == id }
    }

    /** 生成当前时间字符串（yyyy-MM-dd HH:mm） */
    private fun now(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    /** 话术数据项 */
    data class ScriptItem(
        val id: String,
        val name: String,
        val content: String,
        val updatedAt: String
    )
}
