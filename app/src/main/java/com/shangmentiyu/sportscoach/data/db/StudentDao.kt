package com.shangmentiyu.sportscoach.data.db

import androidx.room.*
import com.shangmentiyu.sportscoach.data.model.Student
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query("SELECT * FROM students ORDER BY createdAt ASC")
    fun getAll(): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE name = :name")
    suspend fun getByName(name: String): Student?

    @Query("SELECT COUNT(*) FROM students")
    fun count(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(student: Student)

    @Update
    suspend fun update(student: Student)

    @Delete
    suspend fun delete(student: Student)

    @Query("DELETE FROM students WHERE name = :name")
    suspend fun deleteByName(name: String)

    /** 学员改名：仅更新 students 表的 name 字段，其他表的级联由业务层事务统一处理 */
    @Query("UPDATE students SET name = :newName, updatedAt = :now WHERE name = :oldName")
    suspend fun renameStudent(oldName: String, newName: String, now: Long = System.currentTimeMillis())
}
