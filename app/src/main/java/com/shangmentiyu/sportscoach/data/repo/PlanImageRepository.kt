package com.shangmentiyu.sportscoach.data.repo

import com.shangmentiyu.sportscoach.data.db.PlanImageDao
import com.shangmentiyu.sportscoach.data.model.PlanImage
import kotlinx.coroutines.flow.Flow

/**
 * 训练计划图片仓储：封装 [PlanImageDao] 的数据访问与跨表级联。
 *
 * 职责：
 * - 提供按学员姓名、最近时间维度查询图片
 * - 新增图片记录（与 LanImageReceiver 配合，下载完成后调用）
 * - 学员改名 / 删除时的级联清理
 *
 * 与 [StudentRepository] 配合：
 * - 接收文件名后，由 LanImageReceiver 解析学员姓名
 * - 调用 [StudentRepository.getByName] 确认学员存在后再插入记录
 * - 学员不存在时仍保存图片，但 studentName 字段保留原始解析值，
 *   后续学员创建后可通过手动关联补全
 *
 * === v25 新增 ===
 */
class PlanImageRepository(
    private val dao: PlanImageDao
) {
    /** 按学员姓名查询图片（按创建时间倒序，UI 自动响应） */
    fun getByStudent(name: String): Flow<List<PlanImage>> = dao.getByStudent(name)

    /** 查询最近 N 天内的所有训练计划图片 */
    fun getRecent(sinceTimestamp: Long): Flow<List<PlanImage>> = dao.getRecent(sinceTimestamp)

    /** 查询所有训练计划图片 */
    fun getAll(): Flow<List<PlanImage>> = dao.getAll()

    /** 按 ID 查询单条记录 */
    suspend fun getById(id: String): PlanImage? = dao.getById(id)

    /** 新增一条图片记录 */
    suspend fun insert(plan: PlanImage) = dao.insert(plan)

    /** 按 ID 删除单条记录（同时由调用方负责删除本地图片文件） */
    suspend fun deleteById(id: String) = dao.deleteById(id)

    /** 删除学员的所有训练计划图片（学员删除时级联调用） */
    suspend fun deleteByStudent(name: String) = dao.deleteByStudent(name)

    /** 学员改名：级联更新 student_plan_images 表 */
    suspend fun renameStudent(oldName: String, newName: String) =
        dao.renameStudent(oldName, newName)

    /** 清理超过指定天数的旧记录（返回删除条数） */
    suspend fun deleteOlderThan(beforeTimestamp: Long): Int =
        dao.deleteOlderThan(beforeTimestamp)
}
