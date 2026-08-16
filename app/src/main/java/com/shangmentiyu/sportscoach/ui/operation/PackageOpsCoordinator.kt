package com.shangmentiyu.sportscoach.ui.operation

import com.shangmentiyu.sportscoach.data.model.Coach
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import com.shangmentiyu.sportscoach.data.repo.CoachRepository
import com.shangmentiyu.sportscoach.data.repo.LessonPackageRepository
import com.shangmentiyu.sportscoach.data.repo.StudentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 课程包与教练操作协调器（v53 从 OperationViewModel 拆出）。
 *
 * 承载课程包 CRUD / 课时调整 / 赠送课时与教练增删的业务流程。
 * 逻辑逐字搬迁自 OperationViewModel，行为与拆分前完全一致。
 */
internal class PackageOpsCoordinator(
    private val pkgRepo: LessonPackageRepository,
    private val studentRepo: StudentRepository,
    private val coachRepo: CoachRepository
) {

    /**
     * 新增课时包（自动补齐 studentId 软关联键，避免按 ID 级联改名时断链）。
     */
    suspend fun addPackage(
        studentName: String,
        name: String,
        totalLessons: Int,
        price: Double,
        purchaseDate: String,
        expireDate: String
    ): String {
        pkgRepo.addPackage(
            LessonPackage(
                studentName = studentName,
                // 课时包必须携带 studentId 软关联键，
                // 否则按 ID 级联改名时该课时包不会更新 studentName，学员课时包列表断链
                studentId = studentRepo.getByName(studentName)?.studentId,
                name = name,
                totalLessons = totalLessons,
                price = price,
                purchaseDate = purchaseDate,
                expireDate = expireDate
            )
        )
        return "课程包已添加"
    }

    /** 删除课时包（关联排课由 Repository 级联清除）。 */
    suspend fun deletePackage(id: String): String {
        pkgRepo.deletePackage(id)
        return "已删除课时包，关联排课已同步清除"
    }

    /**
     * 调整课时包课时数（正数增添，负数减少）。
     * 同步修改 totalLessons 与 remainingLessons，保持已用课时数不变。
     */
    suspend fun adjustPackage(packageId: String, delta: Int): String {
        if (delta == 0) return ""
        val pkg = withContext(Dispatchers.IO) { pkgRepo.getPkgById(packageId) }
            ?: return ""
        // remainingLessons 是计算属性 = totalLessons - usedLessons
        // 增添：totalLessons += delta，usedLessons 不变
        // 减少：totalLessons -= delta，但不低于 usedLessons
        val newTotal = if (delta > 0) {
            pkg.totalLessons + delta
        } else {
            (pkg.totalLessons + delta).coerceAtLeast(pkg.usedLessons)
        }
        pkgRepo.updatePackage(pkg.copy(totalLessons = newTotal))
        return if (delta > 0) "已增添 $delta 课时" else "已减少 ${-delta} 课时"
    }

    /**
     * 额外赠送课时：为学员创建一个独立的赠送课时包。
     * 不影响原套餐数据，单独追踪赠送课时的使用情况。
     */
    suspend fun giftLessons(studentName: String, count: Int, today: String): String {
        if (count <= 0) return ""
        pkgRepo.addPackage(
            LessonPackage(
                studentName = studentName,
                // 赠送包同样携带 studentId（软关联唯一键）
                studentId = studentRepo.getByName(studentName)?.studentId,
                name = "赠送${count}课时",
                totalLessons = count,
                usedLessons = 0,
                price = 0.0,
                purchaseDate = today,
                expireDate = "",
                note = "额外赠送"
            )
        )
        return "已为 $studentName 赠送 $count 课时"
    }

    /** 更新课时包全部信息（学员姓名、套餐名、总/已用课时、价格、日期、状态、备注）。 */
    suspend fun updatePackage(pkg: LessonPackage): String {
        pkgRepo.updatePackage(pkg)
        return "课时包已更新"
    }

    /** 新增教练（重名校验），重名时返回错误文案（以"教练已存在"开头）。 */
    suspend fun addCoach(name: String, phone: String, specialty: String, today: String): String {
        if (coachRepo.getByName(name) != null) {
            return "教练已存在"
        }
        coachRepo.upsert(
            Coach(
                name = name,
                phone = phone,
                specialty = specialty,
                hireDate = today
            )
        )
        return "教练已添加"
    }

    /** 删除教练。 */
    suspend fun deleteCoach(name: String): String {
        coachRepo.delete(name)
        return "教练已删除"
    }
}
