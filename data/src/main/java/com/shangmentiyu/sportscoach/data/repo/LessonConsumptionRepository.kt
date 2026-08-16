package com.shangmentiyu.sportscoach.data.repo

import androidx.room.withTransaction
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.db.LessonDao
import com.shangmentiyu.sportscoach.data.db.LessonPackageDao
import com.shangmentiyu.sportscoach.data.db.SignInDao
import com.shangmentiyu.sportscoach.data.internal.AutoBackupScheduler
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.SignInRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 消课域 Repository（v53 从 [OperationRepository] 拆出）：签到 / 签退消课 / 撤销签到。
 *
 * 职责边界：
 * - 签到（v32 排课与签到分离）：翻转占位或新建 Lesson，写 sign_in_records 防重
 * - 签退消课（v27 重构）：事务内扣减课时包 + 更新 Lesson 为已签退
 * - 撤销签到（v23）：事务内删除 Lesson + 恢复课时包 usedLessons
 *
 * 并发保护：三个写操作共享 [consumeMutex] 互斥锁，
 * 确保读 + 写在同一临界区内完成，避免并发签到时多协程读到相同余额并各自扣减。
 */
class LessonConsumptionRepository(
    private val lessonDao: LessonDao,
    private val pkgDao: LessonPackageDao,
    private val signInDao: SignInDao,
    private val db: AppDatabase?
) {

    /**
     * 消课结果：携带扣减的课时包信息，供上层记录到 Lesson 表与 UI 反馈。
     */
    data class ConsumeResult(
        val success: Boolean,
        val packageId: String = "",
        val packageName: String = "",
        val remainingAfter: Int = 0,
        val message: String = ""
    )

    /**
     * === v32：签到结果 ===
     */
    data class SignInResult(
        val success: Boolean,
        val lessonId: String = "",
        val alreadySigned: Boolean = false,
        val message: String = ""
    )

    /**
     * 撤销签到结果：携带操作统计供 UI 反馈。
     *
     * @param success 是否成功
     * @param restoredPackageId 恢复的课时包 ID（无则空串）
     * @param restoredPackageName 恢复的课时包名（用于 Toast 显示）
     * @param remainingAfter 恢复后该课时包的剩余课时数
     * @param message 用户可读消息
     */
    data class UndoResult(
        val success: Boolean,
        val restoredPackageId: String = "",
        val restoredPackageName: String = "",
        val remainingAfter: Int = 0,
        val message: String
    )

    /**
     * 消课并发保护锁：确保读 + 写在同一临界区内完成，
     * 避免并发签到时多协程读到相同余额并各自扣减，导致同一课时被扣多次。
     */
    private val consumeMutex = Mutex()

    /**
     * === v32：教练手动签到（排课与签到分离） ===
     *
     * 签到不再由排课自动触发，而是独立操作：
     * 1. 查该学员今日首条「未签退」课时（排课占位或已签到），存在则翻转 status="已签到"；
     *    不存在则新建一条 Lesson(status="已签到", packageId="")。
     * 2. 写入 sign_in_records(type="签到")，记录时间/学员/课时/操作人。
     *
     * 防重（双防线）：
     * - 应用层：占位课时已是「已签到」状态则直接拒绝；
     * - 数据库层：sign_in_records 唯一索引(studentName, lessonId, type) 冲突时 insert 返回 -1，
     *   回滚事务并拒绝，杜绝同一学员+同一课时+同一日期时间重复签到。
     *
     * @param operator 操作人（教练名，可空）
     */
    suspend fun signIn(
        studentName: String,
        studentId: String?,
        operator: String = ""
    ): SignInResult {
        val database = db ?: return SignInResult(
            success = false,
            message = "签到失败：数据库未初始化"
        )
        return try {
            database.withTransaction {
                val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()))
                val nowTime = java.time.LocalTime.now().format(
                    DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
                )
                val pending = lessonDao.findPendingByStudentDateDual(studentId, studentName, today)

                // 应用层防重：占位已是「已签到」即拒绝
                if (pending != null && pending.status == "已签到") {
                    return@withTransaction SignInResult(
                        success = false,
                        lessonId = pending.id,
                        alreadySigned = true,
                        message = "已签到，请勿重复操作"
                    )
                }

                // 翻转占位或新建课时（均不扣课时包，签退时统一扣减）
                val lesson = if (pending != null) {
                    lessonDao.update(pending.copy(status = "已签到"))
                    pending.copy(status = "已签到")
                } else {
                    val fresh = Lesson(
                        id = java.util.UUID.randomUUID().toString().take(12),
                        date = today,
                        time = nowTime,
                        studentName = studentName,
                        studentId = studentId,
                        packageId = "",
                        status = "已签到"
                    )
                    lessonDao.insert(fresh)
                    fresh
                }

                // 数据库层防重：写签到记录，唯一索引冲突则拒绝
                val rowId = signInDao.insert(
                    SignInRecord(
                        studentName = studentName,
                        studentId = studentId,
                        lessonId = lesson.id,
                        type = "签到",
                        operator = operator
                    )
                )
                if (rowId == -1L) {
                    throw RuntimeException("签到记录已存在（唯一索引冲突）")
                }

                AutoBackupScheduler.notifyDataChange()
                SignInResult(
                    success = true,
                    lessonId = lesson.id,
                    message = "签到成功（签退时再扣减课时）"
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("SignInFlow", "签到失败：${studentName} ${e.message}", e)
            SignInResult(
                success = false,
                alreadySigned = e.message?.contains("唯一索引") == true,
                message = if (e.message?.contains("唯一索引") == true) {
                    "已签到，请勿重复操作"
                } else {
                    "签到失败：${e.message ?: "未知异常"}"
                }
            )
        }
    }

    /**
     * === v27：签退时消耗课时（重构签到消课逻辑） ===
     *
     * 签退时执行的事务化消课：在单事务内完成"扣减课时包 + 更新 Lesson 状态为已签退"，
     * 任一步失败整体回滚，保证数据绝对不会半途出错。
     *
     * 与旧逻辑（v27 前）的区别：
     * - 旧逻辑：签到时直接扣减，签到成功即视为消课完成（该方法已于 v47 移除，
     *   统一走本方法：签到时仅创建 status="已签到" 的 Lesson，不扣减课时包；
     *   签退时（教练保存课后反馈时）才执行本方法，扣减课时包并更新 Lesson.status="已签退"）
     *
     * 执行流程（@Transaction 原子操作）：
     * 1. 调用 [doConsumeLessonInternal] 找到最早购买的活跃课时包并 usedLessons + 1
     * 2. 更新 Lesson：status="已签退"，signOutTime=当前时间，packageId=扣减的课时包ID
     * 3. 任一步失败整体回滚
     *
     * 并发保护：复用 [consumeMutex]，与 [undoCheckIn] 共享锁，
     * 避免签退与撤销并发执行时余额计算错乱。
     *
     * @param lesson 待签退的课时记录（必须已存在，包含学员名、ID 等信息）
     * @return ConsumeResult.success=true 表示签退成功；
     *         false 表示无可用课时包或扣减失败（事务回滚，Lesson 状态不变）
     *
     * === v49 体验课：isTrial=true 时跳过课时包扣减，仅记录签退时间 ===
     */
    suspend fun consumeLessonForCheckOut(lesson: Lesson): ConsumeResult = consumeMutex.withLock {
        android.util.Log.d("CheckOutFlow",
            "consumeLessonForCheckOut 入口：lessonId=${lesson.id} student=${lesson.studentName} " +
                "studentId=${lesson.studentId} isTrial=${lesson.isTrial} status=${lesson.status} " +
                "packageId=${lesson.packageId}")

        val database = db ?: return@withLock ConsumeResult(
            success = false,
            message = "签退失败：数据库未初始化"
        )

        try {
            database.withTransaction {
                // 0. 幂等防线：课时已签退则直接成功返回，杜绝重复点击/重复结算导致二次扣费
                if (lesson.status == "已签退") {
                    android.util.Log.d("CheckOutFlow",
                        "幂等拦截：lessonId=${lesson.id} 已签退，跳过消课")
                    return@withTransaction ConsumeResult(
                        success = true,
                        packageId = lesson.packageId,
                        message = "课时已签退，无需重复操作"
                    )
                }

                // 1. 先更新 Lesson：status="已签退" + signOutTime（表访问顺序：lessons 先于 lesson_packages）
                //    packageId 保留原值：未关联的保持空待回填，已关联的（旧数据）不清空，避免破坏扣费归属记录
                val nowTime = java.time.LocalTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
                )
                val updatedLesson = lesson.copy(
                    status = "已签退",
                    signOutTime = nowTime,
                    packageId = lesson.packageId
                )
                val affected = lessonDao.update(updatedLesson)
                if (affected != 1) {
                    throw RuntimeException("Lesson 更新未生效（affected=$affected）")
                }

                // v32：写签退记录（唯一索引防重，重复签退已被上方幂等防线拦截，此处正常首次写入）
                signInDao.insert(
                    SignInRecord(
                        studentName = lesson.studentName,
                        studentId = lesson.studentId,
                        lessonId = lesson.id,
                        type = "签退",
                        operator = lesson.coach
                    )
                )

                // 2. 体验课：不消耗课时包余额，仅记录签退时间即完成
                if (lesson.isTrial) {
                    android.util.Log.d("CheckOutFlow",
                        "体验课签退成功（不消耗课时）：${lesson.studentName} lessonId=${lesson.id}")
                    AutoBackupScheduler.notifyDataChange()
                    return@withTransaction ConsumeResult(
                        success = true,
                        packageId = "",
                        packageName = "体验课",
                        remainingAfter = 0,
                        message = "体验课已签退（不消耗课时）"
                    )
                }

                // 3. 旧数据兼容：Lesson 已关联课时包（v27 前"签到即扣费"遗留，packageId 非空 ⟺ 已扣课时），
                //    仅标记签退，绝不重复扣费
                if (lesson.packageId.isNotBlank()) {
                    val oldPkg = pkgDao.getById(lesson.packageId)
                    android.util.Log.d("CheckOutFlow",
                        "旧数据已扣费：lessonId=${lesson.id} pkg=${lesson.packageId}，仅标记签退不重复扣费")
                    AutoBackupScheduler.notifyDataChange()
                    return@withTransaction ConsumeResult(
                        success = true,
                        packageId = lesson.packageId,
                        packageName = oldPkg?.name ?: "",
                        remainingAfter = oldPkg?.remainingLessons ?: 0,
                        message = "已签退（该课时此前已扣减课时）"
                    )
                }

                // 4. 常规排课：调用核心消课逻辑扣减课时包（双通道：studentId 优先、studentName 回退）
                val consume = doConsumeLessonInternal(lesson.studentName, lesson.studentId)
                if (!consume.success) {
                    // 抛异常触发事务回滚，Lesson 状态保持"已签到"，防止"签退成功但未扣课时"残缺态
                    throw RuntimeException("课时包扣减失败：${consume.message}")
                }

                // 5. 回填扣减的课时包 ID（精准定位：以 pkg 主键 id 落库，杜绝多课时包错乱）
                lessonDao.update(updatedLesson.copy(packageId = consume.packageId))

                android.util.Log.d("CheckOutFlow",
                    "签退成功：${lesson.studentName} lessonId=${lesson.id} " +
                        "pkg=${consume.packageName}(id=${consume.packageId}) remaining=${consume.remainingAfter}")

                // v30：签退扣课时属于核心数据变更，触发自动备份防抖
                AutoBackupScheduler.notifyDataChange()

                consume
            }
        } catch (e: Exception) {
            android.util.Log.e("CheckOutFlow", "签退失败：lessonId=${lesson.id} ${e.message}", e)
            ConsumeResult(
                success = false,
                message = "签退失败：${e.message ?: "未知异常"}"
            )
        }
    }

    /**
     * 内部消课实现：不持锁，由 [consumeLessonForCheckOut] 在事务内调用。
     *
     * 本方法是全项目消课的唯一实现（v47 起移除各仓库重复拷贝）；
     * 不获取 [consumeMutex]（已由外层调用方持有），避免重入死锁。
     * 直接执行读 + 写 + 校验三步。
     */
    private suspend fun doConsumeLessonInternal(studentName: String, studentId: String?): ConsumeResult {
        // v46 双通道查询：studentId 优先、studentName 回退（杜绝学员改名后断链找错课时包）
        val packages = pkgDao.getByStudentDual(studentId, studentName).first()
        android.util.Log.d("CheckOutFlow",
            "消课查询：学员=$studentName studentId=$studentId 课时包${packages.size}个: " +
                packages.map { "${it.name}(status=${it.status},used=${it.usedLessons}/${it.totalLessons})" })
        val active = packages.filter { it.status == "活跃" && !it.isExhausted && !it.isExpired }
        android.util.Log.d("CheckOutFlow", "消课：过滤后活跃包${active.size}个")
        val target = active.minByOrNull { it.purchaseDate }
            ?: return ConsumeResult(success = false, message = "无可用课时包")

        // 数据自愈防线：usedLessons > totalLessons 属于脏数据，
        // 显式抛 IllegalStateException 中断消课并由外层事务整体回滚，绝不静默 coerce 掩盖错误
        if (target.usedLessons > target.totalLessons) {
            throw IllegalStateException(
                "课时包数据异常：${target.name}(id=${target.id}) used=${target.usedLessons} > total=${target.totalLessons}，" +
                    "已中断消课，请先修正课时包数据"
            )
        }

        val newUsed = target.usedLessons + 1
        val updated = if (newUsed >= target.totalLessons) {
            target.copy(usedLessons = target.totalLessons, status = "已用完")
        } else {
            target.copy(usedLessons = newUsed)
        }
        val affected = pkgDao.update(updated)

        if (affected != 1) {
            android.util.Log.e("CheckOutFlow",
                "课时扣减未落库：pkgId=${target.id} affected=$affected（预期1）")
            return ConsumeResult(
                success = false,
                message = "课时扣减失败（更新未生效）"
            )
        }

        val recheck = pkgDao.getById(target.id)
        if (recheck == null || recheck.usedLessons != updated.usedLessons) {
            android.util.Log.e("CheckOutFlow",
                "课时扣减校验不一致：pkgId=${target.id} 期望used=${updated.usedLessons} 实际=${recheck?.usedLessons}")
            return ConsumeResult(
                success = false,
                message = "课时扣减失败（校验不一致）"
            )
        }

        android.util.Log.d("CheckOutFlow",
            "扣减成功：${target.name}(id=${target.id}) used ${target.usedLessons}->${updated.usedLessons} 剩余${updated.remainingLessons}")
        return ConsumeResult(
            success = true,
            packageId = target.id,
            packageName = target.name,
            remainingAfter = updated.remainingLessons,
            message = "已扣减课时（${target.name}）"
        )
    }

    /**
     * 撤销签到：在单事务内删除 Lesson 记录并恢复对应课时包的 usedLessons。
     *
     * 适用场景：教练误触"签到"按钮后，可通过撤销操作回滚本次签到，
     * 避免手工修改课时包 usedLessons 的二次操作成本。
     *
     * 执行流程（单事务原子操作）：
     * 1. 通过 lessonId 查询 Lesson 记录，获取 packageId / studentName / date 等信息
     * 2. 物理删除该 Lesson 记录（从 lessons 表）
     * 3. 若 Lesson 关联了课时包（packageId 非空）：
     *    - 查询该课时包，校验 usedLessons > 0
     *    - usedLessons - 1，若原状态为"已用完"则恢复为"活跃"
     * 4. 任意一步失败则整体回滚，保证数据一致性
     *
     * 设计要点：
     * - 使用 [consumeMutex] 互斥锁保护读 + 写临界区，
     *   避免"撤销"与"签到/签退"并发执行时出现余额计算错乱
     * - 不允许 usedLessons 减为负数（coerceAtLeast(0)）
     * - 长期自动生成的课时（packageId = ""）仅删除 Lesson，不涉及课时包恢复
     *
     * @param lessonId 待撤销的 Lesson ID
     * @param studentName 学员姓名（用于日志与兜底校验，与 Lesson.studentName 必须一致）
     * @return [UndoResult] 携带操作结果
     */
    suspend fun undoCheckIn(lessonId: String, studentName: String): UndoResult = consumeMutex.withLock {
        val database = db ?: return@withLock UndoResult(
            success = false,
            message = "撤销失败：数据库未初始化"
        )

        try {
            database.withTransaction {
                // 1. 查询待撤销的 Lesson 记录
                val lesson = lessonDao.getById(lessonId)
                    ?: return@withTransaction UndoResult(
                        success = false,
                        message = "撤销失败：课时记录不存在（可能已被删除）"
                    )

                // 兜底校验：Lesson 学员名与传入学员名一致
                if (lesson.studentName != studentName) {
                    return@withTransaction UndoResult(
                        success = false,
                        message = "撤销失败：学员不匹配（${lesson.studentName} ≠ $studentName）"
                    )
                }

                // 2. 物理删除 Lesson 记录
                lessonDao.deleteById(lesson.id)

                // 3. 若关联了课时包，恢复 usedLessons
                val pkgId = lesson.packageId
                if (pkgId.isBlank()) {
                    // 长期自动生成的课时，无关联课时包，仅删除 Lesson
                    return@withTransaction UndoResult(
                        success = true,
                        message = "已撤销签到（未扣减课时，无需恢复）"
                    )
                }

                val pkg = pkgDao.getById(pkgId)
                    ?: return@withTransaction UndoResult(
                        success = true,
                        restoredPackageId = pkgId,
                        message = "已撤销签到，但课时包不存在（可能已被删除）"
                    )

                // 校验 usedLessons > 0，避免恢复后变为负数
                if (pkg.usedLessons <= 0) {
                    return@withTransaction UndoResult(
                        success = true,
                        restoredPackageId = pkg.id,
                        restoredPackageName = pkg.name,
                        remainingAfter = pkg.remainingLessons,
                        message = "已撤销签到，课时包已用数为 0，无需恢复"
                    )
                }

                // 恢复 usedLessons - 1；若原状态为"已用完"，恢复为"活跃"
                val newUsed = pkg.usedLessons - 1
                val newStatus = if (pkg.status == "已用完") "活跃" else pkg.status
                val updated = pkg.copy(usedLessons = newUsed, status = newStatus)
                val affected = pkgDao.update(updated)

                if (affected != 1) {
                    android.util.Log.e("UndoCheckIn",
                        "课时包恢复失败：affected=$affected, pkgId=${pkg.id}")
                    // 即使课时包恢复失败，Lesson 已删除，回滚由 withTransaction 保证一致性
                    throw RuntimeException("课时包恢复未生效（affected=$affected）")
                }

                android.util.Log.i("UndoCheckIn",
                    "撤销成功：${pkg.name} used ${pkg.usedLessons}->${newUsed} 剩余${updated.remainingLessons}")

                // v30：撤销签到恢复课时不属于核心数据变更，但仍影响课时余额，触发防抖备份
                AutoBackupScheduler.notifyDataChange()

                UndoResult(
                    success = true,
                    restoredPackageId = pkg.id,
                    restoredPackageName = pkg.name,
                    remainingAfter = updated.remainingLessons,
                    message = "已撤销签到，恢复 1 节课时（${pkg.name}）"
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("UndoCheckIn", "撤销签到异常：${e.message}", e)
            UndoResult(
                success = false,
                message = "撤销失败：${e.message ?: "未知异常"}"
            )
        }
    }
}
