package com.shangmentiyu.sportscoach.data.repo

import com.shangmentiyu.sportscoach.data.db.LessonDao
import com.shangmentiyu.sportscoach.data.model.Lesson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 课时 Repository（管理层）。
 *
 * 注意：[SimpleDateFormat] 非线程安全，禁止作为成员变量持有；
 * 统一通过 [todayDateStr] / [nowTimeStr] 在方法内新建实例使用。
 */
class LessonRepository(private val dao: LessonDao) {

    /** 当前日期字符串（yyyy-MM-dd），每次调用新建 SimpleDateFormat 实例，避免并发污染 */
    private fun todayDateStr(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    /** 当前时间字符串（HH:mm），每次调用新建 SimpleDateFormat 实例 */
    private fun nowTimeStr(): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    fun getAllLessons(): Flow<List<Lesson>> = dao.getAll()
    fun getLessonsByStudent(name: String): Flow<List<Lesson>> = dao.getByStudent(name)
    fun getTodayLessons(): Flow<List<Lesson>> = dao.getByDate(todayDateStr())
    fun getTodayCount(): Flow<Int> = dao.countByDate(todayDateStr())
    fun getTotalCount(): Flow<Int> = dao.count()

    suspend fun getById(id: String): Lesson? = dao.getById(id)

    /** 一次性获取学员全部课时（非 Flow） */
    suspend fun getByStudentOnce(name: String): List<Lesson> = dao.getByStudent(name).first()

    /**
     * 创建课时记录：自动生成 ID、当前日期与时间。
     * @return 新建的课时 ID
     */
    suspend fun createLesson(studentName: String, coach: String, packageId: String = ""): String {
        // UUID 前 12 位：熵 48 bit，按生日悖论百万级数据无碰撞
        val id = UUID.randomUUID().toString().take(12)
        val lesson = Lesson(
            id = id,
            date = todayDateStr(),
            time = nowTimeStr(),
            studentName = studentName,
            coach = coach,
            packageId = packageId
        )
        dao.insert(lesson)
        return id
    }

    suspend fun updateLesson(lesson: Lesson) = dao.update(lesson)
    suspend fun deleteLesson(id: String) = dao.deleteById(id)
    suspend fun deleteByStudent(name: String) = dao.deleteByStudent(name)

    /**
     * 课后签退：记录签退时间与可选的签退照片路径。
     *
     * 注意：签退不消课（消课在签到时已完成），仅补充签退信息。
     *
     * @param lessonId 课时 ID
     * @param photoPath 签退照片路径（空=未拍照）
     * @return 是否签退成功（课时不存在时返回 false）
     */
    suspend fun signOut(lessonId: String, photoPath: String = ""): Boolean {
        val lesson = dao.getById(lessonId) ?: return false
        dao.update(lesson.copy(
            signOutTime = nowTimeStr(),
            signOutPhotoPath = photoPath
        ))
        return true
    }
}
