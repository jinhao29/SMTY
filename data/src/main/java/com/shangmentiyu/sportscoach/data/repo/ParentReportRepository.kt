package com.shangmentiyu.sportscoach.data.repo

import com.shangmentiyu.sportscoach.core.ReportGenerator
import com.shangmentiyu.sportscoach.data.db.ParentReportDao
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.ParentReport
import com.shangmentiyu.sportscoach.data.model.Student
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * 家长报告仓库：管理报告的生成、持久化与查询。
 */
class ParentReportRepository(
    private val reportDao: ParentReportDao,
    private val studentRepo: StudentRepository,
    private val lessonRepo: LessonRepository
) {
    fun getAll(): Flow<List<ParentReport>> = reportDao.getAll()
    fun getByStudent(name: String): Flow<List<ParentReport>> = reportDao.getByStudent(name)

    /**
     * 生成并保存报告。
     * @param studentName 学员姓名
     * @param type ReportGenerator.TYPE_WEEKLY 或 TYPE_MONTHLY
     * @return 生成的报告 ID，若学员不存在或无课时返回 null
     */
    suspend fun generateAndSave(studentName: String, type: String): String? {
        val student = studentRepo.getByName(studentName) ?: return null
        val lessons = lessonRepo.getByStudentOnce(studentName)
        if (lessons.isEmpty()) return null

        val content = ReportGenerator.generate(student, lessons, type)
        val (startDate, endDate) = ReportGenerator.computePeriod(type)

        // 避免重复生成同周期同类型报告
        if (reportDao.countExisting(studentName, type, startDate, endDate) > 0) {
            return null
        }

        val report = ParentReport(
            id = UUID.randomUUID().toString().take(8),
            studentName = studentName,
            reportType = type,
            startDate = startDate,
            endDate = endDate,
            content = content
        )
        reportDao.insert(report)
        return report.id
    }

    suspend fun getById(id: String): ParentReport? = reportDao.getById(id)
    suspend fun markShared(id: String) {
        val r = reportDao.getById(id) ?: return
        reportDao.update(r.copy(shared = true))
    }
    suspend fun delete(id: String) = reportDao.deleteById(id)
}
