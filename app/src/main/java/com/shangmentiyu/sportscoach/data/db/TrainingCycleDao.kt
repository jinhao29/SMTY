package com.shangmentiyu.sportscoach.data.db

import androidx.room.*
import com.shangmentiyu.sportscoach.data.model.TrainingCycle
import kotlinx.coroutines.flow.Flow

/**
 * 训练周期 DAO：管理学员的多周训练计划周期。
 */
@Dao
interface TrainingCycleDao {
    @Query("SELECT * FROM training_cycles ORDER BY createdAt DESC")
    fun getAll(): Flow<List<TrainingCycle>>

    @Query("SELECT * FROM training_cycles WHERE studentName = :name ORDER BY createdAt DESC")
    fun getByStudent(name: String): Flow<List<TrainingCycle>>

    @Query("SELECT * FROM training_cycles WHERE status = '进行中' ORDER BY createdAt DESC")
    fun getActive(): Flow<List<TrainingCycle>>

    @Query("SELECT * FROM training_cycles WHERE id = :id")
    suspend fun getById(id: String): TrainingCycle?

    @Insert
    suspend fun insert(cycle: TrainingCycle)

    @Update
    suspend fun update(cycle: TrainingCycle)

    @Query("DELETE FROM training_cycles WHERE id = :id")
    suspend fun deleteById(id: String)

    /** 学员改名：级联更新 training_cycles 表的 studentName 字段 */
    @Query("UPDATE training_cycles SET studentName = :newName WHERE studentName = :oldName")
    suspend fun renameStudent(oldName: String, newName: String)

    /** 删除学员的所有训练周期（删除学员时级联调用） */
    @Query("DELETE FROM training_cycles WHERE studentName = :name")
    suspend fun deleteByStudent(name: String)
}
