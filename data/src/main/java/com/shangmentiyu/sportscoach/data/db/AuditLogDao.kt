package com.shangmentiyu.sportscoach.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shangmentiyu.sportscoach.data.model.AuditLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * === v26 优化1：操作日志 DAO ===
 *
 * 提供：
 * - [insert]：插入一条日志（REPLACE 策略防主键冲突）
 * - [getAll]：按时间倒序查询全部日志（Flow，UI 实时刷新）
 * - [getByStudent]：按学员名过滤日志
 * - [clearAll]：清空日志（仅"清理所有日志"入口使用）
 * - [countAll]：日志总数，用于设置页显示统计
 */
@Dao
interface AuditLogDao {

    /** 插入一条操作日志（REPLACE 策略，主键冲突时覆盖，避免 UUID 撞库异常） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AuditLogEntity)

    /** 按时间倒序查询全部日志（最新在前），UI 主查询入口 */
    @Query("SELECT * FROM audit_logs ORDER BY createdAt DESC")
    fun getAll(): Flow<List<AuditLogEntity>>

    /** 按学员名过滤日志（仍按时间倒序），用于"查看某学员的操作历史" */
    @Query("SELECT * FROM audit_logs WHERE targetStudent = :studentName ORDER BY createdAt DESC")
    fun getByStudent(studentName: String): Flow<List<AuditLogEntity>>

    /** 日志总数，用于设置页显示统计 */
    @Query("SELECT COUNT(*) FROM audit_logs")
    suspend fun countAll(): Int

    /** 清空全部日志（用户在设置页主动点击"清理所有日志"时调用） */
    @Query("DELETE FROM audit_logs")
    suspend fun clearAll()

    /** 删除某学员的所有日志（学员彻底删除时调用，避免遗留无主引用） */
    @Query("DELETE FROM audit_logs WHERE targetStudent = :studentName")
    suspend fun deleteByStudent(studentName: String)
}
