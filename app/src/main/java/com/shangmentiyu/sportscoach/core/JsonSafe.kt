package com.shangmentiyu.sportscoach.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON 安全解析工具（处理器层）。
 *
 * 职责：
 * - 统一封装 [JSONObject] / [JSONArray] 的解析，捕获 [Exception] 后返回安全默认值
 * - 防止脏数据（手动改库、迁移残留、旧版格式）导致整页崩溃
 *
 * 使用方式：
 * ```kotlin
 * val obj = JsonSafe.parseObject(json)        // 返回 JSONObject?，失败返回 null
 * val arr = JsonSafe.parseArray(json)         // 返回 JSONArray?，失败返回 null
 * val len = JsonSafe.arrayLength(json)        // 返回 Int，失败返回 0
 * val len = JsonSafe.objectLength(json)       // 返回 Int，失败返回 0
 * ```
 *
 * 线程安全：纯函数，无状态。
 */
object JsonSafe {

    /**
     * 安全解析 JSON 字符串为 [JSONObject]。
     * @param json 待解析字符串，null 或空串返回 null
     * @return 解析成功返回 JSONObject，失败返回 null
     */
    fun parseObject(json: String?): JSONObject? {
        if (json.isNullOrBlank()) return null
        return try {
            JSONObject(json)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 安全解析 JSON 字符串为 [JSONArray]。
     * @param json 待解析字符串，null 或空串返回 null
     * @return 解析成功返回 JSONArray，失败返回 null
     */
    fun parseArray(json: String?): JSONArray? {
        if (json.isNullOrBlank()) return null
        return try {
            JSONArray(json)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 安全获取 JSON 数组长度。
     * @param json 待解析字符串
     * @return 数组长度，解析失败返回 0
     */
    fun arrayLength(json: String?): Int {
        return parseArray(json)?.length() ?: 0
    }

    /**
     * 安全获取 JSON 对象的字段数。
     * @param json 待解析字符串
     * @return 字段数，解析失败返回 0
     */
    fun objectLength(json: String?): Int {
        return parseObject(json)?.length() ?: 0
    }

    /**
     * 安全遍历 JSON 对象的所有 key。
     * @return key 列表，解析失败返回空列表
     */
    fun keys(json: String?): List<String> {
        val obj = parseObject(json) ?: return emptyList()
        return try {
            obj.keys().asSequence().toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 安全获取 JSON 对象中某个 key 对应的子对象。
     * @return 子 JSONObject，失败返回 null
     */
    fun getObject(parent: JSONObject, key: String): JSONObject? {
        return try {
            parent.optJSONObject(key)
        } catch (_: Exception) {
            null
        }
    }
}
