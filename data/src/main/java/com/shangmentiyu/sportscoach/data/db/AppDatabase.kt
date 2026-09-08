package com.shangmentiyu.sportscoach.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.shangmentiyu.sportscoach.data.model.ArchivedLesson
import com.shangmentiyu.sportscoach.data.model.AuditLogEntity
import com.shangmentiyu.sportscoach.data.model.BodyMetricHistory
import com.shangmentiyu.sportscoach.data.model.Coach
import com.shangmentiyu.sportscoach.data.model.CoachPayout
import com.shangmentiyu.sportscoach.data.model.CoachPayoutRequest
import com.shangmentiyu.sportscoach.data.model.CoachSchedule
import com.shangmentiyu.sportscoach.data.model.CoachStudentBinding
import com.shangmentiyu.sportscoach.data.model.DietTemplateEntity
import com.shangmentiyu.sportscoach.data.model.FeeRecord
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.LessonPackage
import com.shangmentiyu.sportscoach.data.model.ParentReport
import com.shangmentiyu.sportscoach.data.model.PcSyncState
// PlanImage 仍被 PreClassScheduleCard / PreClassTab 引用（电脑端训练计划截图展示），
// 并非死代码，保留实体与 DAO。
import com.shangmentiyu.sportscoach.data.model.PlanImage
import com.shangmentiyu.sportscoach.data.model.Schedule
import com.shangmentiyu.sportscoach.data.model.ScheduleMemory
import com.shangmentiyu.sportscoach.data.model.SignInRecord
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.data.model.StudentDietRecord
import com.shangmentiyu.sportscoach.data.model.StudentFts
import com.shangmentiyu.sportscoach.data.model.TrainingCycle

@Database(
    entities = [Student::class, Lesson::class, LessonPackage::class, Coach::class, Schedule::class, ParentReport::class, TrainingCycle::class, BodyMetricHistory::class, ScheduleMemory::class, DietTemplateEntity::class, StudentDietRecord::class, StudentFts::class, ArchivedLesson::class, AuditLogEntity::class, PlanImage::class, SignInRecord::class, CoachSchedule::class, CoachPayout::class, CoachPayoutRequest::class, CoachStudentBinding::class, FeeRecord::class, PcSyncState::class],
    version = 35,
    exportSchema = true
)
@TypeConverters(com.shangmentiyu.sportscoach.data.model.Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun studentFtsDao(): StudentFtsDao
    abstract fun lessonDao(): LessonDao
    abstract fun lessonPackageDao(): LessonPackageDao
    abstract fun coachDao(): CoachDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun parentReportDao(): ParentReportDao
    abstract fun trainingCycleDao(): TrainingCycleDao
    abstract fun bodyMetricHistoryDao(): BodyMetricHistoryDao
    abstract fun scheduleMemoryDao(): ScheduleMemoryDao
    abstract fun dietDao(): DietDao
    /** v22 新增：归档课时 DAO（冷数据访问） */
    abstract fun archivedLessonDao(): ArchivedLessonDao
    /** v26 优化1 新增：操作日志 DAO（审计溯源） */
    abstract fun auditLogDao(): AuditLogDao
    /** v25 新增：训练计划图片 DAO（电脑端截图推送，PreClassScheduleCard 展示） */
    abstract fun planImageDao(): PlanImageDao
    /** v32 新增：签到记录 DAO（排课与签到分离 + 防重） */
    abstract fun signInDao(): SignInDao
    /** v33 新增：教练管理模块 DAO（可上课时段 / 薪资结算 / 提现申请 / 工作量统计） */
    abstract fun coachScheduleDao(): CoachScheduleDao
    abstract fun coachPayoutDao(): CoachPayoutDao
    abstract fun coachPayoutRequestDao(): CoachPayoutRequestDao
    abstract fun coachWorkloadDao(): CoachWorkloadDao
    /** v34 新增：教练-学员绑定 DAO */
    abstract fun coachStudentBindingDao(): CoachStudentBindingDao
    /** v35 新增：PC 收费记录镜像 + PC 消课对账状态 DAO（双端数据真统一） */
    abstract fun feeRecordDao(): FeeRecordDao
    abstract fun pcSyncStateDao(): PcSyncStateDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // === 终极防丢机制：打开数据库前先做启动前避风港备份 + 版本检查 ===
                // 必须在 Room.databaseBuilder().build() 之前执行：
                // 1. backupIfDbExists：复制当前 db 文件到 filesDir/PreUpdateBackup/，保留最近 3 份
                // 2. checkVersionAndEmergencyBackup：若 db 文件版本 > 代码版本（降级场景），
                //    生成急救备份并抛 RuntimeException 让 App 闪退，避免 Room 清库
                // 这两步确保即使后续 Room 打开失败，也有一份"启动前"的完整数据库可恢复
                com.shangmentiyu.sportscoach.data.internal.PreUpdateBackupManager
                    .backupIfDbExists(context.applicationContext)
                com.shangmentiyu.sportscoach.data.internal.PreUpdateBackupManager
                    .checkVersionAndEmergencyBackup(context.applicationContext, DATABASE_VERSION)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    activeDatabaseName()
                )
                    .addMigrations(*AppDatabaseMigrations.ALL)
                    .addCallback(AppDatabaseMigrations.DB_CALLBACK)
                    // === 终极防丢机制：严禁任何破坏性清库 fallback ===
                    // 历史教训：fallbackToDestructiveMigrationOnDowngrade() 在数据库文件版本
                    // 大于代码版本时，会直接删除整个数据库重建，导致学员数据全部丢失。
                    // 现在移除所有 destructive fallback：
                    // - 升级无 migration → Room 抛 IllegalStateException（App 闪退，数据不丢）
                    // - 降级 → Room 抛 IllegalStateException（App 闪退，数据不丢）
                    // 启动前的避风港备份已确保即使闪退，数据也可从 filesDir/PreUpdateBackup/ 恢复
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * 当前模式对应的数据库文件名（v23.12 多租户·物理隔离）。
         *
         * 上门体育 = sports_coach_db（既有库）；俱乐部 = sports_coach_club_db（独立库）。
         * 两个库共用同一套 Entity/DAO/Migration（schema 相同，version 一起走）。
         * 选库依据 [ModeManager.activeMode] 必须在首次 getDatabase 前由
         * Application.onCreate 同步初始化。
         */
        fun activeDatabaseName(): String =
            if (com.shangmentiyu.sportscoach.data.internal.ModeManager.activeMode ==
                com.shangmentiyu.sportscoach.data.internal.ModeManager.MODE_CLUB) {
                CLUB_DATABASE_NAME
            } else {
                DATABASE_NAME
            }

        /**
         * 关闭并重置数据库单例（仅用于整库备份/恢复流程）。
         *
         * 调用时机：
         * - 备份：在复制数据库文件前调用，确保所有 WAL 日志刷盘，避免备份到不完整的数据
         * - 恢复：在覆盖数据库文件前调用，释放文件锁，避免 "database is locked" 错误
         *
         * 调用后下次访问 [getDatabase] 会重新创建实例并打开数据库连接。
         * 注意：调用后所有持有旧 Dao / Repository 引用的 ViewModel 都会失效，
         * 恢复数据后必须重启 App 让 ViewModel 重新初始化。
         *
         * @param context 上下文（用于触发 WAL checkpoint）
         */
        fun closeAndResetInstance(context: Context) {
            synchronized(this) {
                INSTANCE?.let { db ->
                    try {
                        // 强制将 WAL 日志写入主数据库文件，确保备份/覆盖的是完整数据
                        // 使用 raw query 触发 checkpoint，避免备份到残缺数据导致学员/课程丢失
                        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
                    } catch (e: Exception) {
                        // checkpoint 失败不阻断流程，仍尝试关闭，避免文件锁死
                    }
                    try {
                        db.close()
                    } catch (e: Exception) {
                        // 忽略关闭异常，单例仍需重置
                    }
                }
                INSTANCE = null
            }
        }

        /**
         * 数据库文件名（用于备份/恢复定位文件）。
         * Room 默认会在 databasePath 下生成 <dbName>、<dbName>-wal、<dbName>-shm 三个文件。
         */
        const val DATABASE_NAME = "sports_coach_db"

        /** 俱乐部模式专用库（v23.12 物理隔离；schema 与主库完全一致） */
        const val CLUB_DATABASE_NAME = "sports_coach_club_db"

        /**
         * 当前代码声明的数据库版本（与 @Database version 保持一致）。
         *
         * 用于在 [com.shangmentiyu.sportscoach.data.internal.PreUpdateBackupManager.checkVersionAndEmergencyBackup]
         * 中与数据库文件实际版本对比，检测降级场景；
         * 也用于 [com.shangmentiyu.sportscoach.data.internal.BackupManager] 恢复前
         * 对比备份库版本，拒绝来自更高版本 App 的备份，防止恢复后闪退。
         *
         * 修改 @Database version 时必须同步修改此常量，否则版本检查会失效。
         */
        const val DATABASE_VERSION = 35
    }
}
