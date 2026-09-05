package com.shangmentiyu.sportscoach.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shangmentiyu.sportscoach.data.model.FeeRecord
import com.shangmentiyu.sportscoach.data.model.PcSyncState
import kotlinx.coroutines.flow.Flow

/** PC 收费记录镜像 DAO（v35，PC→手机只读同步）。 */
@Dao
interface FeeRecordDao {

    /** 幂等写入：主键为自然键摘要，PC 重复推送同批记录时同键覆盖 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertBlocking(records: List<FeeRecord>)

    @Query("SELECT * FROM fee_records ORDER BY date DESC, studentName")
    fun getAll(): Flow<List<FeeRecord>>

    @Query("SELECT * FROM fee_records WHERE studentName = :name ORDER BY date DESC")
    fun getByStudent(name: String): Flow<List<FeeRecord>>

    @Query("SELECT COUNT(*) FROM fee_records")
    fun count(): Flow<Int>
}

/** PC 消课对账状态 DAO（v35）。 */
@Dao
interface PcSyncStateDao {

    @Query("SELECT * FROM pc_sync_state WHERE studentName = :studentName")
    fun getBlocking(studentName: String): PcSyncState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertBlocking(state: PcSyncState)
}
