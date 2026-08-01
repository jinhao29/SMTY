package com.shangmentiyu.sportscoach.data.db

import androidx.room.*
import com.shangmentiyu.sportscoach.data.model.Coach
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import com.shangmentiyu.sportscoach.data.model.Schedule
import kotlinx.coroutines.flow.Flow

/** 课程包 DAO：管理学员购买的课时包 */
@Dao
interface LessonPackageDao {
    @Query("SELECT * FROM lesson_packages ORDER BY purchaseDate DESC")
    fun getAll(): Flow<List<LessonPackage>>

    @Query("SELECT * FROM lesson_packages WHERE studentName = :name ORDER BY purchaseDate DESC")
    fun getByStudent(name: String): Flow<List<LessonPackage>>

    @Query("SELECT * FROM lesson_packages WHERE status = '活跃' ORDER BY purchaseDate DESC")
    fun getActive(): Flow<List<LessonPackage>>

    /** v26 优化4：一次性获取全部课时包（非 Flow，用于孤儿数据自检） */
    @Query("SELECT * FROM lesson_packages")
    suspend fun getAllOnce(): List<LessonPackage>

    @Query("SELECT * FROM lesson_packages WHERE id = :id")
    suspend fun getById(id: String): LessonPackage?

    @Query("SELECT COUNT(*) FROM lesson_packages WHERE status = '活跃'")
    fun countActive(): Flow<Int>

    @Insert
    suspend fun insert(pkg: LessonPackage)

    /** 返回受影响行数，调用方据此判断更新是否真正生效 */
    @Update
    suspend fun update(pkg: LessonPackage): Int

    @Delete
    suspend fun delete(pkg: LessonPackage)

    @Query("DELETE FROM lesson_packages WHERE id = :id")
    suspend fun deleteById(id: String)

    /** 学员改名：级联更新 lesson_packages 表的 studentName 字段 */
    @Query("UPDATE lesson_packages SET studentName = :newName WHERE studentName = :oldName")
    suspend fun renameStudent(oldName: String, newName: String)

    /**
     * === v33 数据流加固：按 studentId 级联改名（推荐路径） ===
     *
     * 与 [renameStudent] 区别：基于 studentId 精准定位，不受同名干扰。
     * 仅更新 studentId 匹配的行。
     *
     * @param studentId 学员唯一 ID
     * @param newName 新姓名
     * @return 受影响行数
     */
    @Query("UPDATE lesson_packages SET studentName = :newName WHERE studentId = :studentId")
    suspend fun updateStudentNameByStudentId(studentId: String, newName: String): Int

    /** 删除学员的所有课时包（删除学员时级联调用） */
    @Query("DELETE FROM lesson_packages WHERE studentName = :name")
    suspend fun deleteByStudent(name: String)
}

/** 教练 DAO */
@Dao
interface CoachDao {
    @Query("SELECT * FROM coaches WHERE status = '在职' ORDER BY name")
    fun getActive(): Flow<List<Coach>>

    @Query("SELECT * FROM coaches ORDER BY name")
    fun getAll(): Flow<List<Coach>>

    @Query("SELECT * FROM coaches WHERE name = :name")
    suspend fun getByName(name: String): Coach?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(coach: Coach)

    @Delete
    suspend fun delete(coach: Coach)

    @Query("DELETE FROM coaches WHERE name = :name")
    suspend fun deleteByName(name: String)
}

/** 排课 DAO */
@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules WHERE isActive = 1 ORDER BY dayOfWeek, startTime")
    fun getActive(): Flow<List<Schedule>>

    @Query("SELECT * FROM schedules ORDER BY dayOfWeek, startTime")
    fun getAll(): Flow<List<Schedule>>

    /** 一次性获取所有排课（非 Flow，用于长期课自动生成等一次性检查） */
    @Query("SELECT * FROM schedules ORDER BY dayOfWeek, startTime")
    suspend fun getAllOnce(): List<Schedule>

    @Query("SELECT * FROM schedules WHERE studentName = :name AND isActive = 1 ORDER BY dayOfWeek, startTime")
    fun getByStudent(name: String): Flow<List<Schedule>>

    @Query("SELECT * FROM schedules WHERE coachName = :name AND isActive = 1 ORDER BY dayOfWeek, startTime")
    fun getByCoach(name: String): Flow<List<Schedule>>

    @Query("SELECT * FROM schedules WHERE dayOfWeek = :day AND isActive = 1 ORDER BY startTime")
    fun getByDay(day: Int): Flow<List<Schedule>>

    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun getById(id: String): Schedule?

    @Insert
    suspend fun insert(schedule: Schedule)

    /** 返回受影响行数，调用方据此判断更新是否真正生效（0=记录不存在/已删除） */
    @Update
    suspend fun update(schedule: Schedule): Int

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * 批量删除排课（多选模式批量删除使用）。
     *
     * 使用 IN 子句一次性删除多条记录，避免循环调用 [deleteById] 产生多次 SQL 执行。
     * Room 会将 List 参数绑定为 SQL 的 IN (?, ?, ...) 形式。
     *
     * @param ids 待删除的排课 ID 列表，空列表时本方法不执行任何删除
     */
    @Query("DELETE FROM schedules WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    /** 清空所有排课记录（课表管理"清空全部"功能） */
    @Query("DELETE FROM schedules")
    suspend fun deleteAll()

    /** 学员改名：级联更新 schedules 表的 studentName 字段 */
    @Query("UPDATE schedules SET studentName = :newName WHERE studentName = :oldName")
    suspend fun renameStudent(oldName: String, newName: String)

    /**
     * === v33 数据流加固：按 studentId 级联改名（推荐路径） ===
     *
     * 与 [renameStudent] 区别：基于 studentId 精准定位，不受同名干扰。
     * 仅更新 studentId 匹配的行，旧数据 studentId 为 NULL 不会被误改。
     *
     * @param studentId 学员唯一 ID
     * @param newName 新姓名
     * @return 受影响行数
     */
    @Query("UPDATE schedules SET studentName = :newName WHERE studentId = :studentId")
    suspend fun updateStudentNameByStudentId(studentId: String, newName: String): Int

    /** 删除学员的所有排课（删除学员时级联调用） */
    @Query("DELETE FROM schedules WHERE studentName = :name")
    suspend fun deleteByStudent(name: String): Int

    /**
     * === 按课时包排课：清理购买日之前的排课记录 ===
     *
     * 删除该学员 startDate < dateStr 的排课记录（isActive=1）。
     * 用于 autoScheduleFromPackage 调用时自动对齐数据：
     * 课时包 7.24 购买，则 7.24 之前的排课视为无效，自动清理。
     *
     * 仅删除 isActive=1 的记录（已逻辑删除的不重复操作）。
     *
     * @param name 学员姓名
     * @param dateStr 购买日（yyyy-MM-dd），删除 startDate < dateStr 的记录
     * @return 受影响行数
     */
    @Query("DELETE FROM schedules WHERE studentName = :name AND startDate < :dateStr AND isActive = 1")
    suspend fun deleteSchedulesBeforeDate(name: String, dateStr: String): Int
}
