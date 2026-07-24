package com.shangmentiyu.sportscoach.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.shangmentiyu.sportscoach.data.db.AppDatabase
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
    val studentRepo: StudentRepository by lazy {
        StudentRepository(AppDatabase.getDatabase(app).studentDao())
    }
    val lessonRepo: LessonRepository by lazy {
        LessonRepository(AppDatabase.getDatabase(app).lessonDao())
    }
    val settingsRepo: SettingsRepository by lazy {
        SettingsRepository(app)
    }
    val opRepo: OperationRepository by lazy {
        val db = AppDatabase.getDatabase(app)
        OperationRepository(db.lessonPackageDao(), db.coachDao(), db.scheduleDao(), db.trainingCycleDao(), db.lessonDao())
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
        ScheduleRepository(AppDatabase.getDatabase(app).scheduleDao())
    }
    val memoryRepo: com.shangmentiyu.sportscoach.data.repo.ScheduleMemoryRepository by lazy {
        com.shangmentiyu.sportscoach.data.repo.ScheduleMemoryRepository(AppDatabase.getDatabase(app).scheduleMemoryDao())
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(com.shangmentiyu.sportscoach.ui.home.HomeViewModel::class.java) ->
                com.shangmentiyu.sportscoach.ui.home.HomeViewModel(studentRepo, lessonRepo, opRepo, AppDatabase.getDatabase(app)) as T
            modelClass.isAssignableFrom(com.shangmentiyu.sportscoach.ui.scoring.ScoringViewModel::class.java) ->
                com.shangmentiyu.sportscoach.ui.scoring.ScoringViewModel(lessonRepo, studentRepo) as T
            modelClass.isAssignableFrom(com.shangmentiyu.sportscoach.ui.lesson.LessonViewModel::class.java) ->
                com.shangmentiyu.sportscoach.ui.lesson.LessonViewModel(lessonRepo, opRepo) as T
            modelClass.isAssignableFrom(com.shangmentiyu.sportscoach.ui.summary.SummaryViewModel::class.java) ->
                com.shangmentiyu.sportscoach.ui.summary.SummaryViewModel(lessonRepo, studentRepo) as T
            modelClass.isAssignableFrom(com.shangmentiyu.sportscoach.ui.settings.SettingsViewModel::class.java) ->
                com.shangmentiyu.sportscoach.ui.settings.SettingsViewModel(app, lessonRepo, studentRepo, settingsRepo) as T
            modelClass.isAssignableFrom(com.shangmentiyu.sportscoach.ui.growth.GrowthViewModel::class.java) ->
                com.shangmentiyu.sportscoach.ui.growth.GrowthViewModel(studentRepo, lessonRepo) as T
            modelClass.isAssignableFrom(com.shangmentiyu.sportscoach.ui.training.TrainingPlanViewModel::class.java) ->
                com.shangmentiyu.sportscoach.ui.training.TrainingPlanViewModel(studentRepo, lessonRepo) as T
            modelClass.isAssignableFrom(com.shangmentiyu.sportscoach.ui.analytics.AnalyticsViewModel::class.java) ->
                com.shangmentiyu.sportscoach.ui.analytics.AnalyticsViewModel(studentRepo, lessonRepo) as T
            modelClass.isAssignableFrom(com.shangmentiyu.sportscoach.ui.operation.OperationViewModel::class.java) ->
                com.shangmentiyu.sportscoach.ui.operation.OperationViewModel(opRepo, studentRepo, scheduleRepo, memoryRepo) as T
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
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
