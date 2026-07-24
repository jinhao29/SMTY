package com.shangmentiyu.sportscoach.data.repo

import com.shangmentiyu.sportscoach.data.db.StudentDao
import com.shangmentiyu.sportscoach.data.model.Student
import kotlinx.coroutines.flow.Flow

/**
 * 学员仓储：封装学员数据的增删改查。
 *
 * 身体形态字段（age/heightCm/weightKg/bmi）由调用方传入，
 * BMI 通常在 UI 层用 [com.shangmentiyu.sportscoach.core.BmiProcessor] 计算后一并存入。
 */
class StudentRepository(private val dao: StudentDao) {
    fun getAllStudents(): Flow<List<Student>> = dao.getAll()
    fun getStudentCount(): Flow<Int> = dao.count()
    suspend fun getByName(name: String): Student? = dao.getByName(name)

    /**
     * 新增学员。
     *
     * @param age 年龄（岁），0 表示未填
     * @param heightCm 身高（厘米），0 表示未填
     * @param weightKg 体重（千克），0 表示未填
     * @param bmi BMI 数值，0 表示未计算
     */
    suspend fun addStudent(
        name: String, gender: String, grade: String, school: String, phone: String,
        age: Int = 0, heightCm: Int = 0, weightKg: Float = 0f, bmi: Float = 0f
    ) {
        dao.insert(
            Student(
                name = name, gender = gender, grade = grade, school = school, phone = phone,
                age = age, heightCm = heightCm, weightKg = weightKg, bmi = bmi
            )
        )
    }

    suspend fun updateStudent(student: Student) = dao.update(student)
    suspend fun deleteStudent(name: String) = dao.deleteByName(name)

    suspend fun importStudents(students: List<Student>) {
        for (s in students) dao.insert(s)
    }
}
