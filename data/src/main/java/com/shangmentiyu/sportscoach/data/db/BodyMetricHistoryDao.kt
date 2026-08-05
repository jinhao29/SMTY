package com.shangmentiyu.sportscoach.data.db

import androidx.room.*
import com.shangmentiyu.sportscoach.data.model.BodyMetricHistory
import kotlinx.coroutines.flow.Flow

/**
 * 身体形态历史 DAO：记录学员身高/体重/BMI 的变化历史。
 */
@Dao
interface BodyMetricHistoryDao {
    @Query("SELECT * FROM body_metric_history WHERE studentName = :name ORDER BY date ASC")
    fun getByStudent(name: String): Flow<List<BodyMetricHistory>>

    @Query("SELECT * FROM body_metric_history WHERE studentName = :name ORDER BY date ASC")
    suspend fun getByStudentOnce(name: String): List<BodyMetricHistory>

    @Query("SELECT * FROM body_metric_history WHERE studentName = :name AND date BETWEEN :start AND :end ORDER BY date ASC")
    suspend fun getByStudentInRange(name: String, start: String, end: String): List<BodyMetricHistory>

    @Insert
    suspend fun insert(record: BodyMetricHistory)

    @Update
    suspend fun update(record: BodyMetricHistory)

    @Query("DELETE FROM body_metric_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM body_metric_history WHERE studentName = :name")
    suspend fun deleteByStudent(name: String)

    /** 学员改名：级联更新 body_metric_history 表的 studentName 字段 */
    @Query("UPDATE body_metric_history SET studentName = :newName WHERE studentName = :oldName")
    suspend fun renameStudent(oldName: String, newName: String)
}
