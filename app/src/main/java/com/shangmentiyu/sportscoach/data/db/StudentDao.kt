package com.shangmentiyu.sportscoach.data.db

import androidx.room.*
import com.shangmentiyu.sportscoach.data.model.Student
import kotlinx.coroutines.flow.Flow

/**
 * 学员 DAO
 *
 * 软删除约定（v20 引入）：
 * - 所有"日常列表"查询必须带 `WHERE isActive = 1`，仅返回活跃学员；
 * - 历史报表 / 数据完整性核对场景使用 [getAllIncludeDeleted]（含已删除）。
 */
@Dao
interface StudentDao {
    /** 活跃学员列表（按创建时间升序），用于学员管理、排课、签到等日常场景 */
    @Query("SELECT * FROM students WHERE isActive = 1 ORDER BY createdAt ASC")
    fun getAll(): Flow<List<Student>>

    /** 全量学员列表（含已软删除的），用于历史报表 / 数据完整性核对 */
    @Query("SELECT * FROM students ORDER BY createdAt ASC")
    fun getAllIncludeDeleted(): Flow<List<Student>>

    /** 按姓名查活跃学员；找不到返回 null */
    @Query("SELECT * FROM students WHERE name = :name AND isActive = 1")
    suspend fun getByName(name: String): Student?

    /** 按姓名查学员（含已软删除的），用于历史数据回填 / 改名级联 */
    @Query("SELECT * FROM students WHERE name = :name")
    suspend fun getByNameIncludeDeleted(name: String): Student?

    /**
     * === v33 数据流加固：按 studentId 查活跃学员 ===
     *
     * 用于基于 studentId 的级联改名事务：先通过 studentId 定位学员，
     * 再原子级联更新子表的 studentName 字段。
     *
     * @param studentId 学员唯一 ID
     * @return 活跃学员；不存在或已软删除返回 null
     */
    @Query("SELECT * FROM students WHERE studentId = :studentId AND isActive = 1")
    suspend fun getByStudentId(studentId: String): Student?

    /**
     * === v33 数据流加固：按 studentId 查学员（含已软删除的） ===
     *
     * 用于历史数据回填与孤儿数据自检场景。
     */
    @Query("SELECT * FROM students WHERE studentId = :studentId")
    suspend fun getByStudentIdIncludeDeleted(studentId: String): Student?

    /** 活跃学员总数 */
    @Query("SELECT COUNT(*) FROM students WHERE isActive = 1")
    fun count(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(student: Student)

    @Update
    suspend fun update(student: Student)

    @Delete
    suspend fun delete(student: Student)

    /**
     * 软删除：仅将 isActive 置为 0，保留学员行，
     * 历史 Lesson / Schedule / LessonPackage 等子表数据仍能通过 studentName 关联到该学员。
     */
    @Query("UPDATE students SET isActive = 0, updatedAt = :now WHERE name = :name")
    suspend fun softDeleteByName(name: String, now: Long = System.currentTimeMillis())

    /** 旧 API 保留：物理删除（仅用于整库恢复/清空场景，日常业务请用 [softDeleteByName]） */
    @Query("DELETE FROM students WHERE name = :name")
    suspend fun deleteByName(name: String)

    /** 学员改名：仅更新 students 表的 name 字段，其他表的级联由业务层事务统一处理 */
    @Query("UPDATE students SET name = :newName, updatedAt = :now WHERE name = :oldName")
    suspend fun renameStudent(oldName: String, newName: String, now: Long = System.currentTimeMillis())

    /**
     * === v33 数据流加固：按 studentId 改名（推荐路径） ===
     *
     * 与 [renameStudent] 区别：本方法不受"重名"干扰。
     * studentId 是唯一软关联键，按 ID 改名即使存在同名学员也能精准定位。
     *
     * @param studentId 学员唯一 ID（非空）
     * @param newName 新姓名
     * @return 受影响行数（0=未找到该 studentId 的学员）
     */
    @Query("UPDATE students SET name = :newName, updatedAt = :now WHERE studentId = :studentId")
    suspend fun renameStudentById(studentId: String, newName: String, now: Long = System.currentTimeMillis()): Int

    /**
     * === v33 数据流加固：按 studentId 软删除 ===
     *
     * 与 [softDeleteByName] 区别：基于 studentId 不受同名干扰。
     * 用于级联删除时精准定位学员。
     *
     * @param studentId 学员唯一 ID
     * @return 受影响行数
     */
    @Query("UPDATE students SET isActive = 0, updatedAt = :now WHERE studentId = :studentId")
    suspend fun softDeleteByStudentId(studentId: String, now: Long = System.currentTimeMillis()): Int

    /**
     * 回填 studentId：用于把旧数据的 NULL studentId 升级为唯一 ID。
     * 仅在 studentId IS NULL 的行执行，避免覆盖已生成的 ID。
     */
    @Query("UPDATE students SET studentId = :id, updatedAt = :now WHERE name = :name AND studentId IS NULL")
    suspend fun ensureStudentId(name: String, id: String, now: Long = System.currentTimeMillis())
}
