package com.shangmentiyu.sportscoach.ui

/**
 * 路由常量集中声明。
 *
 * 底部 Tab 路由：[HOME]、[SCORE]、[SETTINGS]。
 * 其余均为二级页面路由，通过 NavController.navigate 跳转。
 */
object Routes {
    // === 启动板块选择（v60：多板块入口） ===
    const val STARTUP = "startup"

    // === EVOLVE 俱乐部板块（v24 俱乐部真实 UI） ===
    // 排课 Tab 直接复用 Routes.SCHEDULE、教练管理复用 COACH_MANAGE、签到复用
    // LESSON_CHECKIN——这些页面全部经 AppDatabase.getDatabase() 取数，
    // 俱乐部模式下自动落到 sports_coach_club_db，无需 Club 前缀副本。
    const val CLUB_HOME = "club_home"
    const val CLUB_STUDENTS = "club_students"
    const val CLUB_LESSONS = "club_lessons"
    const val CLUB_SETTINGS = "club_settings"

    // === 底部 Tab ===
    const val HOME = "home"
    const val SCORE = "score"
    const val SETTINGS = "settings"

    // === 二级页面 ===
    const val LESSON = "lesson/{lessonId}"
    const val SUMMARY = "summary/{lessonId}"
    const val ADD_STUDENT = "add_student"
    const val EDIT_STUDENT = "edit_student/{studentName}"
    const val GROWTH = "growth/{studentName}"
    const val TRAINING_PLAN = "training_plan/{studentName}"
    const val HEIGHT_PREDICTION = "height_prediction/{studentName}"
    const val DIET_MANAGE = "diet_manage/{studentName}"

    // === 训练规划类（设置详情页二级入口） ===
    const val STAGE_SUMMARY = "stage_summary"
    const val TRAINING_CYCLE = "training_cycle"
    const val BODY_METRIC = "body_metric"
    const val COACH_REPORT = "coach_report"

    // === 工具类 ===
    const val BMI_CALCULATOR = "bmi_calculator"

    // === 教练管理（设置页二级入口：档案/排班/团队/薪资） ===
    const val COACH_MANAGE = "coach_manage"

    // === 体育中考标准 ===
    const val SPORT_CATEGORY = "sport_category"
    const val SPORT_STANDARD_DETAIL = "sport_standard_detail/{sportId}"

    // === 话术管理（设置页二级入口） ===
    const val SCRIPT_LIST = "script_list"
    const val SCRIPT_DETAIL = "script_detail/{scriptId}"

    fun scriptDetail(scriptId: String?) = if (scriptId.isNullOrBlank()) "script_detail/new" else "script_detail/$scriptId"

    // === 排课/签到 ===
    const val LESSON_CHECKIN = "lesson_checkin"

    /** 签到页路由模式：带可选 filter 查询参数（"unsigned_out" = 未签退筛选模式） */
    const val LESSON_CHECKIN_PATTERN = "lesson_checkin?filter={filter}"

    /**
     * 签到页跳转路由。
     *
     * @param filterUnsignedOut true 时附加 filter=unsigned_out，
     *        签到页进入"未签退筛选"模式（首页忘记签退提醒卡片跳转入口）
     */
    fun lessonCheckIn(filterUnsignedOut: Boolean = false) =
        if (filterUnsignedOut) "lesson_checkin?filter=unsigned_out" else LESSON_CHECKIN

    const val SCHEDULE = "schedule"

    // === 成绩录入（带 lessonId 关联） ===
    const val SCORING_WITH_LESSON = "scoring/{lessonId}"

    fun lesson(id: String) = "lesson/$id"
    fun summary(id: String) = "summary/$id"
    fun scoringWithLesson(id: String) = "scoring/$id"
    fun growth(studentName: String) = "growth/$studentName"
    fun trainingPlan(studentName: String) = "training_plan/$studentName"
    fun heightPrediction(studentName: String) = "height_prediction/$studentName"
    fun dietManage(studentName: String) = "diet_manage/$studentName"
    fun editStudent(studentName: String) = "edit_student/$studentName"
    fun sportStandardDetail(sportId: String) = "sport_standard_detail/$sportId"
}
