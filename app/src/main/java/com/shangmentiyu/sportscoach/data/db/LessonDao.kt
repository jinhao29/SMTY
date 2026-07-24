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
