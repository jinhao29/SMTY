package com.shangmentiyu.sportscoach.data.db

import androidx.room.*
import com.shangmentiyu.sportscoach.data.model.Lesson
import kotlinx.coroutines.flow.Flow

@Dao
interface LessonDao {
    @Query("SELECT * FROM lessons ORDER BY date DESC, time DESC")
    fun getAll(): Flow<List<Lesson>>

    @Query("SELECT * FROM lessons WHERE studentName = :name ORDER BY date DESC, time DESC")
    fun getByStudent(name: String): Flow<List<Lesson>>

    @Query("SELECT * FROM lessons WHERE date = :date ORDER BY time DESC")
    fun getByDate(date: String): Flow<List<Lesson>>

    /**
     * 查询从指定日期起的所有课时（按日期升序、时间升序）。
     * 用于学员列表"下一节课"显示：取每个学员的第一条记录即为下一节课。
     *
     * @param fromDate 起始日期 YYYY-MM-DD（含）
     */
    @Query("SELECT * FROM lessons WHERE date >= :fromDate ORDER BY date ASC, time ASC")
    fun getFrom(fromDate: String): Flow<List<Lesson>>

    @Query("SELECT * FROM lessons WHERE id = :id")
    suspend fun getById(id: String): Lesson?

    @Query("SELECT COUNT(*) FROM lessons WHERE date = :date")
    fun countByDate(date: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM lessons")
    fun count(): Flow<Int>

    /**
     * 查重：同一学员+同一日期+同一时间是否已有课时记录。
     * 用于长期排课自动生成时避免重复插入。
     */
    @Query("SELECT COUNT(*) FROM lessons WHERE studentName = :studentName AND date = :date AND time = :time")
    suspend fun countByStudentDateTime(studentName: String, date: String, time: String): Int

    /**
     * 统计学员指定日期起未消课的课时数量（packageId 为空表示尚未扣减课时包）。
     * 用于长期排课生成时关联课时包余额，避免超额排课。
     *
     * @param studentName 学员姓名
     * @param fromDate 起始日期 YYYY-MM-DD（含）
     * @return 未消课的课时数量
     */
    @Query("SELECT COUNT(*) FROM lessons WHERE studentName = :studentName AND date >= :fromDate AND (packageId = '' OR packageId IS NULL)")
    suspend fun countUnconsumedFrom(studentName: String, fromDate: String): Int

    @Insert
    suspend fun insert(lesson: Lesson)

    @Update
    suspend fun update(lesson: Lesson)

    @Delete
    suspend fun delete(lesson: Lesson)

    @Query("DELETE FROM lessons WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM lessons WHERE studentName = :name")
    suspend fun deleteByStudent(name: String)

    /** 学员改名：级联更新 lessons 表的 studentName 字段 */
    @Query("UPDATE lessons SET studentName = :newName WHERE studentName = :oldName")
    suspend fun renameStudent(oldName: String, newName: String)
}
