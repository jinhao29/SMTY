package com.shangmentiyu.sportscoach.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.shangmentiyu.sportscoach.data.model.PlanImage
import kotlinx.coroutines.flow.Flow

/**
 * 学员训练计划图片 DAO。
 *
 * 提供：
 * - 按学员姓名查询图片（Flow 与一次性两种）
 * - 查询最近 N 天的所有图片（用于"今日同步的训练计划"画廊）
 * - 学员改名级联更新
 * - 删除学员时级联清理图片记录
 *
 * === v25 新增 ===
 */
@Dao
interface PlanImageDao {

    /** 按学员姓名查询图片（按创建时间倒序，UI 自动响应） */
    @Query("SELECT * FROM student_plan_images WHERE studentName = :name ORDER BY createdAt DESC")
    fun getByStudent(name: String): Flow<List<PlanImage>>

    /** 按学员姓名查询图片（一次性，非 Flow，用于后台处理） */
    @Query("SELECT * FROM student_plan_images WHERE studentName = :name ORDER BY createdAt DESC")
    suspend fun getByStudentOnce(name: String): List<PlanImage>

    /** === v48 双通道：按学员 ID 查询图片（优先），studentName 兜底（兼容旧数据）=== */
    @Query(
        "SELECT * FROM student_plan_images " +
            "WHERE studentId = :sid OR (studentId IS NULL AND studentName = :name) " +
            "ORDER BY createdAt DESC"
    )
    fun getByStudentDual(sid: String?, name: String): Flow<List<PlanImage>>

    /** === v48 双通道：一次性查询（非 Flow，用于后台处理）=== */
    @Query(
        "SELECT * FROM student_plan_images " +
            "WHERE studentId = :sid OR (studentId IS NULL AND studentName = :name) " +
            "ORDER BY createdAt DESC"
    )
    suspend fun getByStudentDualOnce(sid: String?, name: String): List<PlanImage>

    /** 查询最近 N 天内的所有训练计划图片（按创建时间倒序） */
    @Query(
        "SELECT * FROM student_plan_images WHERE createdAt >= :sinceTimestamp " +
            "ORDER BY createdAt DESC"
    )
    fun getRecent(sinceTimestamp: Long): Flow<List<PlanImage>>

    /** 查询所有训练计划图片（按创建时间倒序） */
    @Query("SELECT * FROM student_plan_images ORDER BY createdAt DESC")
    fun getAll(): Flow<List<PlanImage>>

    /** 按 ID 查询单条记录 */
    @Query("SELECT * FROM student_plan_images WHERE id = :id")
    suspend fun getById(id: String): PlanImage?

    /** 插入一条图片记录 */
    @Insert
    suspend fun insert(plan: PlanImage)

    /** 按 ID 删除单条记录 */
    @Query("DELETE FROM student_plan_images WHERE id = :id")
    suspend fun deleteById(id: String)

    /** 删除学员的所有训练计划图片（学员删除时级联调用） */
    @Query("DELETE FROM student_plan_images WHERE studentName = :name")
    suspend fun deleteByStudent(name: String)

    /** === v48 双通道：删除学员所有图片（studentId 优先，studentName 兜底兼容旧数据）=== */
    @Query(
        "DELETE FROM student_plan_images " +
            "WHERE studentId = :sid OR (studentId IS NULL AND studentName = :name)"
    )
    suspend fun deleteByStudentIdDual(sid: String?, name: String)

    /** === v48 双通道：学员改名级联（先补 studentId 再改姓名）=== */
    @Query(
        "UPDATE student_plan_images SET studentId = :sid, studentName = :newName " +
            "WHERE studentId = :sid OR (studentId IS NULL AND studentName = :oldName)"
    )
    suspend fun renameStudentIdDual(sid: String?, oldName: String, newName: String)

    /** 清理超过指定天数的旧记录（用于冷数据归档，可选调用） */
    @Query("DELETE FROM student_plan_images WHERE createdAt < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long): Int
}
