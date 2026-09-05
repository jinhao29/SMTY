package com.shangmentiyu.sportscoach.data.repo

import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.db.FeeRecordDao
import com.shangmentiyu.sportscoach.data.model.FeeRecord
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import com.shangmentiyu.sportscoach.data.model.PcSyncState
import com.shangmentiyu.sportscoach.domain.sync.ConsumptionReconciler
import com.shangmentiyu.sportscoach.domain.sync.PackageReconciler
import kotlinx.coroutines.flow.Flow

/** PC 端下发的课时包汇总（/sync/pc_data.json packages[]）。 */
data class PcPackage(
    val studentName: String,
    val totalLessons: Int,
    val usedLessons: Int   // PC 口径的「已上课时」（明细推导）
)

/** PC 端下发的课时明细行（/sync/pc_data.json lessons[]）。 */
data class PcLesson(
    val studentName: String,
    val date: String,
    val count: Int,
    val content: String,
    val note: String
)

/** PC 端下发的收费记录（/sync/pc_data.json fees[]）。 */
data class PcFee(
    val studentName: String,
    val date: String,
    val amount: Double,
    val hours: Double,
    val method: String,
    val note: String
)

/**
 * PC 数据对账仓储（v35，双端数据真统一）。
 *
 * 应用 PC 端总包（/sync/pc_data.json）到手机 Room 库，三步全部走
 * 「合并/单调」语义，绝不删除手机端任何数据：
 * 1. 课时包总量对账：[PackageReconciler]（单包精确设置 / 多包正差值新建，
 *    安全锁拒绝缩减与低于已用的覆盖）；
 * 2. PC 消课差值对账：[ConsumptionReconciler]（PC 独录消课折算进手机课时包
 *    已用，进度存 pc_sync_state，幂等且单调）；
 * 3. 收费记录镜像：自然键摘要主键幂等 upsert（只读展示，删除不传播）。
 *
 * 所有方法为阻塞实现，调用方须在 Dispatchers.IO 上执行（与 v23.7.2 阻塞 DAO
 * 约定一致：suspend DAO 会把外部取消传播进写库链）。
 */
class PcSyncRepository(private val db: AppDatabase) {

    data class Report(
        val pkgAdded: Int = 0,
        val pkgUpdated: Int = 0,
        val pkgSkipped: List<String> = emptyList(),
        val consumedUnits: Int = 0,
        val feeCount: Int = 0
    ) {
        fun toUserMessage(): String {
            val parts = mutableListOf<String>()
            if (pkgAdded > 0) parts.add("课时包新增 $pkgAdded")
            if (pkgUpdated > 0) parts.add("课时包更新 $pkgUpdated")
            if (consumedUnits > 0) parts.add("PC 消课折算 $consumedUnits 节")
            if (feeCount > 0) parts.add("收费记录 $feeCount 笔")
            return if (parts.isEmpty()) "" else parts.joinToString(" · ")
        }
    }

    /**
     * 应用 PC 数据总包。必须在 Dispatchers.IO 调用。
     *
     * @param today 新建课时包的购买日期（YYYY-MM-DD）
     */
    fun applyPcDataBlocking(
        packages: List<PcPackage>,
        lessons: List<PcLesson>,
        fees: List<PcFee>,
        today: String
    ): Report {
        val pkgDao = db.lessonPackageDao()
        val stateDao = db.pcSyncStateDao()
        var pkgAdded = 0
        var pkgUpdated = 0
        val skipped = mutableListOf<String>()
        var consumedUnits = 0

        // PC 单汇总模型：每学员一条；按名去重防御异常数据
        val pcByName = packages.groupBy { it.studentName.trim() }
        for ((name, pcList) in pcByName) {
            if (name.isBlank()) continue
            val pc = pcList.first()

            // 1) 课时包总量对账
            val local = pkgDao.getAllByStudentBlocking(name)
            val r = PackageReconciler.reconcile(name, pc.totalLessons, pc.usedLessons, local, today)
            for (st in r.setTotals) {
                if (pkgDao.updateBlocking(st.pkg.copy(totalLessons = st.newTotal)) > 0) pkgUpdated++
            }
            for (c in r.creates) {
                pkgDao.insertBlocking(
                    LessonPackage(
                        studentName = c.studentName,
                        name = "PC 同步",
                        totalLessons = c.total,
                        usedLessons = 0,
                        purchaseDate = today,
                        note = "PC 端同步（课时记录汇总）"
                    )
                )
                pkgAdded++
            }
            skipped += r.skipped

            // 2) PC 消课差值对账（baseline 口径 = 手机全部课时行，热+冷，
            //    与 PC meta 导出「全部课时行」一致；体验课/占位两端同增相消）
            val state = stateDao.getBlocking(name)
            val applied = state?.appliedPcLessons ?: 0
            val phoneCount = db.lessonDao().countByStudentBlocking(name) +
                db.archivedLessonDao().countByStudentBlocking(name)
            val cr = ConsumptionReconciler.reconcile(pc.usedLessons, phoneCount, applied)
            if (cr.unitsToApply > 0) {
                // 落到最近创建的活跃包（getAllByStudentBlocking 按 createdAt 降序），
                // 按剩余课时截断，绝不允许 total < used（安全锁 3）
                val targetPkg = pkgDao.getAllByStudentBlocking(name)
                    .firstOrNull { it.status == "活跃" }
                if (targetPkg != null) {
                    val actual = cr.unitsToApply.coerceAtMost(targetPkg.remainingLessons)
                    if (actual > 0) {
                        pkgDao.updateBlocking(
                            targetPkg.copy(usedLessons = targetPkg.usedLessons + actual)
                        )
                        consumedUnits += actual
                    }
                    // 无论是否截断，只记录实加进度：剩余差值下个同步周期继续补
                    val newState = (state?.appliedPcLessons ?: 0) + actual
                    stateDao.upsertBlocking(
                        PcSyncState(studentName = name, appliedPcLessons = newState)
                    )
                }
                // 无活跃包时不推进进度：教练在手机续包后下个周期自动补齐
            }
        }

        // 3) 收费记录镜像（幂等 upsert，删除不传播）
        if (fees.isNotEmpty()) {
            val now = System.currentTimeMillis()
            db.feeRecordDao().upsertBlocking(
                fees.mapNotNull { f ->
                    val name = f.studentName.trim()
                    if (name.isBlank()) return@mapNotNull null
                    FeeRecord(
                        id = FeeRecord.stableKey(name, f.date, f.amount, f.hours, f.method, f.note),
                        studentName = name,
                        date = f.date,
                        amount = f.amount,
                        hours = f.hours,
                        method = f.method,
                        note = f.note,
                        syncedAt = now
                    )
                }
            )
        }

        return Report(pkgAdded, pkgUpdated, skipped, consumedUnits, fees.size)
    }
}

/**
 * PC 收费记录镜像只读仓储（v35，课时管理页「PC 收费记录」区块展示用）。
 */
class FeeRecordRepository(private val dao: FeeRecordDao) {
    fun getAll(): Flow<List<FeeRecord>> = dao.getAll()
    fun getByStudent(name: String): Flow<List<FeeRecord>> = dao.getByStudent(name)
}
