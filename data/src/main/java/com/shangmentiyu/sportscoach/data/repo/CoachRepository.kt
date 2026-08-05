package com.shangmentiyu.sportscoach.data.repo

import com.shangmentiyu.sportscoach.data.db.CoachDao
import com.shangmentiyu.sportscoach.data.model.Coach
import kotlinx.coroutines.flow.Flow

/**
 * 教练 Repository（管理层）。
 *
 * 从原 [OperationRepository] 拆分而来，单一职责只管理教练数据。
 *
 * 职责：
 * - 教练 CRUD
 * - 按"在职/全部"两种过滤查询
 * - 教练重名校验（新增/更新前由调用方自行调用 [getByName] 判断）
 *
 * 设计说明：
 * - 教练数据量小（通常 ≤10），不引入分页
 * - 不包含教练工作量统计（属于报表领域，由 StageSummary 负责聚合）
 */
class CoachRepository(private val coachDao: CoachDao) {

    /** 在职教练列表（按姓名升序），用于下拉选择与排课分配 */
    fun getActiveCoaches(): Flow<List<Coach>> = coachDao.getActive()

    /** 全量教练列表（含已离职），用于历史报表 / 设置页管理 */
    fun getAllCoaches(): Flow<List<Coach>> = coachDao.getAll()

    /** 按姓名查教练；找不到返回 null */
    suspend fun getByName(name: String): Coach? = coachDao.getByName(name)

    /**
     * 新增或更新教练（按主键 name REPLACE）。
     *
     * 注意：REPLACE 策略会先 DELETE 再 INSERT，关联表（如 schedules.coachName）
     * 虽然保留原值但日志会显示该教练被删除过——日常教练改名请走业务层的改名流程，
     * 这里仅用于新增或同步更新非主键字段（如状态、备注）。
     */
    suspend fun upsert(coach: Coach) = coachDao.upsert(coach)

    /** 按姓名物理删除教练（仅在教练离职且无历史排课时使用） */
    suspend fun delete(name: String) = coachDao.deleteByName(name)
}
