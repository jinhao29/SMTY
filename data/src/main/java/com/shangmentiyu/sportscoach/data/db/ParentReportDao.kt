package com.shangmentiyu.sportscoach.data.db

import androidx.room.*
import com.shangmentiyu.sportscoach.data.model.ParentReport
import kotlinx.coroutines.flow.Flow

/**
 * 家长报告 DAO：管理报告的增删查改。
 */
@Dao
interface ParentReportDao {
    @Query("SELECT * FROM parent_reports ORDER BY createdAt DESC")
    fun getAll(): Flow<List<ParentReport>>

    @Query("SELECT * FROM parent_reports WHERE studentName = :name ORDER BY createdAt DESC")
    fun getByStudent(name: String): Flow<List<ParentReport>>

    @Query("SELECT * FROM parent_reports WHERE id = :id")
    suspend fun getById(id: String): ParentReport?

    @Query("SELECT COUNT(*) FROM parent_reports WHERE studentName = :name AND reportType = :type AND startDate = :start AND endDate = :end")
    suspend fun countExisting(name: String, type: String, start: String, end: String): Int

    @Insert
    suspend fun insert(report: ParentReport)

    @Update
    suspend fun update(report: ParentReport)

    @Query("DELETE FROM parent_reports WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM parent_reports WHERE studentName = :name")
    suspend fun deleteByStudent(name: String)

    /** 学员改名：级联更新 parent_reports 表的 studentName 字段 */
    @Query("UPDATE parent_reports SET studentName = :newName WHERE studentName = :oldName")
    suspend fun renameStudent(oldName: String, newName: String)
}
