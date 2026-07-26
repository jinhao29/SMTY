package com.shangmentiyu.sportscoach.data.repo

import androidx.room.withTransaction
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.db.AuditLogDao
import com.shangmentiyu.sportscoach.data.model.AuditLogEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

/**
 * === v26 优化1：操作日志 Repository（协调层）===
 *
 * 设计目的：
 * - 给 [StudentRepository] / [OperationRepository] 等业务层提供一个低成本的"埋点"工具，
 *   不需要每个 Repository 都直接持有 [AuditLogDao]，避免依赖耦合
 * - 提供 [log] 简化封装：业务层只需传入 action / targetStudent / before / after 即可
 * - 提供 [buildJson] 工具：把任意键值对序列化为 JSON 字符串，便于存储变更前后数值
 *
 * 使用约定：
 * - 业务 Repository 在 @Transaction 块内成功写入业务数据后，调用 [log] 记录一条日志
 * - 日志写入失败不应影响业务事务：本 Repository 内部 try-catch 吞掉异常，
 *   日志丢失可接受，业务数据必须保证一致性
 * - 不在 [log] 内部启动新事务（避免与外层 @Transaction 冲突），直接调用 dao.insert
 *
 * @param database 数据库实例（用于 withTransaction 包装批量场景）
 * @param dao 操作日志 DAO
 */
class AuditLogRepository(
    private val database: AppDatabase,
    private val dao: AuditLogDao
) {

    /**
     * 记录一条操作日志。
     *
     * 本方法不做事务包装，应在外层业务 @Transaction 中调用，
     * 业务数据与日志数据在同一事务内提交，保证强一致。
     *
     * 异常吞掉：日志写入失败不影响业务流程，仅记录错误到 Logcat。
     *
     * @param action 操作类型（如"新增学员"/"修改课时包"/"删除排课"）
     * @param targetStudent 受影响学员名
     * @param before 变更前核心数值 Map（新增时传 null）
     * @param after 变更后核心数值 Map（删除时传 null）
     * @param summary 一句话摘要（空则根据 action + targetStudent 自动生成）
     */
    suspend fun log(
        action: String,
        targetStudent: String = "",
        before: Map<String, Any?>? = null,
        after: Map<String, Any?>? = null,
        summary: String = ""
    ) {
        try {
            val entity = AuditLogEntity(
                action = action,
                targetStudent = targetStudent,
                beforeJson = buildJson(before),
                afterJson = buildJson(after),
                summary = summary.ifBlank {
                    buildSummary(action, targetStudent, before, after)
                }
            )
            dao.insert(entity)
        } catch (e: Exception) {
            // 日志写入失败不应中断业务流程，仅打印日志
            android.util.Log.w("AuditLog", "写入操作日志失败：${e.message}", e)
        }
    }

    /** 查询全部日志（按时间倒序，UI 主查询入口） */
    fun getAllLogs(): Flow<List<AuditLogEntity>> = dao.getAll()

    /** 按学员名查询日志 */
    fun getLogsByStudent(studentName: String): Flow<List<AuditLogEntity>> =
        dao.getByStudent(studentName)

    /** 日志总数（设置页显示统计用） */
    suspend fun countAll(): Int = dao.countAll()

    /**
     * 清空全部日志。
     *
     * 仅在用户主动点击"清理所有日志"时调用。
     * 本方法独立事务，不影响其他业务数据。
     */
    suspend fun clearAll() {
        database.withTransaction { dao.clearAll() }
    }

    /**
     * 删除某学员的所有日志。
     *
     * 学员彻底从数据库删除时调用，避免遗留无主日志。
     * 应在学员删除的 @Transaction 内调用。
     */
    suspend fun deleteByStudent(studentName: String) {
        try {
            dao.deleteByStudent(studentName)
        } catch (e: Exception) {
            android.util.Log.w("AuditLog", "删除学员日志失败：${e.message}", e)
        }
    }

    /**
     * 工具方法：把 Map 序列化为 JSON 字符串。
     * - value 为 null 时跳过该 key
     * - value 为空 Map / 空字符串时仍保留（表示字段存在但值为空）
     * - 失败时返回空串，避免业务流程崩溃
     */
    private fun buildJson(map: Map<String, Any?>?): String {
        if (map == null) return ""
        return try {
            val obj = JSONObject()
            for ((k, v) in map) {
                if (v == null) continue
                obj.put(k, v)
            }
            obj.toString()
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * 工具方法：根据 action + targetStudent + before + after 自动生成一句话摘要。
     *
     * 生成规则：
     * - 优先级：传入的 summary 不为空时直接使用
     * - 新增类操作："[action] - [targetStudent] - [核心字段值]"
     * - 修改类操作："[action] - [targetStudent] - [变更前]→[变更后]"
     * - 删除类操作："[action] - [targetStudent]"
     */
    private fun buildSummary(
        action: String,
        targetStudent: String,
        before: Map<String, Any?>?,
        after: Map<String, Any?>?
    ): String {
        val sb = StringBuilder(action)
        if (targetStudent.isNotBlank()) {
            sb.append(" - ").append(targetStudent)
        }
        // 附加变更前后的核心数值摘要（取第一个字段的值，避免过长）
        val afterValue = after?.values?.firstOrNull { it != null && it.toString().isNotBlank() }
        val beforeValue = before?.values?.firstOrNull { it != null && it.toString().isNotBlank() }
        if (before == null && afterValue != null) {
            // 新增类
            sb.append(" - ").append(afterValue)
        } else if (afterValue != null && beforeValue != null && beforeValue != afterValue) {
            // 修改类
            sb.append(" - ").append(beforeValue).append("→").append(afterValue)
        }
        return sb.toString()
    }
}
