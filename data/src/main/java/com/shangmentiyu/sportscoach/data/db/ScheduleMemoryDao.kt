package com.shangmentiyu.sportscoach.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shangmentiyu.sportscoach.data.model.ScheduleMemory
import kotlinx.coroutines.flow.Flow

/**
 * 排课记忆 DAO：管理教练历史用过的上课时间/地点。
 *
 * - saveMemory：插入或更新（联合主键冲突时替换，更新 updatedAt）
 * - getMemories：按教练+字段查询，按 updatedAt 降序，供下拉选择
 */
@Dao
interface ScheduleMemoryDao {

    /** 插入或更新记忆（联合主键冲突时替换） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMemory(memory: ScheduleMemory)

    /** 查询指定教练指定字段的历史值列表，按 updatedAt 降序 */
    @Query("SELECT * FROM schedule_memory WHERE coachName = :coachName AND field = :field ORDER BY updatedAt DESC")
    fun getMemories(coachName: String, field: String): Flow<List<ScheduleMemory>>

    /** 查询所有教练指定字段的历史值（不限教练，用于无教练时全局提示） */
    @Query("SELECT * FROM schedule_memory WHERE field = :field ORDER BY updatedAt DESC LIMIT :limit")
    fun getRecentMemories(field: String, limit: Int = 10): Flow<List<ScheduleMemory>>

    /** 删除指定记忆 */
    @Query("DELETE FROM schedule_memory WHERE coachName = :coachName AND field = :field AND value = :value")
    suspend fun deleteMemory(coachName: String, field: String, value: String)
}
