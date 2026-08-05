package com.shangmentiyu.sportscoach.data.db

import androidx.room.*
import com.shangmentiyu.sportscoach.data.model.DietTemplateEntity
import com.shangmentiyu.sportscoach.data.model.StudentDietRecord
import kotlinx.coroutines.flow.Flow

/**
 * 饮食管理 DAO：模板与学员绑定记录的增删改查。
 *
 * - 模板表（diet_templates）：仅预置 3 套，应用启动时插入；查询时不区分学员。
 * - 学员绑定表（student_diet_records）：按学员姓名查询最新绑定方案。
 */
@Dao
interface DietDao {

    // ============ 模板表 ============

    @Query("SELECT * FROM diet_templates ORDER BY createdAt ASC")
    suspend fun getAllTemplates(): List<DietTemplateEntity>

    @Query("SELECT * FROM diet_templates WHERE id = :id")
    suspend fun getTemplateById(id: String): DietTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTemplates(templates: List<DietTemplateEntity>)

    // ============ 学员绑定表 ============

    @Query("SELECT * FROM student_diet_records WHERE studentName = :studentName ORDER BY appliedAt DESC LIMIT 1")
    fun getLatestRecordFlow(studentName: String): Flow<StudentDietRecord?>

    @Query("SELECT * FROM student_diet_records WHERE studentName = :studentName ORDER BY appliedAt DESC LIMIT 1")
    suspend fun getLatestRecord(studentName: String): StudentDietRecord?

    @Insert
    suspend fun insertRecord(record: StudentDietRecord): Long

    @Update
    suspend fun updateRecord(record: StudentDietRecord)

    /**
     * 应用模板给学员：先删除该学员历史绑定，再插入新记录，确保一人一方案。
     */
    @Query("DELETE FROM student_diet_records WHERE studentName = :studentName")
    suspend fun deleteRecordsByStudent(studentName: String)

    @Query("DELETE FROM student_diet_records WHERE studentName = :studentName")
    suspend fun deleteByStudentName(studentName: String)

    /** 学员改名：级联更新 student_diet_records 表的 studentName 字段 */
    @Query("UPDATE student_diet_records SET studentName = :newName WHERE studentName = :oldName")
    suspend fun renameStudent(oldName: String, newName: String)
}
