package com.shangmentiyu.sportscoach.di

import android.app.Application
import com.shangmentiyu.sportscoach.core.MomentUploader
import com.shangmentiyu.sportscoach.data.db.AppDatabase
import com.shangmentiyu.sportscoach.data.db.ArchivedLessonDao
import com.shangmentiyu.sportscoach.data.repo.AuditLogRepository
import com.shangmentiyu.sportscoach.data.repo.BackupRepository
import com.shangmentiyu.sportscoach.data.repo.BodyMetricRepository
import com.shangmentiyu.sportscoach.data.repo.CoachRepository
import com.shangmentiyu.sportscoach.data.repo.DietRepository
import com.shangmentiyu.sportscoach.data.repo.LessonPackageRepository
import com.shangmentiyu.sportscoach.data.repo.LessonRepository
import com.shangmentiyu.sportscoach.data.repo.OperationRepository
import com.shangmentiyu.sportscoach.data.repo.ParentReportRepository
import com.shangmentiyu.sportscoach.data.repo.ScheduleMemoryRepository
import com.shangmentiyu.sportscoach.data.repo.ScheduleQueryRepository
import com.shangmentiyu.sportscoach.data.repo.ScheduleRepository
import com.shangmentiyu.sportscoach.data.repo.ScriptRepository
import com.shangmentiyu.sportscoach.data.repo.SettingsRepository
import com.shangmentiyu.sportscoach.data.repo.StudentRepository
import com.shangmentiyu.sportscoach.data.repo.StageSummaryRepository
import com.shangmentiyu.sportscoach.data.repo.TrainingCycleRepository
import com.shangmentiyu.sportscoach.domain.scheduling.CalculateRemainingLessonsUseCase
import com.shangmentiyu.sportscoach.domain.scheduling.ValidateScheduleUseCase
import com.shangmentiyu.sportscoach.ui.analytics.AnalyticsViewModel
import com.shangmentiyu.sportscoach.ui.bodymetric.BodyMetricChartViewModel
import com.shangmentiyu.sportscoach.ui.coachreport.CoachDailyReportViewModel
import com.shangmentiyu.sportscoach.ui.dailyplan.DailyPlanViewModel
import com.shangmentiyu.sportscoach.ui.diet.DietViewModel
import com.shangmentiyu.sportscoach.ui.growth.GrowthViewModel
import com.shangmentiyu.sportscoach.ui.heightprediction.HeightPredictionViewModel
import com.shangmentiyu.sportscoach.ui.home.HomeViewModel
import com.shangmentiyu.sportscoach.ui.lesson.LessonViewModel
import com.shangmentiyu.sportscoach.ui.lessoncheckin.LessonCheckInViewModel
import com.shangmentiyu.sportscoach.ui.operation.OperationViewModel
import com.shangmentiyu.sportscoach.ui.parent.ParentReportViewModel
import com.shangmentiyu.sportscoach.ui.scoring.ScoringViewModel
import com.shangmentiyu.sportscoach.ui.script.ScriptViewModel
import com.shangmentiyu.sportscoach.ui.settings.SettingsViewModel
import com.shangmentiyu.sportscoach.ui.stagesummary.StageSummaryViewModel
import com.shangmentiyu.sportscoach.ui.summary.SummaryViewModel
import com.shangmentiyu.sportscoach.ui.training.TrainingPlanViewModel
import com.shangmentiyu.sportscoach.ui.trainingcycle.TrainingCycleViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.scope.get
import org.koin.dsl.module

/**
 * Koin 依赖注入模块（架构层四，v46）。
 *
 * 分层声明：数据源（DAO）→ 仓库（Repository）→ 领域用例（UseCase）→ 视图模型（ViewModel）。
 *
 * v46 全量迁移：全部 18 个 ViewModel 均由 Koin 提供，[AppViewModelFactory] 已删除，
 * 各页面通过 `koinViewModel()` 获取实例（唯一依赖注入入口）。
 */
val appModule = module {

    // === 数据层：数据库 + DAO ===
    single { AppDatabase.getDatabase(androidContext()) }
    single { get<AppDatabase>().studentDao() }
    single { get<AppDatabase>().lessonDao() }
    single { get<AppDatabase>().lessonPackageDao() }
    single { get<AppDatabase>().coachDao() }
    single { get<AppDatabase>().scheduleDao() }
    single { get<AppDatabase>().trainingCycleDao() }
    single { get<AppDatabase>().scheduleMemoryDao() }
    single { get<AppDatabase>().studentFtsDao() }
    single { get<AppDatabase>().parentReportDao() }
    single { get<AppDatabase>().bodyMetricHistoryDao() }
    single { get<AppDatabase>().dietDao() }
    single { get<AppDatabase>().archivedLessonDao() }
    single { get<AppDatabase>().auditLogDao() }

    // === 仓库层 ===
    single { AuditLogRepository(get(), get()) }
    single { StudentRepository(get(), get(), get(), get()) }
    single { LessonRepository(get()) }
    single { SettingsRepository(androidContext()) }
    single {
        OperationRepository(
            get(), get(), get(), get(),
            get<ArchivedLessonDao>(), get<AppDatabase>(),
            get<ScheduleRepository>(), get<ScheduleQueryRepository>(),
            get<TrainingCycleRepository>(), get<StageSummaryRepository>()
        )
    }
    single { LessonPackageRepository(get(), get(), get()) }
    single { CoachRepository(get()) }
    single { ScheduleRepository(get(), get()) }
    single { ScheduleQueryRepository(get(), get(), get(), get(), get()) }
    single { TrainingCycleRepository(get()) }
    single { StageSummaryRepository() }
    single { ScheduleMemoryRepository(get()) }
    single { ParentReportRepository(get(), get(), get()) }
    single { BodyMetricRepository(get(), get()) }
    single { DietRepository(get()) }
    single { BackupRepository(androidContext()) }
    single { MomentUploader(androidContext(), get()) }
    single { ScriptRepository(androidContext()) }

    // === 领域层 ===
    // 修复闪退：ValidateScheduleUseCase 构造参数是接口 ScheduleValidationSource，
    // Koin 按精确类型解析注册表内无该接口定义，会抛 NoBeanDefFoundException 闪退；
    // 显式注入实现类 OperationRepository（其实现该接口，双通道查询逻辑唯一）
    single { ValidateScheduleUseCase(get<OperationRepository>()) }
    single { CalculateRemainingLessonsUseCase(get()) }

    // === 视图模型层（v46 架构层四：全量迁移到 Koin） ===
    viewModel { SettingsViewModel(androidContext() as Application, get(), get(), get(), get(), get(), get()) }
    viewModel { HomeViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { ScoringViewModel(get(), get()) }
    viewModel { LessonViewModel(get(), get(), get(), get()) }
    viewModel { SummaryViewModel(get(), get()) }
    viewModel { GrowthViewModel(get(), get(), get(), get()) }
    viewModel { TrainingPlanViewModel(get(), get()) }
    viewModel { AnalyticsViewModel(get(), get()) }
    viewModel { OperationViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { ParentReportViewModel(get(), get()) }
    viewModel { StageSummaryViewModel(get(), get(), get()) }
    viewModel { DailyPlanViewModel(get(), get()) }
    viewModel { TrainingCycleViewModel(get(), get()) }
    viewModel { BodyMetricChartViewModel(get(), get()) }
    viewModel { CoachDailyReportViewModel(get(), get(), get()) }
    viewModel { LessonCheckInViewModel(get(), get(), get()) }
    viewModel { HeightPredictionViewModel(get()) }
    viewModel { DietViewModel(get(), get()) }
    viewModel { ScriptViewModel(get()) }
}
