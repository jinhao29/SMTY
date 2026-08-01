package com.shangmentiyu.sportscoach.data.repo

import androidx.room.withTransaction
import com.shangmentiyu.sportscoach.core.AutoBackupScheduler
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.db.StudentDao
import com.shangmentiyu.sportscoach.data.db.StudentFtsDao
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.excel.ImportStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * 学员仓储：封装学员数据的增删改查 + 跨表事务级联。
 *
 * v20 引入"软关联 + 改名事务"策略：
 * - 子表（Lesson / Schedule / LessonPackage / TrainingCycle / BodyMetricHistory /
 *   ParentReport / StudentDietRecord）保留 studentName 字段用于显示，
 *   同时新增 studentId 软关联字段（NULL=旧数据，由业务层回填）。
 * - 删除学员使用软删除（isActive = 0），保留行用于历史报表关联。
 * - 改名通过 [renameStudentCascade] 在单事务内原子级联更新所有子表的 studentName。
 *
 * v21 引入 FTS 全文检索：
 * - 构造器新增 [ftsDao]（可空，兼容旧调用方）。
 * - [searchStudent] 优先走 FTS 索引，比 `LIKE '%x%'` 快数十倍；
 *   FTS 不可用时降级为 LIKE 兜底，保证搜索功能始终可用。
 *
 * 身体形态字段（age/heightCm/weightKg/bmi）由调用方传入，
 * BMI 通常在 UI 层用 [com.shangmentiyu.sportscoach.core.BmiProcessor] 计算后一并存入。
 */
class StudentRepository(
    private val dao: StudentDao,
    private val db: AppDatabase? = null,
    private val ftsDao: StudentFtsDao? = null,
    // v26 优化1：可选注入操作日志 Repository，未注入时静默跳过日志记录
    private val auditLog: AuditLogRepository? = null
) {
    /** 活跃学员列表（已过滤 isActive=0 的软删除学员） */
    fun getAllStudents(): Flow<List<Student>> = dao.getAll()

    /** 全量学员列表（含已软删除的），用于历史报表 / 数据完整性核对 */
    fun getAllStudentsIncludeDeleted(): Flow<List<Student>> = dao.getAllIncludeDeleted()

    fun getStudentCount(): Flow<Int> = dao.count()
    suspend fun getByName(name: String): Student? = dao.getByName(name)

    /** 按姓名查学员（含已软删除的），用于历史数据回填 */
    suspend fun getByNameIncludeDeleted(name: String): Student? = dao.getByNameIncludeDeleted(name)

    /**
     * 新增学员。
     *
     * v22 新增输入边界校验：在持久化前对关键身体形态字段做合法性断言，
     * 防止误输入（如身高 2500cm / 体重 -5kg）写入数据库。
     * 校验失败抛出 [IllegalArgumentException]，由 ViewModel 层捕获并转 Toast。
     *
     * 校验规则：
     * - 姓名：非空且长度 ≤ 20
     * - 性别：仅允许 "" / "男" / "女"
     * - 年龄：0（未填）或 1..100
     * - 身高：0（未填）或 30..250 cm
     * - 体重：0（未填）或 5..300 kg
     * - BMI：0（未计算）或 5..80
     *
     * @param age 年龄（岁），0 表示未填
     * @param heightCm 身高（厘米），0 表示未填
     * @param weightKg 体重（千克），0 表示未填
     * @param bmi BMI 数值，0 表示未计算
     * @throws IllegalArgumentException 任一字段不合法时抛出，message 为可读提示
     */
    suspend fun addStudent(
        name: String, gender: String, grade: String, school: String, phone: String,
        age: Int = 0, heightCm: Int = 0, weightKg: Float = 0f, bmi: Float = 0f
    ) {
        validateStudentFields(name, gender, age, heightCm, weightKg, bmi)
        dao.insert(
            Student(
                name = name, gender = gender, grade = grade, school = school, phone = phone,
                age = age, heightCm = heightCm, weightKg = weightKg, bmi = bmi,
                // 新增学员时立即生成 studentId（UUID 前 8 位），用于后续软关联
                studentId = java.util.UUID.randomUUID().toString().take(8)
            )
        )
        // v26 优化1：记录操作日志
        auditLog?.log(
            action = "新增学员",
            targetStudent = name,
            after = mapOf(
                "gender" to gender, "grade" to grade, "school" to school,
                "age" to age, "heightCm" to heightCm, "weightKg" to weightKg, "bmi" to bmi
            ),
            summary = "新增学员「$name」(${age}岁/${heightCm}cm/${weightKg}kg)"
        )
        // v30：新增学员属于核心数据变更，触发自动备份防抖
        AutoBackupScheduler.notifyDataChange()
    }

    /**
     * 更新学员信息。
     *
     * v22 新增输入边界校验：在持久化前调用 [validateStudentFields] 校验关键字段，
     * 与 [addStudent] 保持一致的合法性约束。
     *
     * @param student 待更新学员对象
     * @throws IllegalArgumentException 任一字段不合法时抛出
     */
    suspend fun updateStudent(student: Student) {
        // v26 优化1：先查变更前数据用于日志对比
        val before = auditLog?.let { dao.getByName(student.name) }
        validateStudentFields(
            student.name, student.gender, student.age,
            student.heightCm, student.weightKg, student.bmi
        )
        dao.update(student)
        // v26 优化1：记录操作日志
        auditLog?.log(
            action = "修改学员",
            targetStudent = student.name,
            before = before?.let {
                mapOf(
                    "age" to it.age, "heightCm" to it.heightCm,
                    "weightKg" to it.weightKg, "bmi" to it.bmi
                )
            },
            after = mapOf(
                "age" to student.age, "heightCm" to student.heightCm,
                "weightKg" to student.weightKg, "bmi" to student.bmi
            ),
            summary = "修改学员「${student.name}」(身高 ${before?.heightCm ?: 0}→${student.heightCm}cm / 体重 ${before?.weightKg ?: 0f}→${student.weightKg}kg)"
        )
        // v30：修改学员属于核心数据变更，触发自动备份防抖
        AutoBackupScheduler.notifyDataChange()
    }

    /**
     * 学员字段合法性校验（v22 引入）。
     *
     * 设计原则：
     * - 仅校验"明显异常"的输入，不做精细业务规则校验
     * - 0 / 空串视为"未填"，不参与范围判断
     * - 失败时抛出 [IllegalArgumentException]，由调用方捕获并转 UI 提示
     *
     * @param name 姓名（非空，长度 ≤ 20）
     * @param gender 性别（"" / "男" / "女"）
     * @param age 年龄（0 或 1..100）
     * @param heightCm 身高 cm（0 或 30..250）
     * @param weightKg 体重 kg（0 或 5..300）
     * @param bmi BMI（0 或 5..80）
     * @throws IllegalArgumentException 任一字段不合法时抛出
     */
    private fun validateStudentFields(
        name: String,
        gender: String,
        age: Int,
        heightCm: Int,
        weightKg: Float,
        bmi: Float
    ) {
        require(name.isNotBlank()) { "姓名不能为空" }
        require(name.length <= 20) { "姓名长度不能超过 20 个字符" }
        require(gender.isBlank() || gender == "男" || gender == "女") { "性别字段异常：仅允许「男」「女」或空" }
        require(age == 0 || age in 1..100) { "年龄数据异常：应在 1~100 岁之间" }
        require(heightCm == 0 || heightCm in 30..250) { "身高数据异常：应在 30~250 cm 之间" }
        require(weightKg == 0f || weightKg in 5f..300f) { "体重数据异常：应在 5~300 kg 之间" }
        require(bmi == 0f || bmi in 5f..80f) { "BMI 数据异常：应在 5~80 之间" }
    }

    /**
     * 软删除学员：仅置 isActive=false，保留行用于历史报表关联。
     *
     * 子表（Lesson / Schedule / LessonPackage 等）的数据原样保留，
     * 通过 studentName 仍能查到该学员的历史记录。
     * 日常 UI 通过 [StudentDao.getAll] 自动过滤 isActive=0 的行。
     */
    suspend fun deleteStudent(name: String) {
        // v26 优化1：记录删除前数据用于日志
        val before = auditLog?.let { dao.getByName(name) }
        // v44：软删除学员时，同步物理删除该学员的所有排课记录
        // 原因：学员不再上课时，排课表里的周期性排课应同步清除，
        // 否则今日排课数 / 未签到数角标仍会统计到该学员，造成误导
        db?.withTransaction {
            dao.softDeleteByName(name)
            db.scheduleDao().deleteByStudent(name)
        } ?: dao.softDeleteByName(name)
        // v26 优化1：记录操作日志（软删除）
        auditLog?.log(
            action = "删除学员",
            targetStudent = name,
            before = before?.let {
                mapOf(
                    "age" to it.age, "heightCm" to it.heightCm,
                    "weightKg" to it.weightKg, "isActive" to it.isActive
                )
            },
            summary = "软删除学员「$name」（数据保留可恢复，排课已清除）"
        )
        // v30：删除学员属于核心数据变更，触发自动备份防抖
        AutoBackupScheduler.notifyDataChange()
    }

    /**
     * 物理删除学员（仅用于整库恢复 / 清空场景）。
     * 日常业务请使用 [deleteStudent]（软删除）。
     */
    suspend fun physicallyDeleteStudent(name: String) {
        dao.deleteByName(name)
    }

    /**
     * 学员改名：在单事务内原子级联更新所有子表的 studentName 字段。
     *
     * 覆盖范围（v20 全量）：
     * - students        主表
     * - lessons         课堂记录
     * - schedules       排课
     * - lesson_packages 课时包
     * - training_cycles 训练周期
     * - body_metric_history 身体形态历史
     * - parent_reports  家长报告
     * - student_diet_records 学员饮食绑定
     *
     * 注意：必须传入 [AppDatabase] 实例才能启用事务级联；
     * 未传入时（兼容旧调用方）仅更新 students 表主键，子表需调用方自行处理。
     */
    suspend fun renameStudentCascade(oldName: String, newName: String) {
        val database = db ?: run {
            // 兼容旧调用方：仅更新 students 表
            dao.renameStudent(oldName, newName)
            // v26 优化1：兼容旧调用方仍记录日志（无事务保护，但日志独立写入）
            auditLog?.log(
                action = "学员改名",
                targetStudent = oldName,
                before = mapOf("name" to oldName),
                after = mapOf("name" to newName),
                summary = "学员改名「$oldName」→「$newName」（仅主表，无事务级联）"
            )
            return
        }

        // 唯一性校验：目标姓名不能与现有活跃学员重名
        dao.getByName(newName)?.let {
            throw IllegalArgumentException("学员姓名「$newName」已存在，请更换姓名")
        }

        // 在单事务内原子级联更新所有子表的 studentName
        database.withTransaction {
            // 1. 主表改名（必须先执行，后续子表通过新名关联）
            dao.renameStudent(oldName, newName)

            // 2. 各子表 studentName 字段同步更新
            database.lessonDao().renameStudent(oldName, newName)
            database.scheduleDao().renameStudent(oldName, newName)
            database.lessonPackageDao().renameStudent(oldName, newName)
            database.trainingCycleDao().renameStudent(oldName, newName)
            database.bodyMetricHistoryDao().renameStudent(oldName, newName)
            database.parentReportDao().renameStudent(oldName, newName)
            database.dietDao().renameStudent(oldName, newName)

            // v26 优化1：在事务内记录日志，保证数据与日志一致性
            auditLog?.log(
                action = "学员改名",
                targetStudent = newName,
                before = mapOf("oldName" to oldName),
                after = mapOf("newName" to newName, "cascadeUpdated" to true),
                summary = "学员改名「$oldName」→「$newName」（事务级联 7 张子表）"
            )
        }
        // v30：学员改名级联更新 7 张子表，属于核心数据变更，触发自动备份防抖
        AutoBackupScheduler.notifyDataChange()
    }

    /**
     * === v33 数据流加固（模块 1 核心）：基于 studentId 的级联改名事务 ===
     *
     * 替代 [renameStudentCascade] 的推荐路径：通过 studentId 唯一键定位学员，
     * 在单事务内原子级联更新所有子表的 studentName 字段。
     *
     * 与 [renameStudentCascade] 的区别：
     * - 旧路径按 studentName（主键）定位，若存在重名历史数据会误改多条
     * - 新路径按 studentId（唯一软关联键）定位，精准命中单条学员
     * - 旧数据（studentId 为 NULL）不会被误改，由 [backfillStudentIds] 后续回填
     *
     * 事务边界：所有子表更新在同一 [db.withTransaction] 内原子完成，
     * 任一子表更新失败则整体回滚，杜绝"主表改了子表没改"的脏数据。
     *
     * 覆盖范围（与 [renameStudentCascade] 一致）：
     * - students                主表（按 studentId 改名）
     * - lessons                 课堂记录
     * - schedules               排课
     * - lesson_packages         课时包
     * - training_cycles         训练周期
     * - body_metric_history     身体形态历史
     * - parent_reports          家长报告
     * - student_diet_records    学员饮食绑定
     *
     * 异常处理：
     * - studentId 为空 / 学员不存在 → 抛 [IllegalArgumentException]
     * - 目标姓名与现有活跃学员重名 → 抛 [IllegalArgumentException]
     * - 事务内任一子表更新失败 → 整体回滚，抛原始异常给调用方
     *
     * @param studentId 学员唯一 ID（必须非空）
     * @param newName 新姓名
     * @throws IllegalArgumentException 学员不存在 / 重名 / studentId 为空
     */
    suspend fun renameStudentCascadeById(studentId: String, newName: String) {
        require(studentId.isNotBlank()) { "学员 ID 不能为空" }
        require(newName.isNotBlank()) { "新姓名不能为空" }

        val database = db ?: run {
            // 兼容旧调用方（未注入 AppDatabase）：仅更新 students 主表，子表由调用方自行处理
            val affected = dao.renameStudentById(studentId, newName)
            if (affected == 0) {
                throw IllegalArgumentException("未找到 studentId=$studentId 的学员")
            }
            android.util.Log.w("StudentRepo",
                "renameStudentCascadeById 降级执行（无 AppDatabase 注入）：仅更新主表，" +
                    "子表（Lesson/Schedule/LessonPackage 等）需调用方自行处理")
            return
        }

        // 1. 通过 studentId 定位学员（必须存在且活跃）
        val target = dao.getByStudentId(studentId)
            ?: throw IllegalArgumentException("未找到 studentId=$studentId 的活跃学员")
        val oldName = target.name

        // 2. 唯一性校验：目标姓名不能与现有活跃学员重名（除非就是自己）
        if (oldName != newName) {
            dao.getByName(newName)?.let { existing ->
                if (existing.studentId != studentId) {
                    throw IllegalArgumentException("学员姓名「$newName」已存在，请更换姓名")
                }
            }
        }

        // 3. 字段合法性校验（与 addStudent 保持一致）
        validateStudentFields(
            name = newName,
            gender = target.gender,
            age = target.age,
            heightCm = target.heightCm,
            weightKg = target.weightKg,
            bmi = target.bmi
        )

        // 4. 在单事务内原子级联更新所有子表的 studentName
        try {
            database.withTransaction {
                // 4.1 主表改名（按 studentId 精准定位）
                val affected = dao.renameStudentById(studentId, newName)
                if (affected == 0) {
                    throw IllegalStateException("主表改名未生效：studentId=$studentId 可能已被删除")
                }

                // 4.2 子表按 studentId 级联更新 studentName（不受同名干扰）
                database.lessonDao().updateStudentNameByStudentId(studentId, newName)
                database.scheduleDao().updateStudentNameByStudentId(studentId, newName)
                database.lessonPackageDao().updateStudentNameByStudentId(studentId, newName)
                database.trainingCycleDao().renameStudent(oldName, newName)
                database.bodyMetricHistoryDao().renameStudent(oldName, newName)
                database.parentReportDao().renameStudent(oldName, newName)
                database.dietDao().renameStudent(oldName, newName)

                // 4.3 在事务内记录日志，保证数据与日志一致性
                auditLog?.log(
                    action = "学员改名（ID 级联）",
                    targetStudent = newName,
                    before = mapOf("oldName" to oldName, "studentId" to studentId),
                    after = mapOf("newName" to newName, "cascadeUpdated" to true),
                    summary = "学员改名「$oldName」→「$newName」(studentId=$studentId，事务级联)"
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("StudentRepo",
                "renameStudentCascadeById 事务失败：studentId=$studentId, newName=$newName, ${e.message}", e)
            throw e
        }

        // 5. 触发自动备份（事务已提交后才触发）
        AutoBackupScheduler.notifyDataChange()
    }

    /**
     * === v33 数据流加固（模块 1 核心）：基于 studentId 的软删除 ===
     *
     * 替代 [deleteStudent] 的推荐路径：通过 studentId 唯一键定位学员，
     * 仅置 isActive=false，保留行用于历史报表关联。
     *
     * 与 [deleteStudent] 的区别：
     * - 旧路径按 name（主键）定位，重名场景下可能误删
     * - 新路径按 studentId 定位，精准命中单条学员
     *
     * 子表（Lesson / Schedule / LessonPackage 等）数据原样保留，
     * 通过 studentName 仍能查到该学员的历史记录。
     * 日常 UI 通过 [StudentDao.getAll] 自动过滤 isActive=0 的行。
     *
     * @param studentId 学员唯一 ID（必须非空）
     * @throws IllegalArgumentException 学员不存在 / studentId 为空
     */
    suspend fun softDeleteStudentById(studentId: String) {
        require(studentId.isNotBlank()) { "学员 ID 不能为空" }

        val target = dao.getByStudentIdIncludeDeleted(studentId)
            ?: throw IllegalArgumentException("未找到 studentId=$studentId 的学员")

        val database = db
        if (database != null) {
            // 有事务支持：在事务内软删除 + 记日志
            try {
                database.withTransaction {
                    val affected = dao.softDeleteByStudentId(studentId)
                    if (affected == 0) {
                        throw IllegalStateException("软删除未生效：studentId=$studentId 可能已被删除")
                    }
                    auditLog?.log(
                        action = "删除学员（ID 软删）",
                        targetStudent = target.name,
                        before = mapOf(
                            "age" to target.age, "heightCm" to target.heightCm,
                            "weightKg" to target.weightKg, "isActive" to target.isActive
                        ),
                        summary = "软删除学员「${target.name}」(studentId=$studentId，数据保留可恢复)"
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("StudentRepo",
                    "softDeleteStudentById 事务失败：studentId=$studentId, ${e.message}", e)
                throw e
            }
        } else {
            // 兼容旧调用方：无事务支持，仅执行软删除
            val affected = dao.softDeleteByStudentId(studentId)
            if (affected == 0) {
                throw IllegalArgumentException("未找到 studentId=$studentId 的学员或已被软删除")
            }
            auditLog?.log(
                action = "删除学员（ID 软删）",
                targetStudent = target.name,
                summary = "软删除学员「${target.name}」(studentId=$studentId，无事务降级)"
            )
        }

        AutoBackupScheduler.notifyDataChange()
    }

    /**
     * === v33 数据流加固：按 studentId 查活跃学员 ===
     *
     * 供 ViewModel 通过 studentId 反查学员信息（如排课列表点击跳转学员详情）。
     */
    suspend fun getByStudentId(studentId: String): Student? = dao.getByStudentId(studentId)

    /**
     * 为旧数据回填 studentId（NULL → UUID）。
     *
     * 触发时机：
     * - 应用启动后后台扫描一次，将 studentId 为 NULL 的活跃学员补齐唯一 ID；
     * - 子表的 studentId 字段由各业务模块在后续写入时通过 studentName 反查回填。
     *
     * 注意：该方法只更新 studentId IS NULL 的行，不会覆盖已生成的 ID。
     */
    suspend fun backfillStudentIds() {
        // 简化实现：遍历全量学员，对 studentId 为 NULL 的行生成新 ID
        // 通过 Flow first() 一次性获取快照，避免在 suspend 函数中持有 Flow
        dao.getAllIncludeDeleted().first().forEach { student ->
            if (student.studentId.isNullOrBlank()) {
                val newId = java.util.UUID.randomUUID().toString().take(8)
                dao.ensureStudentId(student.name, newId)
            }
        }
    }

    suspend fun importStudents(students: List<Student>) {
        for (s in students) dao.insert(s)
    }

    /**
     * === v25 优化4：按导入策略导入学员 ===
     *
     * 根据用户选择的 [ImportStrategy] 处理同名学员，三种模式的执行流程：
     *
     * - [ImportStrategy.APPEND]（追加）：
     *   1) 按姓名查重（含已软删除的）
     *   2) 同名则跳过，仅对本地不存在的学员执行 [addStudent]
     *   3) 返回 (新增数, 跳过数, 覆盖数=0)
     *
     * - [ImportStrategy.OVERWRITE]（覆盖）：
     *   1) 按姓名查重
     *   2) 同名则物理删除原学员 + 级联删除全部子表（lessons / lesson_packages /
     *      schedules / body_metric_history / parent_reports / training_cycles /
     *      student_diet_records），然后插入新学员
     *   3) 注意：覆盖模式会丢失历史排课与课时包，仅用于"档案数据为准，整库重置"场景
     *   4) 返回 (新增数, 跳过数=0, 覆盖数)
     *
     * - [ImportStrategy.UPDATE_PART]（更新部分）：
     *   1) 按姓名查重
     *   2) 同名则仅更新学员的身体形态指标（age/heightCm/weightKg/bmi）
     *      及基础资料（gender/grade/school/phone），保留历史排课与课时包
     *   3) 不同名则 [addStudent] 新增
     *   4) 返回 (新增数, 跳过数=0, 覆盖数=更新条数)
     *
     * 设计要点：
     * - 覆盖与更新均使用 [AppDatabase.withTransaction] 单事务原子完成，避免中途失败残留脏数据
     * - APPEND 模式仅做新增，无副作用，不需要事务
     * - 校验逻辑复用 [validateStudentFields]，非法字段会被跳过并计入失败数（不影响其他学员）
     *
     * @param students 待导入学员列表（来自 Excel 解析）
     * @param strategy 导入策略
     * @return ImportResult(added, skipped, overwritten, failed)
     */
    suspend fun importStudentsWithStrategy(
        students: List<Student>,
        strategy: ImportStrategy
    ): ImportResult {
        var added = 0
        var skipped = 0
        var overwritten = 0
        var failed = 0

        for (s in students) {
            try {
                // 校验关键字段合法性（与 addStudent 一致），非法字段跳过并计入 failed
                validateStudentFields(s.name, s.gender, s.age, s.heightCm, s.weightKg, s.bmi)

                val existing = dao.getByNameIncludeDeleted(s.name)
                when (strategy) {
                    ImportStrategy.APPEND -> {
                        if (existing == null) {
                            dao.insert(s.copy(studentId = java.util.UUID.randomUUID().toString().take(8)))
                            added++
                        } else {
                            skipped++
                        }
                    }

                    ImportStrategy.OVERWRITE -> {
                        val database = db
                        if (database == null) {
                            // 兼容旧调用方（无 AppDatabase 注入）：仅物理删除主表
                            if (existing != null) dao.deleteByName(s.name)
                            dao.insert(s.copy(studentId = java.util.UUID.randomUUID().toString().take(8)))
                            if (existing != null) overwritten++ else added++
                        } else {
                            database.withTransaction {
                                if (existing != null) {
                                    // 物理删除主表 + 级联子表（重名覆盖场景必须清空旧关联数据）
                                    dao.deleteByName(s.name)
                                    database.lessonDao().deleteByStudent(s.name)
                                    database.lessonPackageDao().deleteByStudent(s.name)
                                    database.scheduleDao().deleteByStudent(s.name)
                                    database.bodyMetricHistoryDao().deleteByStudent(s.name)
                                    database.parentReportDao().deleteByStudent(s.name)
                                    database.trainingCycleDao().deleteByStudent(s.name)
                                    database.dietDao().deleteByStudentName(s.name)
                                }
                                dao.insert(s.copy(studentId = java.util.UUID.randomUUID().toString().take(8)))
                            }
                            if (existing != null) overwritten++ else added++
                        }
                    }

                    ImportStrategy.UPDATE_PART -> {
                        if (existing == null) {
                            dao.insert(s.copy(studentId = java.util.UUID.randomUUID().toString().take(8)))
                            added++
                        } else {
                            // 仅更新身体形态指标 + 基础资料，保留 createdAt / studentId / isActive / 子表数据
                            dao.update(
                                existing.copy(
                                    gender = s.gender,
                                    grade = s.grade,
                                    school = s.school,
                                    phone = s.phone,
                                    age = s.age,
                                    heightCm = s.heightCm,
                                    weightKg = s.weightKg,
                                    bmi = s.bmi,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                            overwritten++
                        }
                    }
                }
            } catch (e: IllegalArgumentException) {
                // 单条学员字段非法：跳过并记录，不影响其他学员导入
                failed++
            }
        }
        // v30：批量导入发生数据变更（added/overwritten > 0 时），触发自动备份防抖
        if (added > 0 || overwritten > 0) {
            AutoBackupScheduler.notifyDataChange()
        }
        return ImportResult(added, skipped, overwritten, failed)
    }

    /**
     * 导入结果统计（v25 优化4 引入）。
     *
     * @param added 新增学员数
     * @param skipped 跳过数（仅 APPEND 模式有效）
     * @param overwritten 覆盖/更新数（OVERWRITE 或 UPDATE_PART 模式有效）
     * @param failed 校验失败数（字段非法，被跳过）
     */
    data class ImportResult(
        val added: Int,
        val skipped: Int,
        val overwritten: Int,
        val failed: Int
    ) {
        /** 拼接面向用户的中文结果文案 */
        fun toUserMessage(): String = buildString {
            if (added > 0) append("新增 ${added} 名学员")
            if (skipped > 0) {
                if (isNotEmpty()) append("，")
                append("跳过 ${skipped} 名同名学员")
            }
            if (overwritten > 0) {
                if (isNotEmpty()) append("，")
                append("更新 ${overwritten} 名学员")
            }
            if (failed > 0) {
                if (isNotEmpty()) append("，")
                append("${failed} 名学员字段异常被跳过")
            }
            if (isEmpty()) append("未发生变更")
        }
    }

    // === v21 全文检索（FTS4） ===

    /**
     * 全文检索学员：按姓名 / 学校 / 电话模糊搜索。
     *
     * 执行流程：
     * 1. 调用方传入原始关键词（如 "张三"、"实验"、"138"）。
     * 2. [escapeFtsQuery] 将关键词转义为安全的 FTS 表达式
     *    （`张三` → `"张三"`，避免被识别为 FTS 操作符）。
     * 3. 优先走 [StudentFtsDao.searchIds] 拿到命中学员姓名列表。
     * 4. 再通过 [StudentDao] 关联查询完整 [Student] 信息。
     *
     * 降级策略：
     * - 若 [ftsDao] 为空（旧调用方未注入）：走 [searchByLike] LIKE 兜底。
     * - 若 FTS 查询抛异常（索引损坏 / SQLite 错误）：捕获后走 LIKE 兜底，
     *   并通过 Logcat 警告，提示后续可调用 [rebuildFtsIndex] 重建。
     *
     * @param rawQuery 用户原始输入，无需手动转义。
     * @return 命中的活跃学员列表（按 createdAt 升序），无结果返回空列表。
     */
    suspend fun searchStudent(rawQuery: String): List<Student> {
        val query = rawQuery.trim()
        if (query.isEmpty()) return emptyList()

        val fts = ftsDao
        if (fts != null) {
            val ftsQuery = escapeFtsQuery(query)
            return try {
                val names = fts.searchIds(ftsQuery)
                if (names.isEmpty()) return emptyList()
                // 按 names 顺序逐个查询学员信息（学员数量通常 <1000，逐个查询可接受）
                // 若未来学员量爆炸，可改为 `WHERE name IN (:names)` 一次性查询
                names.mapNotNull { dao.getByName(it) }
            } catch (e: Exception) {
                android.util.Log.w("StudentRepository", "FTS 搜索失败，降级到 LIKE: ${e.message}")
                searchByLike(query)
            }
        }
        return searchByLike(query)
    }

    /**
     * LIKE 兜底搜索：在姓名 / 学校 / 电话字段做 `LIKE '%x%'` 模糊匹配。
     *
     * 仅在以下场景调用：
     * - [ftsDao] 未注入（旧调用方）；
     * - FTS 查询异常时降级。
     *
     * 性能：在 <1000 条学员规模下与 FTS 差异不明显，
     * 但学员量增长后明显劣于 FTS，建议尽量使用 FTS 路径。
     */
    private suspend fun searchByLike(query: String): List<Student> {
        val all = dao.getAll().first()
        val q = query.lowercase()
        return all.filter {
            it.name.lowercase().contains(q) ||
                it.school.lowercase().contains(q) ||
                it.phone.lowercase().contains(q)
        }
    }

    /**
     * 转义 FTS 查询字符串。
     *
     * FTS4 的 MATCH 操作符对特殊字符（`*` `"` `:` `(` `)` `-` 等）有特殊含义，
     * 直接把用户输入塞进 MATCH 子句会引发 "malformed MATCH expression" 错误。
     *
     * 策略：把整个关键词用双引号包裹，让 SQLite 当作一个短语整体匹配。
     * 例如 `张三` → `"张三"`，`北京 实验小学` → `"北京 实验小学"`。
     *
     * 如果关键词本身包含双引号，先转义为 `""`（FTS 转义约定）。
     */
    private fun escapeFtsQuery(raw: String): String {
        val escaped = raw.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    /**
     * 重建 FTS 索引（外部接口，用于数据恢复后修复损坏的索引）。
     *
     * 调用时机：
     * - [com.shangmentiyu.sportscoach.core.BackupManager] 数据恢复后怀疑索引损坏；
     * - 手动 SQL 维护学员表后索引可能不一致；
     * - 应用启动诊断发现 FTS 与主表 row 数不一致。
     *
     * 内部调用 [StudentFtsDao.rebuild]，由 FTS4 引擎自动重建倒排索引。
     */
    suspend fun rebuildFtsIndex() {
        ftsDao?.rebuild()
    }
}
