package com.shangmentiyu.sportscoach.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.repo.BackupRepository
import com.shangmentiyu.sportscoach.data.repo.LessonRepository
import com.shangmentiyu.sportscoach.data.repo.OperationRepository
import com.shangmentiyu.sportscoach.data.repo.ParentReportRepository
import com.shangmentiyu.sportscoach.data.repo.ScheduleRepository
import com.shangmentiyu.sportscoach.data.repo.SettingsRepository
import com.shangmentiyu.sportscoach.data.repo.StudentRepository

/**
 * 应用级 ViewModel 工厂，统一注入数据库与仓库依赖。
 */
class AppViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    // v26 优化1：操作日志 Repository（审计溯源）—— 放在 studentRepo 之前，
    // 因 studentRepo 在 lazy 初始化时引用本属性
    val auditLogRepo: com.shangmentiyu.sportscoach.data.repo.AuditLogRepository by lazy {
        val db = AppDatabase.getDatabase(app)
        com.shangmentiyu.sportscoach.data.repo.AuditLogRepository(db, db.auditLogDao())
    }
    val studentRepo: StudentRepository by lazy {
        val db = AppDatabase.getDatabase(app)
        // v20：传入 db 实例，启用 renameStudentCascade 跨表事务级联
        // v21：传入 studentFtsDao，启用 searchStudent FTS 全文检索（不可用时自动降级 LIKE）
        // v26 优化1：传入 auditLogRepo，启用关键操作日志记录
        StudentRepository(db.studentDao(), db, db.studentFtsDao(), auditLogRepo)
    }
    val lessonRepo: LessonRepository by lazy {
        LessonRepository(AppDatabase.getDatabase(app).lessonDao())
    }
    val settingsRepo: SettingsRepository by lazy {
        SettingsRepository(app)
    }
    val opRepo: OperationRepository by lazy {
        val db = AppDatabase.getDatabase(app)
        OperationRepository(
            db.lessonPackageDao(), db.coachDao(), db.scheduleDao(),
            db.trainingCycleDao(), db.lessonDao(),
            // v22 新增：归档 DAO + DB 实例，启用 archiveLessonsBefore 冷热归档能力
            db.archivedLessonDao(), db
        )
    }
    // v21 拆分：课时包与教练从 OperationRepository 独立出来，单一职责
    // v44：注入 db + scheduleDao，启用删除课时包时级联清理排课
    val pkgRepo: com.shangmentiyu.sportscoach.data.repo.LessonPackageRepository by lazy {
        val db = AppDatabase.getDatabase(app)
        com.shangmentiyu.sportscoach.data.repo.LessonPackageRepository(
            db.lessonPackageDao(), db, db.scheduleDao()
        )
    }
    val coachRepo: com.shangmentiyu.sportscoach.data.repo.CoachRepository by lazy {
        com.shangmentiyu.sportscoach.data.repo.CoachRepository(AppDatabase.getDatabase(app).coachDao())
    }
    val parentReportRepo: ParentReportRepository by lazy {
        val db = AppDatabase.getDatabase(app)
        ParentReportRepository(db.parentReportDao(), studentRepo, lessonRepo)
    }
    val bodyMetricRepo: com.shangmentiyu.sportscoach.data.repo.BodyMetricRepository by lazy {
        val db = AppDatabase.getDatabase(app)
        com.shangmentiyu.sportscoach.data.repo.BodyMetricRepository(db.bodyMetricHistoryDao(), studentRepo)
    }
    val scheduleRepo: com.shangmentiyu.sportscoach.data.repo.ScheduleRepository by lazy {
        // v33：注入 lessonDao，启用 clearUnfinishedPastLongTermLessons 清理功能
        val db = AppDatabase.getDatabase(app)
        ScheduleRepository(db.scheduleDao(), db.lessonDao())
    }
    val memoryRepo: com.shangmentiyu.sportscoach.data.repo.ScheduleMemoryRepository by lazy {
        com.shangmentiyu.sportscoach.data.repo.ScheduleMemoryRepository(AppDatabase.getDatabase(app).scheduleMemoryDao())
    }
    val backupRepo: BackupRepository by lazy {
        BackupRepository(app)
    }
    /**
     * === v5 新增：精彩瞬间上传器（手机→PC 双向传输） ===
     *
     * 调用桌面端 lan_plan_sender.py 的 POST /upload_moment 端点，
     * 复用 [settingsRepo] 中的 PC 端 IP/Port/Token 配置。
     */
    val momentUploader: com.shangmentiyu.sportscoach.core.MomentUploader by lazy {
        com.shangmentiyu.sportscoach.core.MomentUploader(app, settingsRepo)
    }
    val dietRepo: com.shangmentiyu.sportscoach.data.repo.DietRepository by lazy {
        com.shangmentiyu.sportscoach.data.repo.DietRepository(AppDatabase.getDatabase(app).dietDao())
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(com.shangmentiyu.sportscoach.ui.home.HomeViewModel::class.java) ->
                com.shangmentiyu.sportscoach.ui.home.HomeViewModel(studentRepo, lessonRepo, opRepo, AppDatabase.getDatabase(app), settingsRepo, momentUploader) as T
            modelClass.isAssignableFrom(com.shangmentiyu.sportscoach.ui.scoring.ScoringViewModel::class.java) ->
                com.shangmentiyu.sportscoach.ui.scoring.ScoringViewModel(lessonRepo, studentRepo) as T
            modelClass.isAssignableFrom(com.shangmentiyu.sportscoach.ui.lesson.LessonViewModel::class.java) ->
                com.shangmentiyu.sportscoach.ui.lesson.LessonViewModel(lessonRepo, opRepo) as T
            modelClass.isAssignableFrom(com.shangmentiyu.sportscoach.ui.summary.SummaryViewModel::class.java) ->
                com.shangmentiyu.sportscoach.ui.summary.SummaryViewModel(lessonRepo, studentRepo) as T
            modelClass.isAssignableFrom(com.shangmentiyu.sportscoach.ui.settings.SettingsViewModel::class.java) ->
                com.shangmentiyu.sportscoach.ui.settings.SettingsViewModel(app, lessonRepo, studentRepo, settingsRepo, backupRepo, auditLogRepo) as T
            modelClass.isAssignableFrom(com.shangmentiyu.sportscoach.ui.growth.GrowthViewModel::class.java) ->
                com.shangmentiyu.sportscoach.ui.growth.GrowthViewModel(studentRepo, lessonRepo, opRepo, bodyMetricRepo) as T
            modelClass.isAssignableFrom(com.shangmentiyu.sportscoach.ui.training.TrainingPlanViewModel::class.java) ->
                com.shangmentiyu.sportscoach.ui.training.TrainingPlanViewModel(studentRepo, lessonRepo) as T
            modelClass.isAssignableFrom(com.shangmentiyu.sportscoach.ui.analytics.AnalyticsViewModel::class.java) ->
                com.shangmentiyu.sportscoach.ui.analytics.AnalyticsViewModel(studentRepo, lessonRepo) as T
            modelClass.isAssignableFrom(com.shangmentiyu.sportscoach.ui.operation.OperationViewModel::class.java) ->
                com.shangmentiyu.sportscoach.ui.operation.OperationViewModel(opRepo, studentRepo, scheduleRepo, memoryRepo, pkgRepo, coachRepo) as T
            modelClass.isAssignableFrom(com.shangmentiyu.sportscoach.ui.parent.ParentReportViewModel::class.java) ->
                com.shangmentiyu.sportscoach.ui.parent.ParentReportViewModel(parentReportRepo, studentRepo) as T
            modelClass.isAssignableFrom(com.shangmentiyu.sportscoach.ui.stagesummary.StageSummaryViewModel::class.java) ->
                com.shangmentiyu.sportscoach.ui.stagesummary.StageSummaryViewModel(studentRepo, lessonRepo, opRepo) as T
            modelClass.isAssignableFrom(com.shangmentiyu.sportscoach.ui.dailyplan.DailyPlanViewModel::class.java) ->
                com.shangmentiyu.sportscoach.ui.dailyplan.DailyPlanViewModel(lessonRepo, opRepo) as T
            modelClass.isAssignableFrom(com.shangmentiyu.sportscoach.ui.trainingcycle.TrainingCycleViewModel::class.java) ->
                com.shangmentiyu.sportscoach.ui.trainingcycle.TrainingCycleViewModel(opRepo, studentRepo) as T
            modelClass.isAssignableFrom(com.shangmentiyu.sportscoach.ui.bodymetric.BodyMetricChartViewModel::class.java) ->
                com.shangmentiyu.sportscoach.ui.bodymetric.BodyMetricChartViewModel(bodyMetricRepo, studentRepo) as T
            modelClass.isAssignableFrom(com.shangmentiyu.sportscoach.ui.coachreport.CoachDailyReportViewModel::class.java) ->
                com.shangmentiyu.sportscoach.ui.coachreport.CoachDailyReportViewModel(lessonRepo, opRepo, studentRepo) as T
            modelClass.isAssignableFrom(com.shangmentiyu.sportscoach.ui.lessoncheckin.LessonCheckInViewModel::class.java) ->
                com.shangmentiyu.sportscoach.ui.lessoncheckin.LessonCheckInViewModel(studentRepo, lessonRepo, opRepo, AppDatabase.getDatabase(app)) as T
            modelClass.isAssignableFrom(com.shangmentiyu.sportscoach.ui.heightprediction.HeightPredictionViewModel::class.java) ->
                com.shangmentiyu.sportscoach.ui.heightprediction.HeightPredictionViewModel(studentRepo) as T
            modelClass.isAssignableFrom(com.shangmentiyu.sportscoach.ui.diet.DietViewModel::class.java) ->
                com.shangmentiyu.sportscoach.ui.diet.DietViewModel(dietRepo, studentRepo) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
