package com.shangmentiyu.sportscoach.data.repo

import com.shangmentiyu.sportscoach.data.db.CoachStudentBindingDao
import com.shangmentiyu.sportscoach.data.model.CoachStudentBinding
import kotlinx.coroutines.flow.Flow

/**
 * 教练-学员绑定 Repository（管理层，v34 教练绑定学员）。
 *
 * 职责：绑定关系 CRUD；绑定仅用于教练排课的学员候选列表，
 * 不参与排课校验与课时消耗。
 */
class CoachStudentRepository(
    private val dao: CoachStudentBindingDao
) {

    fun getBindingsByCoach(coachName: String): Flow<List<CoachStudentBinding>> =
        dao.getByCoach(coachName)

    fun getAllBindings(): Flow<List<CoachStudentBinding>> = dao.getAll()

    suspend fun bind(coachName: String, studentName: String, studentId: String?) =
        dao.insert(CoachStudentBinding(coachName, studentName, studentId))

    suspend fun unbind(coachName: String, studentName: String) =
        dao.delete(coachName, studentName)

    /** 删除教练时级联清理其全部绑定 */
    suspend fun deleteByCoach(coachName: String) = dao.deleteByCoach(coachName)
}
